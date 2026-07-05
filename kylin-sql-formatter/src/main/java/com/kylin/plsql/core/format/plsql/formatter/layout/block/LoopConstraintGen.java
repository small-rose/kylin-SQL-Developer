package com.kylin.plsql.core.format.plsql.formatter.layout.block;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class LoopConstraintGen {

    private static final Set<PlSqlBlockType> LOOP_TYPES = Set.of(
        PlSqlBlockType.LOOP, PlSqlBlockType.FOR_LOOP,
        PlSqlBlockType.WHILE_LOOP, PlSqlBlockType.REPEAT_LOOP
    );

    private LoopConstraintGen() {}

    public static void addLoopConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                           List<ConstraintSpec> constraints,
                                           Map<String, ConstraintSpec> gapMap,
                                           List<PlSqlBlock> topLevelBlocks) {
        BlockConstraintUtil.iterateBlocks(topLevelBlocks, block -> {
            if (!LOOP_TYPES.contains(block.type)) return;
            walkLoopBlock(opts, tokens, constraints, gapMap, block);
        });
    }

    private static void walkLoopBlock(FormatOptions opts, List<TokenInfo> tokens,
                                       List<ConstraintSpec> constraints,
                                       Map<String, ConstraintSpec> gapMap,
                                       PlSqlBlock block) {
        int endBound = block.endTokenIdx;
        int start = block.startTokenIdx;

        // Find LOOP keyword (or REPEAT for REPEAT_LOOP)
        int loopOrRepeatIdx = -1;
        if (block.type == PlSqlBlockType.REPEAT_LOOP) {
            loopOrRepeatIdx = BlockConstraintUtil.findKeyword(tokens, start, endBound, "REPEAT");
        } else {
            loopOrRepeatIdx = block.headerEndTokenIdx >= 0
                ? block.headerEndTokenIdx
                : BlockConstraintUtil.findKeyword(tokens, start, endBound, "LOOP");
        }

        // LOOP/REPEAT → first statement indent +1
        if (loopOrRepeatIdx >= 0) {
            int hn = BlockConstraintUtil.nextVisible(tokens, loopOrRepeatIdx + 1, endBound);
            if (hn >= 0) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    loopOrRepeatIdx, hn,
                    ConstraintSpec.NewlineMode.REQUIRED, 1);
            }
        }

        // Parameter: loopOnNewLine — when false, LOOP stays on same line
        if (!opts.isLoopOnNewLine()) {
            int loopIdx = BlockConstraintUtil.findKeyword(tokens, start, endBound, "LOOP");
            if (loopIdx >= 0) {
                int prev = BlockConstraintUtil.prevVisible(tokens, loopIdx - 1, start);
                if (prev >= 0) {
                    BlockConstraintUtil.addForbiddenGap(tokens, constraints, gapMap,
                        prev, loopIdx, 1);
                }
            }
        }

        // REPEAT_LOOP: UNTIL clause
        if (block.type == PlSqlBlockType.REPEAT_LOOP) {
            int untilIdx = BlockConstraintUtil.findKeyword(tokens, start, endBound, "UNTIL");
            if (untilIdx >= 0) {
                int prev = BlockConstraintUtil.prevVisible(tokens, untilIdx - 1, start);
                if (prev >= 0) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        prev, untilIdx,
                        ConstraintSpec.NewlineMode.REQUIRED, 0);
                }
                int untilNext = BlockConstraintUtil.nextVisible(tokens, untilIdx + 1, endBound);
                if (untilNext >= 0) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        untilIdx, untilNext,
                        ConstraintSpec.NewlineMode.REQUIRED, 0);
                }
            }
        }

        // END → outdent
        BlockConstraintUtil.addEndConstraint(tokens, constraints, gapMap, block);
    }
}
