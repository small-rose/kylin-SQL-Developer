package com.kylin.plsql.core.service;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.db.SqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylin.plsql.core.cache.MetadataCache.CachedColumn;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public List<SqlExecutor.ColumnMeta> getColumns(String connName, String schema, String table) {
        var cached = cache.getColumns(connName, schema, table);
        if (cached != null && !cached.isEmpty()) {
            List<SqlExecutor.ColumnMeta> result = new ArrayList<>();
            for (var cc : cached) {
                result.add(new SqlExecutor.ColumnMeta(cc.name, cc.type, cc.size, cc.nullable));
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
            return schemas;
        } catch (Exception e) {
            log.warn("加载 schema 列表失败 ({}): {}", connName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> loadTables(String connName, String schema) {
        if (!cm.isConnected(connName)) return Collections.emptyList();
        try (Connection conn = cm.getConnection(connName);
             java.sql.PreparedStatement ps = conn.prepareStatement(getTableQuerySql())) {
            ps.setString(1, schema);
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            cache.putObjects(connName, schema, "TABLE", tables);
            return tables;
        } catch (Exception e) {
            log.warn("加载表列表失败 ({} {}): {}", connName, schema, e.getMessage());
            return Collections.emptyList();
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
