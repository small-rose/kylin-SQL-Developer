package com.kylin.plsql.core.format.plsql.formatter.layout.solver;

import com.kylin.plsql.core.format.plsql.formatter.layout.plan.StructuralFrame;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.FinalLayout;
import com.kylin.plsql.core.format.plsql.formatter.layout.align.AlignmentCover;

public class LayoutMerger {

    public FinalLayout[] merge(PipelineContext ctx) {
        StructuralFrame[] frames = ctx.frames;
        AlignmentCover[] covers = ctx.covers;
        int n = ctx.tokenCount;

        FinalLayout[] results = new FinalLayout[n];
        for (int i = 0; i < n; i++) {
            StructuralFrame f = (i < frames.length) ? frames[i] : new StructuralFrame();
            AlignmentCover c = (i < covers.length) ? covers[i] : new AlignmentCover();

            FinalLayout out = new FinalLayout();
            out.setNewline(f.isNewline());
            int baseIndent = f.getIndentLevel() * ctx.indentSize;

            if (c.isActive()) {
                out.setIndent(Math.max(baseIndent, c.getAlignColumn()));
                if (!f.isNewline()) {
                    out.setSpaces(Math.max(0, f.getSpaces() + c.getSpaceAdjust()));
                } else {
                    out.setSpaces(f.getSpaces());
                }
            } else {
                out.setIndent(baseIndent);
                out.setSpaces(f.getSpaces());
            }
            results[i] = out;
        }
        ctx.layout = results;
        return results;
    }
}
