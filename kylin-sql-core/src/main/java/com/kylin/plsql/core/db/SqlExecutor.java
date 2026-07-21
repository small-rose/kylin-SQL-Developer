package com.kylin.plsql.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylin.plsql.core.format.dialect.DialectManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SQL execution engine with DDL generation, DML templates, and source retrieval. */
public class SqlExecutor {
    private static final Logger log = LoggerFactory.getLogger(SqlExecutor.class);

    public static class SqlResult {
        public final List<String> columns;
        public final List<List<Object>> rows;
        public final long elapsedMs;
        public final int updateCount;
        public final boolean isQuery;
        public final String error;

        public SqlResult(List<String> columns, List<List<Object>> rows, long elapsedMs) {
            this.columns = columns;
            this.rows = rows;
            this.elapsedMs = elapsedMs;
            this.updateCount = -1;
            this.isQuery = true;
            this.error = null;
        }

        public SqlResult(int updateCount, long elapsedMs) {
            this.columns = null;
            this.rows = null;
            this.elapsedMs = elapsedMs;
            this.updateCount = updateCount;
            this.isQuery = false;
            this.error = null;
        }

        public SqlResult(String error, long elapsedMs) {
            this.columns = null;
            this.rows = null;
            this.elapsedMs = elapsedMs;
            this.updateCount = -1;
            this.isQuery = false;
            this.error = error;
        }

        public boolean isSuccess() { return error == null; }

        public String getSummary() {
            if (error != null) return "\u9519\u8BEF: " + error;
            if (isQuery) return "\u67E5\u8BE2\u5B8C\u6210, " + rows.size() + " \u884C, " + elapsedMs + "ms";
            return "\u5F71\u54CD " + updateCount + " \u884C, " + elapsedMs + "ms";
        }

        public int getRowCount() { return rows != null ? rows.size() : 0; }
    }

    public SqlResult execute(Connection conn, String sql) {
        return execute(conn, sql, 0);
    }

    public SqlResult execute(Connection conn, String sql, int queryTimeoutSec) {
        long start = System.currentTimeMillis();
        sql = sql.trim();
        while (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();

        try {
            boolean isQuery;
            try (Statement stmt = conn.createStatement()) {
                if (queryTimeoutSec > 0) stmt.setQueryTimeout(queryTimeoutSec);
                isQuery = stmt.execute(sql);
                if (isQuery) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        return processResultSet(rs, start);
                    }
                } else {
                    int count = stmt.getUpdateCount();
                    return new SqlResult(count, System.currentTimeMillis() - start);
                }
            }
        } catch (SQLException e) {
            log.error("SQL \u6267\u884C\u5931\u8D25: {}", e.getMessage());
            return new SqlResult(e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private SqlResult processResultSet(ResultSet rs, long start) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i).toUpperCase());
        }
        List<List<Object>> rows = new ArrayList<>();
        int maxRows = 50000;
        while (rs.next() && rows.size() < maxRows) {
            List<Object> row = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                Object val = rs.getObject(i);
                if (val instanceof Clob) {
                    Clob clob = (Clob) val;
                    val = clob.getSubString(1, Math.min((int) clob.length(), 4000));
                }
                row.add(val);
            }
            rows.add(row);
        }
        return new SqlResult(columns, rows, System.currentTimeMillis() - start);
    }

    public static class ColumnMeta {
        public final String name;
        public final String type;
        public final int size;
        public final boolean nullable;

        public ColumnMeta(String name, String type, int size, boolean nullable) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.nullable = nullable;
        }
    }

    // ── Database type detection ──

    public static String getDbType(Connection conn) {
        try {
            String p = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (p.contains("oracle") || p.contains("oceanbase")) return "oracle";
            if (p.contains("postgresql") || p.contains("edb")) return "postgresql";
            if (p.contains("mysql") || p.contains("mariadb")) return "mysql";
            if (p.contains("sqlite")) return "sqlite";
            return p;
        } catch (SQLException e) {
            return "unknown";
        }
    }

    // ── Always-quote version (kept for backward compatibility) ──
    private static String qi(String id, String dbType) {
        if (id == null || id.isEmpty()) return id;
        if ("mysql".equals(dbType)) {
            if (id.startsWith("`")) return id;
            return "`" + id + "`";
        }
        if (id.startsWith("\"")) return id;
        return "\"" + id + "\"";
    }

    private static String qi(String id) { return qi(id, "oracle"); }

    /** 智能加引号：仅在必要时才加（保留字、含特殊字符、数字开头等），否则返回原标识符。 */
    private static String maybeQuote(String id, String dbType) {
        if (id == null || id.isEmpty()) return id;
        if (id.startsWith("\"") || id.startsWith("`")) return id;
        if (needsQuoting(id, dbType)) {
            String q = "mysql".equals(dbType) ? "`" : "\"";
            return q + id + q;
        }
        return id;
    }

    /** 判断标识符是否需要加引号。 */
    private static boolean needsQuoting(String id, String dbType) {
        if (Character.isDigit(id.charAt(0))) return true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return true;
        }
        var dialect = DialectManager.forName(dbType);
        return dialect != null && dialect.getKeywords().contains(id.toUpperCase());
    }

    // ── Preview SQL (100 rows, database-aware) ──

    public String generatePreviewSQL(Connection conn, String schema, String table) {
        String dbType = getDbType(conn);
        String fn = maybeQuote(schema, dbType) + "." + maybeQuote(table, dbType);
        return switch (dbType) {
            case "mysql", "postgresql", "sqlite" -> "SELECT * FROM " + fn + " LIMIT 100";
            default -> "SELECT * FROM " + fn + " FETCH FIRST 100 ROWS ONLY";
        };
    }

    // ── DDL Generation ──

    public String generateDDL(Connection conn, String schema, String name, String type) {
        String dbType = getDbType(conn);
        boolean plsql = "PACKAGE".equals(type) || "PACKAGE_BODY".equals(type) || "PROCEDURE".equals(type) || "FUNCTION".equals(type);
        if (plsql && "oracle".equals(dbType)) {
            return getOracleDdl(conn, schema, name, type);
        }
        if ("SEQUENCE".equalsIgnoreCase(type) && "oracle".equals(dbType)) {
            return buildSequenceDDL(conn, schema, name);
        }
        if ("INDEX".equalsIgnoreCase(type) && "oracle".equals(dbType)) {
            return buildIndexDDL(conn, schema, name);
        }
        if ("TABLE".equalsIgnoreCase(type)) {
            // Oracle: build from metadata（替代 DBMS_METADATA，避免啰嗦的 storage/tablespace 子句）
            if ("oracle".equals(dbType)) {
                return buildDDLFromMeta(conn, schema, name, dbType);
            }
            return buildDDLFromMeta(conn, schema, name, dbType);
        }
        if ("VIEW".equalsIgnoreCase(type)) {
            return buildViewDDL(conn, schema, name, dbType);
        }
        return "-- 无法获取 " + type + " " + schema + "." + name + " 的 DDL";
    }

    private String tryOracleDdl(Connection conn, String schema, String table) {
        try {
            String dbType = getDbType(conn);
            StringBuilder sb = new StringBuilder();

            // Table DDL
            String q = "SELECT DBMS_METADATA.GET_DDL('TABLE', ?, ?) AS DDL FROM DUAL";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, table);
                ps.setString(2, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String ddl = rs.getString("DDL");
                        if (ddl != null) sb.append(ddl.trim()).append("\n");
                    }
                }
            }

            // Table comment
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT comments FROM all_tab_comments WHERE owner='" + schema.replace("'", "''") + "' AND table_name='" + table.replace("'", "''") + "'")) {
                if (rs.next()) {
                    String c = rs.getString("comments");
                    if (c != null && !c.isEmpty())
                        sb.append("COMMENT ON TABLE ").append(maybeQuote(schema, dbType)).append(".").append(maybeQuote(table, dbType)).append(" IS '").append(c.replace("'", "''")).append("';\n");
                }
            }

            // Column comments
            String ccSql = "SELECT column_name, comments FROM all_col_comments WHERE owner='" + schema.replace("'", "''") + "' AND table_name='" + table.replace("'", "''") + "' AND comments IS NOT NULL";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(ccSql)) {
                while (rs.next()) {
                    String col = rs.getString("column_name");
                    String c = rs.getString("comments");
                    sb.append("COMMENT ON COLUMN ").append(maybeQuote(schema, dbType)).append(".").append(maybeQuote(table, dbType)).append(".").append(maybeQuote(col, dbType))
                      .append(" IS '").append(c.replace("'", "''")).append("';\n");
                }
            }

            // Indexes
            String idxSql = "SELECT index_name, index_type, uniqueness, tablespace_name FROM all_indexes WHERE owner='" + schema.replace("'", "''") + "' AND table_name='" + table.replace("'", "''") + "' ORDER BY index_name";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(idxSql)) {
                while (rs.next()) {
                    String idxName = rs.getString("index_name");
                    String idxType = rs.getString("index_type");
                    String unique = rs.getString("uniqueness");
                    if ("NORMAL".equals(idxType) || "BITMAP".equals(idxType) || "FUNCTION-BASED NORMAL".equals(idxType)) {
                        String colsSql = "SELECT column_name, descend FROM all_ind_columns WHERE index_owner='" + schema.replace("'", "''") + "' AND index_name='" + idxName.replace("'", "''") + "' ORDER BY column_position";
                        List<String> idxCols = new ArrayList<>();
                        try (Statement st2 = conn.createStatement(); ResultSet rs2 = st2.executeQuery(colsSql)) {
                            while (rs2.next()) idxCols.add(maybeQuote(rs2.getString("column_name"), dbType));
                        }
                        if (!idxCols.isEmpty()) {
                            sb.append("CREATE ");
                            if ("UNIQUE".equals(unique)) sb.append("UNIQUE ");
                            if ("BITMAP".equals(idxType)) sb.append("BITMAP ");
                            sb.append("INDEX ").append(maybeQuote(schema, dbType)).append(".").append(maybeQuote(idxName, dbType))
                              .append(" ON ").append(maybeQuote(schema, dbType)).append(".").append(maybeQuote(table, dbType))
                              .append(" (").append(String.join(", ", idxCols)).append(");\n");
                        }
                    }
                }
            }

            return sb.toString();
        } catch (SQLException e) {
            log.debug("Oracle DDL 生成失败: {}", e.getMessage());
            return null;
        }
    }

    /** 通过 DBMS_METADATA 获取对象的原始 DDL，供右键"查看 DDL"使用。 */
    public String getOracleDdl(Connection conn, String schema, String name, String type) {
        String ddlType = switch (type) {
            case "PACKAGE" -> "PACKAGE";
            case "PACKAGE_BODY" -> "PACKAGE_BODY";
            case "PROCEDURE" -> "PROCEDURE";
            case "FUNCTION" -> "FUNCTION";
            case "SEQUENCE" -> "SEQUENCE";
            case "INDEX" -> "INDEX";
            default -> null;
        };
        if (ddlType == null) return null;
        try {
            String q = "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) AS DDL FROM DUAL";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, ddlType);
                ps.setString(2, name);
                ps.setString(3, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String ddl = rs.getString("DDL");
                        if (ddl != null) return ddl.replaceAll("\r\n?", "\n").trim() + "\n/";
                    }
                }
            }
            return "-- \u65E0\u6CD5\u83B7\u53D6 " + type + " " + schema + "." + name + " \u7684 DDL";
        } catch (SQLException e) {
            log.error("DBMS_METADATA \u83B7\u53D6 DDL \u5931\u8D25", e);
            return "-- DBMS_METADATA \u83B7\u53D6\u5931\u8D25: " + e.getMessage();
        }
    }

    /** DBMS_METADATA 不支持时，从 ALL_SEQUENCES 手动构建 SEQUENCE DDL。 */
    private String buildSequenceDDL(Connection conn, String schema, String name) {
        String dbType = getDbType(conn);
        String sql = "SELECT sequence_name, min_value, max_value, increment_by, cycle_flag, order_flag, cache_size, last_number FROM all_sequences WHERE sequence_owner = ? AND sequence_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("CREATE SEQUENCE ").append(maybeQuote(schema, dbType)).append(".").append(maybeQuote(name, dbType)).append("\n");
                    sb.append("  INCREMENT BY ").append(rs.getString("increment_by")).append("\n");
                    sb.append("  START WITH ").append(rs.getString("last_number")).append("\n");
                    sb.append("  MINVALUE ").append(rs.getString("min_value")).append("\n");
                    sb.append("  MAXVALUE ").append(rs.getString("max_value")).append("\n");
                    sb.append("  CACHE ").append(rs.getString("cache_size")).append("\n");
                    sb.append("  ").append("Y".equals(rs.getString("cycle_flag")) ? "CYCLE" : "NOCYCLE").append("\n");
                    sb.append("  ").append("Y".equals(rs.getString("order_flag")) ? "ORDER" : "NOORDER");
                    sb.append(";\n");
                    return sb.toString();
                }
            }
        } catch (SQLException e) {
            log.error("\u624B\u52A8\u6784\u5EFA SEQUENCE DDL \u5931\u8D25", e);
        }
        return "-- \u65E0\u6CD5\u83B7\u53D6 " + name + " \u7684 DDL (ALL_SEQUENCES)";
    }

    /** DBMS_METADATA 不支持时，从 ALL_INDEXES + ALL_IND_COLUMNS 手动构建 INDEX DDL。 */
    private String buildIndexDDL(Connection conn, String schema, String name) {
        String dbType = getDbType(conn);
        String idxSql = "SELECT table_name, uniqueness FROM all_indexes WHERE owner = ? AND index_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(idxSql)) {
            ps.setString(1, schema);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String tableName = rs.getString("table_name");
                    StringBuilder sb = new StringBuilder();
                    sb.append("CREATE ");
                    if ("UNIQUE".equalsIgnoreCase(rs.getString("uniqueness"))) sb.append("UNIQUE ");
                    sb.append("INDEX ").append(maybeQuote(schema, dbType)).append(".").append(maybeQuote(name, dbType));
                    sb.append(" ON ").append(maybeQuote(schema, dbType)).append(".").append(maybeQuote(tableName, dbType)).append(" (");

                    String colSql = "SELECT column_name, column_position, descend FROM all_ind_columns WHERE index_owner = ? AND index_name = ? ORDER BY column_position";
                    try (PreparedStatement ps2 = conn.prepareStatement(colSql)) {
                        ps2.setString(1, schema);
                        ps2.setString(2, name);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            boolean first = true;
                            while (rs2.next()) {
                                if (!first) sb.append(", ");
                                sb.append(maybeQuote(rs2.getString("column_name"), dbType));
                                if ("DESC".equalsIgnoreCase(rs2.getString("descend"))) sb.append(" DESC");
                                first = false;
                            }
                        }
                    }
                    sb.append(");\n");
                    return sb.toString();
                    }
                }
        } catch (SQLException e) {
            log.error("\u624B\u52A8\u6784\u5EFA INDEX DDL \u5931\u8D25", e);
        }
        return "-- \u65E0\u6CD5\u83B7\u53D6 " + name + " \u7684 DDL (ALL_INDEXES)";
    }

    /** 构建 VIEW DDL。 */
    private String buildViewDDL(Connection conn, String schema, String view, String dbType) {
        try {
            String sql;
            if ("oracle".equals(dbType)) {
                sql = "SELECT text FROM all_views WHERE owner=? AND view_name=?";
            } else if ("mysql".equals(dbType)) {
                sql = "SELECT view_definition FROM information_schema.views WHERE table_schema=? AND table_name=?";
            } else if ("postgresql".equals(dbType)) {
                sql = "SELECT definition FROM pg_views WHERE schemaname=? AND viewname=?";
            } else {
                return "-- \u65E0\u6CD5\u83B7\u53D6 " + view + " \u7684 DDL";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                ps.setString(2, view);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String def = rs.getString(1);
                        if (def != null && !def.isEmpty()) {
                            return "CREATE OR REPLACE VIEW " + maybeQuote(schema, dbType) + "." + maybeQuote(view, dbType)
                                + " AS\n" + def.trim() + "\n/";
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("\u83B7\u53D6 VIEW DDL \u5931\u8D25", e);
        }
        return "-- \u65E0\u6CD5\u83B7\u53D6 " + view + " \u7684 DDL";
    }

    private String buildDDLFromMeta(Connection conn, String schema, String table, String dbType) {
        try {
            StringBuilder sb = new StringBuilder();
            java.util.function.BiFunction<String, String, String> qn = (s, t) -> maybeQuote(s, dbType) + "." + maybeQuote(t, dbType);

            // Columns
            List<ColumnMeta> cols = getColumns(conn, schema, table);

            // Primary keys
            List<String> pks = new ArrayList<>();
            Map<String, Integer> pkSeq = new LinkedHashMap<>();
            try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, schema, table)) {
                while (rs.next()) {
                    String cn = rs.getString("COLUMN_NAME");
                    pks.add(cn);
                    pkSeq.put(cn, rs.getInt("KEY_SEQ"));
                }
            }

            sb.append("CREATE TABLE ").append(qn.apply(schema, table)).append(" (\n");
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) sb.append(",\n");
                sb.append("    ").append(maybeQuote(cols.get(i).name, dbType)).append(" ").append(cols.get(i).type);
                if (!cols.get(i).nullable) sb.append(" NOT NULL");
            }
            if (!pks.isEmpty()) {
                sb.append(",\n    PRIMARY KEY (");
                pks.sort((a, b) -> Integer.compare(pkSeq.getOrDefault(a, 0), pkSeq.getOrDefault(b, 0)));
                for (int i = 0; i < pks.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(maybeQuote(pks.get(i), dbType));
                }
                sb.append(")");
            }
            sb.append("\n);\n");

            // Comments
            String commentSql;
            if ("mysql".equals(dbType)) {
                commentSql = "SELECT table_comment FROM information_schema.tables WHERE table_schema=? AND table_name=?";
            } else if ("oracle".equals(dbType)) {
                commentSql = "SELECT comments FROM all_tab_comments WHERE owner=? AND table_name=?";
            } else if ("postgresql".equals(dbType)) {
                commentSql = "SELECT obj_description((quote_ident(?)||'.'||quote_ident(?))::regclass)";
                commentSql = ""; // skip for simplicity
            } else {
                commentSql = "";
            }
            if (!commentSql.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(commentSql)) {
                    ps.setString(1, schema);
                    ps.setString(2, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String tc = rs.getString(1);
                            if (tc != null && !tc.isEmpty()) {
                                sb.append("COMMENT ON TABLE ").append(qn.apply(schema, table))
                                  .append(" IS '").append(tc.replace("'", "''")).append("';\n");
                            }
                        }
                    }
                }
            }

            // Column comments
            String colCommentSql;
            if ("mysql".equals(dbType)) {
                colCommentSql = "SELECT column_name, column_comment FROM information_schema.columns WHERE table_schema=? AND table_name=? AND column_comment != ''";
            } else if ("oracle".equals(dbType)) {
                colCommentSql = "SELECT column_name, comments FROM all_col_comments WHERE owner=? AND table_name=? AND comments IS NOT NULL";
            } else if ("postgresql".equals(dbType)) {
                colCommentSql = "";
            } else {
                colCommentSql = "";
            }
            if (!colCommentSql.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(colCommentSql)) {
                    ps.setString(1, schema);
                    ps.setString(2, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String cn = rs.getString(1);
                            String cc = rs.getString(2);
                            if (cc != null && !cc.isEmpty()) {
                                sb.append("COMMENT ON COLUMN ").append(qn.apply(schema, table)).append(".").append(maybeQuote(cn, dbType))
                                  .append(" IS '").append(cc.replace("'", "''")).append("';\n");
                            }
                        }
                    }
                }
            }

            // Indexes
            if ("mysql".equals(dbType)) {
                String idxSql = "SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY INDEX_NAME, SEQ_IN_INDEX";
                String lastIdx = null;
                List<String> idxCols = new ArrayList<>();
                boolean nonUnique = true;
                try (PreparedStatement ps = conn.prepareStatement(idxSql)) {
                    ps.setString(1, schema);
                    ps.setString(2, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String idxName = rs.getString("INDEX_NAME");
                            if (!idxName.equals(lastIdx)) {
                                if (lastIdx != null && !"PRIMARY".equals(lastIdx) && !idxCols.isEmpty()) {
                                    sb.append("CREATE ");
                                    if (!nonUnique) sb.append("UNIQUE ");
                                    sb.append("INDEX ").append(maybeQuote(lastIdx, dbType))
                                      .append(" ON ").append(qn.apply(schema, table))
                                      .append(" (").append(String.join(", ", idxCols)).append(");\n");
                                }
                                lastIdx = idxName;
                                idxCols.clear();
                                nonUnique = rs.getInt("NON_UNIQUE") == 1;
                            }
                            String cn = rs.getString("COLUMN_NAME");
                            if (cn != null) idxCols.add(maybeQuote(cn, dbType));
                        }
                        if (lastIdx != null && !"PRIMARY".equals(lastIdx) && !idxCols.isEmpty()) {
                            sb.append("CREATE ");
                            if (!nonUnique) sb.append("UNIQUE ");
                            sb.append("INDEX ").append(maybeQuote(lastIdx, dbType))
                              .append(" ON ").append(qn.apply(schema, table))
                              .append(" (").append(String.join(", ", idxCols)).append(");\n");
                        }
                    }
                }
            } else if ("oracle".equals(dbType)) {
                // Oracle 索引：从 ALL_INDEXES + ALL_IND_COLUMNS 构建
                String idxSql = "SELECT i.index_name, i.index_type, i.uniqueness, c.column_name, c.column_position "
                    + "FROM all_indexes i JOIN all_ind_columns c ON i.owner=c.index_owner AND i.index_name=c.index_name "
                    + "WHERE i.owner=? AND i.table_name=? ORDER BY i.index_name, c.column_position";
                String lastIdx = null;
                List<String> idxCols = new ArrayList<>();
                String idxUnique = null;
                try (PreparedStatement ps = conn.prepareStatement(idxSql)) {
                    ps.setString(1, schema);
                    ps.setString(2, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String idxName = rs.getString("index_name");
                            if (!idxName.equals(lastIdx)) {
                                if (lastIdx != null && !idxCols.isEmpty()) {
                                    sb.append("CREATE ");
                                    if ("UNIQUE".equalsIgnoreCase(idxUnique)) sb.append("UNIQUE ");
                                    sb.append("INDEX ").append(maybeQuote(lastIdx, dbType))
                                      .append(" ON ").append(qn.apply(schema, table))
                                      .append(" (").append(String.join(", ", idxCols)).append(");\n");
                                }
                                lastIdx = idxName;
                                idxCols.clear();
                                idxUnique = rs.getString("uniqueness");
                            }
                            String cn = rs.getString("column_name");
                            if (cn != null) idxCols.add(maybeQuote(cn, dbType));
                        }
                        if (lastIdx != null && !idxCols.isEmpty()) {
                            sb.append("CREATE ");
                            if ("UNIQUE".equalsIgnoreCase(idxUnique)) sb.append("UNIQUE ");
                            sb.append("INDEX ").append(maybeQuote(lastIdx, dbType))
                              .append(" ON ").append(qn.apply(schema, table))
                              .append(" (").append(String.join(", ", idxCols)).append(");\n");
                        }
                    }
                }
            } else if ("postgresql".equals(dbType)) {
                try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                        "SELECT indexdef FROM pg_indexes WHERE schemaname='" + schema.replace("'", "''") + "' AND tablename='" + table.replace("'", "''") + "' ORDER BY indexname")) {
                    while (rs.next()) sb.append(rs.getString("indexdef")).append(";\n");
                }
            }

            return sb.toString();
        } catch (SQLException e) {
            log.error("\u751F\u6210 DDL \u5931\u8D25", e);
            return "-- \u65E0\u6CD5\u83B7\u53D6 " + schema + "." + table + " \u7684 DDL";
        }
    }

    public List<ColumnMeta> getColumns(Connection conn, String schema, String table) throws SQLException {
        List<ColumnMeta> cols = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getColumns(null, schema, table, "%")) {
            while (rs.next()) {
                cols.add(new ColumnMeta(
                    rs.getString("COLUMN_NAME"),
                    rs.getString("TYPE_NAME"),
                    rs.getInt("COLUMN_SIZE"),
                    rs.getString("IS_NULLABLE").equals("YES")
                ));
            }
        }
        return cols;
    }

    // ── DML generation ──

    public String generateDML(Connection conn, String schema, String table, String dmlType, List<ColumnMeta> columns) {
        String dbType = getDbType(conn);
        String fullName = maybeQuote(schema, dbType) + "." + maybeQuote(table, dbType);
        if ("SELECT".equalsIgnoreCase(dmlType)) {
            return "SELECT *\nFROM " + fullName + "\nWHERE 1=1\nFETCH FIRST 100 ROWS ONLY;";
        }
        if ("INSERT".equalsIgnoreCase(dmlType)) {
            return generateInsert(conn, schema, table, columns);
        }
        if ("UPDATE".equalsIgnoreCase(dmlType)) {
            StringBuilder sb = new StringBuilder("UPDATE ").append(fullName).append("\nSET\n");
            for (int i = 0; i < columns.size(); i++) {
                sb.append("    ").append(maybeQuote(columns.get(i).name, dbType)).append(" = ?");
                if (i < columns.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("WHERE 1=1\n    -- AND ").append(maybeQuote(columns.get(0).name, dbType)).append(" = ?");
            sb.append(";\n-- \u66FF\u6362 ? \u4E3A\u5B9E\u9645\u503C\u540E\u6267\u884C");
            return sb.toString();
        }
        if ("DELETE".equalsIgnoreCase(dmlType)) {
            return "DELETE FROM " + fullName + "\nWHERE 1=1\n    -- AND " + maybeQuote(columns.get(0).name, dbType) + " = ?\n;";
        }
        return "-- \u4E0D\u652F\u6301\u7684\u64CD\u4F5C: " + dmlType;
    }

    public String generateSelect(String schema, String table, List<ColumnMeta> columns) {
        String fullName = qi(schema) + "." + qi(table);
        StringBuilder sb = new StringBuilder("SELECT *\nFROM ").append(fullName).append("\nWHERE 1=1");
        if (!columns.isEmpty()) {
            sb.append("\n-- \u5217: ");
            for (int i = 0; i < Math.min(columns.size(), 5); i++) {
                if (i > 0) sb.append(", ");
                sb.append(columns.get(i).name);
            }
            if (columns.size() > 5) sb.append(" ...");
        }
        sb.append(";\n-- \u6570\u636E\u9884\u89C8: \u76F4\u63A5\u6267\u884C\nSELECT *\nFROM ").append(fullName).append("\nFETCH FIRST 100 ROWS ONLY;");
        return sb.toString();
    }

    public String generateInsert(Connection conn, String schema, String table, List<ColumnMeta> columns) {
        String dbType = getDbType(conn);
        String fullName = maybeQuote(schema, dbType) + "." + maybeQuote(table, dbType);
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) { cols.append(", "); vals.append(", "); }
            cols.append(maybeQuote(columns.get(i).name, dbType));
            vals.append("?");
        }
        return "INSERT INTO " + fullName + " (\n    " + cols + "\n) VALUES (\n    " + vals + "\n);\n-- \u66FF\u6362 ? \u4E3A\u5B9E\u9645\u503C\u540E\u6267\u884C";
    }

    // ── Source code retrieval for procedures/functions/packages ──

    public String getSource(Connection conn, String schema, String name, String type) {
        String dbType = getDbType(conn);
        if ("oracle".equals(dbType)) return getOracleSource(conn, schema, name, type);
        if ("postgresql".equals(dbType)) return getPgSource(conn, schema, name, type);
        return "-- 不支持的数据库类型: " + dbType;
    }

    private String getOracleSource(Connection conn, String schema, String name, String type) {
        // ALL_SOURCE 不含 CREATE OR REPLACE 头，手动拼接
        String sourceType = switch (type) {
            case "PACKAGE_BODY" -> "PACKAGE BODY";
            default -> type;
        };
        try {
            // 1) ALL_SOURCE 按类型过滤查询（Oracle / OceanBase 标准路径）
            String q = "SELECT text FROM all_source WHERE owner = ? AND name = ? AND type = ? ORDER BY line";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, schema.toUpperCase());
                ps.setString(2, name.toUpperCase());
                ps.setString(3, sourceType);
                try (ResultSet rs = ps.executeQuery()) {
                    StringBuilder sb = new StringBuilder();
                    while (rs.next()) {
                        sb.append(rs.getString("text"));
                    }
                    String src = sb.toString().replaceAll("\r\n?", "\n").trim();
                    if (!src.isEmpty()) return "CREATE OR REPLACE " + src;
                }
            }

            // 2) ALL_SOURCE 无数据时尝试 DBMS_METADATA 降级（Oracle 有效，OceanBase 4.x+ 部分支持）
            String ddl = getOracleDdl(conn, schema, name, type);
            if (ddl != null && !ddl.startsWith("-- ")) return ddl;
        } catch (SQLException e) {
            log.error("ALL_SOURCE 查询失败", e);
        }

        return "-- 无法获取 " + type + " " + schema + "." + name + " 的源码";
    }

    private String getPgSource(Connection conn, String schema, String name, String type) {
        try {
            String q = "SELECT prosrc FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = ? AND p.proname = ?";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, schema);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String src = rs.getString("prosrc");
                        if (src != null) return "CREATE OR REPLACE " + type + " " + schema + "." + name + "\n" + src + "\n/";
                    }
                }
            }
        } catch (SQLException e) {
            log.error("pg_proc 查询失败", e);
        }
        return "-- 无法获取 " + type + " " + schema + "." + name + " 的源码";
    }
}