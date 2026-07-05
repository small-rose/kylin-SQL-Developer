package com.kylin.plsql.core.format.plsql.model;

public class Declaration {
    public enum Type {
        VARIABLE,
        CONSTANT,
        CURSOR,
        TYPE_DECL,
        SUBTYPE,
        EXCEPTION_DECL,
        PRAGMA_DECL,
        NESTED_BLOCK
    }

    public final Type type;
    public final String name;
    public final int startTokenIdx;
    public final int endTokenIdx;

    public Declaration(Type type, String name, int startTokenIdx, int endTokenIdx) {
        this.type = type;
        this.name = name;
        this.startTokenIdx = startTokenIdx;
        this.endTokenIdx = endTokenIdx;
    }
}
