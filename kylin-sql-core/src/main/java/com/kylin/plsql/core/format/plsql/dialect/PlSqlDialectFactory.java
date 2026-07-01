package com.kylin.plsql.core.format.plsql.dialect;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlSqlDialectFactory {
    private static final Map<String, PlSqlDialect> DIALECTS = new LinkedHashMap<>();

    static {
        register("Oracle", new OraclePlSqlDialect());
        register("OceanBase (Oracle)", new OceanBaseOraPlSqlDialect());
        register("MySQL", new MySqlPlSqlDialect());
        register("PostgreSQL", new PostgreSqlPlSqlDialect());
    }

    public static PlSqlDialect forName(String name) {
        if (name == null) return DIALECTS.get("Oracle");
        PlSqlDialect d = DIALECTS.get(name);
        if (d == null) d = DIALECTS.get("Oracle");
        return d;
    }

    public static void register(String name, PlSqlDialect dialect) {
        DIALECTS.put(name, dialect);
    }

    private PlSqlDialectFactory() {}
}
