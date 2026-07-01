package com.kylin.plsql.core.format.plsql.dialect;

import java.util.*;

public class MySqlPlSqlDialect implements PlSqlDialect {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "REPLACE",
        "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME",
        "TABLE", "INDEX", "VIEW", "TRIGGER",
        "PROCEDURE", "FUNCTION",
        "BEGIN", "END", "DECLARE",
        "IF", "THEN", "ELSEIF", "ELSE", "END IF",
        "LOOP", "WHILE", "REPEAT", "UNTIL", "DO",
        "CASE", "WHEN", "END CASE",
        "RETURN", "RETURNS", "LEAVE", "ITERATE",
        "COMMIT", "ROLLBACK",
        "OPEN", "FETCH", "CLOSE",
        "CURSOR", "CONTINUE", "EXIT", "HANDLER",
        "CONDITION", "SIGNAL", "RESIGNAL",
        "NOT", "FOUND", "SQLEXCEPTION", "SQLWARNING",
        "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "JOIN", "ON",
        "ORDER", "GROUP", "BY", "HAVING", "LIMIT", "OFFSET",
        "UNION", "MINUS", "INTERSECT",
        "AS", "OUT", "INOUT",
        "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
        "REFERENCES", "CHECK", "UNIQUE",
        "DISTINCT", "ALL",
        "TRUE", "FALSE"
    ));

    @Override
    public String getName() { return "MySQL"; }

    @Override
    public boolean isKeyword(String upper) {
        return KEYWORDS.contains(upper);
    }

    @Override
    public char getStringDelimiter() { return '\''; }

    @Override
    public char getIdentifierQuote() { return '`'; }

    @Override
    public boolean supportsKeyword(String keyword) {
        return KEYWORDS.contains(keyword);
    }
}
