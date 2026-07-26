package com.kylin.plsql.core.db;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 驱动代理，解决跨 ClassLoader 注册时 DriverManager.isDriverAllowed() 安全检查失败的问题。
 * DriverProxy 由系统 ClassLoader 加载，DriverManager 调用 acceptsURL() 时代理到 URLClassLoader 下的真实驱动。
 */
public class DriverProxy implements Driver {
    private final Driver delegate;

    public DriverProxy(Driver delegate) {
        this.delegate = delegate;
    }

    @Override public Connection connect(String url, Properties info) throws SQLException { return delegate.connect(url, info); }
    @Override public boolean acceptsURL(String url) throws SQLException { return delegate.acceptsURL(url); }
    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException { return delegate.getPropertyInfo(url, info); }
    @Override public int getMajorVersion() { return delegate.getMajorVersion(); }
    @Override public int getMinorVersion() { return delegate.getMinorVersion(); }
    @Override public boolean jdbcCompliant() { return delegate.jdbcCompliant(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
}
