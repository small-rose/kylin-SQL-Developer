package com.kylin.plsql.core.format;

public interface SqlFormatterEngine {
    String format(String sql) throws Exception;
    String getName();
    String getDescription();

    default String getDisplayName() {
        return getName();
    }
}