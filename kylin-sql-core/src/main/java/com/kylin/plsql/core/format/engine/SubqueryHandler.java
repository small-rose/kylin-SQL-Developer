package com.kylin.plsql.core.format.engine;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.SqlFormatter;
import com.kylin.plsql.core.format.enums.SelectColumnMode;
import com.kylin.plsql.core.format.enums.SubqueryStyle;
import com.kylin.plsql.core.format.dialect.SqlDialect;

import java.util.List;

/** Detects subqueries, matches parentheses, decides INLINE/EXPAND/AUTO. */
public class SubqueryHandler {

    public static int findMatchingParen(List<org.antlr.v4.runtime.Token> tokens, int openIndex) {
        int depth = 1;
        for (int i = openIndex + 1; i < tokens.size(); i++) {
            String t = tokens.get(i).getText();
            if ("(".equals(t)) depth++;
            else if (")".equals(t)) { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    public static boolean isSubqueryStart(List<org.antlr.v4.runtime.Token> tokens, int openIndex) {
        for (int i = openIndex + 1; i < tokens.size(); i++) {
            int ch = tokens.get(i).getChannel();
            if (ch == org.antlr.v4.runtime.Token.HIDDEN_CHANNEL) continue;
            if (tokens.get(i).getType() == org.antlr.v4.runtime.Token.EOF) return false;
            String t = tokens.get(i).getText().toUpperCase();
            return "SELECT".equals(t) || "WITH".equals(t);
        }
        return false;
    }

    public static boolean shouldExpand(List<org.antlr.v4.runtime.Token> tokens, int openIndex, int closeIndex,
                                        FormatOptions opts) {
        if (opts.getSubqueryStyle() == SubqueryStyle.INLINE) return false;
        if (opts.getSubqueryStyle() == SubqueryStyle.EXPAND) return true;
        // AUTO: heuristic
        int totalChars = 0;
        int joinCount = 0;
        for (int i = openIndex + 1; i < closeIndex; i++) {
            String txt = tokens.get(i).getText();
            totalChars += txt.length();
            String u = txt.toUpperCase();
            if ("JOIN".equals(u) || "WHERE".equals(u) || "GROUP".equals(u)) joinCount++;
        }
        if (totalChars > opts.getSubqueryThreshold()) return true;
        if (joinCount > 0) return true;
        return false;
    }

    public static String formatSubquery(List<org.antlr.v4.runtime.Token> tokens, int openIndex, int closeIndex,
                                         FormatOptions opts, SqlDialect dialect, int indent, FormatContext ctx) {
        StringBuilder sb = new StringBuilder();
        boolean expand = shouldExpand(tokens, openIndex, closeIndex, opts);

        if (expand) {
            sb.append("(\n");
            String inner = extractSql(tokens, openIndex + 1, closeIndex - 1);
            FormatOptions subOpts = opts.snapshot();
            if (subOpts.getSubquerySelectMode() == SelectColumnMode.ALIGN) {
                subOpts.setSelectColumnMode(SelectColumnMode.ALIGN);
            }
            if (!opts.isSubqueryFromNewline()) {
                subOpts.setFromClauseNewline(false);
            }
            String formatted = SqlFormatter.format(inner, subOpts, dialect);
            for (String line : formatted.split("\n")) {
                for (int i = 0; i < indent + 1; i++) {
                    sb.append("    ");
                }
                sb.append(line).append("\n");
            }
            for (int i = 0; i < indent; i++) sb.append("    ");
            sb.append(")");
        } else {
            // INLINE
            sb.append("(");
            sb.append(extractSql(tokens, openIndex + 1, closeIndex - 1).trim());
            sb.append(")");
        }
        return sb.toString();
    }

    private static String extractSql(List<org.antlr.v4.runtime.Token> tokens, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to && i < tokens.size(); i++) {
            if (tokens.get(i).getType() == org.antlr.v4.runtime.Token.EOF) break;
            sb.append(tokens.get(i).getText());
        }
        return sb.toString();
    }
}
