package com.kylin.plsql.core.db.services;

import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.type.DbTypeSpec;
import com.kylin.plsql.core.db.type.OceanBaseMySqlSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OceanBase MySQL 模式 JDBC 行为实现 / OceanBase MySQL-mode JDBC behavior. */
public class OceanBaseMySqlJdbcService extends JdbcService {
    public static final OceanBaseMySqlJdbcService INSTANCE = new OceanBaseMySqlJdbcService();

    @Override public DbTypeSpec spec() { return OceanBaseMySqlSpec.INSTANCE; }

    @Override protected String buildProtocolUrl(ConnectionInfo info) {
        return String.format("jdbc:oceanbase:mysql://%s:%d/%s",
            info.getHost(), info.getPort(), info.getServiceName());
    }

    @Override public ParsedUrl parseJdbcUrl(String url) {
        String prefix = "jdbc:oceanbase:mysql://";
        if (!url.toLowerCase().startsWith(prefix)) return null;
        return parseHostPortDb(url, prefix.length());
    }

    @Override public int getInitFailTimeout() { return 15000; }

    @Override public boolean supportsSetSchema() { return true; }

    @Override public Map<String, String> getDefaultJdbcParams(ConnectionInfo info) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("useServerPrepStmts", "true");
        m.put("characterEncoding", "UTF-8");
        m.put("useCompression", "true");
        m.put("rewriteBatchedStatements", "true");
        m.put("useLocalSessionState", "true");
        return m;
    }

    @Override public Map<String, List<String>> getSystemViewNames() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("information_schema", List.of(
            "tables", "columns", "views", "routines", "parameters",
            "key_column_usage", "table_constraints", "statistics",
            "triggers", "character_sets", "collations", "partitions",
            "check_constraints", "referential_constraints"
        ));
        m.put("mysql", List.of(
            "user", "db", "tables_priv", "columns_priv",
            "procs_priv", "proxies_priv", "role_edges", "default_roles"
        ));
        return m;
    }
}
