package com.kylin.plsql.core.format.plsql.formatter.layout.block;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class IfConstraintGen {

    private IfConstraintGen() {}

    public static void addIfConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                         List<ConstraintSpec> constraints,
                                         Map<String, ConstraintSpec> gapMap,
                                         List<PlSqlBlock> topLevelBlocks) {
        BlockConstraintUtil.iterateBlocks(topLevelBlocks, block -> {
            if (block.type != PlSqlBlockType.IF) return;
            walkIfBlock(opts, tokens, constraints, gapMap, block);
        });
    }

    private static void walkIfBlock(FormatOptions opts, List<TokenInfo> tokens,
                                     List<ConstraintSpec> constraints,
                                     Map<String, ConstraintSpec> gapMap,
                                     PlSqlBlock block) {
        int endBound = block.endTokenIdx;

        // Structural: THEN → statement indent +1
        for (IfBranch branch : block.ifBranches) {
            if (branch.thenTokenIdx >= 0) {
                int tn = BlockConstraintUtil.nextVisible(tokens, branch.thenTokenIdx + 1, endBound);
                if (tn >= 0) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        branch.thenTokenIdx, tn,
                        ConstraintSpec.NewlineMode.REQUIRED, 1);
                }
            }
        }

        // Structural: pop ;last_prev_stmt → ELSIF/ELSE to outdent IF branch keyword
        for (int bi = 1; bi < block.ifBranches.size(); bi++) {
            IfBranch branch = block.ifBranches.get(bi);
            if (branch.conditionStartIdx < 0) continue;
            int kwPos = BlockConstraintUtil.prevVisible(tokens, branch.conditionStartIdx - 1, block.startTokenIdx);
            if (kwPos >= 0) {
                // Pop from body level to IF level: ;last_stmt → ELSIF/ELSE with delta=-1
                int prevSemi = BlockConstraintUtil.findPrevSemicolon(tokens, kwPos);
                if (prevSemi >= 0) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        prevSemi, kwPos,
                        ConstraintSpec.NewlineMode.REQUIRED, -1);
                }
                // ELSIF/ELSE → condition: stay at IF level (delta=0, optional newline)
                int kn = BlockConstraintUtil.nextVisible(tokens, kwPos + 1, endBound);
                if (kn >= 0) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        kwPos, kn,
                        ConstraintSpec.NewlineMode.OPTIONAL, 0);
                }
            }
        }

        // Structural: ELSE block
        if (block.elseStmtStartIdx >= 0) {
            int en = BlockConstraintUtil.nextVisible(tokens, block.elseStmtStartIdx + 1, endBound);
            if (en >= 0) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    block.elseStmtStartIdx, en,
                    ConstraintSpec.NewlineMode.REQUIRED, -1);
            }
        }

        // Parameter: thenOnNewLine — IF condition → THEN
        if (opts.isThenOnNewLine()) {
            for (int i = block.startTokenIdx; i <= endBound; i++) {
                TokenInfo ti = tokens.get(i);
                if (ti.channel != 0) continue;
                if ("THEN".equals(ti.upper)) {
                    int prev = BlockConstraintUtil.prevVisible(tokens, i - 1, block.startTokenIdx);
                    if (prev >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            prev, i,
                            ConstraintSpec.NewlineMode.REQUIRED, 0);
                    }
                }
            }
        }

        // Parameter: elseOnNewLine — prev → ELSIF/ELSE
        if (opts.isElseOnNewLine()) {
            for (int i = block.startTokenIdx; i <= endBound; i++) {
                TokenInfo ti = tokens.get(i);
                if (ti.channel != 0) continue;
                if ("ELSIF".equals(ti.upper) || "ELSE".equals(ti.upper)) {
                    int prev = BlockConstraintUtil.prevVisible(tokens, i - 1, block.startTokenIdx);
                    if (prev >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            prev, i,
                            ConstraintSpec.NewlineMode.REQUIRED, 0);
                    }
                }
            }
        }

        // END → outdent
        BlockConstraintUtil.addEndConstraint(tokens, constraints, gapMap, block);
    }
}
