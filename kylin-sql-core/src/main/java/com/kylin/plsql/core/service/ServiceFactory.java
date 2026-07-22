package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.db.services.JdbcServiceRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务工厂，按 dbType key 路由到对应的方言实现。<br>
 * 缓存已创建的 Service 实例，避免重复创建。<br>
 * OceanBase Oracle 模式路由到 Oracle 实现，OceanBase MySQL 模式路由到 MySQL 实现。
 */
public class ServiceFactory {
    private final ConnectionManager cm;
    private final Map<String, SchemaService> schemaCache = new ConcurrentHashMap<>();
    private final Map<String, DataQueryService> queryCache = new ConcurrentHashMap<>();
    private ExportService exportService;

    public ServiceFactory(ConnectionManager cm) {
        this.cm = cm;
    }

    public SchemaService getSchemaService(String dbTypeKey) {
        return schemaCache.computeIfAbsent(normalize(dbTypeKey), key -> {
            String jdbcKey = JdbcServiceRegistry.forKeyStatic(key).spec().getKey().toLowerCase();
            if (jdbcKey.contains("mysql") || jdbcKey.contains("mariadb")) {
                return new MySqlSchemaService(cm);
            }
            if (jdbcKey.equals("postgresql")) {
                return new PostgreSqlSchemaService(cm);
            }
            return new OracleSchemaService(cm);
        });
    }

    public DataQueryService getDataQueryService(String dbTypeKey) {
        return queryCache.computeIfAbsent(normalize(dbTypeKey), key -> {
            String jdbcKey = JdbcServiceRegistry.forKeyStatic(key).spec().getKey().toLowerCase();
            if (jdbcKey.contains("mysql") || jdbcKey.contains("mariadb")) {
                return new MySqlDataQueryService(cm);
            }
            if (jdbcKey.equals("postgresql")) {
                return new PostgreSqlDataQueryService(cm);
            }
            return new OracleDataQueryService(cm);
        });
    }

    public ExportService getExportService() {
        if (exportService == null) exportService = new ExportService();
        return exportService;
    }

    private static String normalize(String key) {
        return key != null ? key.toLowerCase() : "";
    }
}
