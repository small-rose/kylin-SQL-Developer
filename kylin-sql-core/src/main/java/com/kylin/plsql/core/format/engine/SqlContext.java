package com.kylin.plsql.core.format.engine;

/** SQL context types tracked by FormatContext for contextual formatting. */
public enum SqlContext {
    NONE,
    SELECT_LIST,
    FROM_CLAUSE,
    WHERE_CLAUSE,
    HAVING_CLAUSE,
    GROUP_BY,
    ORDER_BY,
    SET_OPERATOR,
    INSERT_COLS,
    INSERT_VALS,
    SET_CLAUSE,
    DDL_COL_DEFS,
    CONSTRAINT_LIST,
    INDEX_COLS,
    PARAM_LIST,
    IN_LIST,
    CTE_CLAUSE,
    SUBQUERY,
    PLSQL_BLOCK,
    PLSQL_DECLARE,
    PLSQL_EXCEPTION,
    FOR_LOOP,
    CASE_EXPR,
    MERGE_CLAUSE
}
