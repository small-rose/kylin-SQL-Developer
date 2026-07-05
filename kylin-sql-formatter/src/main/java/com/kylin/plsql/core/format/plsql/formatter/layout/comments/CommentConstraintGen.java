package com.kylin.plsql.core.format.plsql.formatter.layout.comments;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.CommentIndent;
import com.kylin.plsql.core.format.enums.CommentPlacement;
import com.kylin.plsql.core.format.enums.TrailingCommentAlign;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public class CommentConstraintGen {

    private CommentConstraintGen() {}

    public static void addCommentConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                              List<ConstraintSpec> constraints,
                                              Map<String, ConstraintSpec> gapMap) {
        if (!opts.isCommentPreserve()) return;

        CommentPlacement placement = opts.getCommentPlacement();
        TrailingCommentAlign trailingAlign = opts.getTrailingCommentAlign();

        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (!isComment(ti)) continue;

            int prevCode = prevVisible(tokens, i - 1);
            int nextCode = nextVisible(tokens, i + 1);
            int commentEnd = findCommentEnd(tokens, i);

            if (prevCode < 0 || nextCode < 0) continue;

            boolean isSingleLine = isLineComment(ti);
            boolean isTrailing = isTrailingComment(tokens, prevCode, i);

            boolean placeBefore = (placement == CommentPlacement.BEFORE)
                || (placement == CommentPlacement.KEEP && !isTrailing);

            if (placeBefore) {
                addCommentGap(tokens, constraints, gapMap, prevCode, i,
                    ConstraintSpec.NewlineMode.REQUIRED, 0);
            } else {
                String alignGid = null;
                if (trailingAlign == TrailingCommentAlign.ALIGN || trailingAlign == TrailingCommentAlign.RIGHT) {
                    alignGid = "TRAILING_COMMENT";
                }

                ConstraintSpec g = gapConstraint(gapMap, prevCode, i)
                    .newline(ConstraintSpec.NewlineMode.OPTIONAL).breakPenalty(100);
                if (alignGid != null) {
                    g.alignGroup(alignGid);
                }
                constraints.add(g);
            }

            i = commentEnd;
        }
    }

    private static boolean isComment(TokenInfo ti) {
        return ti.channel == 1
            && (ti.text.startsWith("--") || ti.text.startsWith("/*"));
    }

    private static boolean isLineComment(TokenInfo ti) {
        return ti.channel == 1 && ti.text.startsWith("--");
    }

    private static boolean isBlockComment(TokenInfo ti) {
        return ti.channel == 1 && ti.text.startsWith("/*");
    }

    private static int findCommentEnd(List<TokenInfo> tokens, int start) {
        TokenInfo ti = tokens.get(start);
        if (isLineComment(ti)) return start;
        for (int i = start; i < tokens.size(); i++) {
            TokenInfo tj = tokens.get(i);
            if (tj.channel == 1 && tj.text.contains("*/")) return i;
        }
        return start;
    }

    private static boolean isTrailingComment(List<TokenInfo> tokens, int prevCodeIdx, int commentIdx) {
        TokenInfo prevCode = tokens.get(prevCodeIdx);
        TokenInfo comment = tokens.get(commentIdx);
        return comment.line == prevCode.line;
    }

    private static int prevVisible(List<TokenInfo> tokens, int from) {
        for (int i = from; i >= 0; i--) {
            if (tokens.get(i).channel == 0) return i;
        }
        return -1;
    }

    private static int nextVisible(List<TokenInfo> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0) return i;
        }
        return -1;
    }

    private static void addCommentGap(List<TokenInfo> tokens, List<ConstraintSpec> constraints,
                                        Map<String, ConstraintSpec> gapMap,
                                        int from, int to,
                                        ConstraintSpec.NewlineMode mode, int indentDelta) {
        if (from < 0 || to < 0 || from >= tokens.size() || to >= tokens.size()) return;
        String key = from + ":" + to;
        if (gapMap.containsKey(key)) return;
        ConstraintSpec c = new ConstraintSpec(from, to).newline(mode).indentDelta(indentDelta);
        gapMap.put(key, c);
        constraints.add(c);
    }

    private static ConstraintSpec gapConstraint(Map<String, ConstraintSpec> gapMap,
                                                  int from, int to) {
        String key = from + ":" + to;
        ConstraintSpec existing = gapMap.get(key);
        if (existing != null) return existing;
        ConstraintSpec c = new ConstraintSpec(from, to);
        gapMap.put(key, c);
        return c;
    }
}
