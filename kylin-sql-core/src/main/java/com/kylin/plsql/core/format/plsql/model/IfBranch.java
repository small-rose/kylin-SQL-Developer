package com.kylin.plsql.core.format.plsql.model;

import java.util.ArrayList;
import java.util.List;

public class IfBranch {
    public enum Type { IF, ELSIF }

    public final Type type;
    public final int conditionStartIdx;
    public final int thenTokenIdx;
    public final int branchEndIdx;
    public final List<Statement> statements;

    public IfBranch(Type type, int conditionStartIdx, int thenTokenIdx, int branchEndIdx) {
        this.type = type;
        this.conditionStartIdx = conditionStartIdx;
        this.thenTokenIdx = thenTokenIdx;
        this.branchEndIdx = branchEndIdx;
        this.statements = new ArrayList<>();
    }
}
