package com.kylin.plsql.core.format.plsql.dialect;

public interface PlSqlDialect {
    String getName();

    boolean isKeyword(String upper);

    char getStringDelimiter();

    char getIdentifierQuote();

    boolean supportsKeyword(String keyword);
}
