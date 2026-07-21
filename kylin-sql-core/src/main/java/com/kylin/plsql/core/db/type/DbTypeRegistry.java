package com.kylin.plsql.core.db.type;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * DbTypeSpec 注册表 / Registry for database type descriptors.
 * <p>
 * 单例，内置注册 6 种数据库类型。外部类型可通过 {@link #register(DbTypeSpec)} 动态注册。
 * Singleton. Built-in types registered in constructor; external types add via register().
 */
public class DbTypeRegistry {

    private final Map<String, DbTypeSpec> specs = new ConcurrentHashMap<>();

    private DbTypeRegistry() {
        register(OracleSpec.INSTANCE);
        register(OceanBaseOracleSpec.INSTANCE);
        register(OceanBaseMySqlSpec.INSTANCE);
        register(MySqlSpec.INSTANCE);
        register(MariaDbSpec.INSTANCE);
        register(PostgreSqlSpec.INSTANCE);
    }

    private static final DbTypeRegistry INSTANCE = new DbTypeRegistry();

    public static DbTypeRegistry getInstance() { return INSTANCE; }

    /** 注册类型描述 / Register a type spec (key → lowercased spec). */
    public void register(DbTypeSpec spec) {
        specs.put(spec.getKey().toLowerCase(), spec);
    }

    /**
     * 根据 key 查找类型 / Look up spec by key.
     * <p>
     * 兼容旧版 "oceanbase" → oceanbase-oracle。未知 key 兜底返回 Oracle。
     * Backward compat: "oceanbase" → oceanbase-oracle. Unknown keys fall back to Oracle.
     */
    public DbTypeSpec fromKey(String key) {
        if (key == null) return OracleSpec.INSTANCE;
        DbTypeSpec s = specs.get(key.toLowerCase());
        if (s != null) return s;
        if ("oceanbase".equalsIgnoreCase(key)) return OceanBaseOracleSpec.INSTANCE;
        return OracleSpec.INSTANCE;
    }

    /** 返回所有已注册类型 / All registered specs (unmodifiable). */
    public List<DbTypeSpec> all() {
        return List.copyOf(specs.values());
    }

    /**
     * 根据 JDBC URL 前缀反向检测数据库类型 / Detect type from URL prefix.
     * <p>
     * 长前缀优先匹配（避免 jdbc:oceanbase:// 误判为 jdbc:oceanbase:oracle://）。
     * Longer prefixes matched first to avoid false positives.
     */
    public Optional<DbTypeSpec> detectFromUrl(String url) {
        if (url == null) return Optional.empty();
        String lower = url.toLowerCase();
        List<Map.Entry<String, DbTypeSpec>> sorted = specs.values().stream()
            .flatMap(s -> s.getUrlPrefixes().stream().map(p -> Map.entry(p, s)))
            .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
            .collect(Collectors.toList());
        for (Map.Entry<String, DbTypeSpec> entry : sorted) {
            if (lower.startsWith(entry.getKey())) return Optional.of(entry.getValue());
        }
        return Optional.empty();
    }

    /** 便捷静态方法 / Static convenience methods. */
    public static DbTypeSpec fromKeyStatic(String key)                  { return getInstance().fromKey(key); }
    public static List<DbTypeSpec> allStatic()                           { return getInstance().all(); }
    public static Optional<DbTypeSpec> detectFromUrlStatic(String url)  { return getInstance().detectFromUrl(url); }
}
