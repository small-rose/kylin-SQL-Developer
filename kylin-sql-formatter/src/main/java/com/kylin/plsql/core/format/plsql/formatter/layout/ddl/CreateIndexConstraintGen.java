package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class CreateIndexConstraintGen {

    private CreateIndexConstraintGen() {}

    static void walkCreateIndex(FormatOptions opts, List<TokenInfo> tokens,
                                 List<ConstraintSpec> constraints,
                                 Map<String, ConstraintSpec> gapMap,
                                 int start, int end) {
        int onIdx = DdlUtil.findKeyword(tokens, start, end, "ON");
        if (onIdx < 0) return;

        int closeParen = DdlUtil.findChar(tokens, onIdx + 1, end, ')');
        if (closeParen > onIdx) {
            int afterParen = DdlUtil.nextVisible(tokens, closeParen + 1, end);
            if (afterParen > closeParen && afterParen <= end
                && !";".equals(tokens.get(afterParen).text)) {
                String gid = "ddlIdxOpt_" + DdlUtil.nextAlignGroup(gapMap);
                DdlUtil.formatOptionsPerLine(tokens, constraints, gapMap,
                    closeParen, end, gid,
                    Set.of("TABLESPACE","STORAGE","PCTFREE","PCTUSED","COMPUTE","ONLINE",
                           "LOCAL","GLOBAL","PARALLEL","NOPARALLEL","LOGGING","NOLOGGING",
                           "MONITORING","SORT","NOSORT","REVERSE"));
            }
        }
    }
}
