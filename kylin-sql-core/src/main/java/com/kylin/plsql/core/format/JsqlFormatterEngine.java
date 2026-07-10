package com.kylin.plsql.core.format;

import com.manticore.jsqlformatter.JSQLFormatter;

public class JsqlFormatterEngine implements SqlFormatterEngine {

    @Override
    public String format(String sql) throws Exception {
        return JSQLFormatter.format(sql,
                "indentWidth=4",
                "keywordSpelling=UPPER",
                "functionSpelling=CAMEL",
                "objectSpelling=LOWER",
                "separation=BEFORE",
                "outputFormat=PLAIN");
    }

    @Override
    public String getName() {
        return "JSQLFormatter";
    }

    @Override
    public String getDescription() {
        return "基于 JSQLParser AST 的格式化引擎，支持多方言";
    }
}