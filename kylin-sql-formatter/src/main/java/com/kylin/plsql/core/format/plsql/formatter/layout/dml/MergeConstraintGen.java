package com.kylin.plsql.core.format.plsql.formatter.layout.dml;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.formatter.layout.dql.SelectColumnConstraintGen;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class MergeConstraintGen {

    private MergeConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        List<Integer> mergePositions = SelectColumnConstraintGen.findKeywords(tokens, startIdx, endIdx, "MERGE");
        for (int mrgPos : mergePositions) {
            int intoPos = SelectColumnConstraintGen.findNextKeyword(tokens, mrgPos + 1, endIdx, "INTO");
            int usingPos = SelectColumnConstraintGen.findNextKeyword(tokens, mrgPos + 1, endIdx, "USING");
            int onPos = SelectColumnConstraintGen.findNextKeyword(tokens, mrgPos + 1, endIdx, "ON");
            int indentSize = opts.getIndentSize();

            // USING newline
            if (usingPos >= 0 && opts.isMergeUsingNewline()) {
                int gapFrom = intoPos > 0 ? intoPos : mrgPos;
                SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                    gapFrom, usingPos, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);
            }

            // ON newline
            if (onPos >= 0 && opts.isMergeOnNewline()) {
                int usingEnd = findUsingEnd(tokens, usingPos, onPos);
                SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                    usingEnd, onPos, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);
            }

            // WHEN MATCHED / WHEN NOT MATCHED newlines
            if (opts.isMergeWhenNewline()) {
                List<Integer> whenPositions = new ArrayList<>();
                for (int i = onPos + 1; i <= endIdx && i < tokens.size(); i++) {
                    TokenInfo ti = tokens.get(i);
                    if (ti.channel != 0) continue;
                    if ("WHEN".equals(ti.upper)) whenPositions.add(i);
                }

                for (int whenPos : whenPositions) {
                    int prevGap = SelectColumnConstraintGen.prevVisibleToken(tokens, whenPos - 1);
                    if (prevGap >= 0) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            prevGap, whenPos, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);
                    }

                    int thenPos = SelectColumnConstraintGen.findNextKeyword(tokens, whenPos + 1, endIdx, "THEN");
                    if (thenPos >= 0) {
                        int thenNext = SelectColumnConstraintGen.nextVisibleToken(tokens, thenPos + 1);
                        if (thenNext > 0) {
                            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                                thenPos, thenNext, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);
                        }
                    }
                }
            }

            // UPDATE SET = alignment (mergeUpdateSetAlign)
            if (opts.isMergeUpdateSetAlign()) {
                int updatePos = SelectColumnConstraintGen.findNextKeyword(tokens, mrgPos + 1, endIdx, "UPDATE");
                if (updatePos >= 0) {
                    String alignGroupId = "MERGE_SET_" + mrgPos;
                    for (int i = updatePos + 1; i <= endIdx && i < tokens.size(); i++) {
                        TokenInfo ti = tokens.get(i);
                        if (ti.channel != 0) continue;
                        if ("=".equals(ti.text)) {
                            int prevTok = SelectColumnConstraintGen.prevVisibleToken(tokens, i - 1);
                            if (prevTok >= 0) {
                                ConstraintSpec c = new ConstraintSpec(prevTok, i);
                                c.alignGroup(alignGroupId);
                                SelectColumnConstraintGen.addDqlConstraint(constraints, gapMap, c);
                            }
                        }
                        if (";".equals(ti.text)) break;
                    }
                }
            }
        }
    }

    private static int findUsingEnd(List<TokenInfo> tokens, int usingPos, int onPos) {
        int last = usingPos;
        for (int i = usingPos + 1; i < onPos && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0) last = i;
        }
        return last;
    }
}
