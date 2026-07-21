package com.kylin.plsql.core.format.dialect;

import com.kylin.plsql.core.format.*;
import com.kylin.plsql.core.format.enums.*;

import java.util.List;
import java.util.Set;

/** MySQL-specific SQL dialect definition. */
public class MySqlDialect implements SqlDialect {

    @Override
    public String getName() { return "MySQL"; }

    @Override
    public Set<String> getKeywords() {
        return Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "ALTER", "DROP", "TABLE", "INDEX", "VIEW",
            "TRIGGER", "FUNCTION", "PROCEDURE",
            "BEGIN", "END", "DECLARE",
            "IF", "THEN", "ELSE", "ELSEIF", "END IF",
            "LOOP", "FOR", "WHILE",
            "CASE", "WHEN", "END CASE",
            "RETURN", "EXIT", "CONTINUE",
            "COMMIT", "ROLLBACK",
            "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "JOIN", "ON",
            "ORDER", "GROUP", "BY", "HAVING",
            "UNION", "ALL", "DISTINCT",
            "AS", "IS",
            "NULL", "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
            "REFERENCES", "CHECK", "UNIQUE",
            "GRANT", "REVOKE",
            "TRUNCATE", "REPLACE",
            "LIMIT", "OFFSET",
            "ON", "DUPLICATE",
            "BETWEEN", "LIKE", "ANY", "SOME",
            "TRUE", "FALSE",
            "CURRENT_TIMESTAMP", "NOW",
            "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT",
            "VARCHAR", "CHAR", "TEXT", "BLOB",
            "FLOAT", "DOUBLE", "DECIMAL",
            "DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR",
            "AUTO_INCREMENT", "ENGINE", "CHARSET", "COLLATE"
        );
    }

    @Override
    public Set<String> getIndentIncrease() {
        return Set.of("BEGIN", "LOOP", "THEN", "IF", "CASE", "FOR", "WHILE", "WHEN", "ELSE", "ELSEIF");
    }

    @Override
    public Set<String> getIndentDecrease() {
        return Set.of("END", "ELSE", "ELSEIF", "END IF", "END LOOP", "END CASE");
    }

    @Override
    public String quoteIdentifier(String name) {
        return "`" + name + "`";
    }

    @Override
    public List<SpecialClause> getSpecialClauses() {
        return List.of(
            new SpecialClause("LIMIT", SpecialClause.AFTER),
            new SpecialClause("OFFSET", SpecialClause.AFTER)
        );
    }

    @Override
    public FormatOptions getDefaultOptions() {
        FormatOptions o = new FormatOptions();
        o.setKeywordCase(FormatOptions.KeywordCase.UPPER);
        o.setIndentSize(2);
        o.setSelectColumnMode(SelectColumnMode.ALIGN);
        o.setWhereAndPosition(WhereAndPosition.LINE_START);
        o.setCommaPosition(CommaPosition.TRAILING);
        return o;
    }
}
