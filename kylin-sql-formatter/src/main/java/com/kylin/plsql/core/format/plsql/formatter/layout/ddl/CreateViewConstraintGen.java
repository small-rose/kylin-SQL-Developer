package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class CreateViewConstraintGen {

    private CreateViewConstraintGen() {}

    static void walkCreateView(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int start, int end) {
        int asIdx = DdlUtil.findKeyword(tokens, start, end, "AS");
        if (asIdx < 0) return;

        int selectIdx = DdlUtil.nextVisibleKw(tokens, asIdx + 1, end, "SELECT");
        if (selectIdx < 0) return;

        DdlUtil.addDdlGap(tokens, constraints, gapMap, asIdx, selectIdx,
            ConstraintSpec.NewlineMode.REQUIRED, 1);

        int openParen = DdlUtil.findLastChar(tokens, start, asIdx, '(');
        if (openParen > 0) {
            int closeParen = DdlUtil.findMatchingParen(tokens, openParen, asIdx);
            if (closeParen > openParen) {
                int firstAlias = DdlUtil.nextVisible(tokens, openParen + 1, closeParen);
                if (firstAlias >= 0) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, openParen, firstAlias,
                        ConstraintSpec.NewlineMode.REQUIRED, 1);
                    int beforeClose = DdlUtil.prevVisible(tokens, closeParen - 1, openParen);
                    if (beforeClose >= 0) {
                        DdlUtil.addDdlGap(tokens, constraints, gapMap, beforeClose, closeParen,
                            ConstraintSpec.NewlineMode.REQUIRED, -1);
                    }
                }
            }
        }
    }

    static void walkCreateMView(FormatOptions opts, List<TokenInfo> tokens,
                                 List<ConstraintSpec> constraints,
                                 Map<String, ConstraintSpec> gapMap,
                                 int start, int end) {
        int asIdx = DdlUtil.findKeyword(tokens, start, end, "AS");
        if (asIdx < 0) return;

        int selectIdx = DdlUtil.nextVisibleKw(tokens, asIdx + 1, end, "SELECT");
        int buildIdx = DdlUtil.findKeyword(tokens, start, asIdx > 0 ? asIdx : end, "BUILD");
        int refreshIdx = DdlUtil.findKeyword(tokens, start, asIdx > 0 ? asIdx : end, "REFRESH");

        int nameEnd = DdlUtil.findMViewNameEnd(tokens, start, asIdx > 0 ? asIdx : end);
        if (nameEnd > 0 && (buildIdx > 0 || refreshIdx > 0)) {
            int firstOpt = buildIdx > 0 ? buildIdx : refreshIdx;
            DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, firstOpt,
                ConstraintSpec.NewlineMode.REQUIRED, 0);
        }

        if (asIdx > 0 && selectIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, asIdx, selectIdx,
                ConstraintSpec.NewlineMode.REQUIRED, 1);
        }
    }
}
