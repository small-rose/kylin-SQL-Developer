package com.kylin.plsql.core.db.services;

import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.type.DbTypeSpec;
import com.kylin.plsql.core.db.type.OracleSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** Oracle JDBC 行为实现 / Oracle JDBC connection behavior. */
public class OracleJdbcService extends JdbcService {
    public static final OracleJdbcService INSTANCE = new OracleJdbcService();

    @Override public DbTypeSpec spec() { return OracleSpec.INSTANCE; }

    @Override protected String buildProtocolUrl(ConnectionInfo info) {
        return String.format("jdbc:oracle:thin:@%s:%d/%s",
            info.getHost(), info.getPort(), info.getServiceName());
    }

    @Override public ParsedUrl parseJdbcUrl(String url) {
        String prefix = "jdbc:oracle:thin:@";
        if (!url.toLowerCase().startsWith(prefix)) return null;
        try {
            String rest = url.substring(prefix.length());
            String[] path = rest.split("/");
            String host = "";
            int port = 1521;
            String svc = "";
            if (path.length >= 1) {
                String[] hp = path[0].split(":");
                if (hp.length >= 1) host = hp[0];
                if (hp.length >= 2) port = Integer.parseInt(hp[1].replaceAll("[^0-9]", ""));
            }
            if (path.length >= 2) {
                svc = path[1].contains("?") ? path[1].substring(0, path[1].indexOf('?')) : path[1];
            }
            return new ParsedUrl(host, port, svc);
        } catch (Exception e) { return null; }
    }

    /** Oracle 需要 DUAL 表验证连接 */
    @Override public String getConnectionTestQuery() { return "SELECT 1 FROM DUAL"; }

    /** Oracle 连接常用参数预设 / Common Oracle connection parameters. */
    @Override public Map<String, String> getDefaultJdbcParams(ConnectionInfo info) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("oracle.jdbc.defaultNChar", "true");
        m.put("oracle.jdbc.J2EE13Compliant", "true");
        m.put("defaultLongFetchSize", "1000");
        m.put("oracle.jdbc.ReadTimeout", "30000");
        m.put("oracle.net.CONNECT_TIMEOUT", "10000");
        return m;
    }
}
