package com.kylin.plsql.core.format.plsql.builder;

import com.kylin.plsql.core.format.plsql.dialect.PlSqlDialect;
import com.kylin.plsql.core.format.plsql.model.*;
import com.kylin.plsql.core.parser.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.*;

public class ParseTreeModelBuilder extends PlSqlParserBaseVisitor<Void> {

    private List<TokenInfo> tokens;
    private final List<PlSqlBlock> topLevelBlocks = new ArrayList<>();
    private final Deque<PlSqlBlock> blockStack = new ArrayDeque<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private PlSqlDialect dialect;

    public PlSqlModel build(String source, PlSqlDialect dialect) {
        this.dialect = dialect;
        this.diagnostics.clear();
        this.topLevelBlocks.clear();
        this.blockStack.clear();

        if (source == null || source.isBlank()) {
            return new PlSqlModel(source, new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(), dialect.getName(), true);
        }

        try {
            var input = CharStreams.fromString(source);
            var lexer = new PlSqlLexer(input);
            var tokenStream = new CommonTokenStream(lexer);
            tokenStream.fill();

            this.tokens = buildTokenInfoList(tokenStream.getTokens(), dialect);

            var parser = new PlSqlParser(tokenStream);
            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                        int line, int charPositionInLine, String msg, RecognitionException e) {
                    diagnostics.add(new Diagnostic(
                            Diagnostic.Severity.WARNING, Diagnostic.Code.UNEXPECTED_KEYWORD,
                            line, charPositionInLine, "语法解析: " + msg, ""));
                }
            });

            var tree = parser.sql_script();
            this.visit(tree);

            while (!blockStack.isEmpty()) {
                PlSqlBlock block = blockStack.pop();
                if (block.endTokenIdx < 0) {
                    block.endTokenIdx = tokens.size() - 1;
                    diagnostics.add(new Diagnostic(
                            Diagnostic.Severity.WARNING,
                            getUnclosedCode(block.type),
                            tokens.get(Math.max(0, block.startTokenIdx)).line,
                            tokens.get(Math.max(0, block.startTokenIdx)).column,
                            block.type + " 块未闭合"
                                    + (block.blockName != null ? " (" + block.blockName + ")" : ""),
                            "自动补全 END"));
                }
            }

            fillBlockContent(topLevelBlocks);

        } catch (Exception e) {
            this.tokens = new ArrayList<>();
            diagnostics.add(new Diagnostic(
                    Diagnostic.Severity.ERROR, Diagnostic.Code.UNEXPECTED_KEYWORD,
                    0, 0, "解析失败: " + e.getMessage(), "检查源码语法"));
        }

        for (PlSqlBlock top : topLevelBlocks) {
            assignNestingDepth(top, 0);
        }

        boolean isComplete = diagnostics.stream()
                .noneMatch(d -> d.severity == Diagnostic.Severity.ERROR);

        return new PlSqlModel(source, tokens, topLevelBlocks, diagnostics,
                dialect.getName(), isComplete);
    }

    // ══════════════════════════════════════════════
    //  TokenInfo building
    // ══════════════════════════════════════════════

    private List<TokenInfo> buildTokenInfoList(List<Token> rawTokens, PlSqlDialect dialect) {
        List<TokenInfo> result = new ArrayList<>();
        boolean inDollarQuote = false;
        String dollarTag = null;
        for (int i = 0; i < rawTokens.size(); i++) {
            Token t = rawTokens.get(i);
            if (t.getType() == Token.EOF) break;
            String text = t.getText();
            String upper = text.toUpperCase();
            int type = t.getType();
            int channel = t.getChannel();

            if ("$".equals(text) || text.startsWith("$")) {
                if (!inDollarQuote) {
                    inDollarQuote = true;
                    dollarTag = text;
                    TokenInfo ti = new TokenInfo(i, type, text, upper,
                        t.getLine(), t.getCharPositionInLine(), channel);
                    ti.isStringLiteral = true;
                    result.add(ti);
                    continue;
                } else if (text.equals(dollarTag)) {
                    inDollarQuote = false;
                    dollarTag = null;
                    TokenInfo ti = new TokenInfo(i, type, text, upper,
                        t.getLine(), t.getCharPositionInLine(), channel);
                    ti.isStringLiteral = true;
                    result.add(ti);
                    continue;
                }
            }
            if (inDollarQuote) {
                TokenInfo ti = new TokenInfo(i, type, text, upper,
                    t.getLine(), t.getCharPositionInLine(), channel);
                ti.isStringLiteral = true;
                result.add(ti);
                continue;
            }

            TokenInfo ti = new TokenInfo(i, type, text, upper,
                t.getLine(), t.getCharPositionInLine(), channel);
            if (channel == 0) {
                boolean isStrLit = isStringLiteralType(type);
                ti.isStringLiteral = isStrLit;
                if (!isStrLit) {
                    ti.isKeyword = dialect.isKeyword(upper);
                }
            }
            result.add(ti);
        }
        return result;
    }

    private boolean isStringLiteralType(int antlrType) {
        return antlrType == PlSqlLexer.CHAR_STRING
            || antlrType == PlSqlLexer.NATIONAL_CHAR_STRING_LIT
            || antlrType == PlSqlLexer.BIT_STRING_LIT
            || antlrType == PlSqlLexer.HEX_STRING_LIT;
    }

    // ══════════════════════════════════════════════
    //  Content parsing (fill block details)
    // ══════════════════════════════════════════════

    private void fillBlockContent(List<PlSqlBlock> topLevelBlocks) {
        for (PlSqlBlock top : topLevelBlocks) {
            parseBlockContent(top);
        }
    }

    private void parseBlockContent(PlSqlBlock block) {
        detectLabel(block);
        if (block.declStartIdx >= 0 && block.declEndIdx >= block.declStartIdx) {
            parseDeclarations(block);
        }
        if (block.stmtStartIdx >= 0 && block.stmtEndIdx >= block.stmtStartIdx) {
            parseStatements(block);
        }
        if (block.exceptStartIdx >= 0 && block.exceptEndIdx >= block.exceptStartIdx) {
            parseExceptionSection(block);
        }
        if (block.type == PlSqlBlockType.IF) {
            parseIfBranches(block);
        }
        if (block.type == PlSqlBlockType.CASE_BLOCK) {
            parseCaseWhens(block);
        }
        for (PlSqlBlock child : block.children) {
            parseBlockContent(child);
        }
    }

    private void detectLabel(PlSqlBlock block) {
        if (block.startTokenIdx <= 0) return;
        TokenInfo prev = tokens.get(block.startTokenIdx - 1);
        if (prev.channel == 0 && ">>".equals(prev.text) && block.startTokenIdx >= 2) {
            TokenInfo prev2 = tokens.get(block.startTokenIdx - 2);
            if (prev2.channel == 0 && !prev2.isKeyword && !prev2.isStringLiteral) {
                TokenInfo prev3 = tokens.get(block.startTokenIdx - 3);
                if (prev3.channel == 0 && "<<".equals(prev3.text)) {
                    block.label = prev2.text;
                }
            }
        }
    }

    private void parseDeclarations(PlSqlBlock block) {
        int i = block.declStartIdx;
        while (i <= block.declEndIdx) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) { i++; continue; }
            int stmtEnd = findSemicolon(tokens, i, block.declEndIdx);
            if (stmtEnd < 0) stmtEnd = block.declEndIdx;
            String firstWord = firstNonHiddenUpper(tokens, i, stmtEnd);
            if (firstWord == null) { i = stmtEnd + 1; continue; }
            Declaration.Type declType = classifyDeclaration(firstWord, tokens, i, stmtEnd);
            String name = declType == Declaration.Type.NESTED_BLOCK
                ? null : firstNonKeywordToken(tokens, i, stmtEnd);
            block.declarations.add(new Declaration(declType, name, i, stmtEnd));
            i = stmtEnd + 1;
        }
    }

    private Declaration.Type classifyDeclaration(String firstWord,
                                                   List<TokenInfo> tokens,
                                                   int start, int end) {
        return switch (firstWord) {
            case "CURSOR" -> Declaration.Type.CURSOR;
            case "TYPE" -> Declaration.Type.TYPE_DECL;
            case "SUBTYPE" -> Declaration.Type.SUBTYPE;
            case "PRAGMA" -> Declaration.Type.PRAGMA_DECL;
            case "PROCEDURE", "FUNCTION" -> Declaration.Type.NESTED_BLOCK;
            default -> {
                if (containsUpper(tokens, start, end, "EXCEPTION")) {
                    yield Declaration.Type.EXCEPTION_DECL;
                }
                if (containsUpper(tokens, start, end, "CONSTANT")) {
                    yield Declaration.Type.CONSTANT;
                }
                yield Declaration.Type.VARIABLE;
            }
        };
    }

    private void parseStatements(PlSqlBlock block) {
        int i = block.stmtStartIdx;
        while (i <= block.stmtEndIdx) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) { i++; continue; }
            PlSqlBlock child = findChildBlockAt(block, i);
            if (child != null) {
                Statement stmt = new Statement(Statement.Type.BLOCK_STMT, i, child.endTokenIdx, block);
                stmt.innerBlock = child;
                block.statements.add(stmt);
                i = child.endTokenIdx + 1;
                continue;
            }
            int stmtEnd = findSemicolon(tokens, i, block.stmtEndIdx);
            if (stmtEnd < 0) stmtEnd = block.stmtEndIdx;
            Statement.Type stmtType = classifyStatement(tokens, i, stmtEnd);
            Statement stmt = new Statement(stmtType, i, stmtEnd, block);
            if (stmtType == Statement.Type.ASSIGNMENT
                || stmtType == Statement.Type.CONCATENATION) {
                int assignIdx = findAssignOp(tokens, i, stmtEnd);
                stmt.assignOpTokenIdx = assignIdx;
                stmt.assignTargetTokenIdx = findAssignTarget(tokens, i, assignIdx);
                if (stmtType == Statement.Type.CONCATENATION) {
                    stmt.concatSegments = buildConcatSegments(tokens, assignIdx + 1, stmtEnd);
                }
            } else if (stmtType == Statement.Type.EXECUTE_IMMEDIATE) {
                stmt.executeTargetIdx = i + 1;
            }
            block.statements.add(stmt);
            i = stmtEnd + 1;
        }
    }

    private Statement.Type classifyStatement(List<TokenInfo> tokens, int start, int end) {
        String firstWord = firstNonHiddenUpper(tokens, start, end);
        if (firstWord == null) return Statement.Type.NULL_STMT;
        return switch (firstWord) {
            case "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "WITH" -> Statement.Type.DML;
            case "EXECUTE" -> Statement.Type.EXECUTE_IMMEDIATE;
            case "OPEN" -> Statement.Type.OPEN;
            case "FETCH" -> Statement.Type.FETCH;
            case "CLOSE" -> Statement.Type.CLOSE;
            case "COMMIT" -> Statement.Type.COMMIT;
            case "ROLLBACK" -> Statement.Type.ROLLBACK;
            case "SAVEPOINT" -> Statement.Type.SAVEPOINT;
            case "PRAGMA" -> Statement.Type.PRAGMA;
            case "RAISE" -> Statement.Type.RAISE;
            case "RETURN" -> Statement.Type.RETURN;
            case "EXIT" -> Statement.Type.EXIT;
            case "CONTINUE" -> Statement.Type.CONTINUE;
            case "NULL" -> Statement.Type.NULL_STMT;
            case "GOTO" -> Statement.Type.GOTO;
            default -> {
                if (findAssignOp(tokens, start, end) >= 0) {
                    if (hasConcatOp(tokens, start, end)
                        && hasStringLiteral(tokens, start, end)) {
                        yield Statement.Type.CONCATENATION;
                    }
                    yield Statement.Type.ASSIGNMENT;
                }
                yield Statement.Type.PROC_CALL;
            }
        };
    }

    private void parseExceptionSection(PlSqlBlock block) {
        ExceptionSection section = new ExceptionSection(block.exceptStartIdx, block.exceptEndIdx);
        int i = block.exceptStartIdx;
        while (i <= block.exceptEndIdx) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1 || ti.isStringLiteral) { i++; continue; }
            if (!"WHEN".equals(ti.upper)) { i++; continue; }
            int whenIdx = i;
            i++;
            StringBuilder cond = new StringBuilder();
            int thenIdx = -1;
            while (i <= block.exceptEndIdx) {
                TokenInfo t = tokens.get(i);
                if (t.channel == 1) { i++; continue; }
                if ("THEN".equals(t.upper)) { thenIdx = i; i++; break; }
                if (cond.length() > 0) cond.append(' ');
                cond.append(t.text);
                i++;
            }
            if (thenIdx < 0) break;
            int handlerEnd = block.exceptEndIdx;
            for (int j = thenIdx + 1; j <= block.exceptEndIdx; j++) {
                TokenInfo t = tokens.get(j);
                if (t.channel == 1) continue;
                if ("WHEN".equals(t.upper)) { handlerEnd = j - 1; break; }
            }
            ExceptionSection.Handler handler =
                new ExceptionSection.Handler(cond.toString(), whenIdx, thenIdx, handlerEnd);
            int si = thenIdx + 1;
            while (si <= handlerEnd) {
                int se = findSemicolon(tokens, si, handlerEnd);
                if (se < 0) se = handlerEnd;
                Statement s = new Statement(classifyStatement(tokens, si, se), si, se, block);
                handler.statements.add(s);
                si = se + 1;
            }
            section.handlers.add(handler);
            i = handlerEnd + 1;
        }
        block.exceptionSection = section;
    }

    private void parseIfBranches(PlSqlBlock block) {
        int i = block.stmtStartIdx >= 0 ? block.stmtStartIdx : block.startTokenIdx + 1;
        int thenIdx = findKeyword(tokens, i, block.endTokenIdx, "THEN");
        if (thenIdx < 0) return;
        int branchEnd = findNextBranchEnd(tokens, thenIdx + 1, block.endTokenIdx);
        IfBranch main = new IfBranch(IfBranch.Type.IF, i + 1, thenIdx, branchEnd);
        parseBranchStatements(main, tokens, thenIdx + 1, branchEnd);
        block.ifBranches.add(main);
        int searchFrom = branchEnd;
        while (searchFrom < block.endTokenIdx) {
            int elsifIdx = findKeyword(tokens, searchFrom, block.endTokenIdx, "ELSIF");
            if (elsifIdx < 0) break;
            int elsifThen = findKeyword(tokens, elsifIdx + 1, block.endTokenIdx, "THEN");
            if (elsifThen < 0) break;
            int ebEnd = findNextBranchEnd(tokens, elsifThen + 1, block.endTokenIdx);
            IfBranch elsifBr = new IfBranch(IfBranch.Type.ELSIF, elsifIdx + 1, elsifThen, ebEnd);
            parseBranchStatements(elsifBr, tokens, elsifThen + 1, ebEnd);
            block.ifBranches.add(elsifBr);
            searchFrom = ebEnd;
        }
        if (searchFrom < block.endTokenIdx) {
            int elseIdx = findKeyword(tokens, searchFrom, block.endTokenIdx, "ELSE");
            if (elseIdx >= 0) {
                block.elseStmtStartIdx = elseIdx;
                int endIfIdx = findEndIf(tokens, elseIdx + 1, block.endTokenIdx);
                int elseEnd = endIfIdx >= 0 ? endIfIdx - 1 : block.endTokenIdx;
                int si = elseIdx + 1;
                while (si <= elseEnd) {
                    int se = findSemicolon(tokens, si, elseEnd);
                    if (se < 0) se = elseEnd;
                    block.statements.add(new Statement(
                        classifyStatement(tokens, si, se), si, se, block));
                    si = se + 1;
                }
            }
        }
    }

    private void parseCaseWhens(PlSqlBlock block) {
        int i = block.stmtStartIdx >= 0 ? block.stmtStartIdx : block.startTokenIdx + 1;
        while (i <= block.endTokenIdx) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) { i++; continue; }
            if (!"WHEN".equals(ti.upper)) { i++; continue; }
            int whenIdx = i;
            int thenIdx = findKeyword(tokens, i + 1, block.endTokenIdx, "THEN");
            if (thenIdx < 0) break;
            int whenEnd = findNextWhenEnd(tokens, thenIdx + 1, block.endTokenIdx);
            CaseWhen cw = new CaseWhen(whenIdx, thenIdx, whenEnd);
            int si = thenIdx + 1;
            while (si <= whenEnd) {
                int se = findSemicolon(tokens, si, whenEnd);
                if (se < 0) se = whenEnd;
                cw.statements.add(new Statement(
                    classifyStatement(tokens, si, se), si, se, block));
                si = se + 1;
            }
            block.caseWhens.add(cw);
            i = whenEnd + 1;
            if (i <= block.endTokenIdx) {
                TokenInfo t = tokens.get(i);
                if (t.channel == 0 && "ELSE".equals(t.upper)) {
                    block.elseStmtStartIdx = i;
                    break;
                }
            }
        }
    }

    private void parseBranchStatements(IfBranch branch, List<TokenInfo> tokens,
                                        int from, int to) {
        int i = from;
        while (i <= to) {
            int se = findSemicolon(tokens, i, to);
            if (se < 0) se = to;
            Statement.Type st = classifyStatement(tokens, i, se);
            branch.statements.add(new Statement(st, i, se, null));
            i = se + 1;
        }
    }

    // ══════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════

    private int findSemicolon(List<TokenInfo> tokens, int from, int to) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1 || ti.isStringLiteral) continue;
            if (";".equals(ti.text)) return i;
        }
        return -1;
    }

    private String firstNonHiddenUpper(List<TokenInfo> tokens, int from, int to) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            return ti.upper;
        }
        return null;
    }

    private String firstNonKeywordToken(List<TokenInfo> tokens, int from, int to) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if (!ti.isKeyword) return ti.upper;
            if ("TYPE".equals(ti.upper)) continue;
            return null;
        }
        return null;
    }

    private boolean containsUpper(List<TokenInfo> tokens, int from, int to, String kw) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if (kw.equals(ti.upper)) return true;
        }
        return false;
    }

    private boolean hasConcatOp(List<TokenInfo> tokens, int from, int to) {
        boolean sawBar = false;
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if ("|".equals(ti.text)) {
                if (sawBar) return true;
                sawBar = true;
            } else {
                sawBar = false;
            }
        }
        return false;
    }

    private boolean hasStringLiteral(List<TokenInfo> tokens, int from, int to) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if (ti.isStringLiteral) return true;
        }
        return false;
    }

    private int findAssignOp(List<TokenInfo> tokens, int from, int to) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1 || ti.isStringLiteral) continue;
            if (":=".equals(ti.text)) return i;
        }
        return -1;
    }

    private int findAssignTarget(List<TokenInfo> tokens, int from, int assignIdx) {
        for (int i = assignIdx - 1; i >= from && i >= 0; i--) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            return i;
        }
        return -1;
    }

    private List<ConcatenationSegment> buildConcatSegments(List<TokenInfo> tokens,
                                                            int from, int to) {
        List<ConcatenationSegment> segments = new ArrayList<>();
        int segStart = from;
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if (";".equals(ti.text)) break;
            if ("||".equals(ti.text)) {
                if (segStart < i) addSegment(segments, tokens, segStart, i - 1);
                segStart = i + 1;
            }
        }
        if (segStart <= to) addSegment(segments, tokens, segStart, to);
        return segments;
    }

    private void addSegment(List<ConcatenationSegment> segments,
                             List<TokenInfo> tokens, int from, int to) {
        if (from > to || from >= tokens.size()) return;
        StringBuilder text = new StringBuilder();
        boolean hasStr = false, hasVar = false;
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if (ti.isStringLiteral) hasStr = true;
            else if (!ti.isKeyword) hasVar = true;
            if (text.length() > 0 && !";".equals(ti.text)) text.append(' ');
            if (!";".equals(ti.text)) text.append(ti.text);
        }
        ConcatenationSegment.Type segType = hasStr
            ? ConcatenationSegment.Type.STRING_LITERAL
            : hasVar ? ConcatenationSegment.Type.VARIABLE_REF
            : ConcatenationSegment.Type.EXPRESSION;
        segments.add(new ConcatenationSegment(segType, from, to, text.toString().trim()));
    }

    private int findKeyword(List<TokenInfo> tokens, int from, int to, String kw) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1 || ti.isStringLiteral) continue;
            if (kw.equals(ti.upper)) return i;
        }
        return -1;
    }

    private int findNextBranchEnd(List<TokenInfo> tokens, int from, int to) {
        int ifDepth = 0;
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1 || ti.isStringLiteral) continue;
            String u = ti.upper;
            if ("ELSIF".equals(u) || "ELSE".equals(u)) {
                if (ifDepth == 0) return i - 1;
                continue;
            }
            if ("IF".equals(u)) {
                ifDepth++;
                continue;
            }
            if ("END".equals(u)) {
                int next = i + 1;
                while (next <= to && (tokens.get(next).channel == 1 || tokens.get(next).isStringLiteral)) next++;
                if (next <= to && "IF".equals(tokens.get(next).upper)) {
                    if (ifDepth > 0) {
                        ifDepth--;
                        int semi = next + 1;
                        while (semi <= to && !";".equals(tokens.get(semi).text)) semi++;
                        i = semi;
                        continue;
                    }
                }
                if (ifDepth == 0) return i - 1;
            }
        }
        return to;
    }

    private int findEndIf(List<TokenInfo> tokens, int from, int to) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1 || ti.isStringLiteral) continue;
            if ("END".equals(ti.upper)) {
                TokenInfo next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
                if (next != null && next.channel == 0 && "IF".equals(next.upper)) return i;
            }
        }
        return -1;
    }

    private int findNextWhenEnd(List<TokenInfo> tokens, int from, int to) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1 || ti.isStringLiteral) continue;
            String u = ti.upper;
            if ("WHEN".equals(u) || "ELSE".equals(u) || "END".equals(u)) return i - 1;
        }
        return to;
    }

    private PlSqlBlock findChildBlockAt(PlSqlBlock parent, int tokenIdx) {
        for (PlSqlBlock child : parent.children) {
            if (child.startTokenIdx == tokenIdx) return child;
        }
        return null;
    }

    @Override
    public Void visitSql_script(PlSqlParser.Sql_scriptContext ctx) {
        for (var child : ctx.children) {
            if (child instanceof PlSqlParser.Unit_statementContext) {
                visit(child);
            }
        }
        return null;
    }

    @Override
    public Void visitUnit_statement(PlSqlParser.Unit_statementContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreate_function_body(PlSqlParser.Create_function_bodyContext ctx) {
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.FUNCTION);
        block.startTokenIdx = ctx.start.getTokenIndex();
        if (ctx.function_name() != null) {
            block.blockName = ctx.function_name().getText();
        }
        handleFuncProcBody(block, ctx.IS(), ctx.AS(), ctx.body(), ctx.DECLARE(),
                ctx.seq_of_declare_specs(), ctx.call_spec());
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitCreate_procedure_body(PlSqlParser.Create_procedure_bodyContext ctx) {
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.PROCEDURE);
        block.startTokenIdx = ctx.start.getTokenIndex();
        if (ctx.procedure_name() != null) {
            block.blockName = ctx.procedure_name().getText();
        }
        handleFuncProcBody(block, ctx.IS(), ctx.AS(), ctx.body(), ctx.DECLARE(),
                ctx.seq_of_declare_specs(), ctx.call_spec());
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitFunction_body(PlSqlParser.Function_bodyContext ctx) {
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.FUNCTION);
        block.startTokenIdx = ctx.start.getTokenIndex();
        if (ctx.identifier() != null) {
            block.blockName = ctx.identifier().getText();
        }
        handleFuncProcBody(block, ctx.IS(), ctx.AS(), ctx.body(), ctx.DECLARE(),
                ctx.seq_of_declare_specs(), ctx.call_spec());
        if (block.endTokenIdx < 0) {
            block.endTokenIdx = ctx.stop.getTokenIndex();
        }
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitProcedure_body(PlSqlParser.Procedure_bodyContext ctx) {
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.PROCEDURE);
        block.startTokenIdx = ctx.start.getTokenIndex();
        if (ctx.identifier() != null) {
            block.blockName = ctx.identifier().getText();
        }
        handleFuncProcBody(block, ctx.IS(), ctx.AS(), ctx.body(), ctx.DECLARE(),
                ctx.seq_of_declare_specs(), ctx.call_spec());
        if (block.endTokenIdx < 0) {
            block.endTokenIdx = ctx.stop.getTokenIndex();
        }
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitCreate_package(PlSqlParser.Create_packageContext ctx) {
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.PACKAGE_SPEC);
        block.startTokenIdx = ctx.start.getTokenIndex();
        if (ctx.IS() != null) {
            block.headerEndTokenIdx = ctx.IS().getSymbol().getTokenIndex();
        } else if (ctx.AS() != null) {
            block.headerEndTokenIdx = ctx.AS().getSymbol().getTokenIndex();
        }
        if (!ctx.package_name().isEmpty()) {
            block.blockName = ctx.package_name(0).getText();
        }
        if (ctx.END() != null) {
            block.endTokenIdx = findEndSemicolon(ctx.END().getSymbol().getTokenIndex());
        }
        if (block.headerEndTokenIdx >= 0) {
            block.declStartIdx = block.headerEndTokenIdx + 1;
            block.declEndIdx = ctx.END().getSymbol().getTokenIndex() - 1;
        }
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitCreate_package_body(PlSqlParser.Create_package_bodyContext ctx) {
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.PACKAGE_BODY);
        block.startTokenIdx = ctx.start.getTokenIndex();
        if (ctx.IS() != null) {
            block.headerEndTokenIdx = ctx.IS().getSymbol().getTokenIndex();
        } else if (ctx.AS() != null) {
            block.headerEndTokenIdx = ctx.AS().getSymbol().getTokenIndex();
        }
        if (!ctx.package_name().isEmpty()) {
            block.blockName = ctx.package_name(0).getText();
        }
        if (block.headerEndTokenIdx >= 0) {
            block.declStartIdx = block.headerEndTokenIdx + 1;
        }
        if (ctx.BEGIN() != null) {
            Token beginToken = ctx.BEGIN().getSymbol();
            block.stmtStartIdx = beginToken.getTokenIndex() + 1;
            Token endToken = ctx.END().getSymbol();
            block.endTokenIdx = findEndSemicolon(endToken.getTokenIndex());
            if (block.declStartIdx >= 0) {
                block.declEndIdx = beginToken.getTokenIndex() - 1;
            }
            var handlers = ctx.exception_handler();
            if (handlers != null && !handlers.isEmpty()) {
                block.stmtEndIdx = handlers.get(0).start.getTokenIndex() - 1;
                block.exceptStartIdx = handlers.get(0).start.getTokenIndex();
                block.exceptEndIdx = endToken.getTokenIndex() - 1;
            } else {
                block.stmtEndIdx = endToken.getTokenIndex() - 1;
            }
        } else if (ctx.END() != null) {
            block.endTokenIdx = findEndSemicolon(ctx.END().getSymbol().getTokenIndex());
            if (block.declStartIdx >= 0) {
                block.declEndIdx = ctx.END().getSymbol().getTokenIndex() - 1;
            }
        }
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitAnonymous_block(PlSqlParser.Anonymous_blockContext ctx) {
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.ANON_BLOCK);
        block.startTokenIdx = ctx.start.getTokenIndex();
        if (ctx.DECLARE() != null) {
            block.declStartIdx = ctx.DECLARE().getSymbol().getTokenIndex() + 1;
        }
        Token beginToken = ctx.BEGIN().getSymbol();
        block.stmtStartIdx = beginToken.getTokenIndex() + 1;
        Token endToken = ctx.END().getSymbol();
        block.endTokenIdx = findEndSemicolon(endToken.getTokenIndex());
        if (block.declStartIdx >= 0) {
            block.declEndIdx = beginToken.getTokenIndex() - 1;
        }
        var exceptToken = ctx.EXCEPTION();
        if (exceptToken != null) {
            block.stmtEndIdx = exceptToken.getSymbol().getTokenIndex() - 1;
            block.exceptStartIdx = exceptToken.getSymbol().getTokenIndex();
            block.exceptEndIdx = endToken.getTokenIndex() - 1;
        }
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitBlock(PlSqlParser.BlockContext ctx) {
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.ANON_BLOCK);
        block.startTokenIdx = ctx.start.getTokenIndex();
        if (ctx.DECLARE() != null) {
            block.declStartIdx = ctx.DECLARE().getSymbol().getTokenIndex() + 1;
        }
        var bodyCtx = ctx.body();
        if (bodyCtx != null) {
            setupBodySections(block, bodyCtx);
            if (block.declStartIdx >= 0) {
                block.declEndIdx = bodyCtx.BEGIN().getSymbol().getTokenIndex() - 1;
            }
        }
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitBody(PlSqlParser.BodyContext ctx) {
        if (ctx.parent instanceof PlSqlParser.StatementContext) {
            PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.ANON_BLOCK);
            block.startTokenIdx = ctx.start.getTokenIndex();
            setupBodySections(block, ctx);
            pushBlock(block);
            visitChildren(ctx);
            popBlock();
        } else {
            visitChildren(ctx);
        }
        return null;
    }

    @Override
    public Void visitIf_statement(PlSqlParser.If_statementContext ctx) {
        int numElsif = ctx.elsif_part().size();
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.IF);
        block.startTokenIdx = ctx.start.getTokenIndex();
        block.endTokenIdx = findEndSemicolon(ctx.stop.getTokenIndex());
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitLoop_statement(PlSqlParser.Loop_statementContext ctx) {
        PlSqlBlockType type = PlSqlBlockType.LOOP;
        if (ctx.WHILE() != null) type = PlSqlBlockType.WHILE_LOOP;
        else if (ctx.FOR() != null) type = PlSqlBlockType.FOR_LOOP;

        PlSqlBlock block = new PlSqlBlock(type);
        block.startTokenIdx = ctx.start.getTokenIndex();
        Token loopToken = ctx.LOOP(0).getSymbol();
        block.headerEndTokenIdx = loopToken.getTokenIndex();
        block.stmtStartIdx = loopToken.getTokenIndex() + 1;
        while (block.stmtStartIdx < tokens.size()
                && tokens.get(block.stmtStartIdx).channel == 1) {
            block.stmtStartIdx++;
        }
        Token endToken = ctx.END().getSymbol();
        block.endTokenIdx = findEndSemicolon(endToken.getTokenIndex());
        block.stmtEndIdx = endToken.getTokenIndex() - 1;

        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    @Override
    public Void visitCase_statement(PlSqlParser.Case_statementContext ctx) {
        PlSqlBlock block = new PlSqlBlock(PlSqlBlockType.CASE_BLOCK);
        block.startTokenIdx = ctx.start.getTokenIndex();
        block.endTokenIdx = findEndSemicolon(ctx.stop.getTokenIndex());
        block.stmtStartIdx = ctx.start.getTokenIndex() + 1;
        pushBlock(block);
        visitChildren(ctx);
        popBlock();
        return null;
    }

    private void pushBlock(PlSqlBlock block) {
        if (blockStack.isEmpty()) {
            topLevelBlocks.add(block);
        } else {
            blockStack.peek().addChild(block);
        }
        blockStack.push(block);
    }

    private PlSqlBlock popBlock() {
        return blockStack.pop();
    }

    private void handleFuncProcBody(PlSqlBlock block,
                                     TerminalNode isNode, TerminalNode asNode,
                                     PlSqlParser.BodyContext bodyCtx,
                                     TerminalNode declareNode,
                                     PlSqlParser.Seq_of_declare_specsContext seqDeclCtx,
                                     PlSqlParser.Call_specContext callSpecCtx) {
        if (isNode != null) {
            block.headerEndTokenIdx = isNode.getSymbol().getTokenIndex();
        } else if (asNode != null) {
            block.headerEndTokenIdx = asNode.getSymbol().getTokenIndex();
        }
        if (bodyCtx != null) {
            setupBodySections(block, bodyCtx);
            if (block.headerEndTokenIdx >= 0) {
                if (declareNode != null) {
                    block.declStartIdx = declareNode.getSymbol().getTokenIndex() + 1;
                } else {
                    block.declStartIdx = block.headerEndTokenIdx + 1;
                }
                block.declEndIdx = bodyCtx.BEGIN().getSymbol().getTokenIndex() - 1;
            }
        } else if (callSpecCtx != null) {
            block.endTokenIdx = callSpecCtx.stop.getTokenIndex();
        }
    }

    private void setupBodySections(PlSqlBlock block, PlSqlParser.BodyContext bodyCtx) {
        Token beginToken = bodyCtx.BEGIN().getSymbol();
        block.stmtStartIdx = beginToken.getTokenIndex() + 1;
        Token endToken = bodyCtx.END().getSymbol();
        block.endTokenIdx = findEndSemicolon(endToken.getTokenIndex());
        var handlers = bodyCtx.exception_handler();
        var exceptToken = bodyCtx.EXCEPTION();
        if (exceptToken != null) {
            block.stmtEndIdx = exceptToken.getSymbol().getTokenIndex() - 1;
            block.exceptStartIdx = exceptToken.getSymbol().getTokenIndex();
            block.exceptEndIdx = endToken.getTokenIndex() - 1;
        } else if (handlers != null && !handlers.isEmpty()) {
            block.stmtEndIdx = handlers.get(0).start.getTokenIndex() - 1;
            block.exceptStartIdx = handlers.get(0).start.getTokenIndex();
            block.exceptEndIdx = endToken.getTokenIndex() - 1;
        } else {
            block.stmtEndIdx = endToken.getTokenIndex() - 1;
        }
    }

    private int findEndSemicolon(int endKwIdx) {
        boolean skipName = false;
        for (int i = endKwIdx + 1; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if (";".equals(ti.text)) return i;
            if (skipName) break;
            skipName = true;
        }
        return endKwIdx;
    }

    private Diagnostic.Code getUnclosedCode(PlSqlBlockType type) {
        return switch (type) {
            case IF -> Diagnostic.Code.IF_UNCLOSED;
            case LOOP, FOR_LOOP, WHILE_LOOP, REPEAT_LOOP -> Diagnostic.Code.LOOP_UNCLOSED;
            case CASE_BLOCK -> Diagnostic.Code.CASE_UNCLOSED;
            default -> Diagnostic.Code.BLOCK_UNCLOSED;
        };
    }

    private void assignNestingDepth(PlSqlBlock block, int depth) {
        block.nestingDepth = depth;
        for (PlSqlBlock child : block.children) {
            assignNestingDepth(child, depth + 1);
        }
    }
}
