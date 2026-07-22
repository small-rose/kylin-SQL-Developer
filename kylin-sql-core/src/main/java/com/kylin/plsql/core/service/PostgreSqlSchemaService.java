package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;

/** PostgreSQL 模式的 Schema 查询实现，使用 pg_catalog。 */
public class PostgreSqlSchemaService extends SchemaService {
    public PostgreSqlSchemaService(ConnectionManager cm) {
        super(cm);
    }

    @Override
    protected String getTableQuerySql() {
        return "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = ? ORDER BY tablename";
    }

    @Override
    protected String getViewQuerySql() {
        return "SELECT viewname FROM pg_catalog.pg_views WHERE schemaname = ? ORDER BY viewname";
    }
}
