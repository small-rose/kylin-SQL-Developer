package com.kylin.plsql.core.format.plsql.formatter.layout.solver;

import java.util.*;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.StructuralFrame;
import com.kylin.plsql.core.format.plsql.formatter.layout.align.AlignmentCover;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;

public class AlignmentResolver {

    private final List<TokenInfo> tokens;

    public AlignmentResolver(List<TokenInfo> tokens) {
        this.tokens = tokens;
    }

    public AlignmentCover[] resolve(PipelineContext ctx) {
        StructuralFrame[] frames = ctx.frames;
        int n = frames.length;

        int[] columns = computeColumns(frames);
        AlignmentCover[] covers = new AlignmentCover[n];
        for (int i = 0; i < n; i++) {
            covers[i] = new AlignmentCover();
        }

        Map<String, List<ConstraintSpec>> groups = new LinkedHashMap<>();
        for (ConstraintSpec c : ctx.specs) {
            if (c.getAlignGroupId() != null) {
                groups.computeIfAbsent(c.getAlignGroupId(), k -> new ArrayList<>()).add(c);
            }
        }

        for (List<ConstraintSpec> group : groups.values()) {
            int maxPos = 0;
            int[] positions = new int[group.size()];
            for (int i = 0; i < group.size(); i++) {
                ConstraintSpec c = group.get(i);
                int toIdx = c.getToTokenIdx();
                int pos = (toIdx >= 0 && toIdx < n) ? columns[toIdx] : -1;
                positions[i] = pos;
                if (pos >= 0) maxPos = Math.max(maxPos, pos);
            }

            for (int i = 0; i < group.size(); i++) {
                ConstraintSpec c = group.get(i);
                if (positions[i] < 0) continue;
                int padNeeded = maxPos - positions[i];
                if (padNeeded <= 0) continue;

                int toIdx = c.getToTokenIdx();
                AlignmentCover cover = covers[toIdx];
                StructuralFrame frame = frames[toIdx];

                if (!frame.isNewline()) {
                    cover.setSpaceAdjust(padNeeded);
                } else {
                    int maxCol = columns[toIdx] + padNeeded;
                    int baseIndent = frame.getIndentLevel() * ctx.indentSize;
                    if (maxCol > baseIndent) {
                        cover.setAlignColumn(maxCol);
                    }
                }
            }
        }
        ctx.covers = covers;
        return covers;
    }

    private int[] computeColumns(StructuralFrame[] frames) {
        int n = tokens.size();
        int[] cols = new int[n];
        int col = 0;
        for (int i = 0; i < n; i++) {
            TokenInfo ti = tokens.get(i);
            StructuralFrame f = (i < frames.length) ? frames[i] : null;
            if (f != null && f.isNewline()) {
                col = f.getIndentLevel() * 4;
            }
            if (ti.channel == 1) {
                cols[i] = -1;
                continue;
            }
            cols[i] = col;
            col += ti.text.length();
            if (i + 1 < frames.length) {
                StructuralFrame g = frames[i + 1];
                if (g != null && !g.isNewline()) {
                    col += g.getSpaces();
                }
            }
        }
        return cols;
    }
}
