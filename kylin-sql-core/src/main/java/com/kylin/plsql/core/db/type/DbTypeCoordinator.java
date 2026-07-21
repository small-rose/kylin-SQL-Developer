package com.kylin.plsql.core.db.type;

import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.services.JdbcService;
import com.kylin.plsql.core.db.services.JdbcServiceRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DbTypeSpec（数据层）与 JdbcService（行为层）的聚合门面。
 * Aggregation facade unifying DbTypeSpec (data) and JdbcService (behavior).
 * <p>
 * 消费者只需与 Coordinator 交互，无需区分 spec 和 service。
 * Consumers interact only with this class — no need to know spec vs service boundaries.
 * <p>
 * 示例 / Usage:
 * <pre>{@code
 *   var coord = DbTypeCoordinator.forConnection(connInfo);
 *   String url = coord.buildUrl(connInfo);
 *   String driver = coord.getDriverClassName();
 * }</pre>
 */
public class DbTypeCoordinator {

    /** Flyweight 缓存：同一 dbType key 共享一个实例 */
    private static final ConcurrentHashMap<String, DbTypeCoordinator> CACHE = new ConcurrentHashMap<>();

    private final DbTypeSpec spec;
    private final JdbcService service;

    private DbTypeCoordinator(DbTypeSpec spec, JdbcService service) {
        this.spec = spec;
        this.service = service;
    }

    /** 根据 ConnectionInfo 获取对应的 Coordinator / Resolve from connection metadata. */
    public static DbTypeCoordinator forConnection(ConnectionInfo info) {
        return forTypeKey(info.getDbType());
    }

    /** 根据 dbType key 获取 Coordinator（带 flyweight 缓存） / Resolve by type key with caching. */
    public static DbTypeCoordinator forTypeKey(String dbTypeKey) {
        String key = dbTypeKey != null ? dbTypeKey.toLowerCase() : "";
        return CACHE.computeIfAbsent(key, k -> {
            DbTypeSpec s = DbTypeRegistry.getInstance().fromKey(k);
            JdbcService svc = JdbcServiceRegistry.getInstance().forSpec(s);
            return new DbTypeCoordinator(s, svc);
        });
    }

    // ── Spec delegates（数据层） ──

    /** 获取底层 {@link DbTypeSpec} / The underlying descriptor. */
    public DbTypeSpec spec()                             { return spec; }
    public String getKey()                               { return spec.getKey(); }
    public String getDisplayName()                       { return spec.getDisplayName(); }
    public int getDefaultPort()                          { return spec.getDefaultPort(); }
    public String getDriverClassName()                   { return spec.getDriverClassName(); }
    public DbFamily getFamily()                          { return spec.getFamily(); }
    public boolean isOracleFamily()                      { return spec.getFamily() == DbFamily.ORACLE; }
    public boolean isMySqlFamily()                       { return spec.getFamily() == DbFamily.MYSQL; }

    // ── Service delegates（行为层） ──

    /** 构建完整 JDBC URL / Build full JDBC URL. */
    public String buildUrl(ConnectionInfo info)                           { return service.buildUrl(info); }
    /** 解析驱动类名（自定义 > URL 检测 > spec 默认） */
    public String resolveDriverClassName(ConnectionInfo info)             { return service.resolveDriverClassName(info); }
    /** 从 JDBC URL 解析 host/port/serviceName */
    public JdbcService.ParsedUrl parseJdbcUrl(String url)                 { return service.parseJdbcUrl(url); }
    /** 连接测试查询语句 / Connection validation query. */
    public String getConnectionTestQuery()                                 { return service.getConnectionTestQuery(); }
    /** 初始化失败超时（毫秒）/ Initial connection fail timeout in ms. */
    public int getInitFailTimeout()                                        { return service.getInitFailTimeout(); }
    /** 是否支持 HikariCP setSchema() / Whether the type supports native schema setting. */
    public boolean supportsSetSchema()                                     { return service.supportsSetSchema(); }
    /** OceanBase Oracle 模式的 compatibleOjdbcVersion 兼容版本号 */
    public Optional<String> getCompatibleOjdbcVersion()                    { return service.getCompatibleOjdbcVersion(); }
    /** 当前类型的默认 JDBC 参数预设 / Default JDBC connection parameters for this type. */
    public Map<String, String> getDefaultJdbcParams(ConnectionInfo info)   { return service.getDefaultJdbcParams(info); }
    /** 系统元数据视图名（用于自动补全）/ System metadata views for auto-completion. */
    public Map<String, List<String>> getSystemViewNames()                  { return service.getSystemViewNames(); }
}
