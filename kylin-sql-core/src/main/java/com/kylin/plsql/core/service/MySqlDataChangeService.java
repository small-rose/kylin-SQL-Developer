package com.kylin.plsql.core.service;

/** MySQL / MariaDB / OceanBase MySQL 模式的 DML 差异提交实现。使用反引号引用标识符。 */
public class MySqlDataChangeService extends DataChangeService {
    @Override
    protected String quoteId(String id) {
        if (id == null) return "";
        return "`" + id.replace("`", "``") + "`";
    }

    @Override
    protected String fullName(String schema, String table) {
        return schema != null && !schema.isEmpty() ? quoteId(schema) + "." + quoteId(table) : quoteId(table);
    }
}
