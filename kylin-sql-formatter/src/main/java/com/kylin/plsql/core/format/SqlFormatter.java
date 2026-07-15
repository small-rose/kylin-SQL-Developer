package com.kylin.plsql.core.format;

import com.kylin.plsql.core.format.dialect.SqlDialect;
import com.kylin.plsql.core.format.enums.CaseExpressionFormat;
import com.kylin.plsql.core.format.enums.CommaPosition;
import com.kylin.plsql.core.format.enums.ConstraintFormat;
import com.kylin.plsql.core.format.enums.ExceptionAlign;
import com.kylin.plsql.core.format.enums.InsertColumnFormat;
import com.kylin.plsql.core.format.enums.SelectColumnMode;
import com.kylin.plsql.core.format.enums.StorageClauseFormat;
import com.kylin.plsql.core.format.enums.WhereAndPosition;
import com.kylin.plsql.core.format.engine.FormatContext;
import com.kylin.plsql.core.format.engine.LineWidthTracker;
import com.kylin.plsql.core.format.engine.SqlContext;
import com.kylin.plsql.core.format.engine.SubqueryHandler;
import com.kylin.plsql.core.format.post.BlankLineProcessor;
import com.kylin.plsql.core.format.post.CommaPositionProcessor;
import com.kylin.plsql.core.format.post.LineEndingProcessor;
import com.kylin.plsql.core.format.post.PostProcessor;
import com.kylin.plsql.core.format.post.TrailingWhitespaceProcessor;
import com.kylin.plsql.core.format.plsql.PlSqlFormatter;
import com.kylin.plsql.core.format.plsql.model.FormatResult;
import com.kylin.plsql.core.parser.PlSqlLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.*;
import java.util.regex.Pattern;

public class SqlFormatter {

    private static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "MERGE",
        "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "COMMENT",
        "TABLE", "INDEX", "VIEW", "SEQUENCE", "SYNONYM", "MATERIALIZED",
        "TRIGGER", "FUNCTION", "PROCEDURE", "PACKAGE", "BODY", "REPLACE",
        "TYPE", "VARRAY", "RECORD", "SUBTYPE", "CURSOR",
        "BEGIN", "END", "DECLARE", "EXCEPTION",
        "IF", "THEN", "ELSE", "ELSIF", "END IF",
        "LOOP", "FOR", "WHILE", "FORALL", "REVERSE",
        "CASE", "WHEN", "END CASE",
        "RETURN", "RETURNING", "EXIT", "CONTINUE", "GOTO", "NULL",
        "COMMIT", "ROLLBACK", "SAVEPOINT", "LOCK",
        "OPEN", "FETCH", "CLOSE", "PIPE", "PIPED",
        "RAISE", "PRAGMA", "EXECUTE", "IMMEDIATE",
        "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "NATURAL", "JOIN", "ON",
        "ORDER", "GROUP", "BY", "HAVING", "OFFSET", "LIMIT", "FETCH",
        "UNION", "MINUS", "INTERSECT", "EXCEPT",
        "AS", "IS", "OUT", "NOCOPY", "REF",
        "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
        "REFERENCES", "CHECK", "UNIQUE", "CASCADE",
        "DISTINCT", "ALL", "NOWAIT",
        "GRANT", "REVOKE",
        "WITH", "CONNECT", "PRIOR", "START", "LEVEL",
        "BETWEEN", "LIKE",
        "ANY", "SOME", "ALL",
        "TRUE", "FALSE", "OTHERS", "UNKNOWN",
        "OVER", "PARTITION", "ROWS", "RANGE", "UNBOUNDED",
        "PRECEDING", "FOLLOWING", "CURRENT", "ROW",
        "FIRST", "LAST", "KEEP", "DENSE_RANK", "RANK", "ROW_NUMBER",
        "NTILE", "CUME_DIST", "PERCENTILE_CONT", "PERCENTILE_DISC",
        "LAG", "LEAD", "LISTAGG",
        "NVL", "NVL2", "DECODE", "COALESCE", "NULLIF",
        "MAX", "MIN", "SUM", "COUNT", "AVG", "MEDIAN", "STDDEV", "VARIANCE",
        "CAST", "MULTISET", "COLLECT",
        "ABS", "CEIL", "COS", "EXP", "FLOOR", "GREATEST", "LEAST",
        "LN", "LOG", "MOD", "POWER", "ROUND", "SIGN", "SQRT", "TRUNC",
        "SIN", "SINH", "TAN", "TANH",
        "UPPER", "LOWER", "INITCAP", "SUBSTR", "INSTR", "LENGTH",
        "REPLACE", "TRANSLATE", "TRIM", "LTRIM", "RTRIM",
        "LPAD", "RPAD", "CONCAT", "CHR", "ASCII",
        "TO_CHAR", "TO_DATE", "TO_NUMBER", "TO_TIMESTAMP", "TO_CLOB",
        "SYSDATE", "SYSTIMESTAMP", "CURRENT_DATE", "CURRENT_TIMESTAMP",
        "ADD_MONTHS", "MONTHS_BETWEEN", "LAST_DAY", "NEXT_DAY",
        "EXTRACT", "CONVERT",
        "VARCHAR2", "VARCHAR", "NVARCHAR2", "CHAR", "NCHAR", "NUMBER",
        "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "SIMPLE_INTEGER",
        "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE",
        "DATE", "TIMESTAMP", "INTERVAL",
        "CLOB", "NCLOB", "BLOB", "BFILE", "LONG", "RAW", "LONG RAW",
        "ROWID", "UROWID", "BOOLEAN", "SYS_REFCURSOR",
        "AUTHID", "DETERMINISTIC", "PIPELINED", "PARALLEL_ENABLE", "RESULT_CACHE",
        "BULK", "COLLECT", "SQLERRM", "SQLCODE",
        "PARTITION", "SUBPARTITION", "RANGE", "HASH", "LIST", "INTERVAL",
        "MAXVALUE", "MINVALUE",
        "ENABLE", "DISABLE", "COMPRESS", "PARALLEL", "MONITORING",
        "STORAGE", "PCTFREE", "PCTUSED", "INITRANS", "MAXTRANS",
        "LOGGING", "NOLOGGING"
    )));

    private static final Set<String> TYPE_KW = new HashSet<>(Arrays.asList(
        "NUMBER", "VARCHAR2", "VARCHAR", "NVARCHAR2", "CHAR", "NCHAR",
        "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "SIMPLE_INTEGER",
        "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE",
        "DATE", "TIMESTAMP", "INTERVAL",
        "CLOB", "NCLOB", "BLOB", "BFILE", "LONG", "RAW", "LONG RAW",
        "ROWID", "UROWID", "BOOLEAN", "SYS_REFCURSOR"
    ));

    private static final Set<String> PARAM_DIR_KW = new HashSet<>(Arrays.asList("IN", "OUT", "NOCOPY"));

    private static final List<PostProcessor> POST_CHAIN = Arrays.asList(
        new BlankLineProcessor(), new TrailingWhitespaceProcessor(),
        new CommaPositionProcessor(), new LineEndingProcessor()
    );

    public static String format(String source, FormatOptions options, SqlDialect dialect) {
        return format(source, options);
    }

    public static String format(String source, FormatOptions options) {
        if (source == null || source.isBlank()) return source;
        if (isSimpleSql(source)) {
            return formatSimple(source, options);
        }
        FormatResult result = PlSqlFormatter.format(source, options);
        return result.getEffectiveText();
    }

    static boolean isSimpleSql(String source) {
        String u = source.toUpperCase();
        // Check if source contains complex PL/SQL structures
        if (containsKeyword(u, "PACKAGE")
            || containsKeyword(u, "PROCEDURE")
            || containsKeyword(u, "FUNCTION")
            || containsKeyword(u, "TRIGGER")
            || containsKeyword(u, "TYPE")) {
            return false;
        }
        int beginCount = countKeyword(u, "BEGIN");
        int endCount = countKeyword(u, "END");
        if (beginCount == 0 && endCount == 0) return true;
        if (beginCount >= 2 || endCount >= 2) return false;
        if (beginCount == 1 && endCount == 1) {
            return !containsKeyword(u, "IF")
                && !containsKeyword(u, "LOOP")
                && !containsKeyword(u, "FOR")
                && !containsKeyword(u, "CASE");
        }
        return false;
    }

    private static boolean containsKeyword(String upper, String kw) {
        int idx = 0;
        while ((idx = upper.indexOf(kw, idx)) >= 0) {
            if (isWordBoundary(upper, idx, kw.length())) return true;
            idx += kw.length();
        }
        return false;
    }

    private static boolean isWordBoundary(String s, int start, int len) {
        boolean leftOk = start == 0 || !Character.isLetterOrDigit(s.charAt(start - 1));
        int end = start + len;
        boolean rightOk = end >= s.length() || !Character.isLetterOrDigit(s.charAt(end));
        return leftOk && rightOk;
    }

    private static int countKeyword(String upper, String kw) {
        int count = 0, idx = 0;
        while ((idx = upper.indexOf(kw, idx)) >= 0) {
            if (isWordBoundary(upper, idx, kw.length())) count++;
            idx += kw.length();
        }
        return count;
    }

    // Original formatter renamed to formatSimple
    public static String formatSimple(String source, FormatOptions options) {
        try {
            var input = CharStreams.fromString(source);
            var lexer = new PlSqlLexer(input);
            var tokens = new CommonTokenStream(lexer);
            tokens.fill();
            List<Token> allTokens = tokens.getTokens();

            var out = new StringBuilder();
            ArrayDeque<Integer> indentStack = new ArrayDeque<>();
            indentStack.push(0);
            boolean startOfLine = true;
            boolean needSpace = false;
            List<String> pendingComments = new ArrayList<>();
            FormatContext ctx = new FormatContext(options);
            LineWidthTracker lwt = new LineWidthTracker(options, ctx);
            String prevUpper = "";
            int colsOnLine = 0;
            int parenDepth = 0;
            int parenPivot = -1;
            int caseWhenAlign = -1;
            boolean inDeclSection = false;

            for (int ti = 0; ti < allTokens.size(); ti++) {
                Token t = allTokens.get(ti);
                int type = t.getType();
                if (type == Token.EOF) break;

                String text = t.getText();
                String upper = text.toUpperCase();

                if (t.getChannel() == Token.HIDDEN_CHANNEL) {
                    if ((text.startsWith("--") || text.startsWith("/*")) && options.isCommentPreserve())
                        pendingComments.add(text.trim());
                    continue;
                }

                int indent = indentStack.peek();
                int effIndent = ctx.getEffectiveIndentSize();
                boolean isKw = KEYWORDS.contains(upper);
                boolean isTypeKey = TYPE_KW.contains(upper);
                boolean isParamDir = PARAM_DIR_KW.contains(upper);

                // Subquery
                if ("(".equals(text) && ti + 1 < allTokens.size() - 1 && SubqueryHandler.isSubqueryStart(allTokens, ti)) {
                    int ci = SubqueryHandler.findMatchingParen(allTokens, ti);
                    if (ci > ti) {
                        if (needSpace) out.append(' ');
                        out.append(SubqueryHandler.formatSubquery(allTokens, ti, ci, options, null, indent, ctx));
                        ti = ci; needSpace = false; continue;
                    }
                }

                ctx.updateFromKeyword(upper);
                if ("SELECT".equals(upper) && options.getSelectColumnMode() == SelectColumnMode.COMPACT
                        && options.getSelectColumnsPerRow() > 0) colsOnLine = 0;
                if (",".equals(text) && (ctx.isSelectList() || ctx.isInList())) ctx.expectColumn = true;

                // Line break
                boolean breakBefore = checkBreakBefore(upper, ctx, options, prevUpper);
                if (("AND".equals(upper) || "OR".equals(upper)) && ctx.isWhere() && parenDepth > 0) {
                    boolean sq = false;
                    for (int n = ti + 1; n < allTokens.size() && n < ti + 6; n++) {
                        if (allTokens.get(n).getChannel() == Token.HIDDEN_CHANNEL) continue;
                        String w = allTokens.get(n).getText().toUpperCase();
                        if ("IN".equals(w) || "EXISTS".equals(w) || "SELECT".equals(w)) { sq = true; break; }
                        if (w.matches("[a-zA-Z_][a-zA-Z0-9_#]*")) continue; else break;
                    }
                    if (!sq) {
                        int mw = options.getMaxLineWidth();
                        if (mw <= 0 || out.length() - (out.lastIndexOf("\n") + 1) < mw) breakBefore = false;
                    }
                }
                if ("WHEN".equals(upper) && ctx.isIn(SqlContext.CASE_EXPR)
                        && options.getCaseExpressionFormat() == CaseExpressionFormat.EXPAND && caseWhenAlign < 0)
                    breakBefore = false;
                if (lwt.shouldBreak(ctx) && !startOfLine) breakBefore = true;
                if (type == PlSqlLexer.PERIOD) breakBefore = false;
                if (breakBefore && !startOfLine) {
                    out.append('\n'); startOfLine = true; needSpace = false; lwt.reset();
                }

                // Comments
                for (var c : pendingComments) {
                    if (!startOfLine) out.append('\n');
                    if (options.isCommentIndent()) appendIndent(out, indent * effIndent);
                    out.append(c).append('\n'); startOfLine = true;
                }
                pendingComments.clear();

                // END: pop indent stack before emit
                if (isKw && "END".equals(upper) && indentStack.size() > 1) {
                    indentStack.pop();
                    indent = indentStack.peek();
                }

                // Emit whitespace + indent
                if (startOfLine) {
                    int ci = indent * effIndent;
                    if ("ELSE".equals(upper) && indent > 0) ci = (indent - 1) * effIndent;
                    if ("ELSIF".equals(upper) && indent > 0) ci = (indent - 1) * effIndent;
                    if (ctx.isWhere() && ctx.whereIndentSet > 0) ci = ctx.whereIndentSet * ctx.getWhereWidth();
                    else if (ctx.isFrom() && options.getFromClauseIndent() > 0) ci += options.getFromClauseIndent() * effIndent;
                    if ("END".equals(upper) && options.isEndAlign() && ci > 0) ci -= effIndent;
                    if (ctx.isDirectly(SqlContext.PLSQL_EXCEPTION)
                            && options.getExceptionAlign() == ExceptionAlign.OUTDENT && ci > 0) ci -= effIndent;
                    if (options.getSelectColumnMode() == SelectColumnMode.ALIGN
                            && ctx.isSelectList() && ctx.selectListAlign > 0 && ctx.expectColumn && !upper.equals(","))
                        ci = ctx.selectListAlign;
                    if ("WHEN".equals(upper) && ctx.isIn(SqlContext.CASE_EXPR)) {
                        if (caseWhenAlign < 0) caseWhenAlign = ci; else ci = caseWhenAlign;
                    }
                    if (parenPivot > 0 && parenDepth > 0 && ("AND".equals(upper) || "OR".equals(upper)) && ctx.isWhere())
                        ci = parenPivot + 1;
                    if (ci > 60) ci = 60;
                    appendIndent(out, Math.max(0, ci));
                    startOfLine = false; needSpace = false; lwt.reset(); lwt.setLineWidth(ci);
                } else if (needSpace) {
                    if (type != PlSqlLexer.PERIOD && type != PlSqlLexer.SEMICOLON)
                        { out.append(' '); lwt.addToken(" "); }
                }

                // Emit token
                if (isKw) {
                    if (isTypeKey && options.getColumnDefTypeCase() != FormatOptions.KeywordCase.PRESERVE)
                        out.append(applyCase(text, options.getColumnDefTypeCase()));
                    else if (isTypeKey && options.getParameterTypeCase() != FormatOptions.KeywordCase.PRESERVE && ctx.isParamList())
                        out.append(applyCase(text, options.getParameterTypeCase()));
                    else if (isParamDir && options.getParameterDirectionCase() != FormatOptions.KeywordCase.PRESERVE)
                        out.append(applyCase(text, options.getParameterDirectionCase()));
                    else out.append(applyCase(text, options));
                } else out.append(text);
                lwt.addToken(text);

                if ("WHERE".equals(upper)) { ctx.whereIndentSet = indent; ctx.whereIndent = out.length() - text.length(); }
                if ("FROM".equals(upper) && options.getFromClauseIndent() > 0) ctx.fromIndentSet = indent;
                if (ctx.isSelectList() && ctx.selectListAlign < 0 && !isKw && !text.equals(",") && !text.isEmpty()) {
                    ctx.selectListAlign = Math.min(60, Math.max(0, out.length() - Math.max(out.lastIndexOf("\n"), 0) - 1 - text.length()));
                    colsOnLine = 1;
                }

                // Scope-based indent push (after emit)
                if (isKw && ("IS".equals(upper) || "AS".equals(upper)) && !inDeclSection
                        && !ctx.isSelectList() && !ctx.isWhere() && !ctx.isFrom()) {
                    indentStack.push(indentStack.peek() + 1);
                    inDeclSection = true;
                    out.append('\n'); startOfLine = true; needSpace = false; lwt.reset();
                }
                if ("PROCEDURE".equals(upper) || "FUNCTION".equals(upper) || "END".equals(upper))
                    inDeclSection = false;
                if (isKw && "BEGIN".equals(upper)) {
                    if (inDeclSection) indentStack.pop(); // undo IS/AS
                    inDeclSection = false;
                    indentStack.push(indentStack.peek() + 1);
                    out.append('\n'); startOfLine = true; needSpace = false; lwt.reset();
                } else if (isKw && ("LOOP".equals(upper) || "THEN".equals(upper))) {
                    // THEN after ELSIF shares the same indent level (no double push)
                    if (!("THEN".equals(upper) && "ELSIF".equals(prevUpper))) {
                        indentStack.push(indentStack.peek() + 1);
                    }
                }

                // Newline / comma / parens / period
                boolean isSelComma = ",".equals(text) && ctx.isSelectList();
                boolean isInComma = ",".equals(text) && ctx.isInList();

                if (type == PlSqlLexer.SEMICOLON) {
                    out.append('\n'); startOfLine = true; needSpace = false; lwt.reset();
                    ctx = new FormatContext(options);
                } else if (isSelComma) {
                    SelectColumnMode scm = options.getSelectColumnMode();
                    boolean leading = options.getCommaPosition() == CommaPosition.LEADING;
                    if (scm == SelectColumnMode.ALIGN || scm == SelectColumnMode.ONE_PER_LINE) {
                        if (!leading) out.append('\n');
                        startOfLine = true; needSpace = false; lwt.reset();
                        ctx.expectColumn = (scm == SelectColumnMode.ALIGN);
                        colsOnLine = 0;
                        if (leading) { out.append(','); out.append('\n'); }
                    } else {
                        colsOnLine++;
                        int mc = options.getSelectColumnsPerRow();
                        if (mc > 0 && colsOnLine >= mc) {
                            if (leading) { out.deleteCharAt(out.length() - 1); out.append("\n,"); }
                            else out.append('\n');
                            startOfLine = true; needSpace = false; colsOnLine = 1;
                        }
                    }
                } else if (isInComma) {
                    boolean leading = options.getCommaPosition() == CommaPosition.LEADING;
                    if (!leading) out.append('\n');
                    startOfLine = true; needSpace = false; lwt.reset();
                    ctx.expectColumn = true;
                    if (leading) { out.append(','); out.append('\n'); }
                } else if ("(".equals(text)) {
                    parenDepth++;
                    if (ctx.isWhere()) parenPivot = out.length();
                    needSpace = false;
                    if (options.getParenthesisSpacing() == com.kylin.plsql.core.format.enums.ParenthesisSpacing.BOTH) out.append(' ');
                    if (ctx.isInsertValsOrCols()) ctx.push(SqlContext.IN_LIST);
                    if (options.getParameterListMode() == com.kylin.plsql.core.format.enums.ParameterListMode.ONE_PER_LINE
                            && ctx.isParamList() && !startOfLine) {
                        out.append('\n'); startOfLine = true;
                    }
                } else if (")".equals(text)) {
                    parenDepth = Math.max(0, parenDepth - 1);
                    if (parenDepth == 0) parenPivot = -1;
                    needSpace = true;
                    if (ctx.isDirectly(SqlContext.IN_LIST)) ctx.pop();
                } else if (type == PlSqlLexer.PERIOD) {
                    needSpace = false;
                } else {
                    needSpace = true;
                }
                prevUpper = upper;
            }

            String result = out.toString().trim();
            if (options.isBlankLineBeforeBlock())
                result = result.replaceAll("(;\\n)(BEGIN|DECLARE|CREATE)", "$1\n$2");
            for (PostProcessor pp : POST_CHAIN) result = pp.process(result, options);
            return result;
        } catch (Exception e) {
            return source;
        }
    }

    private static boolean checkBreakBefore(String upper, FormatContext ctx, FormatOptions opts, String prevUpper) {
        if ("FROM".equals(upper) && !opts.isFromClauseNewline()) return false;
        if (Set.of("FROM", "WHERE", "ORDER", "GROUP", "HAVING", "CONNECT", "FETCH", "OFFSET", "LIMIT").contains(upper)) return true;
        if (("AND".equals(upper) || "OR".equals(upper)) && ctx.isWhere())
            return opts.getWhereAndPosition() != WhereAndPosition.LINE_END;
        if ("JOIN".equals(upper) && opts.isJoinOnNewline() && !Set.of("LEFT","RIGHT","INNER","OUTER","CROSS").contains(prevUpper)) return true;
        if (Set.of("LEFT","RIGHT","INNER","OUTER","CROSS").contains(upper) && opts.isJoinOnNewline()) return true;
        if ("THEN".equals(upper) && opts.isThenOnNewLine()) return true;
        if ("LOOP".equals(upper) && opts.isLoopOnNewLine()) return true;
        if ("ELSE".equals(upper) && opts.isElseOnNewLine()) return true;
        if ("DELETE".equals(upper) && opts.isDeleteFromNewline()) return true;
        if (Set.of("UNION","MINUS","INTERSECT").contains(upper) && opts.isSetOperatorNewline()) return true;
        if ("WHEN".equals(upper) && ctx.isIn(SqlContext.MERGE_CLAUSE) && opts.isMergeWhenNewline()) return true;
        if ("MERGE".equals(upper)) return true;
        if ("INTO".equals(upper) && "MERGE".equals(prevUpper) && opts.isMergeIntoNewline()) return true;
        if ("CONSTRAINT".equals(upper) && opts.getConstraintFormat() == ConstraintFormat.SEPARATE_LINE) return true;
        if (Set.of("TABLESPACE","STORAGE","PCTFREE","PCTUSED","LOGGING","NOLOGGING").contains(upper)
                && opts.getStorageClauseFormat() == StorageClauseFormat.LINE_BREAK) return true;
        if ("INSERT".equals(upper) && opts.getInsertColumnFormat() == InsertColumnFormat.ONE_PER_LINE) return true;
        if ("WHEN".equals(upper) && ctx.isIn(SqlContext.CASE_EXPR) && opts.getCaseExpressionFormat() == CaseExpressionFormat.EXPAND) return true;
        return false;
    }

    private static String applyCase(String text, FormatOptions.KeywordCase kc) {
        switch (kc) { case UPPER: return text.toUpperCase(); case LOWER: return text.toLowerCase(); default: return text; }
    }

    private static String applyCase(String text, FormatOptions opts) { return applyCase(text, opts.getKeywordCase()); }

    private static void appendIndent(StringBuilder out, int size) { for (int i = 0; i < size; i++) out.append(' '); }
}
