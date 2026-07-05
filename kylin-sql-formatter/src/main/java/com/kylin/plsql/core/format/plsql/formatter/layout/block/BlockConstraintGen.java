package com.kylin.plsql.core.format.plsql.formatter.layout.block;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class BlockConstraintGen {

    private BlockConstraintGen() {}

    public static void addBlockConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                            List<ConstraintSpec> constraints,
                                            Map<String, ConstraintSpec> gapMap,
                                            List<PlSqlBlock> topLevelBlocks) {
        for (PlSqlBlock block : topLevelBlocks) {
            refineBlock(opts, tokens, constraints, gapMap, block);
            for (PlSqlBlock child : block.children) {
                refineBlock(opts, tokens, constraints, gapMap, child);
            }
        }
    }

    private static void refineBlock(FormatOptions opts, List<TokenInfo> tokens,
                                     List<ConstraintSpec> constraints,
                                     Map<String, ConstraintSpec> gapMap,
                                     PlSqlBlock block) {
        if (block.startTokenIdx < 0) return;

        // 1) blankLineBeforeBlock — applicable to all block types
        if (opts.isBlankLineBeforeBlock()) {
            addBlankLineBeforeBlock(tokens, constraints, gapMap, block);
        }

        // 2) isEndAlign — refine END gap for all blocks with END
        if (opts.isEndAlign()) {
            refineEndAlign(tokens, constraints, gapMap, block);
        }

        // 3) isDeclarationAlign — := alignment in DECLARE sections
        if (opts.isDeclarationAlign() && !block.declarations.isEmpty()) {
            alignDeclarations(tokens, constraints, gapMap, block);
        }
    }

    // ──────────────────────────────────────────────
    //  blankLineBeforeBlock
    // ──────────────────────────────────────────────

    private static void addBlankLineBeforeBlock(List<TokenInfo> tokens,
                                                  List<ConstraintSpec> constraints,
                                                  Map<String, ConstraintSpec> gapMap,
                                                  PlSqlBlock block) {
        if (block.startTokenIdx <= 0) return;
        int prevSemi = BlockConstraintUtil.findPrevSemicolon(tokens, block.startTokenIdx);
        if (prevSemi >= 0) {
            int next = BlockConstraintUtil.nextVisible(tokens, prevSemi + 1, block.startTokenIdx);
            if (next >= 0 && next < block.startTokenIdx) {
                String key = prevSemi + ":" + next;
                if (!gapMap.containsKey(key)) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        prevSemi, next,
                        ConstraintSpec.NewlineMode.REQUIRED, 0);
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    //  isEndAlign — amend END gap to use endAlign=true
    // ──────────────────────────────────────────────

    private static void refineEndAlign(List<TokenInfo> tokens,
                                        List<ConstraintSpec> constraints,
                                        Map<String, ConstraintSpec> gapMap,
                                        PlSqlBlock block) {
        if (block.endTokenIdx < 0) return;
        int endKw = BlockConstraintUtil.findEndKeyword(tokens, block.endTokenIdx);
        if (endKw < 0) return;
        int prevSemi = BlockConstraintUtil.findPrevSemicolon(tokens, endKw);
        if (prevSemi < 0) return;
        String key = prevSemi + ":" + endKw;
        ConstraintSpec g = gapMap.get(key);

    }

    // ──────────────────────────────────────────────
    //  isDeclarationAlign — := alignment in DECLARE
    // ──────────────────────────────────────────────

    private static void alignDeclarations(List<TokenInfo> tokens,
                                           List<ConstraintSpec> constraints,
                                           Map<String, ConstraintSpec> gapMap,
                                           PlSqlBlock block) {
        int assignCount = 0;
        String groupId = "decl_assign_" + block.startTokenIdx;

        for (Declaration decl : block.declarations) {
            int colonIdx = findAssignColon(tokens, decl.startTokenIdx, decl.endTokenIdx);
            if (colonIdx < 0) continue;
            assignCount++;

            BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                colonIdx, colonIdx + 1,
                ConstraintSpec.NewlineMode.FORBIDDEN, 0);
            if (colonIdx > 0) {
                ConstraintSpec ac = BlockConstraintUtil.gapConstraint(gapMap,
                    colonIdx - 1, colonIdx);
                ac.alignGroup(groupId);
                constraints.add(ac);
            }
        }
    }

    private static int findAssignColon(List<TokenInfo> tokens, int from, int to) {
        int limit = Math.min(to, tokens.size() - 2);
        for (int i = from; i <= limit; i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if (!":".equals(ti.text)) continue;
            TokenInfo tj = tokens.get(i + 1);
            if (tj.channel == 0 && "=".equals(tj.text)) return i;
        }
        return -1;
    }
}
