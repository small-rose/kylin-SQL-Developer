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
    private final Map<String, DataChangeService> changeCache = new ConcurrentHashMap<>();

    public ServiceFactory(ConnectionManager cm) {
        this.cm = cm;
    }

    /** 根据数据库产品名映射到方言 key，"mysql"/"mariadb"/"postgresql"/"edb"/"oceanbase"/"oracle"。 */
    public static String dbProductToKey(String dbProduct) {
        if (dbProduct == null) return "oracle";
        String p = dbProduct.toLowerCase();
        if (p.contains("mysql") || p.contains("mariadb")) return "mysql";
        if (p.contains("postgresql") || p.contains("edb")) return "postgresql";
        if (p.contains("oceanbase")) return "oceanbase";
        return "oracle";
    }

    public SchemaService getSchemaService(String dbProduct) {
        String key = dbProductToKey(dbProduct);
        return schemaCache.computeIfAbsent(normalize(key), actualKey -> {
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
        String key = dbProductToKey(dbTypeKey);
        return queryCache.computeIfAbsent(normalize(key), actualKey -> {
            String jdbcKey = JdbcServiceRegistry.forKeyStatic(actualKey).spec().getKey().toLowerCase();
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

    public DataChangeService getDataChangeService(String dbTypeKey) {
        String key = dbProductToKey(dbTypeKey);
        return changeCache.computeIfAbsent(normalize(key), actualKey -> {
            String jdbcKey = JdbcServiceRegistry.forKeyStatic(actualKey).spec().getKey().toLowerCase();
            if (jdbcKey.contains("mysql") || jdbcKey.contains("mariadb")) {
                return new MySqlDataChangeService();
            }
            if (jdbcKey.equals("postgresql")) {
                return new PostgreSqlDataChangeService();
            }
            if ("oceanbase".equals(jdbcKey) || "oceanbase-oracle".equals(jdbcKey) ){
                return new OracleDataChangeService();
            }
            return new OracleDataChangeService();
        });
    }

    private static String normalize(String key) {
        return key != null ? key.toLowerCase() : "";
    }
}
