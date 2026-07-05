package com.kylin.plsql.core.format.plsql.model;

public class ConcatenationSegment {
    public enum Type {
        STRING_LITERAL,
        VARIABLE_REF,
        EXPRESSION
    }

    public final Type type;
    public final int startTokenIdx;
    public final int endTokenIdx;
    public final String text;

    public ConcatenationSegment(Type type, int startTokenIdx, int endTokenIdx, String text) {
        this.type = type;
        this.startTokenIdx = startTokenIdx;
        this.endTokenIdx = endTokenIdx;
        this.text = text;
    }
}
