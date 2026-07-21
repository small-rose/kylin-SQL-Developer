package com.kylin.plsql.core.db.type;

import java.util.List;

/** OceanBase Oracle 模式类型描述 / OceanBase in Oracle-compatible mode. */
public class OceanBaseOracleSpec implements DbTypeSpec {
    public static final OceanBaseOracleSpec INSTANCE = new OceanBaseOracleSpec();

    private OceanBaseOracleSpec() {}

    @Override public String getKey()             { return "oceanbase-oracle"; }
    @Override public String getDisplayName()     { return "OceanBase-Oracle"; }
    @Override public int getDefaultPort()        { return 2881; }
    @Override public String getDriverClassName() { return "com.oceanbase.jdbc.Driver"; }
    @Override public List<String> getUrlPrefixes() { return List.of("jdbc:oceanbase:oracle://", "jdbc:oceanbase://"); }
    @Override public DbFamily getFamily()         { return DbFamily.ORACLE; }
}
