package com.kylin.plsql.core.format.plsql.formatter.layout.dml;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.formatter.layout.dql.SelectColumnConstraintGen;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;

public class BulkCollectConstraintGen {

    private BulkCollectConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        if (opts.isDmlBulkCollectAlign()) {
            processBulkCollect(opts, tokens, constraints, gapMap, startIdx, endIdx, "BULK");
        }
        if (opts.isDmlUsingAlign()) {
            processUsing(opts, tokens, constraints, gapMap, startIdx, endIdx);
        }
    }

    private static void processBulkCollect(FormatOptions opts, List<TokenInfo> tokens,
                                            List<ConstraintSpec> constraints,
                                            Map<String, ConstraintSpec> gapMap,
                                            int startIdx, int endIdx, String keyword) {
        List<Integer> bulkPositions = SelectColumnConstraintGen.findKeywords(tokens, startIdx, endIdx, "BULK");
        for (int bulkPos : bulkPositions) {
            int collectPos = SelectColumnConstraintGen.findNextKeyword(tokens, bulkPos + 1, endIdx, "COLLECT");
            if (collectPos < 0) continue;
            int intoPos = SelectColumnConstraintGen.findNextKeyword(tokens, collectPos + 1, endIdx, "INTO");
            if (intoPos < 0) continue;

            int firstVar = SelectColumnConstraintGen.nextVisibleToken(tokens, intoPos + 1);
            if (firstVar < 0) continue;

            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                intoPos, firstVar, ConstraintSpec.NewlineMode.REQUIRED, 0, 0.3);

            String groupId = "BULK_INTO_" + bulkPos;
            for (int i = firstVar; i <= endIdx && i < tokens.size(); i++) {
                TokenInfo ti = tokens.get(i);
                if (ti.channel != 0) continue;
                if (",".equals(ti.text)) {
                    int nextTok = SelectColumnConstraintGen.nextVisibleToken(tokens, i + 1);
                    if (nextTok > 0) {
                        ConstraintSpec c = new ConstraintSpec(i, nextTok)
                            .newline(ConstraintSpec.NewlineMode.OPTIONAL)
                            .alignGroup(groupId);
                        SelectColumnConstraintGen.addDqlConstraint(constraints, gapMap, c);
                    }
                }
                if (";".equals(ti.text) || "FROM".equals(ti.upper)) break;
            }
        }
    }

    private static void processUsing(FormatOptions opts, List<TokenInfo> tokens,
                                       List<ConstraintSpec> constraints,
                                       Map<String, ConstraintSpec> gapMap,
                                       int startIdx, int endIdx) {
        List<Integer> execPositions = SelectColumnConstraintGen.findKeywords(tokens, startIdx, endIdx, "EXECUTE");
        for (int execPos : execPositions) {
            int immPos = SelectColumnConstraintGen.findNextKeyword(tokens, execPos + 1, endIdx, "IMMEDIATE");
            if (immPos < 0) continue;
            int usingPos = SelectColumnConstraintGen.findNextKeyword(tokens, immPos + 1, endIdx, "USING");
            if (usingPos < 0) continue;

            int firstExpr = SelectColumnConstraintGen.nextVisibleToken(tokens, usingPos + 1);
            if (firstExpr < 0) continue;

            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                usingPos, firstExpr, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);

            String groupId = "DML_USING_" + execPos;
            int semiIdx = -1;
            for (int i = firstExpr; i <= endIdx && i < tokens.size(); i++) {
                TokenInfo ti = tokens.get(i);
                if (ti.channel != 0) continue;
                if (",".equals(ti.text)) {
                    int nextTok = SelectColumnConstraintGen.nextVisibleToken(tokens, i + 1);
                    if (nextTok > 0) {
                        ConstraintSpec c = new ConstraintSpec(i, nextTok)
                            .newline(ConstraintSpec.NewlineMode.OPTIONAL)
                            .alignGroup(groupId);
                        SelectColumnConstraintGen.addDqlConstraint(constraints, gapMap, c);
                    }
                }
                if (";".equals(ti.text)) {
                    semiIdx = i;
                    break;
                }
            }
            // Pop continuation indent after the ; that ends the EXECUTE IMMEDIATE statement
            if (semiIdx >= 0) {
                int nextTok = SelectColumnConstraintGen.nextVisibleToken(tokens, semiIdx + 1);
                if (nextTok > 0) {
                    SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                        semiIdx, nextTok, ConstraintSpec.NewlineMode.REQUIRED, -1, 0.3);
                }
            }
        }
    }
}
