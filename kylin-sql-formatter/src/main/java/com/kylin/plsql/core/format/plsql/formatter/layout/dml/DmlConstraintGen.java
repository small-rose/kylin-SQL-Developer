package com.kylin.plsql.core.format.plsql.formatter.layout.dml;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.formatter.layout.dql.SelectColumnConstraintGen;
import com.kylin.plsql.core.format.plsql.model.PlSqlBlock;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;

public class DmlConstraintGen {

    private DmlConstraintGen() {}

    public static void addDmlConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                          List<ConstraintSpec> constraints,
                                          Map<String, ConstraintSpec> gapMap,
                                          List<PlSqlBlock> topLevelBlocks) {
        for (PlSqlBlock block : topLevelBlocks) {
            processBlock(opts, tokens, constraints, gapMap, block);
            for (PlSqlBlock child : block.children) {
                processBlock(opts, tokens, constraints, gapMap, child);
            }
        }
    }

    private static void processBlock(FormatOptions opts, List<TokenInfo> tokens,
                                      List<ConstraintSpec> constraints,
                                      Map<String, ConstraintSpec> gapMap,
                                      PlSqlBlock block) {
        int start = Math.max(0, block.startTokenIdx);
        int end = Math.min(tokens.size() - 1, block.endTokenIdx);
        if (start >= end) return;

        boolean hasDml = hasDmlKeyword(tokens, start, end);
        if (!hasDml) return;

        InsertConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        UpdateConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        DeleteConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        MergeConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        BulkCollectConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
    }

    private static boolean hasDmlKeyword(List<TokenInfo> tokens, int start, int end) {
        for (int i = start; i <= end && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            String u = ti.upper;
            if ("INSERT".equals(u) || "UPDATE".equals(u)
                || "DELETE".equals(u) || "MERGE".equals(u)) return true;
        }
        return false;
    }
}
