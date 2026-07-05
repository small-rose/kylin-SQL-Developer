package com.kylin.plsql.core.format.plsql.formatter.layout.dql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.PlSqlBlock;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;

public class DqlConstraintGen {

    private DqlConstraintGen() {}

    public static void addDqlConstraints(FormatOptions opts, List<TokenInfo> tokens,
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

        if (!hasDqlKeyword(tokens, start, end)) return;

        CteConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        SelectColumnConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        FromJoinConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        WhereConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        InClauseConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        SubqueryConstraintGen.process(opts, tokens, constraints, gapMap, start, end);
        SetOpConstraintGen.process(opts, tokens, constraints, gapMap, start, end);

        // Pop continuation indent after each DQL statement's closing ;
        for (int i = start; i <= end; i++) {
            if (tokens.get(i).channel == 0
                && ("SELECT".equals(tokens.get(i).upper) || "WITH".equals(tokens.get(i).upper))) {
                int semi = findStatementSemicolon(tokens, i + 1, end);
                if (semi >= 0) {
                    int next = SelectColumnConstraintGen.nextVisibleToken(tokens, semi + 1);
                    if (next < tokens.size()) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            semi, next, ConstraintSpec.NewlineMode.REQUIRED, -1, 0.1);
                    }
                }
            }
        }
    }

    private static int findStatementSemicolon(List<TokenInfo> tokens, int from, int end) {
        for (int i = from; i <= end && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0 && ";".equals(ti.text)) return i;
        }
        return -1;
    }

    private static boolean hasDqlKeyword(List<TokenInfo> tokens, int start, int end) {
        for (int i = start; i <= end && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            String u = ti.upper;
            if ("SELECT".equals(u) || "WITH".equals(u)) return true;
        }
        return false;
    }
}
