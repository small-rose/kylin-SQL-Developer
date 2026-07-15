package com.kylin.plsql.core.format;

import com.kylin.plsql.core.format.plsql.PlSqlFormatter;

public class AdvancedFormatterEngine implements SqlFormatterEngine {

    private final FormatOptions options;

    public AdvancedFormatterEngine(FormatOptions options) {
        this.options = options;
    }

    @Override
    public String format(String sql) throws Exception {
        return PlSqlFormatter.format(sql, options).getEffectiveText();
    }

    @Override
    public String getName() {
        return "Advanced (v3)";
    }

    @Override
    public String getDescription() {
        return "基于 ParseTree + 约束求解的全功能格式化引擎，支持复杂 PL/SQL";
    }
}
