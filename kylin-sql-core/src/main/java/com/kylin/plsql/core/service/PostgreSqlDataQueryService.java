package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;

/** PostgreSQL 模式的数据查询实现，使用 LIMIT 语法。 */
public class PostgreSqlDataQueryService extends DataQueryService {
    public PostgreSqlDataQueryService(ConnectionManager cm) {
        super(cm);
    }

    @Override
    protected String buildPreviewSql(String schema, String table, int limit) {
        return "SELECT * FROM " + quoteIdentifier(schema) + "." + quoteIdentifier(table)
            + " LIMIT " + limit;
    }

    @Override
    protected String quoteIdentifier(String id) {
        if (id == null || id.isEmpty() || id.startsWith("\"")) return id;
        return "\"" + id + "\"";
    }
}
