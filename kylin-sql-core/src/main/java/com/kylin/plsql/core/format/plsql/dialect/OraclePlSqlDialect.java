package com.kylin.plsql.core.format.plsql.dialect;

import java.util.*;

public class OraclePlSqlDialect implements PlSqlDialect {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "MERGE",
        "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "COMMENT",
        "TABLE", "INDEX", "VIEW", "SEQUENCE", "SYNONYM", "MATERIALIZED",
        "TRIGGER", "FUNCTION", "PROCEDURE", "PACKAGE", "BODY", "REPLACE",
        "TYPE", "VARRAY", "RECORD", "SUBTYPE", "CURSOR",
        "BEGIN", "END", "DECLARE", "EXCEPTION",
        "IF", "THEN", "ELSE", "ELSIF", "END IF",
        "LOOP", "FOR", "WHILE", "FORALL", "REVERSE",
        "CASE", "WHEN", "END CASE",
        "RETURN", "RETURNING", "EXIT", "CONTINUE", "GOTO", "NULL",
        "COMMIT", "ROLLBACK", "SAVEPOINT", "LOCK",
        "OPEN", "FETCH", "CLOSE", "PIPE", "PIPELINED",
        "RAISE", "PRAGMA", "EXECUTE", "IMMEDIATE",
        "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "NATURAL", "JOIN", "ON",
        "ORDER", "GROUP", "BY", "HAVING", "OFFSET", "LIMIT",
        "UNION", "MINUS", "INTERSECT", "EXCEPT",
        "AS", "IS", "OUT", "NOCOPY", "REF",
        "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
        "REFERENCES", "CHECK", "UNIQUE", "CASCADE",
        "DISTINCT", "ALL", "NOWAIT",
        "GRANT", "REVOKE",
        "WITH", "CONNECT", "PRIOR", "START", "LEVEL",
        "BETWEEN", "LIKE",
        "ANY", "SOME",
        "TRUE", "FALSE", "OTHERS", "UNKNOWN",
        "OVER", "PARTITION", "ROWS", "RANGE", "UNBOUNDED",
        "PRECEDING", "FOLLOWING", "CURRENT", "ROW",
        "MAXVALUE", "MINVALUE",
        "ENABLE", "DISABLE", "COMPRESS", "PARALLEL", "MONITORING",
        "STORAGE", "PCTFREE", "PCTUSED", "INITRANS", "MAXTRANS",
        "LOGGING", "NOLOGGING",
        "AUTHID", "DETERMINISTIC", "PARALLEL_ENABLE", "RESULT_CACHE",
        "BULK", "COLLECT", "SQLERRM", "SQLCODE",
        "EDITIONABLE", "NONEDITIONABLE",
        "OBJECT", "FINAL", "INSTANTIABLE", "MEMBER", "STATIC",
        "OVERRIDING", "CONSTRUCTOR", "MAP", "ORDER",
        "SUBPARTITION", "HASH", "LIST", "INTERVAL"
    ));

    @Override
    public String getName() { return "Oracle"; }

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
