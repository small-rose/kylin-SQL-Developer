package com.kylin.plsql.core.db.services;

import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.type.DbTypeSpec;
import com.kylin.plsql.core.db.type.PostgreSqlSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** PostgreSQL JDBC 行为实现 / PostgreSQL JDBC connection behavior. */
public class PostgreSqlJdbcService extends JdbcService {
    public static final PostgreSqlJdbcService INSTANCE = new PostgreSqlJdbcService();

    @Override public DbTypeSpec spec() { return PostgreSqlSpec.INSTANCE; }

    @Override protected String buildProtocolUrl(ConnectionInfo info) {
        return String.format("jdbc:postgresql://%s:%d/%s",
            info.getHost(), info.getPort(), info.getServiceName());
    }

    @Override public ParsedUrl parseJdbcUrl(String url) {
        String prefix = "jdbc:postgresql://";
        if (!url.toLowerCase().startsWith(prefix)) return null;
        return parseHostPortDb(url, prefix.length());
    }

    /** PostgreSQL 支持 HikariCP setSchema */
    @Override public boolean supportsSetSchema() { return true; }

    @Override public Map<String, String> getDefaultJdbcParams(ConnectionInfo info) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ApplicationName", "KylinSQL");
        m.put("stringtype", "unspecified");
        m.put("prepareThreshold", "5");
        m.put("ssl", "false");
        m.put("sslmode", "prefer");
        m.put("reWriteBatchedInserts", "true");
        return m;
    }
}
