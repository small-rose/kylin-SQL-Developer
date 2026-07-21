package com.kylin.plsql.core.db.services;

import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.type.DbTypeSpec;
import com.kylin.plsql.core.db.type.MySqlSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** MySQL JDBC 行为实现 / MySQL JDBC connection behavior. */
public class MySqlJdbcService extends JdbcService {
    public static final MySqlJdbcService INSTANCE = new MySqlJdbcService();

    @Override public DbTypeSpec spec() { return MySqlSpec.INSTANCE; }

    @Override protected String buildProtocolUrl(ConnectionInfo info) {
        return String.format("jdbc:mysql://%s:%d/%s",
            info.getHost(), info.getPort(), info.getServiceName());
    }

    @Override public ParsedUrl parseJdbcUrl(String url) {
        String prefix = "jdbc:mysql://";
        if (!url.toLowerCase().startsWith(prefix)) return null;
        return parseHostPortDb(url, prefix.length());
    }

    @Override public Map<String, String> getDefaultJdbcParams(ConnectionInfo info) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("useSSL", "false");
        m.put("serverTimezone", "Asia/Shanghai");
        m.put("characterEncoding", "UTF-8");
        m.put("rewriteBatchedStatements", "true");
        m.put("allowPublicKeyRetrieval", "true");
        return m;
    }
}
