package com.kylin.plsql.core.db.type;

import java.util.List;

/** MariaDB 数据库类型描述 / MariaDB database type descriptor. */
public class MariaDbSpec implements DbTypeSpec {
    public static final MariaDbSpec INSTANCE = new MariaDbSpec();

    private MariaDbSpec() {}

    @Override public String getKey()             { return "mariadb"; }
    @Override public String getDisplayName()     { return "MariaDB"; }
    @Override public int getDefaultPort()        { return 3307; }
    @Override public String getDriverClassName() { return "org.mariadb.jdbc.Driver"; }
    @Override public List<String> getUrlPrefixes() { return List.of("jdbc:mariadb://"); }
    @Override public DbFamily getFamily()         { return DbFamily.MYSQL; }
}
