package com.kylin.plsql.core.db.services;

import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.type.DbTypeSpec;
import com.kylin.plsql.core.db.type.MariaDbSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** MariaDB JDBC 行为实现 / MariaDB JDBC connection behavior. */
public class MariaDbJdbcService extends JdbcService {
    public static final MariaDbJdbcService INSTANCE = new MariaDbJdbcService();

    @Override public DbTypeSpec spec() { return MariaDbSpec.INSTANCE; }

    @Override protected String buildProtocolUrl(ConnectionInfo info) {
        return String.format("jdbc:mariadb://%s:%d/%s",
            info.getHost(), info.getPort(), info.getServiceName());
    }

    @Override public ParsedUrl parseJdbcUrl(String url) {
        String prefix = "jdbc:mariadb://";
        if (!url.toLowerCase().startsWith(prefix)) return null;
        return parseHostPortDb(url, prefix.length());
    }

    @Override public Map<String, String> getDefaultJdbcParams(ConnectionInfo info) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("useSSL", "false");
        m.put("serverTimezone", "Asia/Shanghai");
        m.put("characterEncoding", "UTF-8");
        m.put("rewriteBatchedStatements", "true");
        return m;
    }
}
