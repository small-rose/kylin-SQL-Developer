package com.kylin.plsql.core.format.plsql.formatter.layout.block;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class CaseConstraintGen {

    private CaseConstraintGen() {}

    public static void addCaseConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                           List<ConstraintSpec> constraints,
                                           Map<String, ConstraintSpec> gapMap,
                                           List<PlSqlBlock> topLevelBlocks) {
        BlockConstraintUtil.iterateBlocks(topLevelBlocks, block -> {
            if (block.type != PlSqlBlockType.CASE_BLOCK) return;
            walkCaseBlock(opts, tokens, constraints, gapMap, block);
        });
    }

    private static void walkCaseBlock(FormatOptions opts, List<TokenInfo> tokens,
                                       List<ConstraintSpec> constraints,
                                       Map<String, ConstraintSpec> gapMap,
                                       PlSqlBlock block) {
        int endBound = block.endTokenIdx;

        // WHEN → THEN stays on same line; THEN → statement indent +1
        for (CaseWhen cw : block.caseWhens) {
            if (cw.thenTokenIdx >= 0) {
                int tn = BlockConstraintUtil.nextVisible(tokens, cw.thenTokenIdx + 1, endBound);
                if (tn >= 0) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        cw.thenTokenIdx, tn,
                        ConstraintSpec.NewlineMode.REQUIRED, 1);
                }
            }
        }

        // ELSE block: ELSE → first statement indent +1
        if (block.elseStmtStartIdx >= 0) {
            int en = BlockConstraintUtil.nextVisible(tokens, block.elseStmtStartIdx + 1, endBound);
            if (en >= 0) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    block.elseStmtStartIdx, en,
                    ConstraintSpec.NewlineMode.REQUIRED, 0);
            }
        }

        // END → outdent
        BlockConstraintUtil.addEndConstraint(tokens, constraints, gapMap, block);
    }
}
