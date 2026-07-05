package com.kylin.plsql.core.format.plsql.model;

import java.util.ArrayList;
import java.util.List;

public class CaseWhen {
    public final int whenTokenIdx;
    public final int thenTokenIdx;
    public final int whenEndIdx;
    public final List<Statement> statements;

    public CaseWhen(int whenTokenIdx, int thenTokenIdx, int whenEndIdx) {
        this.whenTokenIdx = whenTokenIdx;
        this.thenTokenIdx = thenTokenIdx;
        this.whenEndIdx = whenEndIdx;
        this.statements = new ArrayList<>();
    }
}
