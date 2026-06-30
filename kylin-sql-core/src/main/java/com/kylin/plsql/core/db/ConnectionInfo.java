package com.kylin.plsql.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Database connection information model with JDBC URL and driver auto-detection. */
public class ConnectionInfo {
    private String name;
    private String dbType;       // "oracle" | "postgresql" | "oceanbase"
    private String host;
    private int port;
    private String serviceName;
    private String username;
    private String password;
    private String schema;
    private String charset = "UTF-8";
    private boolean autoCommit = false;
    private long createdAt;
    private long updatedAt;

    private boolean useUrl;      // true = 使用直接 JDBC URL 模式
    private String jdbcUrl;      // 直接 JDBC URL（useUrl=true 时生效）
    private int queryTimeout;    // SQL/存过执行超时秒数（0=不限）

    public ConnectionInfo() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public ConnectionInfo(String name, String host, int port, String serviceName, String username, String password, String dbType) {
        this();
        this.name = name;
        this.host = host;
        this.port = port;
        this.serviceName = serviceName;
        this.username = username;
        this.password = password;
        this.dbType = dbType;
    }

    // ── JDBC URL 生成 ──

    public String getJdbcUrl() {
        if (useUrl && jdbcUrl != null && !jdbcUrl.isBlank()) {
            return jdbcUrl;
        }
        if ("oceanbase".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:oceanbase:oracle://%s:%d/%s", host, port, serviceName);
        }
        if ("postgresql".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, serviceName);
        }
        return String.format("jdbc:oracle:thin:@%s:%d/%s", host, port, serviceName);
    }

    public String getDriverClass() {
        if (useUrl && jdbcUrl != null) {
            return detectDriverFromUrl(jdbcUrl);
        }
        if ("oceanbase".equalsIgnoreCase(dbType)) {
            return "com.oceanbase.jdbc.Driver";
        }
        if ("postgresql".equalsIgnoreCase(dbType)) {
            return "org.postgresql.Driver";
        }
        return "oracle.jdbc.OracleDriver";
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConnectionInfo.class);

    private static String detectDriverFromUrl(String url) {
        if (url == null) return "oracle.jdbc.OracleDriver";
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:oceanbase:")) return "com.oceanbase.jdbc.Driver";
        if (lower.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (lower.startsWith("jdbc:oracle:")) return "oracle.jdbc.OracleDriver";
        if (lower.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
        if (lower.startsWith("jdbc:mariadb:")) return "org.mariadb.jdbc.Driver";
        if (lower.startsWith("jdbc:sqlserver:")) return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        if (lower.startsWith("jdbc:db2:")) return "com.ibm.db2.jcc.DB2Driver";
        if (lower.startsWith("jdbc:h2:")) return "org.h2.Driver";
        if (lower.startsWith("jdbc:sqlite:")) return "org.sqlite.JDBC";
        log.warn("未知 JDBC URL 前缀，使用通用驱动类: {}", url);
        return "";
    }

    // ── Getter / Setter ──

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getCharset() { return charset; }
    public void setCharset(String charset) { this.charset = charset; }
    public boolean isAutoCommit() { return autoCommit; }
    public void setAutoCommit(boolean autoCommit) { this.autoCommit = autoCommit; }
    public boolean isUseUrl() { return useUrl; }
    public void setUseUrl(boolean useUrl) { this.useUrl = useUrl; }
    public String getRawJdbcUrl() { return jdbcUrl; }
    public void setRawJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public int getQueryTimeout() { return queryTimeout; }
    public void setQueryTimeout(int queryTimeout) { this.queryTimeout = queryTimeout; }

    @Override
    public String toString() {
        if (useUrl && jdbcUrl != null && !jdbcUrl.isBlank()) {
            return name + " (" + username + "@" + jdbcUrl + ")";
        }
        return name + " (" + username + "@" + host + ":" + port + "/" + serviceName + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionInfo)) return false;
        ConnectionInfo that = (ConnectionInfo) o;
        return port == that.port && name.equals(that.name) && host.equals(that.host) && serviceName.equals(that.serviceName) && username.equals(that.username) && Objects.equals(jdbcUrl, that.jdbcUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, host, port, serviceName, username, jdbcUrl);
    }
}
