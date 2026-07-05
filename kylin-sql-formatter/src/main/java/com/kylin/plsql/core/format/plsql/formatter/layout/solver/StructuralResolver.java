package com.kylin.plsql.core.format.plsql.formatter.layout.solver;

import java.util.*;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.StructuralFrame;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;

public class StructuralResolver {

    public StructuralFrame[] resolve(PipelineContext ctx) {
        int n = ctx.tokenCount;
        StructuralFrame[] frames = new StructuralFrame[n];
        for (int i = 0; i < n; i++) {
            frames[i] = new StructuralFrame();
        }

        List<ConstraintSpec> sorted = new ArrayList<>(ctx.specs);
        sorted.sort(Comparator.comparingInt(ConstraintSpec::getFromTokenIdx));

        int currentLevel = 0;

        for (ConstraintSpec c : sorted) {
            int toIdx = c.getToTokenIdx();
            if (toIdx < 0 || toIdx >= n) continue;

            StructuralFrame f = frames[toIdx];

            switch (c.getNewlineMode()) {
                case REQUIRED:
                    f.setNewline(true);
                    f.setSpaces(1);
                    if (c.getIndentDelta() != 0) {
                        currentLevel = Math.max(0, currentLevel + c.getIndentDelta());
                    }
                    f.setIndentLevel(currentLevel);
                    break;
                case FORBIDDEN:
                    f.setNewline(false);
                    f.setSpaces(clamp(c.getPreferredSpaces(), c.getMinSpaces(), c.getMaxSpaces()));
                    break;
                case OPTIONAL:
                    break;
            }
        }

        ctx.frames = frames;
        return frames;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
