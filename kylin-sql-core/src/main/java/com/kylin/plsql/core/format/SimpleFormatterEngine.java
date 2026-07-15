package com.kylin.plsql.core.format;

public class SimpleFormatterEngine implements SqlFormatterEngine {

    private final FormatOptions options;

    public SimpleFormatterEngine(FormatOptions options) {
        this.options = options;
    }

    @Override
    public String format(String sql) throws Exception {
        return SqlFormatter.formatSimple(sql, options);
    }

    @Override
    public String getName() {
        return "Simple (v2)";
    }

    @Override
    public String getDescription() {
        return "基于 Token 流的传统格式化引擎，适合简单 SQL";
    }
}
