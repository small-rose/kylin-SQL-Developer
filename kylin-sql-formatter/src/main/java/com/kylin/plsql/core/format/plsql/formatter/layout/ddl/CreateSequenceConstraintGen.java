package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class CreateSequenceConstraintGen {

    private CreateSequenceConstraintGen() {}

    static void walkCreateSequence(FormatOptions opts, List<TokenInfo> tokens,
                                    List<ConstraintSpec> constraints,
                                    Map<String, ConstraintSpec> gapMap,
                                    int start, int end) {
        int seqKw = DdlUtil.findKeyword(tokens, start, end, "SEQUENCE");
        if (seqKw < 0) return;
        int nameEnd = DdlUtil.nextVisible(tokens, seqKw + 1, end);
        if (nameEnd < 0) return;
        int firstClause = DdlUtil.nextVisible(tokens, nameEnd + 1, end);
        if (firstClause < 0 || firstClause > end) return;

        if (opts.isDdlSeqOptionPerLine()) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, firstClause,
                ConstraintSpec.NewlineMode.REQUIRED, 0);
            String gid = "ddlSeqClause_" + DdlUtil.nextAlignGroup(gapMap);
            DdlUtil.formatOptionsPerLine(tokens, constraints, gapMap, firstClause, end, gid,
                Set.of("START","INCREMENT","MAXVALUE","MINVALUE","CYCLE","NOCYCLE",
                       "CACHE","NOCACHE","ORDER","NOORDER","KEEP","NOKEEP","SCALE",
                       "NOSCALE","SHARD","NOSHARD","SESSION"));
        } else {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, firstClause,
                ConstraintSpec.NewlineMode.REQUIRED, 0);
        }
    }
}
