package com.kylin.plsql.core.format.plsql.formatter.layout.solver;

import java.util.*;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.StructuralFrame;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;

public class LineWidthResolver {

    public void resolve(PipelineContext ctx) {
        int maxWidth = ctx.opts.getMaxLineWidth();
        StructuralFrame[] frames = ctx.frames;
        int n = ctx.tokenCount;

        Map<Integer, ConstraintSpec> optionalGaps = new LinkedHashMap<>();
        for (ConstraintSpec c : ctx.specs) {
            if (c.getNewlineMode() == ConstraintSpec.NewlineMode.OPTIONAL) {
                optionalGaps.put(c.getToTokenIdx(), c);
            }
        }

        int lineStart = 0;
        int lineStartLevel = 0;
        int lineWidth = 0;
        int breakCount = 0;

        for (int i = 0; i < n; i++) {
            TokenInfo ti = ctx.tokens.get(i);
            if (ti.channel > 0) continue;

            StructuralFrame f = frames[i];
            if (f.isNewline()) {
                lineStart = i;
                lineStartLevel = f.getIndentLevel();
                lineWidth = lineStartLevel * ctx.indentSize;
            }

            int tw = ti.text.length();
            if (maxWidth > 0 && lineWidth + tw > maxWidth) {
                int bestIdx = -1;

                // 1) Prefer the nearest || gap to the LEFT of overflow
                for (int j = i - 1; j >= lineStart; j--) {
                    ConstraintSpec opt = optionalGaps.get(j + 1);
                    if (opt != null && "|".equals(ctx.tokens.get(j + 1).text)
                            && opt.getBreakPenalty() < 0.5) {
                        bestIdx = j;
                        break;
                    }
                }

                // 2) Fallback: lowest-cost optional gap
                if (bestIdx < 0) {
                    double bestCost = Double.MAX_VALUE;
                    for (int j = lineStart; j < i; j++) {
                        ConstraintSpec opt = optionalGaps.get(j + 1);
                        if (opt == null) continue;

                        double cost = opt.getBreakPenalty();
                        if (breakCount > 0) {
                            cost += breakCount * 20.0;
                        }
                        if (opt.getIndentDelta() < 0) {
                            cost -= 0.5;
                        }
                        if (cost < bestCost) {
                            bestCost = cost;
                            bestIdx = j;
                        }
                    }
                    if (bestIdx >= 0 && bestCost >= 100.0) {
                        bestIdx = -1;
                    }
                }

                if (bestIdx >= 0) {
                    StructuralFrame brk = frames[bestIdx + 1];
                    int level = lineStartLevel;
                    ConstraintSpec opt = optionalGaps.get(bestIdx + 1);
                    if (opt != null && opt.getIndentDelta() != 0) {
                        level = Math.max(0, level + opt.getIndentDelta());
                    }
                    brk.setNewline(true);
                    brk.setIndentLevel(level);
                    breakCount++;
                    i = bestIdx;
                    continue;
                }
            }

            lineWidth += tw;
            if (i + 1 < n) {
                StructuralFrame next = frames[i + 1];
                if (!next.isNewline()) {
                    lineWidth += next.getSpaces();
                }
            }
        }
    }
}
