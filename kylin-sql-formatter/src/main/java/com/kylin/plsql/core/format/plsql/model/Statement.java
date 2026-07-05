package com.kylin.plsql.core.format.plsql.model;

import java.util.List;

public class Statement {
    public enum Type {
        ASSIGNMENT,
        CONCATENATION,
        DML,
        EXECUTE_IMMEDIATE,
        OPEN,
        FETCH,
        CLOSE,
        COMMIT,
        ROLLBACK,
        SAVEPOINT,
        PROC_CALL,
        PRAGMA,
        RAISE,
        RETURN,
        EXIT,
        CONTINUE,
        NULL_STMT,
        GOTO,
        BLOCK_STMT
    }

    public final Type type;
    public final int startTokenIdx;
    public final int endTokenIdx;
    public final PlSqlBlock ownerBlock;

    public int assignTargetTokenIdx;
    public int assignOpTokenIdx;
    public List<ConcatenationSegment> concatSegments;

    public PlSqlBlock innerBlock;

    public int executeTargetIdx;
    public int intoTargetIdx;

    public Statement(Type type, int startTokenIdx, int endTokenIdx, PlSqlBlock ownerBlock) {
        this.type = type;
        this.startTokenIdx = startTokenIdx;
        this.endTokenIdx = endTokenIdx;
        this.ownerBlock = ownerBlock;
        this.assignTargetTokenIdx = -1;
        this.assignOpTokenIdx = -1;
        this.executeTargetIdx = -1;
        this.intoTargetIdx = -1;
    }
}
