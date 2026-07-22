package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.db.SqlExecutor;
import com.kylin.plsql.core.service.model.DataPreview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据查询服务抽象基类。<br>
 * 提供数据预览（preview）和自定义 SQL 执行（executeQuery），
 * 子类覆盖 buildPreviewSql() / quoteIdentifier() 以适配不同数据库方言。
 */
public abstract class DataQueryService {
    private static final Logger log = LoggerFactory.getLogger(DataQueryService.class);

    protected final ConnectionManager cm;
    protected final SqlExecutor executor = new SqlExecutor();

    public DataQueryService(ConnectionManager cm) {
        this.cm = cm;
    }

    protected abstract String buildPreviewSql(String schema, String table, int limit);
    protected abstract String quoteIdentifier(String id);

    public DataPreview preview(String connName, String schema, String table, int limit) {
        if (!cm.isConnected(connName)) return new DataPreview(List.of(), List.of());
        try (Connection conn = cm.getConnection(connName)) {
            String sql = buildPreviewSql(schema, table, limit);
            var result = executor.execute(conn, sql);
            if (result.isSuccess() && result.columns != null) {
                return new DataPreview(result.columns, result.rows);
            }
            return new DataPreview(List.of(), List.of());
        } catch (Exception e) {
            log.warn("预览数据失败 ({}: {}.{}): {}", connName, schema, table, e.getMessage());
            return new DataPreview(List.of(), List.of());
        }
    }

    public DataPreview executeQuery(String connName, String sql) {
        if (!cm.isConnected(connName)) return new DataPreview("\u672A\u8FDE\u63A5: " + connName);
        try (Connection conn = cm.getConnection(connName)) {
            var result = executor.execute(conn, sql);
            if (result.error != null) return new DataPreview(result.error);
            if (result.columns != null) return new DataPreview(result.columns, result.rows);
            return new DataPreview("\u67E5\u8BE2\u672A\u8FD4\u56DE\u7ED3\u679C\u96C6");
        } catch (Exception e) {
            log.warn("\u81EA\u5B9A\u4E49SQL\u67E5\u8BE2\u5931\u8D25: {}", e.getMessage());
            return new DataPreview(e.getMessage());
        }
    }

    public List<SqlExecutor.ColumnMeta> getColumns(String connName, String schema, String table) {
        if (!cm.isConnected(connName)) return List.of();
        try (Connection conn = cm.getConnection(connName)) {
            return executor.getColumns(conn, schema, table);
        } catch (Exception e) {
            log.warn("获取列信息失败 ({}: {}.{}): {}", connName, schema, table, e.getMessage());
            return List.of();
        }
    }
}
