package com.kylin.plsql.core.db.type;

import java.util.List;

/** MySQL 数据库类型描述 / MySQL database type descriptor. */
public class MySqlSpec implements DbTypeSpec {
    public static final MySqlSpec INSTANCE = new MySqlSpec();

    private MySqlSpec() {}

    @Override public String getKey()             { return "mysql"; }
    @Override public String getDisplayName()     { return "MySQL"; }
    @Override public int getDefaultPort()        { return 3306; }
    @Override public String getDriverClassName() { return "com.mysql.cj.jdbc.Driver"; }
    @Override public List<String> getUrlPrefixes() { return List.of("jdbc:mysql://"); }
    @Override public DbFamily getFamily()         { return DbFamily.MYSQL; }
}
