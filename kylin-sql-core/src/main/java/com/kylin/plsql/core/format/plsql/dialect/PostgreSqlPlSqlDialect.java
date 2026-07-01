package com.kylin.plsql.core.format.plsql.dialect;

import java.util.*;

public class PostgreSqlPlSqlDialect implements PlSqlDialect {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
        "CREATE", "ALTER", "DROP", "TRUNCATE",
        "TABLE", "INDEX", "VIEW", "SEQUENCE", "TRIGGER",
        "FUNCTION", "PROCEDURE", "RETURNS", "LANGUAGE",
        "BEGIN", "END", "DECLARE", "EXCEPTION",
        "IF", "THEN", "ELSE", "ELSIF", "END IF",
        "LOOP", "FOR", "WHILE", "REVERSE",
        "CASE", "WHEN", "END CASE",
        "RETURN", "EXIT", "CONTINUE", "GOTO", "NULL",
        "COMMIT", "ROLLBACK", "SAVEPOINT",
        "OPEN", "FETCH", "CLOSE",
        "CURSOR", "MOVE",
        "RAISE", "EXECUTE",
        "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "JOIN", "ON",
        "ORDER", "GROUP", "BY", "HAVING", "LIMIT", "OFFSET", "FETCH",
        "UNION", "INTERSECT", "EXCEPT",
        "AS", "OUT", "INOUT", "VARIADIC",
        "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
        "REFERENCES", "CHECK", "UNIQUE",
        "DISTINCT", "ALL",
        "TRUE", "FALSE", "OTHERS",
        "PERFORM", "STRICT", "IMMUTABLE", "STABLE", "VOLATILE",
        "COST", "ROWS", "SETOF", "RECORD"
    ));

    @Override
    public String getName() { return "PostgreSQL"; }

    @Override
    public boolean isKeyword(String upper) {
        return KEYWORDS.contains(upper);
    }

    @Override
    public char getStringDelimiter() { return '\''; }

    @Override
    public char getIdentifierQuote() { return '"'; }

    @Override
    public boolean supportsKeyword(String keyword) {
        return KEYWORDS.contains(keyword);
    }
}
