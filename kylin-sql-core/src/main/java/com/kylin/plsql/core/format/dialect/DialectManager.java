package com.kylin.plsql.core.format.dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DialectManager {

    private static final Map<String, SqlDialect> DIALECTS = new ConcurrentHashMap<>();

    static {
        register(new OracleDialect());
        register(new OceanBaseDialect());
        register(new MySqlDialect());
        register(new PostgreSqlDialect());
    }

    public static void register(SqlDialect dialect) {
        DIALECTS.put(dialect.getName().toLowerCase(), dialect);
    }

    public static SqlDialect forName(String name) {
        if (name == null) return DIALECTS.get("oracle");
        SqlDialect d = DIALECTS.get(name.toLowerCase());
        return d != null ? d : DIALECTS.get("oracle");
    }

    public static SqlDialect detect(Connection conn) {
        if (conn == null) return DIALECTS.get("oracle");
        try {
            String product = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (product.contains("mysql") || product.contains("mariadb")) {
                return DIALECTS.get("mysql");
            }
            if (product.contains("postgresql") || product.contains("edb")) {
                return DIALECTS.get("postgresql");
            }
            if (product.contains("oceanbase")) {
                return DIALECTS.get("oceanbase");
            }
            // oracle, h2, others → default to Oracle dialect
            return DIALECTS.get("oracle");
        } catch (SQLException e) {
            return DIALECTS.get("oracle");
        }
    }

    public static SqlDialect detect(String dbProduct) {
        if (dbProduct == null) return DIALECTS.get("oracle");
        String p = dbProduct.toLowerCase();
        if (p.contains("mysql") || p.contains("mariadb")) return DIALECTS.get("mysql");
        if (p.contains("postgresql") || p.contains("edb")) return DIALECTS.get("postgresql");
        if (p.contains("oceanbase")) return DIALECTS.get("oceanbase");
        return DIALECTS.get("oracle");
    }
}
