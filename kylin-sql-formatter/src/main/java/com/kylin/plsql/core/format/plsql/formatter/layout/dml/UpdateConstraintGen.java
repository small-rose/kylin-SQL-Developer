package com.kylin.plsql.core.format.plsql.formatter.layout.dml;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.CommaPosition;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.formatter.layout.dql.SelectColumnConstraintGen;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class UpdateConstraintGen {

    private UpdateConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        List<Integer> updatePositions = SelectColumnConstraintGen.findKeywords(tokens, startIdx, endIdx, "UPDATE");
        for (int updPos : updatePositions) {
            int setPos = SelectColumnConstraintGen.findNextKeyword(tokens, updPos + 1, endIdx, "SET");
            if (setPos < 0) continue;

            int wherePos = SelectColumnConstraintGen.findNextKeyword(tokens, setPos + 1, endIdx, "WHERE");
            int setEnd = wherePos > 0 ? wherePos : endIdx;

            boolean perLine = opts.isDmlUpdateSetPerLine();
            boolean align = opts.isDmlUpdateSetAlign();
            int colsPerRow = opts.getDmlUpdateColumnsPerRow();
            CommaPosition commaPos = opts.getUpdateSetCommaPosition();
            int indentSize = opts.getIndentSize();

            List<Integer> assignCommas = new ArrayList<>();
            List<Integer> assignEquals = new ArrayList<>();
            for (int i = setPos + 1; i < setEnd; i++) {
                TokenInfo ti = tokens.get(i);
                if (ti.channel != 0) continue;
                if ("=".equals(ti.text)) assignEquals.add(i);
                if (",".equals(ti.text)) assignCommas.add(i);
            }

            if (assignCommas.isEmpty()) return;

            String alignGroupId = align ? ("UPDATE_SET_" + updPos) : null;

            if (perLine || colsPerRow > 0) {
                int effectivePerRow = perLine ? 1 : colsPerRow;
                for (int i = 0; i < assignCommas.size(); i++) {
                    int comma = assignCommas.get(i);
                    int nextTok = SelectColumnConstraintGen.nextVisibleToken(tokens, comma + 1);
                    if (nextTok > 0 && nextTok <= setEnd) {
                        boolean forceBreak = (effectivePerRow > 0 && (i + 1) % effectivePerRow == 0)
                            || perLine;
                        if (forceBreak) {
                            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                                comma, nextTok, ConstraintSpec.NewlineMode.REQUIRED, 0, 0.3);
                        } else {
                            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                                comma, nextTok, ConstraintSpec.NewlineMode.OPTIONAL, 0, 0.3);
                        }
                    }
                }
            }

            // = alignment (dmlUpdateSetAlign)
            if (align) {
                for (int eqIdx : assignEquals) {
                    int prevTok = SelectColumnConstraintGen.prevVisibleToken(tokens, eqIdx - 1);
                    if (prevTok >= 0) {
                        ConstraintSpec c = new ConstraintSpec(prevTok, eqIdx);
                        c.alignGroup(alignGroupId);
                        c.newline(ConstraintSpec.NewlineMode.OPTIONAL);
                        SelectColumnConstraintGen.addDqlConstraint(constraints, gapMap, c);
                    }
                }
            }

            // WHERE clause newline
            if (wherePos > 0) {
                int prevSetEnd = assignCommas.isEmpty() ? setPos : assignCommas.get(assignCommas.size() - 1);
                SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                    prevSetEnd, wherePos, ConstraintSpec.NewlineMode.OPTIONAL, 0, 0.3);
            }
        }
    }
}
