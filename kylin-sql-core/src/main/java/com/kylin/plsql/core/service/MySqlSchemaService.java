package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;

/** MySQL / MariaDB / OceanBase MySQL 模式的 Schema 查询实现，使用 information_schema。 */
public class MySqlSchemaService extends SchemaService {
    public MySqlSchemaService(ConnectionManager cm) {
        super(cm);
    }

    @Override protected String getTableQuerySql() {
        return "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name";
    }

    @Override protected String getViewQuerySql() {
        return "SELECT table_name FROM information_schema.views WHERE table_schema = ? ORDER BY table_name";
    }

    @Override protected String getIndexQuerySql() { return null; }

    @Override protected String getSequenceQuerySql() { return null; }

    @Override protected String getFunctionQuerySql() {
        return "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'FUNCTION' ORDER BY routine_name";
    }

    @Override protected String getProcedureQuerySql() {
        return "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'PROCEDURE' ORDER BY routine_name";
    }

    @Override protected String getTableCommentQuerySql() {
        return "SELECT table_name, table_comment FROM information_schema.tables WHERE table_schema = ?";
    }

    @Override protected String getSchemaFallbackSql() {
        return "SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_schema NOT IN ('information_schema','pg_catalog','pg_toast') ORDER BY table_schema";
    }

    @Override protected String[] getTypeDefs() {
        return new String[]{
            "模式;SCHEMA;false",
            "表;TABLE;true",
            "视图;VIEW;false",
            "函数;FUNCTION;false",
            "过程;PROCEDURE;false"
        };
    }
}
