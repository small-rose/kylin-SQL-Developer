package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.block.BlockConstraintUtil;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class ForallConstraintGen {

    private ForallConstraintGen() {}

    public static void addForallConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                             List<ConstraintSpec> constraints,
                                             Map<String, ConstraintSpec> gapMap,
                                             List<PlSqlBlock> topLevelBlocks) {
        if (!opts.isForallFormat()) return;

        int endBound = tokens.size() - 1;
        // Scan all tokens for FORALL keyword
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (!"FORALL".equals(ti.upper)) continue;

            // Found FORALL, find its bounds (next DML keyword or end of statement)
            // Find first DML keyword after FORALL
            int dmlIdx = -1;
            for (int j = i + 1; j <= endBound; j++) {
                TokenInfo tj = tokens.get(j);
                if (tj.channel != 0) continue;
                String u = tj.upper;
                if (Set.of("INSERT","UPDATE","DELETE","MERGE","SELECT","EXECUTE").contains(u)) {
                    dmlIdx = j;
                    break;
                }
            }
            if (dmlIdx < 0) continue;

            // Find last token of FORALL header (token before the DML keyword)
            int headerLast = BlockConstraintUtil.prevVisible(tokens, dmlIdx - 1, i);
            if (headerLast < 0) continue;

            // FORALL header → DML: force newline with indent
            BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                headerLast, dmlIdx,
                ConstraintSpec.NewlineMode.REQUIRED, 1);
        }
    }
}
