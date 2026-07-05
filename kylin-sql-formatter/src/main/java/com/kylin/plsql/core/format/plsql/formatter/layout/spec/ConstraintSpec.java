package com.kylin.plsql.core.format.plsql.formatter.layout.spec;

public class ConstraintSpec {

    public enum NewlineMode {
        FORBIDDEN,
        REQUIRED,
        OPTIONAL
    }

    private final int fromTokenIdx;
    private final int toTokenIdx;

    private NewlineMode newlineMode = NewlineMode.OPTIONAL;
    private int indentDelta;
    private int minSpaces = 1;
    private int maxSpaces = Integer.MAX_VALUE;
    private int preferredSpaces = 1;
    private String alignGroupId;
    private double breakPenalty = 1.0;

    public ConstraintSpec(int from, int to) {
        this.fromTokenIdx = from;
        this.toTokenIdx = to;
    }

    public ConstraintSpec spaces(int min, int max, int pref) {
        this.minSpaces = min; this.maxSpaces = max; this.preferredSpaces = pref;
        return this;
    }

    public ConstraintSpec newline(NewlineMode mode) {
        this.newlineMode = mode;
        return this;
    }

    public ConstraintSpec indentDelta(int delta) {
        this.indentDelta = delta;
        return this;
    }

    public ConstraintSpec alignGroup(String groupId) {
        this.alignGroupId = groupId;
        return this;
    }

    public ConstraintSpec breakPenalty(double p) {
        this.breakPenalty = p;
        return this;
    }

    public int getFromTokenIdx() { return fromTokenIdx; }
    public int getToTokenIdx() { return toTokenIdx; }
    public NewlineMode getNewlineMode() { return newlineMode; }
    public int getIndentDelta() { return indentDelta; }
    public int getMinSpaces() { return minSpaces; }
    public int getMaxSpaces() { return maxSpaces; }
    public int getPreferredSpaces() { return preferredSpaces; }
    public String getAlignGroupId() { return alignGroupId; }
    public double getBreakPenalty() { return breakPenalty; }
}
