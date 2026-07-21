package com.kylin.plsql.core.db.type;

import java.util.List;

/** Oracle 数据库类型描述 / Oracle database type descriptor. */
public class OracleSpec implements DbTypeSpec {
    public static final OracleSpec INSTANCE = new OracleSpec();

    private OracleSpec() {}

    @Override public String getKey()             { return "oracle"; }
    @Override public String getDisplayName()     { return "Oracle"; }
    @Override public int getDefaultPort()        { return 1521; }
    @Override public String getDriverClassName() { return "oracle.jdbc.OracleDriver"; }
    @Override public List<String> getUrlPrefixes() { return List.of("jdbc:oracle:thin:@"); }
    @Override public DbFamily getFamily()         { return DbFamily.ORACLE; }
}
