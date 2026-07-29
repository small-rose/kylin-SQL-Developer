package com.kylin.plsql.core.service;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.db.SqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylin.plsql.core.cache.MetadataCache.CachedColumn;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema 元数据查询服务抽象基类。<br>
 * 提供 schema/表/列 的缓存优先 + JDBC 回退查询。<br>
 * 子类覆盖 getTableQuerySql() / getViewQuerySql() 以适配不同数据库方言。
 */
public abstract class SchemaService {
    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);

    protected final ConnectionManager cm;
    protected final MetadataCache cache = MetadataCache.getInstance();
    protected final SqlExecutor executor = new SqlExecutor();

    public SchemaService(ConnectionManager cm) {
        this.cm = cm;
    }

    protected abstract String getTableQuerySql();
    protected abstract String getViewQuerySql();
    protected abstract String getIndexQuerySql();
    protected abstract String getSequenceQuerySql();
    protected abstract String getFunctionQuerySql();
    protected abstract String getProcedureQuerySql();
    protected String getPackageQuerySql() { return null; }
    protected String getSynonymQuerySql() { return null; }
    protected String getTableCommentQuerySql() { return null; }
    protected String getSchemaFallbackSql() { return null; }

    public String getDbProduct(String connName) {
        return cache.getDbProduct(connName);
    }

    public boolean hasMetadata(String connName) {
        return cache.hasMetadata(connName);
    }

    public List<String> getSchemas(String connName) {
        List<String> cached = cache.getSchemas(connName);
        if (cached != null && !cached.isEmpty()) return cached;
        return loadSchemas(connName);
    }

    public List<String> getTables(String connName, String schema) {
        List<String> cached = cache.getObjects(connName, schema, "TABLE");
        if (cached != null) return cached;
        return loadTables(connName, schema);
    }

    public List<String> getViews(String connName, String schema) {
        return queryCachedObjects(connName, schema, "VIEW", getViewQuerySql());
    }

    public List<String> getIndexes(String connName, String schema) {
        String sql = getIndexQuerySql();
        return sql != null ? queryCachedObjects(connName, schema, "INDEX", sql) : Collections.emptyList();
    }

    public List<String> getSequences(String connName, String schema) {
        String sql = getSequenceQuerySql();
        return sql != null ? queryCachedObjects(connName, schema, "SEQUENCE", sql) : Collections.emptyList();
    }

    public List<String> getFunctions(String connName, String schema) {
        String sql = getFunctionQuerySql();
        return sql != null ? queryCachedObjects(connName, schema, "FUNCTION", sql) : Collections.emptyList();
    }

    public List<String> getProcedures(String connName, String schema) {
        String sql = getProcedureQuerySql();
        return sql != null ? queryCachedObjects(connName, schema, "PROCEDURE", sql) : Collections.emptyList();
    }

    public List<String> getPackages(String connName, String schema) {
        String sql = getPackageQuerySql();
        return sql != null ? queryCachedObjects(connName, schema, "PACKAGE", sql) : Collections.emptyList();
    }

    public List<String> getSynonyms(String connName, String schema) {
        String sql = getSynonymQuerySql();
        return sql != null ? queryCachedObjects(connName, schema, "SYNONYM", sql) : Collections.emptyList();
    }

    /** 根据对象类型代码获取对应的中文标签。 */
    public String getTypeLabel(String typeCode) {
        for (var t : typeLabels()) {
            if (t.getValue().equals(typeCode)) return t.getKey();
        }
        return typeCode;
    }

    /** 根据中文标签获取对应的类型代码。 */
    public String getTypeCode(String label) {
        for (var t : typeLabels()) {
            if (t.getKey().equals(label)) return t.getValue();
        }
        return label;
    }

    /** 判断某个类型代码是否可展开（如 PACKAGE）。 */
    public boolean isExpandable(String typeCode) {
        for (TypeInfo ti : getSupportedTypes()) {
            if (ti.typeCode.equals(typeCode)) return ti.expandable;
        }
        return false;
    }

    /** 通用查询：根据类型代码查询对象列表，结果自动缓存。 */
    public List<String> getObjects(String connName, String schema, String typeCode) {
        return switch (typeCode) {
            case "TABLE" -> getTables(connName, schema);
            case "VIEW" -> getViews(connName, schema);
            case "INDEX" -> getIndexes(connName, schema);
            case "SEQUENCE" -> getSequences(connName, schema);
            case "FUNCTION" -> getFunctions(connName, schema);
            case "PROCEDURE" -> getProcedures(connName, schema);
            case "PACKAGE" -> getPackages(connName, schema);
            case "SYNONYM" -> getSynonyms(connName, schema);
            default -> Collections.emptyList();
        };
    }

    /** 获取当前数据库支持的所有对象类型列表（label, typeCode, expandable）。 */
    public java.util.List<TypeInfo> getSupportedTypes() {
        String[] defs = getTypeDefs();
        java.util.List<TypeInfo> result = new java.util.ArrayList<>();
        for (String def : defs) {
            String[] parts = def.split(";");
            if (parts.length >= 3) {
                result.add(new TypeInfo(parts[0], parts[1], "true".equals(parts[2])));
            }
        }
        return result;
    }

    public static class TypeInfo {
        public final String label;
        public final String typeCode;
        public final boolean expandable;
        public TypeInfo(String label, String typeCode, boolean expandable) {
            this.label = label; this.typeCode = typeCode; this.expandable = expandable;
        }
    }

    private java.util.List<java.util.Map.Entry<String, String>> typeLabels() {
        java.util.List<java.util.Map.Entry<String, String>> r = new java.util.ArrayList<>();
        for (TypeInfo t : getSupportedTypes()) {
            r.add(new java.util.AbstractMap.SimpleEntry<>(t.label, t.typeCode));
        }
        return r;
    }

    /** 子类提供类型定义串（格式：label;typeCode;expandable）。 */
    protected String[] getTypeDefs() { return new String[0]; }

    private List<String> queryCachedObjects(String connName, String schema, String type, String sql) {
        List<String> cached = cache.getObjects(connName, schema, type);
        if (cached != null) return cached;
        return loadObjects(connName, schema, type, sql);
    }

    private List<String> loadObjects(String connName, String schema, String type, String sql) {
        if (!cm.isConnected(connName)) return Collections.emptyList();
        try (Connection conn = cm.getConnection(connName);
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            List<String> names = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) names.add(rs.getString(1)); }
            Collections.sort(names);
            cache.putObjects(connName, schema, type, names);
            return names;
        } catch (Exception e) {
            log.warn("加载 {} 列表失败 ({} {}): {}", type, connName, schema, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<SqlExecutor.ColumnMeta> getColumns(String connName, String schema, String table) {
        var cached = cache.getColumns(connName, schema, table);
        if (cached != null && !cached.isEmpty()) {
            List<SqlExecutor.ColumnMeta> result = new ArrayList<>();
            for (var cc : cached) {
                result.add(new SqlExecutor.ColumnMeta(cc.name, cc.type, cc.size, cc.nullable, cc.comment));
            }
            return result;
        }
        return loadColumns(connName, schema, table);
    }

    private List<String> loadSchemas(String connName) {
        if (!cm.isConnected(connName)) return Collections.emptyList();
        try (Connection conn = cm.getConnection(connName);
             ResultSet rs = conn.getMetaData().getSchemas()) {
            List<String> schemas = new ArrayList<>();
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
            if (schemas.isEmpty()) {
                String fallback = getSchemaFallbackSql();
                if (fallback != null) {
                    try (Statement st = conn.createStatement(); ResultSet frs = st.executeQuery(fallback)) {
                        while (frs.next()) { String s = frs.getString(1); if (s != null) schemas.add(s); }
                    }
                }
            }
            return schemas;
        } catch (Exception e) {
            log.warn("加载 schema 列表失败 ({}): {}", connName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> loadTables(String connName, String schema) {
        if (!cm.isConnected(connName)) return Collections.emptyList();
        try (Connection conn = cm.getConnection(connName);
             PreparedStatement ps = conn.prepareStatement(getTableQuerySql())) {
            ps.setString(1, schema);
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            cache.putObjects(connName, schema, "TABLE", tables);
            loadTableComments(conn, connName, schema);
            return tables;
        } catch (Exception e) {
            log.warn("加载表列表失败 ({} {}): {}", connName, schema, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void loadTableComments(Connection conn, String connName, String schema) {
        String commentSql = getTableCommentQuerySql();
        if (commentSql == null) return;
        try (PreparedStatement ps = conn.prepareStatement(commentSql)) {
            ps.setString(1, schema);
            if (commentSql.indexOf('?') != commentSql.lastIndexOf('?')) {
                ps.setString(2, schema);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String comment = rs.getString(2);
                    if (name != null && comment != null && !comment.isEmpty()) {
                        cache.putTableComment(connName, schema, name, comment);
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("加载表注释失败 ({} {}): {}", connName, schema, e.getMessage());
        }
    }

    /** 获取包内的子程序列表（Oracle/OB 专有，默认返回空）。 */
    public List<String> getPackageContents(String connName, String schema, String packageName) {
        return Collections.emptyList();
    }

    public void preloadTableComments(String connName, List<String> schemas) {
        if (!cm.isConnected(connName)) return;
        try (Connection conn = cm.getConnection(connName)) {
            preloadTableComments(conn, connName, schemas);
        } catch (SQLException e) {
            log.warn("批量加载表注释失败 ({}): {}", connName, e.getMessage());
        }
    }

    /** 复用已有连接的批量加载版本，适用于调用方已持有 Connection 的场景。 */
    public void preloadTableComments(Connection conn, String connName, List<String> schemas) {
        String commentSql = getTableCommentQuerySql();
        if (commentSql == null) return;
        boolean needsLoad = false;
        for (String schema : schemas) {
            List<String> tables = cache.getObjects(connName, schema, "TABLE");
            if (tables != null && !tables.isEmpty()
                && cache.getTableComment(connName, schema, tables.get(0)) == null) {
                needsLoad = true;
                break;
            }
        }
        if (!needsLoad) return;
        for (String schema : schemas) {
            loadTableComments(conn, connName, schema);
        }
    }

    private List<SqlExecutor.ColumnMeta> loadColumns(String connName, String schema, String table) {
        if (!cm.isConnected(connName)) return Collections.emptyList();
        try (Connection conn = cm.getConnection(connName)) {
            var cols = executor.getColumns(conn, schema, table);
            if (!cols.isEmpty()) {
                List<CachedColumn> cached = new ArrayList<>();
                for (var cm : cols) {
                    CachedColumn cc = new CachedColumn();
                    cc.name = cm.name;
                    cc.type = cm.type;
                    cc.size = cm.size;
                    cc.nullable = cm.nullable;
                    cc.comment = cm.comment;
                    cached.add(cc);
                }
                cache.putColumns(connName, schema, table, cached);
            }
            return cols;
        } catch (Exception e) {
            log.warn("加载列列表失败 ({} {} {}): {}", connName, schema, table, e.getMessage());
            return Collections.emptyList();
        }
    }
}
