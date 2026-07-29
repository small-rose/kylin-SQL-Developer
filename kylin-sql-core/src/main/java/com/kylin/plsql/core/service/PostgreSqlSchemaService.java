package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;

/** PostgreSQL 模式的 Schema 查询实现，使用 pg_catalog。 */
public class PostgreSqlSchemaService extends SchemaService {
    public PostgreSqlSchemaService(ConnectionManager cm) {
        super(cm);
    }

    @Override protected String getTableQuerySql() {
        return "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = ? ORDER BY tablename";
    }

    @Override protected String getViewQuerySql() {
        return "SELECT viewname FROM pg_catalog.pg_views WHERE schemaname = ? ORDER BY viewname";
    }

    @Override protected String getIndexQuerySql() {
        return "SELECT indexname FROM pg_catalog.pg_indexes WHERE schemaname = ? ORDER BY indexname";
    }

    @Override protected String getSequenceQuerySql() {
        return "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ? ORDER BY sequence_name";
    }

    @Override protected String getFunctionQuerySql() {
        return "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'FUNCTION' ORDER BY routine_name";
    }

    @Override protected String getProcedureQuerySql() {
        return "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'PROCEDURE' ORDER BY routine_name";
    }

    @Override protected String getTableCommentQuerySql() {
        return "SELECT tablename, obj_description((schemaname||'.'||tablename)::regclass, 'pg_class') FROM pg_catalog.pg_tables WHERE schemaname = ?"
             + " UNION SELECT viewname, obj_description((schemaname||'.'||viewname)::regclass, 'pg_class') FROM pg_catalog.pg_views WHERE schemaname = ?";
    }

    @Override protected String getSchemaFallbackSql() {
        return "SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_schema NOT IN ('information_schema','pg_catalog','pg_toast') ORDER BY table_schema";
    }

    @Override protected String[] getTypeDefs() {
        return new String[]{
            "模式;SCHEMA;false",
            "表;TABLE;true",
            "视图;VIEW;false",
            "索引;INDEX;false",
            "序列;SEQUENCE;false",
            "函数;FUNCTION;false",
            "过程;PROCEDURE;false"
        };
    }
}
