package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.CaseExpressionFormat;
import com.kylin.plsql.core.format.plsql.formatter.layout.block.BlockConstraintUtil;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class CaseExprConstraintGen {

    private CaseExprConstraintGen() {}

    public static void addCaseExprConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                                List<ConstraintSpec> constraints,
                                                Map<String, ConstraintSpec> gapMap,
                                                List<PlSqlBlock> topLevelBlocks) {
        CaseExpressionFormat fmt = opts.getCaseExpressionFormat();
        if (fmt == null) return;

        int endBound = tokens.size() - 1;
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (!"CASE".equals(ti.upper)) continue;

            // Try to find matching END — scan forward counting CASE/END
            int endIdx = findMatchingEnd(tokens, i, endBound);
            if (endIdx < 0) continue;

            // Check if this is a CASE expression (not a CASE_BLOCK)
            // CASE_BLOCKs end with END CASE; CASE expressions end with END
            int endKw = BlockConstraintUtil.prevVisible(tokens, endIdx - 1, i);
            if (endKw < 0) continue;

            // Check if followed by CASE — it's a CASE_BLOCK
            // Simple heuristic: CASE_BLOCK has block type CASE_BLOCK
            boolean isBlock = false;
            // We can't easily distinguish here; just apply expression rules
            // and let the block handler override for CASE_BLOCK

            List<Integer> whenIdxs = new ArrayList<>();
            int elseIdx = -1;
            for (int j = i + 1; j < endIdx; j++) {
                TokenInfo tj = tokens.get(j);
                if (tj.channel != 0) continue;
                if ("WHEN".equals(tj.upper)) whenIdxs.add(j);
                if ("ELSE".equals(tj.upper)) elseIdx = j;
            }

            if (whenIdxs.isEmpty()) continue;

            // Apply CASE expression formatting
            if (fmt == CaseExpressionFormat.COMPACT) {
                // WHEN → expr: FORBIDDEN (no newline)
                for (int wi : whenIdxs) {
                    int afterWhen = BlockConstraintUtil.nextVisible(tokens, wi + 1, endIdx);
                    if (afterWhen >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            wi, afterWhen, ConstraintSpec.NewlineMode.FORBIDDEN, 0);
                    }
                }
                // When-i → When-i+1: OPTIONAL with high penalty
                for (int k = 0; k < whenIdxs.size() - 1; k++) {
                    int curWhen = whenIdxs.get(k);
                    // Find THEN END boundary
                    int thenIdx = findKeywordAfter(tokens, curWhen + 1, endIdx, "THEN");
                    int exprEnd = -1;
                    if (thenIdx >= 0) {
                        exprEnd = BlockConstraintUtil.nextVisible(tokens, thenIdx + 1, endIdx);
                        // Find next WHEN or ELSE or END
                        int nextWhen = (k + 1 < whenIdxs.size()) ? whenIdxs.get(k + 1) : endIdx;
                        int prevToNext = BlockConstraintUtil.prevVisible(tokens, nextWhen == endIdx ? endIdx - 1 : nextWhen - 1, thenIdx);
                        if (prevToNext >= 0) exprEnd = prevToNext;
                        else exprEnd = nextWhen;
                    }
                    if (exprEnd >= 0) {
                        int nw = whenIdxs.get(k + 1);
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            exprEnd, nw, ConstraintSpec.NewlineMode.OPTIONAL, 0);
                    }
                }
            } else if (fmt == CaseExpressionFormat.EXPAND) {
                // Each WHEN on its own line
                for (int k = 0; k < whenIdxs.size() - 1; k++) {
                    int curWhen = whenIdxs.get(k);
                    int nextWhen = whenIdxs.get(k + 1);
                    int prevToNext = BlockConstraintUtil.prevVisible(tokens, nextWhen - 1, curWhen);
                    if (prevToNext >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            prevToNext, nextWhen, ConstraintSpec.NewlineMode.REQUIRED, 0);
                    }
                }
                // ELSE on its own line
                if (elseIdx >= 0) {
                    int prevElse = BlockConstraintUtil.prevVisible(tokens, elseIdx - 1, whenIdxs.get(whenIdxs.size() - 1));
                    if (prevElse >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            prevElse, elseIdx, ConstraintSpec.NewlineMode.REQUIRED, 0);
                    }
                }
            }
        }
    }

    private static int findMatchingEnd(List<TokenInfo> tokens, int caseIdx, int endBound) {
        int depth = 1;
        int limit = Math.min(endBound, tokens.size() - 1);
        for (int i = caseIdx + 1; i <= limit; i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            String u = ti.upper;
            if ("CASE".equals(u)) depth++;
            else if ("END".equals(u)) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findKeywordAfter(List<TokenInfo> tokens, int start, int end, String kw) {
        int limit = Math.min(end, tokens.size() - 1);
        for (int i = Math.max(0, start); i <= limit; i++) {
            if (tokens.get(i).channel == 0 && kw.equals(tokens.get(i).upper)) return i;
        }
        return -1;
    }
}
