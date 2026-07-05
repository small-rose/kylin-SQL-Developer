package com.kylin.plsql.core.format.plsql.formatter.layout.dql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.WhereAndPosition;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;

public class WhereConstraintGen {

    private WhereConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        List<Integer> selectPositions = SelectColumnConstraintGen.findKeywords(tokens, startIdx, endIdx, "SELECT");
        for (int selPos : selectPositions) {
            int wherePos = SelectColumnConstraintGen.findNextKeyword(tokens, selPos + 1, endIdx, "WHERE");
            if (wherePos < 0) continue;

            WhereAndPosition pos = opts.getDmlWhereAndPosition();
            int indentSize = opts.getIndentSize();
            int whereIndent = opts.getWhereIndentSize();

            int nextKw = findClauseEnd(tokens, wherePos + 1, endIdx);
            if (nextKw < 0) nextKw = endIdx;

            boolean isLineStart = (pos == WhereAndPosition.LINE_START);
            boolean isLineEnd = (pos == WhereAndPosition.LINE_END);

            for (int i = wherePos + 1; i < nextKw; i++) {
                TokenInfo ti = tokens.get(i);
                if (ti.channel != 0) continue;
                String u = ti.upper;
                if ("AND".equals(u) || "OR".equals(u)) {
                    int prevVis = SelectColumnConstraintGen.prevVisibleToken(tokens, i - 1);
                    if (prevVis < wherePos) continue;

                    ConstraintSpec.NewlineMode nlMode = isLineStart
                        ? ConstraintSpec.NewlineMode.REQUIRED
                        : ConstraintSpec.NewlineMode.OPTIONAL;
                    int indentDelta = whereIndent;

                    if (isLineStart) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            prevVis, i, nlMode, indentDelta, 0.3);
                    } else if (isLineEnd) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            i - 1, i, nlMode, indentDelta, 0.3);
                    }
                }
            }
        }
    }

    private static int findClauseEnd(List<TokenInfo> tokens, int from, int endIdx) {
        for (int i = from; i <= endIdx && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            String u = ti.upper;
            if ("GROUP".equals(u) || "ORDER".equals(u) || "HAVING".equals(u)
                || "UNION".equals(u) || "INTERSECT".equals(u) || "MINUS".equals(u)
                || "FETCH".equals(u) || "FOR".equals(u)) {
                return i;
            }
        }
        return -1;
    }
}
