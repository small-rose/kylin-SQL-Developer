package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.enums.CommaPosition;
import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.block.BlockConstraintUtil;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.PlSqlBlock;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class CommaConstraintGen {

    private CommaConstraintGen() {}

    public static void addCommaConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                            List<ConstraintSpec> constraints,
                                            Map<String, ConstraintSpec> gapMap,
                                            List<PlSqlBlock> topLevelBlocks) {
        CommaPosition pos = opts.getCommaPosition();
        if (pos == null) return;

        int endBound = tokens.size() - 1;
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0 || !",".equals(ti.text)) continue;

            int prev = BlockConstraintUtil.prevVisible(tokens, i - 1, 0);
            int next = BlockConstraintUtil.nextVisible(tokens, i + 1, endBound);
            if (prev < 0 || next >= tokens.size()) continue;

            if (pos == CommaPosition.TRAILING) {
                String pk = prev + ":" + i;
                if (!gapMap.containsKey(pk)) {
                    ConstraintSpec pg = new ConstraintSpec(prev, i)
                        .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(0, 0, 0);
                    gapMap.put(pk, pg); constraints.add(pg);
                }
                String nk = i + ":" + next;
                if (!gapMap.containsKey(nk)) {
                    ConstraintSpec ng = new ConstraintSpec(i, next)
                        .newline(ConstraintSpec.NewlineMode.OPTIONAL).breakPenalty(0.2);
                    gapMap.put(nk, ng); constraints.add(ng);
                }
            } else if (pos == CommaPosition.LEADING) {
                String nk = i + ":" + next;
                if (!gapMap.containsKey(nk)) {
                    ConstraintSpec ng = new ConstraintSpec(i, next)
                        .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(1, 1, 1);
                    gapMap.put(nk, ng); constraints.add(ng);
                }
                String pk = prev + ":" + i;
                if (!gapMap.containsKey(pk)) {
                    ConstraintSpec pg = new ConstraintSpec(prev, i)
                        .newline(ConstraintSpec.NewlineMode.OPTIONAL).breakPenalty(0.2);
                    gapMap.put(pk, pg); constraints.add(pg);
                }
            }
        }
    }
}
