package com.kylin.plsql.core.service;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.DbMetadataConfig;

/** 元数据加载服务，负责从数据库采集 Schema / 对象类型 / 对象列表，写入缓存并返回加载结果。 */

import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.db.type.DbTypeCoordinator;
import com.kylin.plsql.core.pojo.MetadataLoadResult;
import com.kylin.plsql.core.pojo.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

public class MetadataLoadService {
    private static final Logger log = LoggerFactory.getLogger(MetadataLoadService.class);

    private final ConnectionManager cm;
    private final ServiceFactory serviceFactory;
    private final ConfigManager configManager;

    public MetadataLoadService(ConnectionManager cm, ServiceFactory serviceFactory, ConfigManager configManager) {
        this.cm = cm;
        this.serviceFactory = serviceFactory;
        this.configManager = configManager;
    }

    public MetadataLoadResult load(String connName, ConnectionInfo info, IntConsumer progress) {
        MetadataCache cache = MetadataCache.getInstance();

        // ── Cache hit ──
        if (cache.hasMetadata(connName)) {
            String dbProduct = cache.getDbProduct(connName);
            if (dbProduct == null) {
                dbProduct = inferDbProduct(info, connName);
                cache.setDbProduct(connName, dbProduct);
            }
            List<String> schemasList = cache.getSchemas(connName);
            if (schemasList == null) return null;
            Map<String, Set<String>> hidden = calcHiddenSchemas(info, connName, schemasList);
            return new MetadataLoadResult(dbProduct, schemasList, hidden);
        }

        // ── Cache miss: query DB ──
        if (!cm.isConnected(connName)) {
            try { cm.connect(info); } catch (Exception e) {
                log.warn("自动连接 '{}' 失败: {}", connName, e.getMessage());
                return null;
            }
        }

        try (Connection conn = cm.getConnection(connName)) {
            String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
            boolean isOracleLike = dbProduct.contains("oracle") || dbProduct.contains("oceanbase");
            int qTimeout = cm.getQueryTimeout(connName);

            List<ObjectType> types = detectTypes(dbProduct);
            SchemaService ss = serviceFactory.getSchemaService(dbProduct);
            progress.accept(5);

            Set<String> schemas = collectSchemas(conn, isOracleLike);

            cache.putSchemas(connName, dbProduct, schemas);
            List<String> schemaList = new ArrayList<>(schemas);
            Map<String, Set<String>> hidden = calcHiddenSchemas(info, connName, schemaList);

            if (!schemas.isEmpty()) {
                int queryTypeCount = (int) types.stream().filter(ot -> !"SCHEMA".equals(ot.typeCode)).count();
                int totalOps = schemas.size() * queryTypeCount;
                int doneOps = 0;
                for (String schema : schemas) {
                    for (ObjectType ot : types) {
                        if ("SCHEMA".equals(ot.typeCode)) continue;
                        List<String> objects = queryObjects(conn, ot, schema, qTimeout, connName, ss);
                        cache.putObjects(connName, schema, ot.typeCode, objects);
                        doneOps++;
                        int pct = Math.min(95, 10 + doneOps * 85 / totalOps);
                        progress.accept(pct);
                    }
                    if (ss != null) ss.preloadTableComments(conn, connName, Collections.singletonList(schema));
                }
            }
            cache.flush(connName);
            progress.accept(100);
            return new MetadataLoadResult(dbProduct, schemaList, hidden);
        } catch (SQLException e) {
            log.error("加载连接 '{}' 失败", connName, e);
            return null;
        }
    }

    // ── Schema collection ──

    private Set<String> collectSchemas(Connection conn, boolean isOracleLike) {
        Set<String> schemas = new LinkedHashSet<>();
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                String s = rs.getString("TABLE_SCHEM");
                if (s == null) continue;
                String l = s.toLowerCase();
                if (l.startsWith("information_schema") || l.startsWith("pg_")
                    || "pg_catalog".equals(l) || "pg_toast".equals(l)
                    || "sys".equals(l) || "system".equals(l)
                    || "oceanbase".equals(l) || "mysql".equals(l)) continue;
                schemas.add(s);
            }
        } catch (SQLException e) {
            log.debug("getSchemas failed: {}", e.getMessage());
        }

        if (schemas.isEmpty()) {
            try {
                String sql = isOracleLike
                    ? "SELECT DISTINCT owner FROM all_objects WHERE owner NOT IN ('SYS','SYSTEM','PUBLIC','OCEANBASE','MYSQL') ORDER BY owner"
                    : "SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_schema NOT IN ('information_schema','pg_catalog','pg_toast') ORDER BY table_schema";
                try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) { String s = rs.getString(1); if (s != null) schemas.add(s); }
                }
            } catch (SQLException ex) {
                log.debug("schema fallback failed: {}", ex.getMessage());
            }
        }
        return schemas;
    }

    // ── Object type detection ──

    public List<ObjectType> detectTypes(String dbProduct) {
        if (dbProduct == null) dbProduct = "";
        if (configManager != null) {
            String key = ServiceFactory.dbProductToKey(dbProduct);
            for (DbMetadataConfig cfg : configManager.loadMetadataConfigs()) {
                if (cfg.getDbTypeKey().equals(key) && cfg.isEnabled()) {
                    return cfg.getTypes().stream()
                        .map(td -> {
                            if ("FIXED_LIST".equals(td.getQueryType())) {
                                return new ObjectType(td.getLabel(), td.getTypeCode(),
                                    null,
                                    new ArrayList<>(td.getFixedValues() != null ? td.getFixedValues() : List.of()),
                                    td.isExpandable());
                            }
                            return new ObjectType(td.getLabel(), td.getTypeCode(),
                                td.getQuerySql(), null, td.isExpandable());
                        })
                        .collect(Collectors.toList());
                }
            }
        }
        SchemaService ss = serviceFactory.getSchemaService(dbProduct);
        if (ss == null) return List.of();
        return ss.getSupportedTypes().stream()
            .map(ti -> new ObjectType(ti.label, ti.typeCode, null, null, ti.expandable))
            .collect(Collectors.toList());
    }

    // ── Object query ──

    private List<String> queryObjects(Connection conn, ObjectType ot, String schema, int timeout, String connName, SchemaService ss) {
        if (ot.fixedValues != null) return new ArrayList<>(ot.fixedValues);
        if (!ot.useSchemaService() && ot.querySql != null) {
            List<String> names = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(ot.querySql)) {
                ps.setString(1, schema);
                if (timeout > 0) ps.setQueryTimeout(timeout);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) names.add(rs.getString(1));
                }
            } catch (SQLException e) {
                log.warn("query {} {} failed: {}", ot.label, schema, e.getMessage());
            }
            Collections.sort(names);
            return names;
        }
        return ss != null ? ss.getObjects(connName, schema, ot.typeCode) : Collections.emptyList();
    }

    // ── Hidden schema calculation ──

    private Map<String, Set<String>> calcHiddenSchemas(ConnectionInfo info, String connName, List<String> all) {
        String defaultSchema = info.getSchema();
        Set<String> hidden = new java.util.LinkedHashSet<>();
        if (defaultSchema != null && !defaultSchema.isEmpty()) {
            String matched = null;
            for (String s : all) {
                if (s.equalsIgnoreCase(defaultSchema)) { matched = s; break; }
            }
            if (matched != null) {
                hidden.addAll(all);
                hidden.remove(matched);
            }
        }
        return Map.of(connName, hidden);
    }

    // ── DB Product inference ──

    private String inferDbProduct(ConnectionInfo info, String connName) {
        MetadataCache cache = MetadataCache.getInstance();
        String cached = cache.getDbProduct(connName);
        if (cached != null) return cached;
        String fromInfo = info.getDbType();
        if (fromInfo != null && !fromInfo.isBlank()) return fromInfo.toLowerCase();
        String url = DbTypeCoordinator.forConnection(info).buildUrl(info);
        if (url != null) {
            String u = url.toLowerCase();
            if (u.startsWith("jdbc:oceanbase:")) return "oceanbase";
            if (u.startsWith("jdbc:postgresql:")) return "postgresql";
            if (u.startsWith("jdbc:mysql:")) return "mysql";
            if (u.startsWith("jdbc:mariadb:")) return "mariadb";
            if (u.startsWith("jdbc:oracle:")) return "oracle";
            if (u.contains("edb")) return "edb";
        }
        return "oracle";
    }
}
