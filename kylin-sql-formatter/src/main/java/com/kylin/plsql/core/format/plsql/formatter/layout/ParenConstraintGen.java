package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.ParenthesisSpacing;
import com.kylin.plsql.core.format.plsql.formatter.layout.block.BlockConstraintUtil;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.PlSqlBlock;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class ParenConstraintGen {

    private ParenConstraintGen() {}

    public static void addParenConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                            List<ConstraintSpec> constraints,
                                            Map<String, ConstraintSpec> gapMap,
                                            List<PlSqlBlock> topLevelBlocks) {
        ParenthesisSpacing ps = opts.getParenthesisSpacing();
        if (ps == null) ps = ParenthesisSpacing.NONE;

        int endBound = tokens.size() - 1;
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (!"(".equals(ti.text) && !")".equals(ti.text)) continue;

            if ("(".equals(ti.text)) {
                int rparen = findMatchingParen(tokens, i);
                if (rparen < 0) continue;

                int prev = BlockConstraintUtil.prevVisible(tokens, i - 1, 0);
                int afterOpen = BlockConstraintUtil.nextVisible(tokens, i + 1, rparen);
                int beforeClose = BlockConstraintUtil.prevVisible(tokens, rparen - 1, i + 1);
                int next = BlockConstraintUtil.nextVisible(tokens, rparen + 1, endBound);

                if (ps == ParenthesisSpacing.NONE) {
                    if (prev >= 0) addForbiddenGap(gapMap, constraints, prev, i, 0);
                    if (afterOpen >= 0) addForbiddenGap(gapMap, constraints, i, afterOpen, 0);
                    if (beforeClose >= 0) addForbiddenGap(gapMap, constraints, beforeClose, rparen, 0);
                    if (next >= 0) addRparenNextGap(gapMap, constraints, tokens, rparen, next);
                } else if (ps == ParenthesisSpacing.INSIDE) {
                    if (prev >= 0) addForbiddenGap(gapMap, constraints, prev, i, 0);
                    if (afterOpen >= 0) setSpacing(gapMap, constraints, i, afterOpen, 1);
                    if (beforeClose >= 0) setSpacing(gapMap, constraints, beforeClose, rparen, 1);
                    if (next >= 0) addRparenNextGap(gapMap, constraints, tokens, rparen, next);
                } else if (ps == ParenthesisSpacing.BOTH) {
                    if (prev >= 0) setSpacing(gapMap, constraints, prev, i, 1);
                    if (afterOpen >= 0) setSpacing(gapMap, constraints, i, afterOpen, 1);
                    if (beforeClose >= 0) setSpacing(gapMap, constraints, beforeClose, rparen, 1);
                    if (next >= 0) setSpacing(gapMap, constraints, rparen, next, 1);
                }
            }
        }
    }

    private static boolean isWordToken(TokenInfo ti) {
        if (ti == null || ti.text.isEmpty()) return false;
        if (ti.isStringLiteral) return false;
        char c = ti.text.charAt(0);
        return Character.isLetter(c);
    }

    private static void addRparenNextGap(Map<String, ConstraintSpec> gapMap,
                                          List<ConstraintSpec> constraints,
                                          List<TokenInfo> tokens,
                                          int rparen, int next) {
        // Keep a space between ) and a following keyword (RETURN, IS, THEN, etc.)
        // Only force 0 spaces for punctuation/operator tokens (. , ; ) etc.)
        if (next < tokens.size() && isWordToken(tokens.get(next))) {
            setSpacing(gapMap, constraints, rparen, next, 1);
        } else {
            addForbiddenGap(gapMap, constraints, rparen, next, 0);
        }
    }

    private static int findMatchingParen(List<TokenInfo> tokens, int lparen) {
        int depth = 1;
        for (int i = lparen + 1; i < tokens.size(); i++) {
            String t = tokens.get(i).text;
            if ("(".equals(t)) depth++;
            else if (")".equals(t)) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static void addForbiddenGap(Map<String, ConstraintSpec> gapMap,
                                          List<ConstraintSpec> constraints,
                                          int from, int to, int spaces) {
        String key = from + ":" + to;
        if (gapMap.containsKey(key)) return;
        ConstraintSpec c = new ConstraintSpec(from, to)
            .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(spaces, spaces, spaces);
        gapMap.put(key, c);
        constraints.add(c);
    }

    private static void setSpacing(Map<String, ConstraintSpec> gapMap,
                                     List<ConstraintSpec> constraints,
                                     int from, int to, int spaces) {
        String key = from + ":" + to;
        if (gapMap.containsKey(key)) return;
        ConstraintSpec c = new ConstraintSpec(from, to).spaces(spaces, spaces, spaces);
        gapMap.put(key, c);
        constraints.add(c);
    }
}
