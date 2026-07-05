package com.kylin.plsql.core.format.plsql.formatter.layout.dql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.CommaPosition;
import com.kylin.plsql.core.format.enums.SelectColumnMode;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class SelectColumnConstraintGen {

    private SelectColumnConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        List<Integer> selectPositions = findKeywords(tokens, startIdx, endIdx, "SELECT");
        for (int selPos : selectPositions) {
            int fromPos = findNextKeyword(tokens, selPos + 1, endIdx, "FROM");
            if (fromPos < 0) continue;
            processSelectList(opts, tokens, constraints, gapMap, selPos, fromPos);
        }
    }

    private static void processSelectList(FormatOptions opts, List<TokenInfo> tokens,
                                           List<ConstraintSpec> constraints,
                                           Map<String, ConstraintSpec> gapMap,
                                           int selectIdx, int fromIdx) {
        SelectColumnMode mode = opts.getSelectColumnMode();
        CommaPosition commaPos = opts.getCommaPosition();
        int selIndent = opts.getIndentSize();
        int perRow = opts.getSelectColumnsPerRow();

        List<Integer> commaPositions = new ArrayList<>();
        List<Integer> prevComma = new ArrayList<>();
        prevComma.add(selectIdx);
        for (int i = selectIdx + 1; i < fromIdx; i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (",".equals(ti.text)) {
                commaPositions.add(i);
                prevComma.add(i);
            }
        }
        prevComma.add(fromIdx);
        if (commaPositions.isEmpty()) return;

        String alignGroupId = (mode == SelectColumnMode.ALIGN) ? "SEL_COL_" + selectIdx : null;

        if (mode == SelectColumnMode.COMPACT) {
            if (perRow <= 0) {
                for (int i = 0; i < commaPositions.size(); i++) {
                    int comma = commaPositions.get(i);
                    addDqlGap(tokens, constraints, gapMap, comma, comma + 1, ConstraintSpec.NewlineMode.OPTIONAL, 0, 0.2);
                }
            } else {
                for (int i = 0; i < prevComma.size() - 1; i++) {
                    if (i > 0 && i % perRow == 0) {
                        addDqlGap(tokens, constraints, gapMap, prevComma.get(i), prevComma.get(i) + 1,
                            ConstraintSpec.NewlineMode.REQUIRED, 1, 0.2);
                    } else if (i < prevComma.size() - 1) {
                        addDqlGap(tokens, constraints, gapMap, prevComma.get(i), prevComma.get(i) + 1,
                            ConstraintSpec.NewlineMode.OPTIONAL, 0, 0.2);
                    }
                }
            }
        } else {
            int colIndent = selectIdx + selIndent + 1;
            int colStartIdx = colIndent < tokens.size() ? colIndent : selectIdx + 1;
            for (int i = 0; i < prevComma.size() - 1; i++) {
                int gapFrom = prevComma.get(i);
                int gapTo = (i == 0 && commaPos == CommaPosition.LEADING) ? prevComma.get(i + 1) : prevComma.get(i) + 1;
                if (i > 0 || mode == SelectColumnMode.ONE_PER_LINE || mode == SelectColumnMode.ALIGN) {
                    ConstraintSpec c = new ConstraintSpec(gapFrom, gapTo)
                        .newline(ConstraintSpec.NewlineMode.REQUIRED).indentDelta(0);
                    if (mode == SelectColumnMode.ALIGN && alignGroupId != null) {
                        c.alignGroup(alignGroupId);
                    }
                    addDqlConstraint(constraints, gapMap, c);
                }
            }
        }
    }

    public static int findNextKeyword(List<TokenInfo> tokens, int from, int to, String keyword) {
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0 && keyword.equals(ti.upper)) return i;
        }
        return -1;
    }

    public static List<Integer> findKeywords(List<TokenInfo> tokens, int from, int to, String keyword) {
        List<Integer> result = new ArrayList<>();
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0 && keyword.equals(ti.upper)) result.add(i);
        }
        return result;
    }

    public static void addDqlGap(List<TokenInfo> tokens, List<ConstraintSpec> constraints,
                           Map<String, ConstraintSpec> gapMap, int fromIdx, int toIdx,
                           ConstraintSpec.NewlineMode mode, int indentDelta, double penalty) {
        ConstraintSpec c = new ConstraintSpec(fromIdx, toIdx)
            .newline(mode)
            .indentDelta(indentDelta)
            .breakPenalty(penalty);
        addDqlConstraint(constraints, gapMap, c);
    }

    public static int prevVisibleToken(List<TokenInfo> tokens, int fromIdx) {
        int i = fromIdx;
        while (i >= 0 && tokens.get(i).channel != 0) i--;
        return i;
    }

    public static int nextVisibleToken(List<TokenInfo> tokens, int fromIdx) {
        int i = fromIdx;
        while (i < tokens.size() && tokens.get(i).channel != 0) i++;
        return i;
    }

    public static void addDqlConstraint(List<ConstraintSpec> constraints,
                                  Map<String, ConstraintSpec> gapMap, ConstraintSpec c) {
        String key = c.getFromTokenIdx() + ":" + c.getToTokenIdx();
        ConstraintSpec prev = gapMap.get(key);
        if (prev == null) {
            gapMap.put(key, c);
            constraints.add(c);
        } else {
            if (c.getNewlineMode() == ConstraintSpec.NewlineMode.REQUIRED
                && prev.getNewlineMode() != ConstraintSpec.NewlineMode.REQUIRED) {
                prev.newline(ConstraintSpec.NewlineMode.REQUIRED);
            }
            if (c.getIndentDelta() != 0 && prev.getIndentDelta() == 0) {
                prev.indentDelta(c.getIndentDelta());
            }
            if (c.getBreakPenalty() < prev.getBreakPenalty()) prev.breakPenalty(c.getBreakPenalty());
            if (c.getAlignGroupId() != null) prev.alignGroup(c.getAlignGroupId());
        }
    }
}
