package com.kylin.plsql.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private String getDbType(Connection conn) {
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

    // ── Preview SQL (100 rows, database-aware) ──

    public String generatePreviewSQL(Connection conn, String schema, String table) {
        String dbType = getDbType(conn);
        String fn = qi(schema, dbType) + "." + qi(table, dbType);
        return switch (dbType) {
            case "mysql", "postgresql", "sqlite" -> "SELECT * FROM " + fn + " LIMIT 100";
            default -> "SELECT * FROM " + fn + " FETCH FIRST 100 ROWS ONLY";
        };
    }

    // ── DDL Generation ──

    public String generateDDL(Connection conn, String schema, String table, String type) {
        if (!"TABLE".equalsIgnoreCase(type)) {
            return "-- \u65E0\u6CD5\u83B7\u53D6 " + type + " " + schema + "." + table + " \u7684 DDL";
        }

        String dbType = getDbType(conn);

        // Oracle: try DBMS_METADATA
        if ("oracle".equals(dbType)) {
            String oracleDDL = tryOracleDdl(conn, schema, table);
            if (oracleDDL != null) return oracleDDL;
        }

        // Fallback: build from metadata
        return buildDDLFromMeta(conn, schema, table, dbType);
    }

    private String tryOracleDdl(Connection conn, String schema, String table) {
        try {
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
                        sb.append("COMMENT ON TABLE ").append(qi(schema)).append(".").append(qi(table)).append(" IS '").append(c.replace("'", "''")).append("';\n");
                }
            }

            // Column comments
            String ccSql = "SELECT column_name, comments FROM all_col_comments WHERE owner='" + schema.replace("'", "''") + "' AND table_name='" + table.replace("'", "''") + "' AND comments IS NOT NULL";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(ccSql)) {
                while (rs.next()) {
                    String col = rs.getString("column_name");
                    String c = rs.getString("comments");
                    sb.append("COMMENT ON COLUMN ").append(qi(schema)).append(".").append(qi(table)).append(".").append(qi(col))
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
                            while (rs2.next()) idxCols.add(qi(rs2.getString("column_name")));
                        }
                        if (!idxCols.isEmpty()) {
                            sb.append("CREATE ");
                            if ("UNIQUE".equals(unique)) sb.append("UNIQUE ");
                            if ("BITMAP".equals(idxType)) sb.append("BITMAP ");
                            sb.append("INDEX ").append(qi(schema)).append(".").append(qi(idxName))
                              .append(" ON ").append(qi(schema)).append(".").append(qi(table))
                              .append(" (").append(String.join(", ", idxCols)).append(");\n");
                        }
                    }
                }
            }

            return sb.toString();
        } catch (SQLException e) {
            log.debug("Oracle DDL \u751F\u6210\u5931\u8D25: {}", e.getMessage());
            return null;
        }
    }

    private String buildDDLFromMeta(Connection conn, String schema, String table, String dbType) {
        try {
            StringBuilder sb = new StringBuilder();
            java.util.function.BiFunction<String, String, String> qn = (s, t) -> qi(s, dbType) + "." + qi(t, dbType);

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
                sb.append("    ").append(qi(cols.get(i).name, dbType)).append(" ").append(cols.get(i).type);
                if (!cols.get(i).nullable) sb.append(" NOT NULL");
            }
            if (!pks.isEmpty()) {
                sb.append(",\n    PRIMARY KEY (");
                pks.sort((a, b) -> Integer.compare(pkSeq.getOrDefault(a, 0), pkSeq.getOrDefault(b, 0)));
                for (int i = 0; i < pks.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(qi(pks.get(i), dbType));
                }
                sb.append(")");
            }
            sb.append("\n);\n");

            // Comments
            String commentSql;
            if ("mysql".equals(dbType)) {
                commentSql = "SELECT table_comment FROM information_schema.tables WHERE table_schema=? AND table_name=?";
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

            // Column comments (MySQL/PostgreSQL)
            String colCommentSql;
            if ("mysql".equals(dbType)) {
                colCommentSql = "SELECT column_name, column_comment FROM information_schema.columns WHERE table_schema=? AND table_name=? AND column_comment != ''";
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
                                sb.append("COMMENT ON COLUMN ").append(qn.apply(schema, table)).append(".").append(qi(cn, dbType))
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
                                    sb.append("INDEX ").append(qi(lastIdx, dbType))
                                      .append(" ON ").append(qn.apply(schema, table))
                                      .append(" (").append(String.join(", ", idxCols)).append(");\n");
                                }
                                lastIdx = idxName;
                                idxCols.clear();
                                nonUnique = rs.getInt("NON_UNIQUE") == 1;
                            }
                            String cn = rs.getString("COLUMN_NAME");
                            if (cn != null) idxCols.add(qi(cn, dbType));
                        }
                        if (lastIdx != null && !"PRIMARY".equals(lastIdx) && !idxCols.isEmpty()) {
                            sb.append("CREATE ");
                            if (!nonUnique) sb.append("UNIQUE ");
                            sb.append("INDEX ").append(qi(lastIdx, dbType))
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

    public String generateDML(String schema, String table, String dmlType, List<ColumnMeta> columns) {
        String fullName = qi(schema) + "." + qi(table);
        if ("SELECT".equalsIgnoreCase(dmlType)) {
            return "SELECT *\nFROM " + fullName + "\nWHERE 1=1\nFETCH FIRST 100 ROWS ONLY;";
        }
        if ("INSERT".equalsIgnoreCase(dmlType)) {
            return generateInsert(schema, table, columns);
        }
        if ("UPDATE".equalsIgnoreCase(dmlType)) {
            StringBuilder sb = new StringBuilder("UPDATE ").append(fullName).append("\nSET\n");
            for (int i = 0; i < columns.size(); i++) {
                sb.append("    ").append(qi(columns.get(i).name)).append(" = ?");
                if (i < columns.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("WHERE 1=1\n    -- AND ").append(qi(columns.get(0).name)).append(" = ?");
            sb.append(";\n-- \u66FF\u6362 ? \u4E3A\u5B9E\u9645\u503C\u540E\u6267\u884C");
            return sb.toString();
        }
        if ("DELETE".equalsIgnoreCase(dmlType)) {
            return "DELETE FROM " + fullName + "\nWHERE 1=1\n    -- AND " + qi(columns.get(0).name) + " = ?\n;";
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

    public String generateInsert(String schema, String table, List<ColumnMeta> columns) {
        String fullName = qi(schema) + "." + qi(table);
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) { cols.append(", "); vals.append(", "); }
            cols.append(qi(columns.get(i).name));
            vals.append("?");
        }
        return "INSERT INTO " + fullName + " (\n    " + cols + "\n) VALUES (\n    " + vals + "\n);\n-- 替换 ? 为实际值后执行";
    }

    // ── Source code retrieval for procedures/functions/packages ──

    public String getSource(Connection conn, String schema, String name, String type) {
        String dbType = getDbType(conn);
        if ("oracle".equals(dbType)) return getOracleSource(conn, schema, name, type);
        if ("postgresql".equals(dbType)) return getPgSource(conn, schema, name, type);
        return "-- 不支持的数据库类型: " + dbType;
    }

    private String getOracleSource(Connection conn, String schema, String name, String type) {
        // Try DBMS_METADATA (skip PACKAGE - returns spec+body combined in some DBs)
        String ddlType = switch (type) {
            case "PACKAGE_BODY" -> "PACKAGE_BODY";
            case "PROCEDURE" -> "PROCEDURE";
            case "FUNCTION" -> "FUNCTION";
            default -> null;
        };
        if (ddlType != null) {
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
            } catch (SQLException e) {
                log.debug("DBMS_METADATA fallback to ALL_SOURCE: {}", e.getMessage());
            }
        }

        // Fallback to ALL_SOURCE
        String sourceType = switch (type) {
            case "PACKAGE_BODY" -> "PACKAGE BODY";
            default -> type;
        };
        try {
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
                    if (!src.isEmpty()) return src;
                }
            }
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