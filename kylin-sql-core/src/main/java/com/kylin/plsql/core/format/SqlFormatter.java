package com.kylin.plsql.core.format;

import com.kylin.plsql.core.format.dialect.SqlDialect;
import com.kylin.plsql.core.parser.PlSqlLexer;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;

import java.util.*;

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
        "GREATEST", "LEAST", "SYS_GUID",
        "VARCHAR2", "VARCHAR", "NVARCHAR2", "CHAR", "NCHAR", "NUMBER",
        "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "SIMPLE_INTEGER",
        "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE",
        "DATE", "TIMESTAMP", "INTERVAL",
        "CLOB", "NCLOB", "BLOB", "BFILE", "LONG", "RAW", "LONG RAW",
        "ROWID", "UROWID", "BOOLEAN", "SYS_REFCURSOR",
        "TO_DATE", "TO_CHAR", "TO_NUMBER"
    )));

    private static final Set<String> INDENT_INCREASE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "BEGIN", "LOOP", "THEN"
    )));

    private static final Set<String> INDENT_DECREASE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "END", "ELSIF", "ELSE", "EXCEPTION"
    )));

    public static String format(String source, FormatOptions options, SqlDialect dialect) {
        if (dialect == null) return format(source, options);
        // Pass dialect keyword sets through system properties for formatter use
        return format(source, options);
    }

    public static String format(String source, FormatOptions options) {
        if (source == null || source.isBlank()) return source;

        try {
            var input = CharStreams.fromString(source);
            var lexer = new PlSqlLexer(input);
            var tokens = new CommonTokenStream(lexer);
            tokens.fill();

            var out = new StringBuilder();
            int indent = 0;
            boolean startOfLine = true;
            boolean needSpace = false;
            List<String> pendingComments = new ArrayList<>();

            // Alignment state
            int alignBase = -1;
            int selectListAlign = -1;
            boolean inSelectList = false;
            boolean expectColumn = false;

            for (var token : tokens.getTokens()) {
                int type = token.getType();
                if (type == Token.EOF) break;

                String text = token.getText();
                String upper = text.toUpperCase();

                if (token.getChannel() == Token.HIDDEN_CHANNEL) {
                    if (text.startsWith("--") || text.startsWith("/*")) {
                        pendingComments.add(text.trim());
                    }
                    continue;
                }

                boolean isKw = KEYWORDS.contains(upper);

                // Track SELECT list state for column alignment
                if (options.isAlignSelectColumns() && isKw) {
                    if ("SELECT".equals(upper)) {
                        inSelectList = true;
                        expectColumn = false;
                        selectListAlign = -1;
                    } else if (Set.of("FROM", "WHERE", "ORDER", "GROUP", "HAVING", "UNION", "MINUS", "INTERSECT").contains(upper)) {
                        inSelectList = false;
                        selectListAlign = -1;
                    }
                }

                // Flush pending comments before the current token
                for (var comment : pendingComments) {
                    if (!startOfLine) out.append('\n');
                    appendIndent(out, indent * options.getIndentSize());
                    out.append(comment).append('\n');
                    startOfLine = true;
                }
                pendingComments.clear();

                // Handle indent decrease before emitting
                if (startOfLine && isKw) {
                    if (INDENT_DECREASE.contains(upper)) {
                        indent = Math.max(0, indent - 1);
                    }
                }

                // Emit whitespace
                if (startOfLine) {
                    if (options.isAlignSelectColumns() && selectListAlign > 0 && expectColumn && inSelectList) {
                        appendIndent(out, selectListAlign);
                    } else {
                        appendIndent(out, indent * options.getIndentSize());
                    }
                    startOfLine = false;
                    needSpace = false;
                    if (options.isAlignSelectColumns() && selectListAlign > 0 && expectColumn && inSelectList) {
                        // Align mode - don't add extra indent
                    }
                } else if (needSpace) {
                    if (type == PlSqlLexer.PERIOD || text.equals("(") || text.equals(")")) {
                        // No space needed
                    } else {
                        out.append(' ');
                    }
                }

                // Apply case to keywords
                if (isKw) {
                    out.append(applyCase(text, options));
                } else {
                    out.append(text);
                }

                // Handle indent increase after emitting
                if (isKw && INDENT_INCREASE.contains(upper)) {
                    indent++;
                }

                // Record alignment position for SELECT list (first column position)
                if (options.isAlignSelectColumns() && inSelectList && selectListAlign < 0 && !isKw && !text.equals(",")) {
                    selectListAlign = out.length() - text.length() - 1;
                    if (selectListAlign < 0) selectListAlign = 0;
                }

                // After comma in SELECT list, set flag for column alignment
                if (options.isAlignSelectColumns() && inSelectList && text.equals(",")) {
                    expectColumn = true;
                } else if (expectColumn && !text.equals(",") && !Character.isWhitespace(text.charAt(0))) {
                    expectColumn = false;
                }

                // Semicolon → newline
                if (type == PlSqlLexer.SEMICOLON) {
                    out.append('\n');
                    startOfLine = true;
                    needSpace = false;
                    if (options.isAlignSelectColumns()) {
                        selectListAlign = -1;
                        inSelectList = false;
                    }
                } else if (text.equals(",") && inSelectList) {
                    out.append('\n');
                    startOfLine = true;
                    needSpace = false;
                } else {
                    needSpace = true;
                }
            }

            return out.toString().trim();
        } catch (Exception e) {
            return source;
        }
    }

    private static String applyCase(String text, FormatOptions options) {
        switch (options.getKeywordCase()) {
            case UPPER: return text.toUpperCase();
            case LOWER: return text.toLowerCase();
            default: return text;
        }
    }

    private static void appendIndent(StringBuilder out, int size) {
        for (int i = 0; i < size; i++) out.append(' ');
    }
}
