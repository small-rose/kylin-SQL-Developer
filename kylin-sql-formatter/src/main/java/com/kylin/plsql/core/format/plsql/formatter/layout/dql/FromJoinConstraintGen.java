package com.kylin.plsql.core.format.plsql.formatter.layout.dql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;

public class FromJoinConstraintGen {

    private FromJoinConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        List<Integer> selectPositions = SelectColumnConstraintGen.findKeywords(tokens, startIdx, endIdx, "SELECT");
        for (int selPos : selectPositions) {
            int fromPos = SelectColumnConstraintGen.findNextKeyword(tokens, selPos + 1, endIdx, "FROM");
            if (fromPos < 0) continue;

            boolean fromNewline = opts.isFromClauseNewline();
            int fromIndent = opts.getFromClauseIndent();
            int indentSize = opts.getIndentSize();

            if (fromNewline) {
                int gapFrom = fromPos;
                int gapTo = SelectColumnConstraintGen.nextVisibleToken(tokens, fromPos + 1);
                if (gapTo < tokens.size()) {
                    SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                        gapFrom, gapTo, ConstraintSpec.NewlineMode.REQUIRED,
                        fromIndent, 0.4);
                }
            }

            int wherePos = SelectColumnConstraintGen.findNextKeyword(tokens, fromPos + 1, endIdx, "WHERE");
            int limitEnd = wherePos > 0 ? wherePos : endIdx;

            boolean joinNewline = opts.isDmlJoinOnNewLine();
            boolean joinIndent = opts.isDmlJoinIndent();
            boolean onAlign = opts.isJoinOnAlign();

            int scanFrom = fromPos + 1;
            while (scanFrom < limitEnd) {
                int joinPos = SelectColumnConstraintGen.findNextKeyword(tokens, scanFrom, limitEnd, "JOIN");
                if (joinPos < 0) {
                    joinPos = SelectColumnConstraintGen.findNextKeyword(tokens, scanFrom, limitEnd, "INNER");
                    if (joinPos >= 0) {
                        int nextJoin = SelectColumnConstraintGen.findNextKeyword(tokens, joinPos + 1, limitEnd, "JOIN");
                        if (nextJoin < 0) joinPos = -1;
                        else joinPos = nextJoin;
                    }
                }
                if (joinPos < 0) break;

                if (joinNewline) {
                    int prevKw = prevVisibleKeyword(tokens, joinPos - 1);
                    if (prevKw >= fromPos) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            prevKw, joinPos, ConstraintSpec.NewlineMode.REQUIRED,
                            0, 0.4);
                    }
                }

                int onPos = SelectColumnConstraintGen.findNextKeyword(tokens, joinPos + 1, limitEnd, "ON");
                if (onPos >= 0 && onPos < limitEnd) {
                    int onNext = SelectColumnConstraintGen.nextVisibleToken(tokens, onPos + 1);
                    if (onNext < tokens.size()) {
                        if (onAlign) {
                            ConstraintSpec c = new ConstraintSpec(onPos, onNext)
                                .newline(ConstraintSpec.NewlineMode.REQUIRED).indentDelta(0);
                            c.alignGroup("JOIN_ON_" + joinPos);
                            SelectColumnConstraintGen.addDqlConstraint(constraints, gapMap, c);
                        } else {
                            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                                onPos, onNext, ConstraintSpec.NewlineMode.REQUIRED,
                                0, 0.4);
                        }
                    }
                }

                scanFrom = joinPos + 1;
            }
        }
    }

    private static int prevVisibleKeyword(List<TokenInfo> tokens, int from) {
        for (int i = from; i >= 0; i--) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            String u = ti.upper;
            if ("JOIN".equals(u) || "INNER".equals(u) || "LEFT".equals(u)
                || "RIGHT".equals(u) || "FULL".equals(u) || "CROSS".equals(u)
                || "OUTER".equals(u) || "NATURAL".equals(u)
                || "FROM".equals(u) || ",".equals(ti.text)) {
                return i;
            }
        }
        return -1;
    }
}
