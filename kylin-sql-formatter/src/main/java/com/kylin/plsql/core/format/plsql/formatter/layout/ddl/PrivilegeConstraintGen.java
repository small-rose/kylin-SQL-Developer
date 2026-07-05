package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class PrivilegeConstraintGen {

    private PrivilegeConstraintGen() {}

    static void walkGrant(FormatOptions opts, List<TokenInfo> tokens,
                           List<ConstraintSpec> constraints,
                           Map<String, ConstraintSpec> gapMap,
                           int start, int end) {
        int onIdx = DdlUtil.findKeyword(tokens, start, end, "ON");
        int toIdx = DdlUtil.findKeyword(tokens, start, end, "TO");
        int privEnd = onIdx > 0 ? onIdx : (toIdx > 0 ? toIdx : end);

        for (int i = start + 1; i < privEnd && i < end; i++) {
            if (",".equals(tokens.get(i).text)) {
                DdlUtil.addDdlGap(tokens, constraints, gapMap, i, i + 1,
                    ConstraintSpec.NewlineMode.FORBIDDEN, 0);
            }
        }

        if (onIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap,
                DdlUtil.prevVisible(tokens, onIdx - 1, start), onIdx,
                ConstraintSpec.NewlineMode.OPTIONAL, 0);
        }
        if (toIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap,
                DdlUtil.prevVisible(tokens, toIdx - 1, start), toIdx,
                ConstraintSpec.NewlineMode.OPTIONAL, 0);
        }
        if (onIdx > 0 && toIdx > 0) {
            int betweenStart = DdlUtil.nextVisible(tokens, onIdx + 1, toIdx);
            if (betweenStart > onIdx) {
                DdlUtil.addDdlGap(tokens, constraints, gapMap,
                    DdlUtil.prevVisible(tokens, betweenStart - 1, start), betweenStart,
                    ConstraintSpec.NewlineMode.OPTIONAL, 0);
            }
        }
    }

    static void walkRevoke(FormatOptions opts, List<TokenInfo> tokens,
                            List<ConstraintSpec> constraints,
                            Map<String, ConstraintSpec> gapMap,
                            int start, int end) {
        int onIdx = DdlUtil.findKeyword(tokens, start, end, "ON");
        int fromIdx = DdlUtil.findKeyword(tokens, start, end, "FROM");
        int privEnd = onIdx > 0 ? onIdx : (fromIdx > 0 ? fromIdx : end);

        for (int i = start + 1; i < privEnd && i < end; i++) {
            if (",".equals(tokens.get(i).text)) {
                DdlUtil.addDdlGap(tokens, constraints, gapMap, i, i + 1,
                    ConstraintSpec.NewlineMode.FORBIDDEN, 0);
            }
        }

        if (onIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap,
                DdlUtil.prevVisible(tokens, onIdx - 1, start), onIdx,
                ConstraintSpec.NewlineMode.OPTIONAL, 0);
        }
        if (fromIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap,
                DdlUtil.prevVisible(tokens, fromIdx - 1, start), fromIdx,
                ConstraintSpec.NewlineMode.OPTIONAL, 0);
        }
        if (onIdx > 0 && fromIdx > 0) {
            int betweenStart = DdlUtil.nextVisible(tokens, onIdx + 1, fromIdx);
            if (betweenStart > onIdx) {
                DdlUtil.addDdlGap(tokens, constraints, gapMap,
                    DdlUtil.prevVisible(tokens, betweenStart - 1, start), betweenStart,
                    ConstraintSpec.NewlineMode.OPTIONAL, 0);
            }
        }
    }
}
