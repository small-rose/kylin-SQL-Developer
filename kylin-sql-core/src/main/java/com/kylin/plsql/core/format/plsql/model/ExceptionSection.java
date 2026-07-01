package com.kylin.plsql.core.format.plsql.model;

import java.util.ArrayList;
import java.util.List;

public class ExceptionSection {
    public final int startTokenIdx;
    public final int endTokenIdx;
    public final List<Handler> handlers;

    public ExceptionSection(int startTokenIdx, int endTokenIdx) {
        this.startTokenIdx = startTokenIdx;
        this.endTokenIdx = endTokenIdx;
        this.handlers = new ArrayList<>();
    }

    public static class Handler {
        public final String condition;
        public final int whenTokenIdx;
        public final int thenTokenIdx;
        public final int handlerEndIdx;
        public final List<Statement> statements;

        public Handler(String condition, int whenTokenIdx, int thenTokenIdx,
                       int handlerEndIdx) {
            this.condition = condition;
            this.whenTokenIdx = whenTokenIdx;
            this.thenTokenIdx = thenTokenIdx;
            this.handlerEndIdx = handlerEndIdx;
            this.statements = new ArrayList<>();
        }
    }
}
