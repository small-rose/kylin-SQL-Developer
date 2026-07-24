package com.kylin.plsql.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * DML 差异提交服务抽象基类。<br>
 * 子类只需实现 {@link #quoteId(String)} 和 {@link #fullName(String, String)} 以适配不同数据库的标识符引用规则。
 */
public abstract class DataChangeService {
    private static final Logger log = LoggerFactory.getLogger(DataChangeService.class);

    public static class DiffResult {
        public final List<RowOp> deletes = new ArrayList<>();
        public final List<RowOp> inserts = new ArrayList<>();
        public final List<RowOp> updates = new ArrayList<>();

        public boolean isEmpty() {
            return deletes.isEmpty() && inserts.isEmpty() && updates.isEmpty();
        }

        public int totalOps() {
            return deletes.size() + inserts.size() + updates.size();
        }
    }

    public static class RowOp {
        public final int absRow;
        public final List<Object> originalValues;
        public final List<Object> currentValues;
        public final Set<Integer> changedColumns;

        public RowOp(int absRow, List<Object> originalValues, List<Object> currentValues, Set<Integer> changedColumns) {
            this.absRow = absRow;
            this.originalValues = originalValues;
            this.currentValues = currentValues;
            this.changedColumns = changedColumns;
        }
    }

    public DiffResult diff(List<String> columns, List<List<Object>> originalRows,
                           List<List<Object>> currentRows,
                           Set<Integer> newRowIndices, Set<Integer> deletedRowIndices) {
        DiffResult result = new DiffResult();

        // deleted rows
        for (int absRow : deletedRowIndices) {
            if (absRow >= 0 && absRow < originalRows.size()) {
                result.deletes.add(new RowOp(absRow, originalRows.get(absRow), null, null));
            }
        }

        // modified and new rows
        for (int absRow = 0; absRow < currentRows.size(); absRow++) {
            List<Object> current = currentRows.get(absRow);

            if (newRowIndices.contains(absRow)) {
                result.inserts.add(new RowOp(absRow, null, current, null));
                continue;
            }

            if (absRow >= originalRows.size()) {
                result.inserts.add(new RowOp(absRow, null, current, null));
                continue;
            }

            List<Object> original = originalRows.get(absRow);
            Set<Integer> changedColumns = new java.util.HashSet<>();
            for (int c = 0; c < columns.size(); c++) {
                Object ov = c < original.size() ? original.get(c) : null;
                Object cv = c < current.size() ? current.get(c) : null;
                if (!Objects.equals(ov, cv)) {
                    changedColumns.add(c);
                }
            }
            if (!changedColumns.isEmpty()) {
                result.updates.add(new RowOp(absRow, original, current, changedColumns));
            }
        }

        return result;
    }

    public int execute(Connection conn, String schema, String table,
                       List<String> columns, DiffResult diff) throws SQLException {
        int total = 0;
        boolean origAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            total += executeDeletes(conn, schema, table, columns, diff.deletes);
            total += executeInserts(conn, schema, table, columns, diff.inserts);
            total += executeUpdates(conn, schema, table, columns, diff.updates);
            conn.commit();
            log.info("提交变更完成: 共 {} 行 (删={}, 增={}, 改={})",
                total, diff.deletes.size(), diff.inserts.size(), diff.updates.size());
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(origAutoCommit);
        }
        return total;
    }

    private int executeDeletes(Connection conn, String schema, String table,
                                List<String> columns, List<RowOp> ops) throws SQLException {
        if (ops.isEmpty()) return 0;
        String name = fullName(schema, table);
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(name).append(" WHERE ");
        appendWhereEq(sql, columns);
        log.info("提交变更 - DELETE: {} ({} rows)", sql, ops.size());
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (RowOp op : ops) {
                int idx = 1;
                for (int i = 0; i < columns.size(); i++) {
                    Object v = get(op.originalValues, i);
                    ps.setObject(idx++, v);
                    ps.setObject(idx++, v);
                }
                ps.addBatch();
            }
            return sumBatch(ps.executeBatch());
        }
    }

    private int executeInserts(Connection conn, String schema, String table,
                                List<String> columns, List<RowOp> ops) throws SQLException {
        if (ops.isEmpty()) return 0;
        String name = fullName(schema, table);
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) { cols.append(", "); vals.append(", "); }
            cols.append(quoteId(columns.get(i)));
            vals.append("?");
        }
        String sql = "INSERT INTO " + name + " (" + cols + ") VALUES (" + vals + ")";
        log.info("提交变更 - INSERT: {} ({} rows)", sql, ops.size());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (RowOp op : ops) {
                for (int i = 0; i < columns.size(); i++) {
                    ps.setObject(i + 1, get(op.currentValues, i));
                }
                ps.addBatch();
            }
            return sumBatch(ps.executeBatch());
        }
    }

    private int executeUpdates(Connection conn, String schema, String table,
                                List<String> columns, List<RowOp> ops) throws SQLException {
        if (ops.isEmpty()) return 0;
        String name = fullName(schema, table);

        try (PreparedStatement ps = buildUpdatePs(conn, name, columns, ops)) {
            int idx;
            for (RowOp op : ops) {
                idx = 1;
                // SET params: only changed columns
                for (int c : op.changedColumns) {
                    ps.setObject(idx++, get(op.currentValues, c));
                }
                // WHERE params: all columns (doubled)
                for (int i = 0; i < columns.size(); i++) {
                    Object v = get(op.originalValues, i);
                    ps.setObject(idx++, v);
                    ps.setObject(idx++, v);
                }
                ps.addBatch();
            }
            return sumBatch(ps.executeBatch());
        }
    }

    private PreparedStatement buildUpdatePs(Connection conn, String name,
                                            List<String> columns, List<RowOp> ops) throws SQLException {
        // Build SET clause from first op's changed columns (they should all have the same set)
        Set<Integer> changedCols = ops.get(0).changedColumns;
        StringBuilder setClause = new StringBuilder();
        List<Integer> colList = new ArrayList<>(changedCols);
        java.util.Collections.sort(colList);
        for (int i = 0; i < colList.size(); i++) {
            if (i > 0) setClause.append(", ");
            setClause.append(quoteId(columns.get(colList.get(i)))).append(" = ?");
        }
        StringBuilder sql = new StringBuilder("UPDATE ").append(name)
                .append(" SET ").append(setClause).append(" WHERE ");
        appendWhereEq(sql, columns);
        log.info("提交变更 - UPDATE: {} ({} rows)", sql, ops.size());
        return conn.prepareStatement(sql.toString());
    }

    private void appendWhereEq(StringBuilder sql, List<String> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(" AND ");
            String id = quoteId(columns.get(i));
            sql.append("(").append(id).append(" = ? OR (").append(id).append(" IS NULL AND ? IS NULL))");
        }
    }

    private Object get(List<Object> list, int index) {
        return index < list.size() ? list.get(index) : null;
    }

    private int sumBatch(int[] results) {
        int sum = 0;
        for (int r : results) if (r > 0) sum += r;
        return sum;
    }

    protected abstract String quoteId(String id);

    protected abstract String fullName(String schema, String table);
}
