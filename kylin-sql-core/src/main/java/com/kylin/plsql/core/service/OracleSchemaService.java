package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;

/** Oracle / OceanBase Oracle 模式的 Schema 查询实现，使用 ALL_TABLES / ALL_VIEWS 数据字典。 */
public class OracleSchemaService extends SchemaService {
    public OracleSchemaService(ConnectionManager cm) {
        super(cm);
    }

    @Override
    protected String getTableQuerySql() {
        return "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name";
    }

    @Override
    protected String getViewQuerySql() {
        return "SELECT view_name FROM all_views WHERE owner = ? ORDER BY view_name";
    }
}
