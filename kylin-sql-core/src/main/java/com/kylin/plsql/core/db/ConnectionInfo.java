package com.kylin.plsql.core.db;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 数据库连接信息纯 POJO / Pure POJO for database connection metadata.
 * <p>
 * 仅含字段和 getter/setter，所有业务逻辑在 {@code com.kylin.plsql.core.db.type.DbTypeCoordinator} 中。
 * Contains only fields and accessors. All behavior is in DbTypeCoordinator.
 * <p>
 * 与 connections.json 的 Gson 序列化兼容 / Gson-serialization compatible.
 */
public class ConnectionInfo {
    private String name;
    private String dbType;
    private String dbVersion;
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

    private boolean useUrl;
    private String jdbcUrl;
    private int queryTimeout;
    private Map<String, String> jdbcParams = new LinkedHashMap<>();
    private String customDriverClass;
    private String customDriverJar;
    private String colorTag;
    private boolean colorEnabled;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getDbVersion() { return dbVersion; }
    public void setDbVersion(String dbVersion) { this.dbVersion = dbVersion; }
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
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public boolean isUseUrl() { return useUrl; }
    public void setUseUrl(boolean useUrl) { this.useUrl = useUrl; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public int getQueryTimeout() { return queryTimeout; }
    public void setQueryTimeout(int queryTimeout) { this.queryTimeout = queryTimeout; }
    public Map<String, String> getJdbcParams() { return jdbcParams; }
    public void setJdbcParams(Map<String, String> jdbcParams) { this.jdbcParams = jdbcParams; }
    public String getCustomDriverClass() { return customDriverClass; }
    public void setCustomDriverClass(String customDriverClass) { this.customDriverClass = customDriverClass; }
    public String getCustomDriverJar() { return customDriverJar; }
    public void setCustomDriverJar(String customDriverJar) { this.customDriverJar = customDriverJar; }
    public String getColorTag() { return colorTag; }
    public void setColorTag(String colorTag) { this.colorTag = colorTag; }
    public boolean isColorEnabled() { return colorEnabled; }
    public void setColorEnabled(boolean colorEnabled) { this.colorEnabled = colorEnabled; }

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
        return port == that.port && name.equals(that.name) && host.equals(that.host)
            && serviceName.equals(that.serviceName) && username.equals(that.username)
            && Objects.equals(jdbcUrl, that.jdbcUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, host, port, serviceName, username, jdbcUrl);
    }
}
