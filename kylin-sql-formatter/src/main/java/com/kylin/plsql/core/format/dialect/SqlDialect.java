package com.kylin.plsql.core.format.dialect;

import com.kylin.plsql.core.format.FormatOptions;

import java.util.List;
import java.util.Set;

/** Interface for database dialect-specific formatting rules. */
public interface SqlDialect {

    String getName();

    Set<String> getKeywords();

    Set<String> getIndentIncrease();

    Set<String> getIndentDecrease();

    String quoteIdentifier(String name);

    List<SpecialClause> getSpecialClauses();

    FormatOptions getDefaultOptions();

    record SpecialClause(String keyword, int position) {
        public static final int BEFORE = 0;
        public static final int AFTER = 1;
    }
}
