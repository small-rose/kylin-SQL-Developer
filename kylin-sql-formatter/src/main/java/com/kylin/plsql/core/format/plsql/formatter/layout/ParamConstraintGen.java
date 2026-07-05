package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.ParameterAlignMode;
import com.kylin.plsql.core.format.enums.ParameterListMode;
import com.kylin.plsql.core.format.plsql.formatter.layout.block.BlockConstraintUtil;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class ParamConstraintGen {

    private ParamConstraintGen() {}

    private static final Set<String> PCT_ATTR = Set.of(
        "TYPE", "ROWTYPE", "NOTFOUND", "FOUND", "ISOPEN", "ROWCOUNT"
    );

    public static void addParamConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                              List<ConstraintSpec> constraints,
                                              Map<String, ConstraintSpec> gapMap,
                                              List<PlSqlBlock> topLevelBlocks) {
        BlockConstraintUtil.iterateBlocks(topLevelBlocks, block -> {
            if (block.type != PlSqlBlockType.FUNCTION
                && block.type != PlSqlBlockType.PROCEDURE) return;

            int endBound = Math.min(block.headerEndTokenIdx, tokens.size() - 1);
            if (endBound < 0) endBound = Math.min(block.endTokenIdx, tokens.size() - 1);

            int lparen = -1;
            int rparen = -1;
            for (int i = block.startTokenIdx; i <= endBound; i++) {
                TokenInfo ti = tokens.get(i);
                if (ti.channel != 0) continue;
                if ("(".equals(ti.text)) {
                    lparen = i;
                    break;
                }
            }
            if (lparen < 0) return;

            rparen = findMatchingParen(tokens, lparen, endBound);
            if (rparen < 0) return;

            // Collect commas within the parens (parameter separators)
            List<Integer> commas = new ArrayList<>();
            for (int i = lparen + 1; i < rparen; i++) {
                TokenInfo ti = tokens.get(i);
                if (ti.channel != 0) continue;
                if (",".equals(ti.text)) commas.add(i);
            }

            // ( → first param: FORBIDDEN (no newline after open paren)
            int firstParam = BlockConstraintUtil.nextVisible(tokens, lparen + 1, rparen);
            if (firstParam >= 0) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    lparen, firstParam, ConstraintSpec.NewlineMode.FORBIDDEN, 0);
            }

            // Last param → ): FORBIDDEN (no newline before close paren)
            int lastParam = BlockConstraintUtil.prevVisible(tokens, rparen - 1, lparen + 1);
            if (lastParam >= 0) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    lastParam, rparen, ConstraintSpec.NewlineMode.FORBIDDEN, 0);
            }

            // Parameter separation
            boolean perLine = opts.isParameterPerLine();
            ParameterAlignMode alignMode = opts.getParameterAlignMode();

            List<Integer> paramStarts = new ArrayList<>();
            paramStarts.add(firstParam);
            for (int c : commas) {
                int nextParam = BlockConstraintUtil.nextVisible(tokens, c + 1, rparen);
                if (nextParam >= 0) paramStarts.add(nextParam);
            }

            if (perLine) {
                for (int i = 0; i < commas.size(); i++) {
                    int comma = commas.get(i);
                    int nextStart = paramStarts.get(i + 1);
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        comma, nextStart, ConstraintSpec.NewlineMode.REQUIRED, 0);
                }
            } else {
                for (int i = 0; i < commas.size(); i++) {
                    int comma = commas.get(i);
                    int nextStart = paramStarts.get(i + 1);
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        comma, nextStart, ConstraintSpec.NewlineMode.OPTIONAL, 0);
                }
            }

            // FORBIDDEN between param name → type → %ROWTYPE/%TYPE
            for (int ps : paramStarts) {
                addParamNameTypeForbidden(tokens, constraints, gapMap, ps, rparen);
            }

            if (alignMode == ParameterAlignMode.ALIGNED) {
                String alignId = "PARAM_" + block.startTokenIdx;
                for (int ps : paramStarts) {
                    ConstraintSpec g = BlockConstraintUtil.gapConstraint(gapMap, ps, ps);
                    g.alignGroup(alignId);
                }
            }
        });
    }

    private static void addParamNameTypeForbidden(List<TokenInfo> tokens,
                                                   List<ConstraintSpec> constraints,
                                                   Map<String, ConstraintSpec> gapMap,
                                                   int paramStart, int endParen) {
        // Find param name → first non-name token (type keywordlike VARCHAR2, %ROWTYPE, etc.)
        int limit = Math.min(endParen, tokens.size() - 1);
        for (int i = paramStart; i < limit; i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            // Skip the parameter name
            if (isParamName(ti, tokens, i)) continue;

            // Found the type token (or IN/OUT modifier or %)
            // Connect it to the previous visible token
            int prev = BlockConstraintUtil.prevVisible(tokens, i - 1, paramStart);
            if (prev >= 0 && prev >= paramStart) {
                BlockConstraintUtil.addForbiddenGap(tokens, constraints, gapMap,
                    prev, i, 1);
            }

            // If this is %, follow through
            if ("%".equals(ti.text)) {
                int pctNext = BlockConstraintUtil.nextVisible(tokens, i + 1, endParen);
                if (pctNext >= 0 && PCT_ATTR.contains(tokens.get(pctNext).upper)) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        i, pctNext, ConstraintSpec.NewlineMode.FORBIDDEN, 0);
                    // prev → % is already forbidden above with spaces(1,1,1),
                    // but %ROWTYPE should have 0 spaces. Override.
                    String key = prev + ":" + i;
                    ConstraintSpec existing = gapMap.get(key);
                    if (existing != null) {
                        existing.spaces(0, 0, 0);
                    }
                }
            }
            break;
        }
    }

    private static boolean isParamName(TokenInfo ti, List<TokenInfo> tokens, int idx) {
        if (ti.channel != 0) return false;
        // Skip leading whitespace
        String t = ti.text;
        if (t.isEmpty()) return false;
        char fc = t.charAt(0);
        if (Character.isDigit(fc)) return false;
        if ("'\"".indexOf(fc) >= 0) return false;
        // Check if it's a keyword like IN, OUT, IN OUT, NOCOPY
        String tu = ti.upper;
        if ("IN".equals(tu) || "OUT".equals(tu) || "NOCOPY".equals(tu)) return false;
        // Check if it looks like a type name
        // Simple heuristic: If the next token after this one is some non-name token
        // and this token itself hasn't been consumed, treat it as a name
        return true;
    }

    private static int findMatchingParen(List<TokenInfo> tokens, int lparen, int endBound) {
        int depth = 1;
        int limit = Math.min(endBound, tokens.size() - 1);
        for (int i = lparen + 1; i <= limit; i++) {
            if (tokens.get(i).channel != 0) continue;
            String t = tokens.get(i).text;
            if ("(".equals(t)) depth++;
            else if (")".equals(t)) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
