package com.kylin.plsql.core.db.type;

import java.util.List;

/** PostgreSQL 数据库类型描述 / PostgreSQL database type descriptor. */
public class PostgreSqlSpec implements DbTypeSpec {
    public static final PostgreSqlSpec INSTANCE = new PostgreSqlSpec();

    private PostgreSqlSpec() {}

    @Override public String getKey()             { return "postgresql"; }
    @Override public String getDisplayName()     { return "PostgreSQL"; }
    @Override public int getDefaultPort()        { return 2883; }
    @Override public String getDriverClassName() { return "org.postgresql.Driver"; }
    @Override public List<String> getUrlPrefixes() { return List.of("jdbc:postgresql://"); }
    @Override public DbFamily getFamily()         { return DbFamily.OTHER; }
}
