package com.kylin.plsql.core.db.services;

import com.kylin.plsql.core.db.type.DbTypeSpec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * JdbcService 注册表 / Registry for JdbcService implementations.
 * <p>
 * 每个 {@link JdbcService} 通过关联的 {@link DbTypeSpec#getKey()} 自动注册。
 * 加新数据库：建 XxxJdbcService → 调用 register()。
 * Auto-wired by spec key. Extension: create XxxJdbcService → register().
 */
public class JdbcServiceRegistry {

    private final Map<String, JdbcService> services = new ConcurrentHashMap<>();

    private JdbcServiceRegistry() {
        register(OracleJdbcService.INSTANCE);
        register(OceanBaseOracleJdbcService.INSTANCE);
        register(OceanBaseMySqlJdbcService.INSTANCE);
        register(MySqlJdbcService.INSTANCE);
        register(MariaDbJdbcService.INSTANCE);
        register(PostgreSqlJdbcService.INSTANCE);
    }

    private static final JdbcServiceRegistry INSTANCE = new JdbcServiceRegistry();

    public static JdbcServiceRegistry getInstance() { return INSTANCE; }

    /** 注册服务（按 spec().getKey() 索引） */
    public void register(JdbcService service) {
        services.put(service.spec().getKey().toLowerCase(), service);
    }

    /** 通过 DbTypeSpec 查找 / Look up by type spec. */
    public JdbcService forSpec(DbTypeSpec spec) {
        return services.get(spec.getKey().toLowerCase());
    }

    /**
     * 通过 key 查找 / Look up by type key.
     * <p>
     * 兼容旧版 "oceanbase" → oceanbase-oracle。未知 key 兜底返回 Oracle。
     */
    public JdbcService forKey(String key) {
        if (key == null) return services.get("oracle");
        JdbcService s = services.get(key.toLowerCase());
        if (s != null) return s;
        if ("oceanbase".equalsIgnoreCase(key)) return services.get("oceanbase-oracle");
        return services.get("oracle");
    }

    /** 便捷静态方法 / Static convenience methods. */
    public static JdbcService forSpecStatic(DbTypeSpec spec)  { return getInstance().forSpec(spec); }
    public static JdbcService forKeyStatic(String key)         { return getInstance().forKey(key); }
}
