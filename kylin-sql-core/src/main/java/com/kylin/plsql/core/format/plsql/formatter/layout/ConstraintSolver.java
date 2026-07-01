package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public class ConstraintSolver {

    private final FormatOptions opts;
    private final List<TokenInfo> tokens;
    private final int indentSize;

    public ConstraintSolver(FormatOptions opts, List<TokenInfo> tokens) {
        this.opts = opts;
        this.tokens = tokens;
        this.indentSize = opts.getIndentSize() > 0 ? opts.getIndentSize() : 4;
    }

    public GapResult[] solve(List<GapConstraint> constraints) {
        int tokenCount = tokens.size();
        GapResult[] results = new GapResult[Math.max(tokenCount, 0)];
        if (tokenCount == 0) return results;

        for (int i = 0; i < results.length; i++) {
            results[i] = new GapResult();
        }

        int currentIndentLevel = 0;
        int savedIndentLevel = 0;

        List<GapConstraint> sorted = new ArrayList<>(constraints);
        sorted.sort(Comparator.comparingInt((GapConstraint c) -> c.fromTokenIdx)
            .thenComparingDouble(c -> c.breakPenalty));

        for (GapConstraint c : sorted) {
            if (c.toTokenIdx < 0 || c.toTokenIdx >= results.length) continue;
            GapResult r = results[c.toTokenIdx];

            if (c.newlineMode == GapConstraint.NewlineMode.REQUIRED) {
                r.newline = true;
                r.spaces = 1;

                if (c.endAlign != null && c.endAlign) {
                    r.indent = savedIndentLevel * indentSize;
                    currentIndentLevel = Math.max(0, currentIndentLevel - 1);
                } else if (c.indentDelta > 0) {
                    savedIndentLevel = currentIndentLevel;
                    currentIndentLevel += c.indentDelta;
                    r.indent = currentIndentLevel * indentSize;
                } else if (c.indentDelta < 0) {
                    currentIndentLevel = Math.max(0, currentIndentLevel + c.indentDelta);
                    r.indent = currentIndentLevel * indentSize;
                } else {
                    r.indent = currentIndentLevel * indentSize;
                }
            } else if (c.newlineMode == GapConstraint.NewlineMode.FORBIDDEN) {
                r.newline = false;
                r.spaces = clamp(c.preferredSpaces, c.minSpaces, c.maxSpaces);
            } else if (c.newlineMode == GapConstraint.NewlineMode.OPTIONAL && c.indentDelta != 0) {
                if (c.indentDelta > 0) {
                    savedIndentLevel = currentIndentLevel;
                    currentIndentLevel += c.indentDelta;
                } else {
                    currentIndentLevel = Math.max(0, currentIndentLevel + c.indentDelta);
                }
                r.indent = currentIndentLevel * indentSize;
            }
            // OPTIONAL: keep default (newline=false, spaces=1)
        }

        // 1) Alignment post-processing (:= alignment in declarations)
        applyAlignment(results, constraints);

        // 2) Line-width DP
        if (opts.getMaxLineWidth() > 0) {
            applyLineWidth(results, constraints);
        }

        return results;
    }

    // ──────────────────────────────────────────────
    //  Alignment:  alignGroupId → adjust spaces
    // ──────────────────────────────────────────────

    private void applyAlignment(GapResult[] results, List<GapConstraint> constraints) {
        Map<String, List<GapConstraint>> groups = new LinkedHashMap<>();
        for (GapConstraint c : constraints) {
            if (c.alignGroupId != null) {
                groups.computeIfAbsent(c.alignGroupId, k -> new ArrayList<>()).add(c);
            }
        }

        for (Map.Entry<String, List<GapConstraint>> entry : groups.entrySet()) {
            List<GapConstraint> group = entry.getValue();
            // compute the horizontal position of toTokenIdx for each member
            int maxPos = 0;
            int[] positions = new int[group.size()];
            for (int i = 0; i < group.size(); i++) {
                GapConstraint c = group.get(i);
                positions[i] = textWidthTo(c.toTokenIdx, results);
                if (positions[i] >= 0) maxPos = Math.max(maxPos, positions[i]);
            }

            for (int i = 0; i < group.size(); i++) {
                GapConstraint c = group.get(i);
                if (c.toTokenIdx < 0 || c.toTokenIdx >= results.length) continue;
                if (positions[i] < 0) continue;
                int padNeeded = maxPos - positions[i];
                if (padNeeded <= 0) continue;
                GapResult r = results[c.toTokenIdx];
                if (r != null && !r.newline) {
                    r.spaces = Math.max(1, r.spaces + padNeeded);
                }
            }
        }
    }

    private int textWidthTo(int toIdx, GapResult[] results) {
        int width = 0;
        for (int i = 0; i < toIdx && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            width += ti.text.length();
            if (i + 1 < results.length) {
                GapResult g = results[i + 1];
                if (g.newline) return -1;
                width += Math.max(1, g.spaces);
            } else {
                width += 1;
            }
        }
        return width;
    }

    // ──────────────────────────────────────────────
    //  Line-width DP:  break at lowest-penalty gap
    // ──────────────────────────────────────────────

    private void applyLineWidth(GapResult[] results, List<GapConstraint> constraints) {
        int maxWidth = opts.getMaxLineWidth();

        // Index OPTIONAL gaps by position for fast lookup
        Map<Integer, GapConstraint> optionalGaps = new LinkedHashMap<>();
        for (GapConstraint c : constraints) {
            if (c.newlineMode == GapConstraint.NewlineMode.OPTIONAL) {
                optionalGaps.put(c.toTokenIdx, c);
            }
        }

        int lineStart = 0;
        GapResult lineStartGap = null;
        int lineWidth = 0;

        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            GapResult g = results[i];
            if (g != null && g.newline) {
                lineStart = i;
                lineStartGap = g;
                lineWidth = g.indent;
            }

            int tw = ti.text.length();
            if (lineWidth + tw > maxWidth) {
                // Find the best break point on this line (lowest penalty)
                int bestBreak = -1;
                double bestPenalty = Double.MAX_VALUE;
                for (int j = lineStart; j < i; j++) {
                    GapConstraint opt = optionalGaps.get(j + 1);
                    if (opt != null && opt.breakPenalty < bestPenalty) {
                        bestBreak = j;
                        bestPenalty = opt.breakPenalty;
                    }
                }
                if (bestBreak >= 0) {
                    GapResult brk = results[bestBreak + 1];
                    if (brk != null) {
                        brk.newline = true;
                        int level = 0;
                        if (lineStartGap != null) {
                            level = lineStartGap.indent / indentSize;
                        }
                        brk.indent = level * indentSize;
                        // Restart line width from this point
                        i = bestBreak;
                        continue;
                    }
                }
            }
            lineWidth += tw;
            if (i + 1 < tokens.size()) {
                GapResult nextGap = results[i + 1];
                if (nextGap != null && !nextGap.newline) {
                    lineWidth += Math.max(1, nextGap.spaces);
                } else if (nextGap == null) {
                    lineWidth += 1;
                }
            }
        }
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
