package com.kylin.plsql.core.format.dialect;

import com.kylin.plsql.core.format.*;
import com.kylin.plsql.core.format.enums.*;

import java.util.List;
import java.util.Set;

/** Oracle-specific SQL dialect definition. */
public class OracleDialect implements SqlDialect {

    @Override
    public String getName() { return "Oracle"; }

    @Override
    public Set<String> getKeywords() {
        return Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "ALTER", "DROP", "TABLE", "INDEX", "VIEW", "SEQUENCE",
            "TRIGGER", "FUNCTION", "PROCEDURE", "PACKAGE", "BODY", "REPLACE",
            "BEGIN", "END", "DECLARE", "EXCEPTION",
            "IF", "THEN", "ELSE", "ELSIF", "END IF",
            "LOOP", "FOR", "WHILE",
            "CASE", "WHEN", "END CASE",
            "RETURN", "EXIT", "CONTINUE", "GOTO",
            "COMMIT", "ROLLBACK", "SAVEPOINT",
            "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "JOIN", "ON",
            "ORDER", "GROUP", "BY", "HAVING",
            "UNION", "MINUS", "INTERSECT", "EXCEPT",
            "AS", "IS", "IN", "OUT", "NOCOPY", "REF",
            "NULL", "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
            "REFERENCES", "CHECK", "UNIQUE", "CASCADE",
            "DISTINCT", "ALL", "FOR", "UPDATE", "NOWAIT",
            "GRANT", "REVOKE",
            "TYPE", "VARRAY", "RECORD", "SUBTYPE", "CURSOR",
            "FETCH", "OPEN", "CLOSE",
            "RAISE", "PRAGMA", "EXECUTE", "IMMEDIATE",
            "AUTHID", "DETERMINISTIC", "PIPELINED", "PARALLEL_ENABLE",
            "RESULT_CACHE", "ACCESSIBLE",
            "SHARING", "METADATA", "NONE",
            "TRUNCATE", "MERGE",
            "WITH", "CONNECT", "PRIOR", "START",
            "BETWEEN", "LIKE", "ANY", "SOME",
            "TRUE", "FALSE",
            "AT", "LOCAL", "TIME", "ZONE",
            "SESSION", "CURRENT",
            "SYSDATE", "SYSTIMESTAMP",
            "ROWNUM", "ROWID",
            "VARCHAR2", "VARCHAR", "NVARCHAR2", "CHAR", "NCHAR", "NUMBER",
            "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "SIMPLE_INTEGER",
            "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE",
            "DATE", "TIMESTAMP", "INTERVAL",
            "CLOB", "NCLOB", "BLOB", "BFILE", "LONG", "RAW", "LONG RAW",
            "UROWID", "BOOLEAN", "SYS_REFCURSOR"
        );
    }

    @Override
    public Set<String> getIndentIncrease() {
        return Set.of("BEGIN", "LOOP", "THEN", "IF", "CASE", "FOR", "WHILE", "WHEN", "ELSE", "ELSIF");
    }

    @Override
    public Set<String> getIndentDecrease() {
        return Set.of("END", "ELSIF", "ELSE", "EXCEPTION", "END IF", "END LOOP", "END CASE");
    }

    @Override
    public String quoteIdentifier(String name) {
        return "\"" + name + "\"";
    }

    @Override
    public List<SpecialClause> getSpecialClauses() {
        return List.of();
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
