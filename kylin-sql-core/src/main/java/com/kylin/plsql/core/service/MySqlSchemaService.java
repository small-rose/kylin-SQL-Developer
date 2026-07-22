package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;

/** MySQL / MariaDB / OceanBase MySQL 模式的 Schema 查询实现，使用 information_schema。 */
public class MySqlSchemaService extends SchemaService {
    public MySqlSchemaService(ConnectionManager cm) {
        super(cm);
    }

    @Override
    protected String getTableQuerySql() {
        return "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name";
    }

    @Override
    protected String getViewQuerySql() {
        return "SELECT table_name FROM information_schema.views WHERE table_schema = ? ORDER BY table_name";
    }
}
