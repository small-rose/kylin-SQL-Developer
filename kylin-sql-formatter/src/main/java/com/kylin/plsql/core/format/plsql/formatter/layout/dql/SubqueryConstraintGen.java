package com.kylin.plsql.core.format.plsql.formatter.layout.dql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.SubqueryStyle;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;

public class SubqueryConstraintGen {

    private SubqueryConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        for (int i = startIdx; i <= endIdx && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;

            int lparen = -1;
            if ("(".equals(ti.text)) {
                lparen = i;
            } else if ("IN".equals(ti.upper)) {
                int next = SelectColumnConstraintGen.nextVisibleToken(tokens, i + 1);
                if (next < 0 || !"(".equals(tokens.get(next).text)) continue;
                lparen = next;
            } else {
                continue;
            }

            int rparen = findMatchingParen(tokens, lparen, endIdx);
            if (rparen < 0) continue;

            int first = SelectColumnConstraintGen.nextVisibleToken(tokens, lparen + 1);
            if (first < 0 || first >= rparen) continue;
            String fu = tokens.get(first).upper;
            if (!"SELECT".equals(fu) && !"WITH".equals(fu)) continue;

            SubqueryPosition pos = detectPosition(tokens, startIdx, lparen);
            SubqueryStyle style = getStyleForPosition(opts, pos);

            boolean expand = (style == SubqueryStyle.EXPAND);
            if (style == SubqueryStyle.AUTO) {
                expand = shouldExpand(tokens, lparen, rparen, opts);
            }

            if (expand) {
                int openNext = SelectColumnConstraintGen.nextVisibleToken(tokens, lparen + 1);
                if (openNext < rparen) {
                    SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                        lparen, openNext, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);
                }
                int lastInner = SelectColumnConstraintGen.prevVisibleToken(tokens, rparen - 1);
                if (lastInner > lparen) {
                    SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                        lastInner, rparen, ConstraintSpec.NewlineMode.REQUIRED, -1, 0.3);
                }
            }
        }
    }

    enum SubqueryPosition {
        SELECT_LIST, WHERE_CLAUSE, FROM_CLAUSE, HAVING_CLAUSE,
        ON_CLAUSE, EXISTS, LATERAL, SCALAR, OTHER
    }

    private static SubqueryPosition detectPosition(List<TokenInfo> tokens, int startIdx, int lparen) {
        int prevKw = SelectColumnConstraintGen.prevVisibleToken(tokens, lparen - 1);
        if (prevKw < 0) return SubqueryPosition.OTHER;

        String upper = tokens.get(prevKw).upper;
        if ("SELECT".equals(upper)) return SubqueryPosition.SELECT_LIST;

        if ("EXISTS".equals(upper)) return SubqueryPosition.EXISTS;

        if ("LATERAL".equals(upper)) return SubqueryPosition.LATERAL;

        if ("ON".equals(upper)) return SubqueryPosition.ON_CLAUSE;

        if ("FROM".equals(upper)) return SubqueryPosition.FROM_CLAUSE;

        if ("WHERE".equals(upper)) return SubqueryPosition.WHERE_CLAUSE;

        if ("HAVING".equals(upper)) return SubqueryPosition.HAVING_CLAUSE;

        int beforePrev = SelectColumnConstraintGen.prevVisibleToken(tokens, prevKw - 1);
        String beforeUpper = beforePrev >= 0 ? tokens.get(beforePrev).upper : "";
        if ("WHERE".equals(beforeUpper) && ("AND".equals(upper) || "OR".equals(upper))) {
            return SubqueryPosition.WHERE_CLAUSE;
        }

        if ("=".equals(tokens.get(prevKw).text) || "<".equals(tokens.get(prevKw).text)
            || ">".equals(tokens.get(prevKw).text)) {
            return SubqueryPosition.SCALAR;
        }

        if (upper.equals(",") || beforeUpper.equals("SELECT")) {
            return SubqueryPosition.SELECT_LIST;
        }

        return SubqueryPosition.OTHER;
    }

    private static SubqueryStyle getStyleForPosition(FormatOptions opts, SubqueryPosition pos) {
        switch (pos) {
            case SELECT_LIST: return opts.getSelectListSubqueryStyle();
            case WHERE_CLAUSE: return opts.getWhereSubqueryStyle();
            case FROM_CLAUSE: return opts.getFromSubqueryStyle();
            case HAVING_CLAUSE: return opts.getWhereSubqueryStyle();
            case ON_CLAUSE: return SubqueryStyle.INLINE;
            case EXISTS: return SubqueryStyle.INLINE;
            case LATERAL: return opts.getFromSubqueryStyle();
            case SCALAR: return SubqueryStyle.INLINE;
            default: return opts.getDmlSubqueryFormat();
        }
    }

    private static boolean shouldExpand(List<TokenInfo> tokens, int lparen, int rparen,
                                          FormatOptions opts) {
        int threshold = opts.getSubqueryThreshold();
        int charCount = 0;
        boolean hasSetOp = false;
        int subDepth = 0;

        for (int i = lparen + 1; i < rparen && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            charCount += ti.text.length() + 1;
            String u = ti.upper;
            if ("UNION".equals(u) || "INTERSECT".equals(u) || "MINUS".equals(u)) {
                hasSetOp = true;
            }
            if ("(".equals(ti.text)) subDepth++;
        }

        if (charCount > threshold) return true;
        if (hasSetOp) return true;
        if (subDepth >= 2) return true;
        return false;
    }

    private static int findMatchingParen(List<TokenInfo> tokens, int openIdx, int endIdx) {
        int depth = 0;
        for (int i = openIdx; i <= endIdx && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if ("(".equals(ti.text)) depth++;
            else if (")".equals(ti.text)) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
