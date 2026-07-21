package com.kylin.plsql.core.db.services;

import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.type.DbTypeSpec;
import com.kylin.plsql.core.db.type.OceanBaseOracleSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** OceanBase Oracle 模式 JDBC 行为实现 / OceanBase Oracle-mode JDBC behavior. */
public class OceanBaseOracleJdbcService extends JdbcService {
    public static final OceanBaseOracleJdbcService INSTANCE = new OceanBaseOracleJdbcService();

    @Override public DbTypeSpec spec() { return OceanBaseOracleSpec.INSTANCE; }

    @Override protected String buildProtocolUrl(ConnectionInfo info) {
        return String.format("jdbc:oceanbase:oracle://%s:%d/%s",
            info.getHost(), info.getPort(), info.getServiceName());
    }

    @Override public ParsedUrl parseJdbcUrl(String url) {
        String lower = url.toLowerCase();
        String prefix;
        if (lower.startsWith("jdbc:oceanbase:oracle://")) {
            prefix = "jdbc:oceanbase:oracle://";
        } else if (lower.startsWith("jdbc:oceanbase://")) {
            prefix = "jdbc:oceanbase://";
        } else {
            return null;
        }
        return parseHostPortDb(url, prefix.length());
    }

    /** OceanBase Oracle 模式需要 compatibleOjdbcVersion=8 */
    @Override public Optional<String> getCompatibleOjdbcVersion() { return Optional.of("8"); }

    /** 与 Oracle 一样需要 DUAL 表验证 */
    @Override public String getConnectionTestQuery() { return "SELECT 1 FROM DUAL"; }

    /** OceanBase 驱动建连较慢，放宽超时 */
    @Override public int getInitFailTimeout() { return 15000; }

    /** OceanBase 支持 HikariCP setSchema */
    @Override public boolean supportsSetSchema() { return true; }

    @Override public Map<String, String> getDefaultJdbcParams(ConnectionInfo info) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("compatibleOjdbcVersion", "8");
        m.put("useServerPrepStmts", "true");
        m.put("characterEncoding", "UTF-8");
        m.put("useCompression", "true");
        m.put("rewriteBatchedStatements", "true");
        m.put("useLocalSessionState", "true");
        m.put("maintainTimeStats", "false");
        return m;
    }

    @Override public Map<String, List<String>> getSystemViewNames() {
        return Map.of("", List.of(
            "ALL_TABLES", "ALL_OBJECTS", "ALL_TAB_COLUMNS", "ALL_VIEWS",
            "ALL_CONSTRAINTS", "ALL_INDEXES", "ALL_SEQUENCES", "ALL_SYNONYMS",
            "ALL_SOURCE", "ALL_PROCEDURES", "ALL_ARGUMENTS", "ALL_TRIGGERS",
            "ALL_TAB_COMMENTS", "ALL_COL_COMMENTS",
            "USER_TABLES", "USER_OBJECTS", "USER_TAB_COLUMNS", "USER_VIEWS",
            "USER_CONSTRAINTS", "USER_INDEXES", "USER_SEQUENCES", "USER_SYNONYMS",
            "USER_SOURCE", "USER_PROCEDURES", "USER_ARGUMENTS", "USER_TRIGGERS",
            "USER_TAB_COMMENTS", "USER_COL_COMMENTS",
            "DBA_TABLES", "DBA_OBJECTS", "DBA_TAB_COLUMNS", "DBA_VIEWS",
            "V$SESSION", "V$SQL", "V$SQLAREA",
            "DICTIONARY", "DICT_COLUMNS"
        ));
    }
}
