package com.kylin.plsql.core.service;

import com.kylin.plsql.core.db.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Oracle / OceanBase Oracle 模式的 Schema 查询实现，使用 ALL_TABLES / ALL_VIEWS 数据字典。 */
public class OracleSchemaService extends SchemaService {
    private static final Logger log = LoggerFactory.getLogger(OracleSchemaService.class);

    public OracleSchemaService(ConnectionManager cm) {
        super(cm);
    }

    @Override protected String getTableQuerySql() {
        return "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name";
    }

    @Override protected String getViewQuerySql() {
        return "SELECT view_name FROM all_views WHERE owner = ? ORDER BY view_name";
    }

    @Override protected String getIndexQuerySql() {
        return "SELECT index_name FROM all_indexes WHERE owner = ? ORDER BY index_name";
    }

    @Override protected String getSequenceQuerySql() {
        return "SELECT sequence_name FROM all_sequences WHERE sequence_owner = ? ORDER BY sequence_name";
    }

    @Override protected String getFunctionQuerySql() {
        return "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'FUNCTION' ORDER BY object_name";
    }

    @Override protected String getProcedureQuerySql() {
        return "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'PROCEDURE' ORDER BY object_name";
    }

    @Override protected String getPackageQuerySql() {
        return "SELECT DISTINCT object_name FROM all_objects WHERE owner = ? AND object_type IN ('PACKAGE','PACKAGE BODY') ORDER BY object_name";
    }

    @Override protected String getSynonymQuerySql() {
        return "SELECT synonym_name FROM all_synonyms WHERE owner = ? ORDER BY synonym_name";
    }

    @Override protected String getTableCommentQuerySql() {
        return "SELECT table_name, comments FROM all_tab_comments WHERE owner = ?";
    }

    @Override protected String getSchemaFallbackSql() {
        return "SELECT DISTINCT owner FROM all_objects WHERE owner NOT IN ('SYS','SYSTEM','PUBLIC','OCEANBASE','MYSQL') ORDER BY owner";
    }

    @Override
    public List<String> getPackageContents(String connName, String schema, String packageName) {
        // ① ALL_PROCEDURES 标准路径
        String sql = "SELECT OBJECT_NAME, PROCEDURE_NAME, OBJECT_TYPE FROM ALL_PROCEDURES WHERE OWNER = ? AND OBJECT_NAME = ? ORDER BY PROCEDURE_NAME";
        try (Connection conn = cm.getConnection(connName); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, packageName);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rs.next()) {
                    String sub = rs.getString("PROCEDURE_NAME");
                    if (sub != null) result.add(sub);
                }
                if (!result.isEmpty()) return result;
            }
        } catch (SQLException e) {
            log.warn("ALL_PROCEDURES 查询失败: {}", e.getMessage());
        }

        // ② OceanBase 降级：ALL_SOURCE 解析
        try (Connection conn = cm.getConnection(connName)) {
            String product = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (!product.contains("oceanbase")) return Collections.emptyList();

            String q = "SELECT text FROM all_source WHERE owner=? AND name=? AND type='PACKAGE' ORDER BY line";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, schema);
                ps.setString(2, packageName);
                try (ResultSet rs = ps.executeQuery()) {
                    StringBuilder src = new StringBuilder();
                    while (rs.next()) src.append(rs.getString("text"));
                    if (src.length() == 0) return Collections.emptyList();
                    Pattern p = Pattern.compile(
                        "^\\s*(FUNCTION|PROCEDURE)\\s+(\\w+)\\b",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
                    Matcher m = p.matcher(src);
                    List<String> result = new ArrayList<>();
                    while (m.find()) { result.add(m.group(2)); }
                    return result;
                }
            }
        } catch (SQLException e) {
            log.warn("ALL_SOURCE 展开包失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override protected String[] getTypeDefs() {
        return new String[]{
            "模式;SCHEMA;false",
            "表;TABLE;true",
            "视图;VIEW;false",
            "索引;INDEX;false",
            "序列;SEQUENCE;false",
            "同义词;SYNONYM;false",
            "函数;FUNCTION;false",
            "过程;PROCEDURE;false",
            "包;PACKAGE;true"
        };
    }
}
