package com.kylin.plsql.core.db;

import com.kylin.plsql.core.db.type.DbTypeCoordinator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** HikariCP 连接池管理器 / HikariCP-based connection pool manager with transaction support. */
public class ConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, Connection> transactionConns = new ConcurrentHashMap<>();
    private final Map<String, Boolean> autoCommitStates = new ConcurrentHashMap<>();
    private final Map<String, Integer> queryTimeouts = new ConcurrentHashMap<>();

    public ConnectionManager() {}

    /** 测试连接可用性 / Test connection validity using DriverManager. */
    public boolean testConnection(ConnectionInfo info) {
        String url = DbTypeCoordinator.forConnection(info).buildUrl(info);
        try (Connection conn = DriverManager.getConnection(url, info.getUsername(), info.getPassword())) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("测试连接失败: {} - {}", info.getName(), e.getMessage());
            return false;
        }
    }

    /** 建立连接池 / Create HikariCP connection pool. */
    public void connect(ConnectionInfo info) throws SQLException {
        String key = info.getName();
        if (dataSources.containsKey(key)) {
            log.info("连接已存在(将重新连接): {}", key);
            disconnect(key);
        }
        var coord = DbTypeCoordinator.forConnection(info);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(coord.buildUrl(info));
        config.setUsername(info.getUsername());
        config.setPassword(info.getPassword());
        config.setDriverClassName(coord.resolveDriverClassName(info));
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(10000);
        config.setAutoCommit(true);
        config.setConnectionTestQuery(coord.getConnectionTestQuery());

        // 部分数据库（OceanBase）建连较慢，需要放宽初始化失败超时
        int failTimeout = coord.getInitFailTimeout();
        if (failTimeout > 0) config.setInitializationFailTimeout(failTimeout);

        // Schema 设置：支持 setSchema 的原生走 HikariCP API，否则用连接参数
        if (info.getSchema() != null && !info.getSchema().isBlank()) {
            if (coord.supportsSetSchema()) {
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
            log.error("连接失败: {} - {}", key, e.getMessage(), e);
            autoCommitStates.remove(key);
            queryTimeouts.remove(key);
            throw new SQLException("连接失败: " + e.getMessage(), e);
        }
    }

    /** 断开连接池 / Disconnect and close pool. */
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

    /** 获取连接（事务模式优先）/ Get connection (transaction-mode优先). */
    public Connection getConnection(String name) throws SQLException {
        Connection tc = transactionConns.get(name);
        if (tc != null && !tc.isClosed()) return tc;

        HikariDataSource ds = dataSources.get(name);
        if (ds == null) throw new SQLException("连接 '" + name + "' 未打开");
        if (ds.isClosed()) {
            dataSources.remove(name);
            throw new SQLException("连接 '" + name + "' 已关闭");
        }
        return ds.getConnection();
    }

    /** 强制重连 / Force reconnect and get connection. */
    public Connection reconnectAndGet(String name, ConnectionInfo info) throws SQLException {
        disconnect(name);
        connect(info);
        return getConnection(name);
    }

    public boolean isConnected(String name) {
        if (name == null || name.isEmpty()) return false;
        HikariDataSource ds = dataSources.get(name);
        return ds != null && !ds.isClosed();
    }

    public boolean isAutoCommit(String name) {
        return autoCommitStates.getOrDefault(name, true);
    }

    /** 切换自动提交模式 / Toggle auto-commit (opens/closes transaction connection). */
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
