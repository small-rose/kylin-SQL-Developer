package com.kylin.plsql.core.format.plsql.formatter.layout.dql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.InListFormat;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;

public class InClauseConstraintGen {

    private InClauseConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        for (int i = startIdx; i <= endIdx && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (!"IN".equals(ti.upper)) continue;

            int lparen = findNextNonWs(tokens, i + 1, endIdx);
            if (lparen < 0 || !"(".equals(tokens.get(lparen).text)) continue;

            int rparen = findMatchingParen(tokens, lparen, endIdx);
            if (rparen < 0) continue;

            if (isSubquery(tokens, lparen, rparen)) continue;

            InListFormat fmt = opts.getDmlInClauseExpand();
            int threshold = opts.getDmlInClauseThreshold();
            int perRow = opts.getDmlInColumnsPerRow();
            int indentSize = opts.getIndentSize();

            List<Integer> valueTokens = findValueTokens(tokens, lparen, rparen);
            int valueCount = valueTokens.size();

            if (valueCount > threshold && fmt == InListFormat.COMPACT) {
                fmt = InListFormat.ONE_PER_LINE;
            }

            switch (fmt) {
                case COMPACT:
                    for (int j = lparen + 1; j < rparen; j++) {
                        TokenInfo ct = tokens.get(j);
                        if (ct.channel != 0) continue;
                        if (",".equals(ct.text)) {
                            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                                j, j + 1, ConstraintSpec.NewlineMode.FORBIDDEN, 0, 1.0);
                        }
                    }
                    break;

                case ONE_PER_LINE:
                    SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                        lparen, lparen + 1, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.2);
                    for (int j = lparen + 1; j < rparen; j++) {
                        TokenInfo ct = tokens.get(j);
                        if (ct.channel != 0) continue;
                        if (",".equals(ct.text)) {
                            int nextVal = SelectColumnConstraintGen.nextVisibleToken(tokens, j + 1);
                            if (nextVal < rparen) {
                                SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                                    j, nextVal, ConstraintSpec.NewlineMode.REQUIRED, 0, 0.2);
                            }
                        }
                    }
                    int lastVal = SelectColumnConstraintGen.prevVisibleToken(tokens, rparen - 1);
                    if (lastVal >= lparen) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            lastVal, rparen, ConstraintSpec.NewlineMode.REQUIRED, -1, 0.2);
                    }
                    break;
            }
        }
    }

    private static int findNextNonWs(List<TokenInfo> tokens, int from, int endIdx) {
        for (int i = from; i <= endIdx && i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0) return i;
        }
        return -1;
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

    private static boolean isSubquery(List<TokenInfo> tokens, int lparen, int rparen) {
        int first = SelectColumnConstraintGen.nextVisibleToken(tokens, lparen + 1);
        if (first < 0 || first >= rparen) return false;
        String u = tokens.get(first).upper;
        return "SELECT".equals(u) || "WITH".equals(u);
    }

    private static List<Integer> findValueTokens(List<TokenInfo> tokens, int lparen, int rparen) {
        List<Integer> result = new java.util.ArrayList<>();
        for (int i = lparen + 1; i < rparen; i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (!",".equals(ti.text) && !"(".equals(ti.text) && !")".equals(ti.text)) {
                if (result.isEmpty() || !result.get(result.size() - 1).equals(i - 1)
                    || ",".equals(tokens.get(i - 1).text)) {
                    result.add(i);
                }
            }
        }
        return result;
    }
}
