package com.kylin.plsql.core.format.plsql.formatter.layout.block;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class TriggerConstraintGen {

    private TriggerConstraintGen() {}

    public static void addTriggerConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                              List<ConstraintSpec> constraints,
                                              Map<String, ConstraintSpec> gapMap,
                                              List<PlSqlBlock> topLevelBlocks) {
        BlockConstraintUtil.iterateBlocks(topLevelBlocks, block -> {
            if (block.type != PlSqlBlockType.TRIGGER) return;
            walkTriggerBlock(opts, tokens, constraints, gapMap, block);
        });
    }

    private static void walkTriggerBlock(FormatOptions opts, List<TokenInfo> tokens,
                                          List<ConstraintSpec> constraints,
                                          Map<String, ConstraintSpec> gapMap,
                                          PlSqlBlock block) {
        int endBound = block.endTokenIdx;

        // Find header end (DECLARE or BEGIN)
        int declIdx = block.declStartIdx;
        int bodyIdx = block.stmtStartIdx;
        int headerEnd = declIdx >= 0 ? declIdx : (bodyIdx >= 0 ? bodyIdx : endBound);

        if (headerEnd > block.startTokenIdx) {
            walkTriggerHeader(opts, tokens, constraints, gapMap,
                block.startTokenIdx, headerEnd);
        }

        // DECLARE section
        if (declIdx >= 0) {
            int next = BlockConstraintUtil.nextVisible(tokens, declIdx + 1, endBound);
            if (next >= 0) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    declIdx, next,
                    ConstraintSpec.NewlineMode.REQUIRED, 1);
            }
        }

        // BEGIN → indent +1
        if (bodyIdx >= 0) {
            int beginIdx = BlockConstraintUtil.findKeyword(tokens, block.startTokenIdx, endBound, "BEGIN");
            if (beginIdx >= 0) {
                int next = BlockConstraintUtil.nextVisible(tokens, beginIdx + 1, endBound);
                if (next >= 0) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        beginIdx, next,
                        ConstraintSpec.NewlineMode.REQUIRED, 1);
                }
            }
        }

        // EXCEPTION section
        BlockConstraintUtil.addExceptionSectionConstraints(opts, tokens, constraints, gapMap, block);

        // END → outdent
        BlockConstraintUtil.addEndConstraint(tokens, constraints, gapMap, block);

        // Pop indent at block's closing ;
        int ei = block.endTokenIdx;
        if (ei >= 0 && ei < tokens.size() && ";".equals(tokens.get(ei).text)) {
            int en = BlockConstraintUtil.nextVisible(tokens, ei + 1, endBound > ei ? endBound : tokens.size() - 1);
            if (en < tokens.size()) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    ei, en,
                    ConstraintSpec.NewlineMode.REQUIRED, -1);
            }
        }
    }

    private static void walkTriggerHeader(FormatOptions opts, List<TokenInfo> tokens,
                                            List<ConstraintSpec> constraints,
                                            Map<String, ConstraintSpec> gapMap,
                                            int start, int headerEnd) {
        boolean expand = opts.getTriggerFormat() == com.kylin.plsql.core.format.enums.TriggerFormat.EXPAND;

        int i = start;
        while (i < headerEnd) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) { i++; continue; }

            if (isTriggerClauseStart(ti)) {
                int prev = BlockConstraintUtil.prevVisible(tokens, i - 1, start);
                if (prev >= 0 && prev > start) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        prev, i,
                        expand ? ConstraintSpec.NewlineMode.REQUIRED
                               : ConstraintSpec.NewlineMode.OPTIONAL, 1);
                }
            }
            i++;
        }
    }

    private static boolean isTriggerClauseStart(TokenInfo t) {
        switch (t.upper) {
            case "BEFORE": case "AFTER": case "INSTEAD":
            case "ON": case "FOR": case "WHEN":
            case "FOLLOWS": case "PRECEDES":
            case "REFERENCING": case "ENABLE": case "DISABLE":
                return true;
            default: return false;
        }
    }
}
