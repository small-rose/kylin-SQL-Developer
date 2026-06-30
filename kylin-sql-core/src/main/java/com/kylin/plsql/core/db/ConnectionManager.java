package com.kylin.plsql.core.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** HikariCP-based connection pool manager with transaction support. */
public class ConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, Connection> transactionConns = new ConcurrentHashMap<>();
    private final Map<String, Boolean> autoCommitStates = new ConcurrentHashMap<>();
    private final Map<String, Integer> queryTimeouts = new ConcurrentHashMap<>();

    public ConnectionManager() {}

    public boolean testConnection(ConnectionInfo info) {
        String url = info.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(url, info.getUsername(), info.getPassword())) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("测试连接失败: {} - {}", info.getName(), e.getMessage());
            return false;
        }
    }

    public void connect(ConnectionInfo info) {
        String key = info.getName();
        if (dataSources.containsKey(key)) {
            log.info("连接已存在(将重新连接): {}", key);
            disconnect(key);
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(info.getJdbcUrl());
        config.setUsername(info.getUsername());
        config.setPassword(info.getPassword());
        config.setDriverClassName(info.getDriverClass());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(10000);
        config.setAutoCommit(true);

        // Connection test query: Oracle/OceanBase needs FROM DUAL
        String url = info.getJdbcUrl().toLowerCase();
        if (url.contains("oracle") || "oracle".equalsIgnoreCase(info.getDbType())
            || "oceanbase".equalsIgnoreCase(info.getDbType())) {
            config.setConnectionTestQuery("SELECT 1 FROM DUAL");
        } else {
            config.setConnectionTestQuery("SELECT 1");
        }

        if (info.getSchema() != null && !info.getSchema().isBlank()) {
            String db = info.getDbType() != null ? info.getDbType().toLowerCase() : "";
            if ("postgresql".equals(db) || "oceanbase".equals(db)) {
                config.setSchema(info.getSchema());
            } else {
                config.addDataSourceProperty("currentSchema", info.getSchema());
            }
        }

        try {
            HikariDataSource ds = new HikariDataSource(config);
            dataSources.put(key, ds);
            autoCommitStates.put(key, true);
            queryTimeouts.put(key, info.getQueryTimeout());
            log.info("已连接: {}", info);
        } catch (Exception e) {
            log.error("连接失败: {} - {}", key, e.getMessage());
            autoCommitStates.remove(key);
            queryTimeouts.remove(key);
        }
    }

    public void disconnect(String name) {
        closeTransactionConn(name);
        HikariDataSource ds = dataSources.remove(name);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("已断开: {}", name);
        }
        autoCommitStates.remove(name);
        queryTimeouts.remove(name);
    }

    public Connection getConnection(String name) throws SQLException {
        Connection tc = transactionConns.get(name);
        if (tc != null && !tc.isClosed()) return tc;

        HikariDataSource ds = dataSources.get(name);
        if (ds == null) throw new SQLException("连接 '" + name + "' 未打开");
        return ds.getConnection();
    }

    public boolean isConnected(String name) {
        HikariDataSource ds = dataSources.get(name);
        return ds != null && !ds.isClosed();
    }

    public boolean isAutoCommit(String name) {
        return autoCommitStates.getOrDefault(name, true);
    }

    public void setAutoCommit(String name, boolean autoCommit) {
        autoCommitStates.put(name, autoCommit);
        if (autoCommit) {
            closeTransactionConn(name);
        } else {
            openTransactionConn(name);
        }
    }

    private void openTransactionConn(String name) {
        closeTransactionConn(name);
        try {
            HikariDataSource ds = dataSources.get(name);
            if (ds == null) return;
            Connection conn = ds.getConnection();
            conn.setAutoCommit(false);
            transactionConns.put(name, conn);
            log.info("事务模式已开启: {}", name);
        } catch (SQLException e) {
            log.error("开启事务模式失败: {}", name, e);
        }
    }

    private void closeTransactionConn(String name) {
        Connection conn = transactionConns.remove(name);
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    public void commit(String name) {
        Connection conn = transactionConns.get(name);
        if (conn == null) return;
        try {
            conn.commit();
            log.info("已提交: {}", name);
        } catch (SQLException e) {
            log.error("提交失败: {}", name, e);
        }
    }

    public void rollback(String name) {
        Connection conn = transactionConns.get(name);
        if (conn == null) return;
        try {
            conn.rollback();
            log.info("已回滚: {}", name);
        } catch (SQLException e) {
            log.error("回滚失败: {}", name, e);
        }
    }

    public void disconnectAll() {
        for (String key : dataSources.keySet()) {
            disconnect(key);
        }
    }

    public String[] getActiveConnections() {
        return dataSources.keySet().toArray(new String[0]);
    }

    public int getQueryTimeout(String name) {
        return queryTimeouts.getOrDefault(name, 0);
    }
}
