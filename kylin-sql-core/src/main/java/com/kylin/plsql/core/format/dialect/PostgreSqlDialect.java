package com.kylin.plsql.core.format.dialect;

import com.kylin.plsql.core.format.*;

import java.util.List;
import java.util.Set;

public class PostgreSqlDialect implements SqlDialect {

    @Override
    public String getName() { return "PostgreSQL"; }

    @Override
    public Set<String> getKeywords() {
        return Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "ALTER", "DROP", "TABLE", "INDEX", "VIEW", "SEQUENCE",
            "TRIGGER", "FUNCTION", "PROCEDURE",
            "BEGIN", "END", "DECLARE", "EXCEPTION",
            "IF", "THEN", "ELSE", "ELSIF", "END IF",
            "LOOP", "FOR", "WHILE",
            "CASE", "WHEN", "END CASE",
            "RETURN", "EXIT", "CONTINUE",
            "COMMIT", "ROLLBACK", "SAVEPOINT",
            "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "JOIN", "ON",
            "ORDER", "GROUP", "BY", "HAVING",
            "UNION", "ALL", "DISTINCT", "INTERSECT", "EXCEPT",
            "AS", "IS",
            "NULL", "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
            "REFERENCES", "CHECK", "UNIQUE",
            "GRANT", "REVOKE",
            "TRUNCATE",
            "LIMIT", "OFFSET",
            "RETURNING",
            "ON", "CONFLICT", "DO", "NOTHING",
            "BETWEEN", "LIKE", "ILIKE", "ANY", "SOME", "ALL",
            "TRUE", "FALSE",
            "CURRENT_TIMESTAMP", "NOW",
            "INT", "INTEGER", "BIGINT", "SMALLINT",
            "VARCHAR", "CHAR", "TEXT",
            "FLOAT", "DOUBLE", "NUMERIC", "DECIMAL", "REAL",
            "DATE", "TIMESTAMP", "TIME", "INTERVAL",
            "SERIAL", "BIGSERIAL",
            "BOOLEAN", "JSON", "JSONB", "UUID", "ARRAY"
        );
    }

    @Override
    public Set<String> getIndentIncrease() {
        return Set.of("BEGIN", "LOOP", "THEN", "IF", "CASE", "FOR", "WHILE", "WHEN", "ELSE", "ELSIF");
    }

    @Override
    public Set<String> getIndentDecrease() {
        return Set.of("END", "ELSE", "ELSIF", "END IF", "END LOOP", "END CASE", "EXCEPTION");
    }

    @Override
    public String quoteIdentifier(String name) {
        return "\"" + name + "\"";
    }

    @Override
    public List<SpecialClause> getSpecialClauses() {
        return List.of(
            new SpecialClause("LIMIT", SpecialClause.AFTER),
            new SpecialClause("OFFSET", SpecialClause.AFTER),
            new SpecialClause("RETURNING", SpecialClause.AFTER)
        );
    }

    @Override
    public FormatOptions getDefaultOptions() {
        FormatOptions o = new FormatOptions();
        o.setKeywordCase(FormatOptions.KeywordCase.UPPER);
        o.setIndentSize(4);
        o.setSelectColumnMode(SelectColumnMode.ALIGN);
        o.setWhereAndPosition(WhereAndPosition.LINE_START);
        o.setCommaPosition(CommaPosition.TRAILING);
        return o;
    }
}
