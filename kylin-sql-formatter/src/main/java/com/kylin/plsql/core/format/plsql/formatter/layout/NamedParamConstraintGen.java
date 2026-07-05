package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.block.BlockConstraintUtil;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class NamedParamConstraintGen {

    private NamedParamConstraintGen() {}

    public static void addNamedParamConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                                  List<ConstraintSpec> constraints,
                                                  Map<String, ConstraintSpec> gapMap,
                                                  List<PlSqlBlock> topLevelBlocks) {
        if (!opts.isNamedParameterAlign()) return;

        int endBound = tokens.size() - 1;
        // Scan for => symbol which indicates named parameter association
        // Group consecutive => occurrences for alignment
        List<int[]> arrowGroups = new ArrayList<>(); // each group is [firstArrow, lastArrow]

        boolean inGroup = false;
        int groupStart = -1;
        int groupEnd = -1;

        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (!"=>".equals(ti.text)) {
                if (inGroup && (",".equals(ti.text) || ")".equals(ti.text))) {
                    // Group continues if comma, ends at )
                    if (")".equals(ti.text)) {
                        inGroup = false;
                    }
                } else if (inGroup) {
                    // End group
                    inGroup = false;
                }
                continue;
            }

            if (!inGroup) {
                groupStart = i;
                inGroup = true;
            }
            groupEnd = i;
        }

        if (groupStart < 0) return;

        // For each =>, ensure FORBIDDEN gaps on both sides
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (!"=>".equals(ti.text)) continue;

            int prev = BlockConstraintUtil.prevVisible(tokens, i - 1, 0);
            int next = BlockConstraintUtil.nextVisible(tokens, i + 1, endBound);

            // param_name => value
            // gap(param_name, =>) = FORBIDDEN (no break between name and =>)
            if (prev >= 0) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    prev, i, ConstraintSpec.NewlineMode.FORBIDDEN, 0);
            }
            // gap(=>, value) = FORBIDDEN (no break between => and value)
            if (next < tokens.size()) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    i, next, ConstraintSpec.NewlineMode.FORBIDDEN, 0);
            }
        }
    }
}
