package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.DdlAnalyzeFormat;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class AnalyzeFlashbackConstraintGen {

    private AnalyzeFlashbackConstraintGen() {}

    static void walkFlashback(FormatOptions opts, List<TokenInfo> tokens,
                               List<ConstraintSpec> constraints,
                               Map<String, ConstraintSpec> gapMap,
                               int start, int end) {
        if (opts.isDdlFlashbackCompact()) {
            DdlUtil.forceSimpleLayout(tokens, constraints, gapMap, start, end);
            return;
        }

        int toIdx = DdlUtil.findKeyword(tokens, start, end, "TO");
        if (toIdx > 0 && opts.isDdlFlashbackOptionPerLine()) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap,
                DdlUtil.prevVisible(tokens, toIdx - 1, start), toIdx,
                ConstraintSpec.NewlineMode.REQUIRED, 1);

            int modeIdx = DdlUtil.findAnyKeyword(tokens, toIdx + 1, end, "BEFORE", "TIMESTAMP", "SCN");
            if (modeIdx > 0) {
                DdlUtil.addDdlGap(tokens, constraints, gapMap, toIdx, modeIdx,
                    ConstraintSpec.NewlineMode.REQUIRED, 1);
            }
        }
    }

    static void walkPurge(List<TokenInfo> tokens,
                            List<ConstraintSpec> constraints,
                            Map<String, ConstraintSpec> gapMap,
                            int start, int end) {
        DdlUtil.forceSimpleLayout(tokens, constraints, gapMap, start, end);

        int tsIdx = DdlUtil.findKeyword(tokens, start, end, "TABLESPACE");
        int userIdx = DdlUtil.findKeyword(tokens, start, end, "USER");
        if (tsIdx > 0 && userIdx > userIdx) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, tsIdx, userIdx,
                ConstraintSpec.NewlineMode.OPTIONAL, 1);
        }
    }

    static void walkAnalyze(FormatOptions opts, List<TokenInfo> tokens,
                              List<ConstraintSpec> constraints,
                              Map<String, ConstraintSpec> gapMap,
                              int start, int end) {
        if (opts.getDdlAnalyzeFormat() == DdlAnalyzeFormat.COMPACT) {
            DdlUtil.forceSimpleLayout(tokens, constraints, gapMap, start, end);
            return;
        }

        int partitionIdx = DdlUtil.findAnyKeyword(tokens, start, end, "PARTITION", "SUBPARTITION");
        int operationIdx = DdlUtil.findAnyKeyword(tokens, start, end,
            "COMPUTE", "ESTIMATE", "VALIDATE", "LIST", "DELETE");

        if (partitionIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap,
                DdlUtil.prevVisible(tokens, partitionIdx - 1, start), partitionIdx,
                opts.isDdlAnalyzePartitionPerLine()
                    ? ConstraintSpec.NewlineMode.REQUIRED
                    : ConstraintSpec.NewlineMode.OPTIONAL, 1);
        }

        if (operationIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap,
                DdlUtil.prevVisible(tokens, operationIdx - 1, start), operationIdx,
                ConstraintSpec.NewlineMode.OPTIONAL, 1);
        }
    }
}
