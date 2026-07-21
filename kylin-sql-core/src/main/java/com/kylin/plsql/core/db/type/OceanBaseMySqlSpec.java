package com.kylin.plsql.core.db.type;

import java.util.List;

/** OceanBase MySQL 模式类型描述 / OceanBase in MySQL-compatible mode. */
public class OceanBaseMySqlSpec implements DbTypeSpec {
    public static final OceanBaseMySqlSpec INSTANCE = new OceanBaseMySqlSpec();

    private OceanBaseMySqlSpec() {}

    @Override public String getKey()             { return "oceanbase-mysql"; }
    @Override public String getDisplayName()     { return "OceanBase-MySQL"; }
    @Override public int getDefaultPort()        { return 2881; }
    @Override public String getDriverClassName() { return "com.oceanbase.jdbc.Driver"; }
    @Override public List<String> getUrlPrefixes() { return List.of("jdbc:oceanbase:mysql://"); }
    @Override public DbFamily getFamily()         { return DbFamily.MYSQL; }
}
