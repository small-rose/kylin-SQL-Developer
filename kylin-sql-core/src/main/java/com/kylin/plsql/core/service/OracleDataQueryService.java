package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;

/** Oracle / OceanBase Oracle 模式的数据查询实现，使用 FETCH FIRST N ROWS ONLY 语法。 */
public class OracleDataQueryService extends DataQueryService {
    public OracleDataQueryService(ConnectionManager cm) {
        super(cm);
    }

    @Override
    protected String buildPreviewSql(String schema, String table, int limit) {
        return "SELECT * FROM " + quoteIdentifier(schema) + "." + quoteIdentifier(table)
            + " FETCH FIRST " + limit + " ROWS ONLY";
    }

    @Override
    protected String quoteIdentifier(String id) {
        if (id == null || id.isEmpty() || id.startsWith("\"")) return id;
        return "\"" + id + "\"";
    }
}
