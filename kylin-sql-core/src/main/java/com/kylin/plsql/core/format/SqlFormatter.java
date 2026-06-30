package com.kylin.plsql.core.format;

import com.kylin.plsql.core.format.enums.CommaPosition;
import com.kylin.plsql.core.format.enums.SelectColumnMode;
import com.kylin.plsql.core.format.enums.SubqueryStyle;
import com.kylin.plsql.core.format.dialect.SqlDialect;
import com.kylin.plsql.core.format.engine.FormatContext;
import com.kylin.plsql.core.format.engine.LineWidthTracker;
import com.kylin.plsql.core.format.engine.SqlContext;
import com.kylin.plsql.core.format.engine.SubqueryHandler;
import com.kylin.plsql.core.format.post.BlankLineProcessor;
import com.kylin.plsql.core.format.post.CommaPositionProcessor;
import com.kylin.plsql.core.format.post.LineEndingProcessor;
import com.kylin.plsql.core.format.post.PostProcessor;
import com.kylin.plsql.core.format.post.TrailingWhitespaceProcessor;
import com.kylin.plsql.core.parser.PlSqlLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.*;

/** Main SQL formatter: token-based formatting engine with context stack, line width tracking, subquery handling, and post-processor chain. */
public class SqlFormatter {

    private static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
        "CREATE", "ALTER", "DROP", "TABLE", "INDEX", "VIEW", "SEQUENCE",
        "TRIGGER", "FUNCTION", "PROCEDURE", "PACKAGE", "BODY", "REPLACE",
        "BEGIN", "END", "DECLARE", "EXCEPTION",
        "IF", "THEN", "ELSE", "ELSIF", "END IF",
        "LOOP", "FOR", "WHILE",
        "CASE", "WHEN", "END CASE",
        "RETURN", "EXIT", "CONTINUE", "GOTO",
        "COMMIT", "ROLLBACK", "SAVEPOINT",
        "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "JOIN", "ON",
        "ORDER", "GROUP", "BY", "HAVING",
        "UNION", "MINUS", "INTERSECT", "EXCEPT",
        "AS", "IS", "OUT", "NOCOPY", "REF",
        "NULL", "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
        "REFERENCES", "CHECK", "UNIQUE", "CASCADE",
        "DISTINCT", "ALL", "NOWAIT",
        "GRANT", "REVOKE",
        "TYPE", "VARRAY", "RECORD", "SUBTYPE", "CURSOR",
        "FETCH", "OPEN", "CLOSE",
        "RAISE", "PRAGMA", "EXECUTE", "IMMEDIATE",
        "AUTHID", "DETERMINISTIC", "PIPELINED", "PARALLEL_ENABLE",
        "RESULT_CACHE", "ACCESSIBLE",
        "SHARING", "METADATA", "NONE",
        "TRUNCATE", "MERGE",
        "WITH", "CONNECT", "PRIOR", "START",
        "BETWEEN", "LIKE", "ANY", "SOME",
        "TRUE", "FALSE",
        "AT", "LOCAL", "TIME", "ZONE",
        "SESSION", "CURRENT",
        "MAX", "MIN", "SUM", "COUNT", "AVG",
        "NVL", "NVL2", "DECODE",
        "SYSDATE", "SYSTIMESTAMP", "TRUNC", "ROUND",
        "CEIL", "FLOOR", "MOD", "POWER", "SQRT",
        "UPPER", "LOWER", "INITCAP", "SUBSTR", "INSTR", "LENGTH",
        "REPLACE", "TRANSLATE", "TRIM", "LTRIM", "RTRIM",
        "LPAD", "RPAD", "CONCAT", "COALESCE", "NULLIF",
        "LAG", "LEAD", "RANK", "DENSE_RANK", "ROW_NUMBER",
        "LISTAGG", "EXTRACT", "CAST", "CONVERT",
        "ADD_MONTHS", "MONTHS_BETWEEN", "LAST_DAY", "NEXT_DAY",
        "VARCHAR2", "VARCHAR", "NVARCHAR2", "CHAR", "NCHAR", "NUMBER",
        "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "SIMPLE_INTEGER",
        "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE",
        "DATE", "TIMESTAMP", "INTERVAL",
        "CLOB", "NCLOB", "BLOB", "BFILE", "LONG", "RAW", "LONG RAW",
        "ROWID", "UROWID", "BOOLEAN", "SYS_REFCURSOR",
        "TO_DATE", "TO_CHAR", "TO_NUMBER"
    )));

    private static final Set<String> TYPE_KW = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "NUMBER", "VARCHAR2", "VARCHAR", "NVARCHAR2", "CHAR", "NCHAR",
        "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "SIMPLE_INTEGER",
        "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE",
        "DATE", "TIMESTAMP", "INTERVAL",
        "CLOB", "NCLOB", "BLOB", "BFILE", "LONG", "RAW", "LONG RAW",
        "ROWID", "UROWID", "BOOLEAN", "SYS_REFCURSOR"
    )));

    private static final Set<String> PARAM_DIR_KW = new HashSet<>(Arrays.asList("IN", "OUT", "NOCOPY"));

    private static final Set<String> LINE_BREAK_BEFORE = new HashSet<>(Arrays.asList(
        "FROM", "WHERE", "ORDER", "GROUP", "HAVING",
        "CONNECT", "FETCH", "OFFSET", "LIMIT"
    ));

    private static final Set<String> INDENT_INCR = new HashSet<>(Arrays.asList(
        "BEGIN", "LOOP", "THEN"
    ));

    private static final Set<String> INDENT_DECR = new HashSet<>(Arrays.asList(
        "END", "ELSIF", "ELSE", "EXCEPTION"
    ));

    private static final List<PostProcessor> POST_CHAIN = Collections.unmodifiableList(Arrays.asList(
        new BlankLineProcessor(),
        new TrailingWhitespaceProcessor(),
        new CommaPositionProcessor(),
        new LineEndingProcessor()
    ));

    private static final Set<String> DML_STARTS = new HashSet<>(Arrays.asList(
        "INSERT", "UPDATE", "DELETE", "MERGE"
    ));

    public static String format(String source, FormatOptions options, SqlDialect dialect) {
        if (dialect == null) return format(source, options);
        return format(source, options);
    }

    public static String format(String source, FormatOptions options) {
        if (source == null || source.isBlank()) return source;
        try {
            var input = CharStreams.fromString(source);
            var lexer = new PlSqlLexer(input);
            var tokens = new CommonTokenStream(lexer);
            tokens.fill();
            List<Token> allTokens = tokens.getTokens();

            var out = new StringBuilder();
            int indent = 0;
            boolean startOfLine = true;
            boolean needSpace = false;
            List<String> pendingComments = new ArrayList<>();
            boolean inMultiline = false;

            FormatContext ctx = new FormatContext(options);
            LineWidthTracker lwt = new LineWidthTracker(options, ctx);

            for (int ti = 0; ti < allTokens.size(); ti++) {
                Token token = allTokens.get(ti);
                int type = token.getType();
                if (type == Token.EOF) break;

                String text = token.getText();
                String upper = text.toUpperCase();

                if (token.getChannel() == Token.HIDDEN_CHANNEL) {
                    if (text.startsWith("--") || text.startsWith("/*")) {
                        if (options.isCommentPreserve()) pendingComments.add(text.trim());
                    }
                    continue;
                }

                boolean isKw = KEYWORDS.contains(upper);
                int effIndent = ctx.getEffectiveIndentSize();

                // ── Subquery detection ──
                if ("(".equals(text) && ti + 1 < allTokens.size() - 1
                        && SubqueryHandler.isSubqueryStart(allTokens, ti)) {
                    int closeIdx = SubqueryHandler.findMatchingParen(allTokens, ti);
                    if (closeIdx > ti) {
                        String subSql = SubqueryHandler.formatSubquery(allTokens, ti, closeIdx, options, null, indent, ctx);
                        out.append(subSql);
                        ti = closeIdx;
                        needSpace = false;
                        continue;
                    }
                }

                // ── Context tracking ──
                ctx.updateFromKeyword(upper);

                // ── Comma → column tracking for SELECT list / IN list ──
                if (",".equals(text) && ctx.isSelectList()) {
                    ctx.expectColumn = true;
                } else if (",".equals(text) && ctx.isInList()) {
                    ctx.expectColumn = true;
                }

                // ── Line break decisions ──
                boolean breakBefore = LINE_BREAK_BEFORE.contains(upper);
                if (Set.of("LEFT", "RIGHT", "JOIN").contains(upper) && options.isJoinOnNewline()) breakBefore = true;
                if ("FROM".equals(upper) && !options.isFromClauseNewline()) breakBefore = false;
                if ("THEN".equals(upper) && options.isThenOnNewLine()) breakBefore = true;
                if ("LOOP".equals(upper) && options.isLoopOnNewLine()) breakBefore = true;
                if ("ELSE".equals(upper) && options.isElseOnNewLine()) breakBefore = true;
                if ("DELETE".equals(upper) && options.isDeleteFromNewline()) breakBefore = true;
                if (Set.of("UNION", "MINUS", "INTERSECT").contains(upper) && options.isSetOperatorNewline()) breakBefore = true;
                if ("WHEN".equals(upper) && ctx.isIn(SqlContext.MERGE_CLAUSE) && options.isMergeWhenNewline()) breakBefore = true;

                // ── LineWidthTracker check ──
                if (lwt.shouldBreak(ctx) && !startOfLine) {
                    breakBefore = true;
                }

                if (breakBefore && !startOfLine) {
                    out.append('\n'); startOfLine = true; needSpace = false;
                    lwt.reset();
                }

                // ── Flush comments ──
                for (var comment : pendingComments) {
                    if (!startOfLine) out.append('\n');
                    if (options.isCommentIndent()) appendIndent(out, indent * effIndent);
                    out.append(comment).append('\n');
                    startOfLine = true;
                }
                pendingComments.clear();

                // ── Indent decrease ──
                if (startOfLine && isKw) {
                    if (INDENT_DECR.contains(upper)) indent = Math.max(0, indent - 1);
                }

                // ── Emit whitespace ──
                if (startOfLine) {
                    int ci = indent * effIndent;
                    if (ctx.isWhere() && ctx.whereIndentSet > 0) ci = ctx.whereIndentSet * ctx.getWhereWidth();
                    else if (ctx.isFrom() && options.getFromClauseIndent() > 0) ci += options.getFromClauseIndent() * effIndent;
                    if ("END".equals(upper) && options.isEndAlign() && ci > 0) ci -= effIndent;

                    // SELECT list align
                    if (ctx.isSelectList() && ctx.selectListAlign > 0 && ctx.expectColumn && !text.equals(",")) {
                        ci = ctx.selectListAlign;
                    }
                    appendIndent(out, Math.max(0, ci));
                    startOfLine = false; needSpace = false;
                    lwt.reset();
                    lwt.setLineWidth(ci);
                } else if (needSpace) {
                    if (type != PlSqlLexer.PERIOD && !text.equals("(") && !text.equals(")")) {
                        out.append(' ');
                        lwt.addToken(" ");
                    }
                }

                // ── Apply case ──
                if (isKw) {
                    if (TYPE_KW.contains(upper) && options.getColumnDefTypeCase() != FormatOptions.KeywordCase.PRESERVE) {
                        out.append(applyCase(text, options.getColumnDefTypeCase()));
                    } else if (PARAM_DIR_KW.contains(upper) && options.getParameterDirectionCase() != FormatOptions.KeywordCase.PRESERVE) {
                        out.append(applyCase(text, options.getParameterDirectionCase()));
                    } else {
                        out.append(applyCase(text, options));
                    }
                } else {
                    out.append(text);
                }
                lwt.addToken(text);

                // ── Track WHERE / FROM indent ──
                if ("WHERE".equals(upper)) {
                    ctx.whereIndent = out.length() - text.length();
                    ctx.whereIndentSet = indent;
                }
                if ("FROM".equals(upper) && options.getFromClauseIndent() > 0) {
                    ctx.fromIndentSet = indent;
                }

                // ── SELECT list alignment tracking ──
                if (ctx.isSelectList() && ctx.selectListAlign < 0 && !isKw && !text.equals(",") && !text.isEmpty()) {
                    ctx.selectListAlign = Math.max(0, out.length() - text.length() - 1);
                }

                // ── Indent increase ──
                if (isKw && INDENT_INCR.contains(upper)) indent++;

                // ── Newline after current token ──
                boolean isSelComma = ",".equals(text) && ctx.isSelectList();
                boolean isInComma = ",".equals(text) && ctx.isInList();

                if (type == PlSqlLexer.SEMICOLON) {
                    out.append('\n'); startOfLine = true; needSpace = false; lwt.reset();
                    ctx = new FormatContext(options);
                } else if ((isSelComma || isInComma)) {
                    out.append('\n'); startOfLine = true; needSpace = false; lwt.reset();
                    ctx.expectColumn = true;
                } else if (")".equals(text)) {
                    needSpace = false;
                    ctx.pop();
                } else {
                    needSpace = true;
                }

                // ── Track `(` → sub-context ──
                if ("(".equals(text)) {
                    if (ctx.isInsertValsOrCols()) ctx.push(SqlContext.IN_LIST);
                }
            }

            String result = out.toString().trim();

            // ── Blank line before block ──
            if (options.isBlankLineBeforeBlock()) {
                result = result.replaceAll("(;\\n)(BEGIN|DECLARE|CREATE)", "$1\n$2");
            }

            // ── PostProcessor chain ──
            for (PostProcessor pp : POST_CHAIN) {
                result = pp.process(result, options);
            }

            return result;
        } catch (Exception e) {
            return source;
        }
    }

    private static String applyCase(String text, FormatOptions options) {
        return applyCase(text, options.getKeywordCase());
    }

    private static String applyCase(String text, FormatOptions.KeywordCase kc) {
        switch (kc) {
            case UPPER: return text.toUpperCase();
            case LOWER: return text.toLowerCase();
            default: return text;
        }
    }

    private static void appendIndent(StringBuilder out, int size) {
        for (int i = 0; i < size; i++) out.append(' ');
    }
}
