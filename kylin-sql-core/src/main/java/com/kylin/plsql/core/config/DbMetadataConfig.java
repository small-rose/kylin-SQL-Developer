package com.kylin.plsql.core.config;

import java.util.ArrayList;
import java.util.List;

public class DbMetadataConfig {
    private String dbTypeKey;
    private String displayName;
    private boolean enabled;
    private List<TypeDef> types;
    private List<CustomColumn> extraColumns;

    public DbMetadataConfig() {}

    public DbMetadataConfig(String dbTypeKey, String displayName, boolean enabled,
                            List<TypeDef> types, List<CustomColumn> extraColumns) {
        this.dbTypeKey = dbTypeKey;
        this.displayName = displayName;
        this.enabled = enabled;
        this.types = types;
        this.extraColumns = extraColumns;
    }

    public String getDbTypeKey() { return dbTypeKey; }
    public void setDbTypeKey(String v) { this.dbTypeKey = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public List<TypeDef> getTypes() { return types; }
    public void setTypes(List<TypeDef> v) { this.types = v; }
    public List<CustomColumn> getExtraColumns() { return extraColumns; }
    public void setExtraColumns(List<CustomColumn> v) { this.extraColumns = v; }

    public static class TypeDef {
        private String label;
        private String typeCode;
        private String queryType; // "SQL" or "FIXED_LIST"
        private String querySql;
        private List<String> fixedValues;
        private boolean expandable;

        public TypeDef() {}

        public TypeDef(String label, String typeCode, String querySql, boolean expandable) {
            this.label = label;
            this.typeCode = typeCode;
            this.queryType = "SQL";
            this.querySql = querySql;
            this.fixedValues = new ArrayList<>();
            this.expandable = expandable;
        }

        public TypeDef(String label, String typeCode, List<String> fixedValues, boolean expandable) {
            this.label = label;
            this.typeCode = typeCode;
            this.queryType = "FIXED_LIST";
            this.querySql = "";
            this.fixedValues = fixedValues;
            this.expandable = expandable;
        }

        public String getLabel() { return label; }
        public void setLabel(String v) { this.label = v; }
        public String getTypeCode() { return typeCode; }
        public void setTypeCode(String v) { this.typeCode = v; }
        public String getQueryType() { return queryType; }
        public void setQueryType(String v) { this.queryType = v; }
        public String getQuerySql() { return querySql; }
        public void setQuerySql(String v) { this.querySql = v; }
        public List<String> getFixedValues() { return fixedValues; }
        public void setFixedValues(List<String> v) { this.fixedValues = v; }
        public boolean isExpandable() { return expandable; }
        public void setExpandable(boolean v) { this.expandable = v; }
    }

    public static class CustomColumn {
        private String header;
        private String expression;

        public CustomColumn() {}

        public CustomColumn(String header, String expression) {
            this.header = header;
            this.expression = expression;
        }

        public String getHeader() { return header; }
        public void setHeader(String v) { this.header = v; }
        public String getExpression() { return expression; }
        public void setExpression(String v) { this.expression = v; }
    }

    // ── Defaults ──

    public static DbMetadataConfig createOracleDefault() {
        List<TypeDef> types = List.of(
            new TypeDef("模式", "SCHEMA",    new ArrayList<>(), false),
            new TypeDef("表", "TABLE",       "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name", true),
            new TypeDef("视图", "VIEW",      "SELECT view_name FROM all_views WHERE owner = ? ORDER BY view_name", false),
            new TypeDef("索引", "INDEX",     "SELECT index_name FROM all_indexes WHERE owner = ? ORDER BY index_name", false),
            new TypeDef("序列", "SEQUENCE",  "SELECT sequence_name FROM all_sequences WHERE sequence_owner = ? ORDER BY sequence_name", false),
            new TypeDef("同义词", "SYNONYM", "SELECT synonym_name FROM all_synonyms WHERE owner = ? ORDER BY synonym_name", false),
            new TypeDef("函数", "FUNCTION",  "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'FUNCTION' ORDER BY object_name", false),
            new TypeDef("过程", "PROCEDURE", "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'PROCEDURE' ORDER BY object_name", false),
            new TypeDef("包", "PACKAGE",     "SELECT DISTINCT object_name FROM all_procedures WHERE owner = ? AND object_type IN ('PACKAGE','PACKAGE BODY') ORDER BY object_name", true)
        );
        List<CustomColumn> cols = List.of(
            new CustomColumn("状态", "SELECT status FROM all_objects WHERE owner = ? AND object_name = ? AND object_type = ?"),
            new CustomColumn("创建时间", "SELECT created FROM all_objects WHERE owner = ? AND object_name = ? AND object_type = ?")
        );
        return new DbMetadataConfig("oracle", "Oracle", false, types, new ArrayList<>(cols));
    }

    public static DbMetadataConfig createOceanBaseDefault() {
        List<TypeDef> types = List.of(
            new TypeDef("模式", "SCHEMA",    new ArrayList<>(), false),
            new TypeDef("表", "TABLE",       "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name", true),
            new TypeDef("视图", "VIEW",      "SELECT view_name FROM all_views WHERE owner = ? ORDER BY view_name", false),
            new TypeDef("索引", "INDEX",     "SELECT index_name FROM all_indexes WHERE owner = ? ORDER BY index_name", false),
            new TypeDef("序列", "SEQUENCE",  "SELECT sequence_name FROM all_sequences WHERE sequence_owner = ? ORDER BY sequence_name", false),
            new TypeDef("同义词", "SYNONYM", "SELECT synonym_name FROM all_synonyms WHERE owner = ? ORDER BY synonym_name", false),
            new TypeDef("函数", "FUNCTION",  "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'FUNCTION' ORDER BY object_name", false),
            new TypeDef("过程", "PROCEDURE", "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'PROCEDURE' ORDER BY object_name", false),
            new TypeDef("包", "PACKAGE",     "SELECT DISTINCT object_name FROM all_procedures WHERE owner = ? AND object_type IN ('PACKAGE','PACKAGE BODY') ORDER BY object_name", true)
        );
        return new DbMetadataConfig("oceanbase", "OceanBase", false, types, new ArrayList<>());
    }

    public static DbMetadataConfig createMySQLDefault() {
        List<TypeDef> types = List.of(
            new TypeDef("模式", "SCHEMA",    new ArrayList<>(), false),
            new TypeDef("表", "TABLE",       "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name", true),
            new TypeDef("视图", "VIEW",      "SELECT table_name FROM information_schema.views WHERE table_schema = ? ORDER BY table_name", false),
            new TypeDef("函数", "FUNCTION",  "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'FUNCTION' ORDER BY routine_name", false),
            new TypeDef("过程", "PROCEDURE", "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'PROCEDURE' ORDER BY routine_name", false)
        );
        return new DbMetadataConfig("mysql", "MySQL", false, types, new ArrayList<>());
    }

    public static DbMetadataConfig createPostgreSQLDefault() {
        List<TypeDef> types = List.of(
            new TypeDef("模式", "SCHEMA",    new ArrayList<>(), false),
            new TypeDef("表", "TABLE",       "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = ? ORDER BY tablename", true),
            new TypeDef("视图", "VIEW",      "SELECT viewname FROM pg_catalog.pg_views WHERE schemaname = ? ORDER BY viewname", false),
            new TypeDef("索引", "INDEX",     "SELECT indexname FROM pg_catalog.pg_indexes WHERE schemaname = ? ORDER BY indexname", false),
            new TypeDef("序列", "SEQUENCE",  "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ? ORDER BY sequence_name", false),
            new TypeDef("函数", "FUNCTION",  "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'FUNCTION' ORDER BY routine_name", false),
            new TypeDef("过程", "PROCEDURE", "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'PROCEDURE' ORDER BY routine_name", false)
        );
        return new DbMetadataConfig("postgresql", "PostgreSQL", false, types, new ArrayList<>());
    }

    public static List<DbMetadataConfig> createDefaults() {
        return List.of(
            createOracleDefault(),
            createOceanBaseDefault(),
            createMySQLDefault(),
            createPostgreSQLDefault()
        );
    }
}
