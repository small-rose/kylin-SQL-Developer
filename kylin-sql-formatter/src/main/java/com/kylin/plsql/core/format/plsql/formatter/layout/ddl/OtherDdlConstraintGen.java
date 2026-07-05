package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class OtherDdlConstraintGen {

    private OtherDdlConstraintGen() {}

    static void walkDrop(List<TokenInfo> tokens,
                           List<ConstraintSpec> constraints,
                           Map<String, ConstraintSpec> gapMap,
                           int start, int end) {
        DdlUtil.forceSimpleLayout(tokens, constraints, gapMap, start, end);
    }

    static void walkTruncate(List<TokenInfo> tokens,
                               List<ConstraintSpec> constraints,
                               Map<String, ConstraintSpec> gapMap,
                               int start, int end) {
        DdlUtil.forceSimpleLayout(tokens, constraints, gapMap, start, end);
    }

    static void walkRename(List<TokenInfo> tokens,
                             List<ConstraintSpec> constraints,
                             Map<String, ConstraintSpec> gapMap,
                             int start, int end) {
        DdlUtil.forceSimpleLayout(tokens, constraints, gapMap, start, end);
    }

    static void walkComment(List<TokenInfo> tokens,
                              List<ConstraintSpec> constraints,
                              Map<String, ConstraintSpec> gapMap,
                              int start, int end) {
        DdlUtil.forceSimpleLayout(tokens, constraints, gapMap, start, end);
    }
}
