package com.kylin.plsql.core.format.plsql.formatter.layout;

public class GapConstraint {
    public int fromTokenIdx;
    public int toTokenIdx;
    public int minSpaces = 1;
    public int maxSpaces = Integer.MAX_VALUE;
    public int preferredSpaces = 1;
    public NewlineMode newlineMode = NewlineMode.OPTIONAL;
    public int indentDelta;
    public Boolean endAlign;
    public String alignGroupId;
    public double breakPenalty = 1.0;

    public enum NewlineMode { FORBIDDEN, REQUIRED, OPTIONAL }

    public GapConstraint(int from, int to) {
        this.fromTokenIdx = from;
        this.toTokenIdx = to;
    }

    public GapConstraint spaces(int min, int max, int pref) {
        this.minSpaces = min; this.maxSpaces = max; this.preferredSpaces = pref;
        return this;
    }

    public GapConstraint forceNewline(boolean force) {
        this.newlineMode = force ? NewlineMode.REQUIRED : NewlineMode.FORBIDDEN;
        return this;
    }

    public GapConstraint indentDelta(int delta) {
        this.indentDelta = delta;
        return this;
    }

    public GapConstraint endAlign(boolean align) {
        this.endAlign = align;
        return this;
    }

    public GapConstraint breakPenalty(double p) {
        this.breakPenalty = p;
        return this;
    }
}
