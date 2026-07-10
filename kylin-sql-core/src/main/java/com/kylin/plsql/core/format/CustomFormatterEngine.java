package com.kylin.plsql.core.format;

import com.kylin.plsql.core.format.plsql.PlSqlFormatter;
import com.kylin.plsql.core.format.FormatOptions;

public class CustomFormatterEngine implements SqlFormatterEngine {

    private final FormatOptions options;

    public CustomFormatterEngine(FormatOptions options) {
        this.options = options;
    }

    @Override
    public String format(String sql) throws Exception {
        return PlSqlFormatter.format(sql, options).getEffectiveText();
    }

    @Override
    public String getName() {
        return "Kylin 格式化引擎";
    }

    @Override
    public String getDescription() {
        return "本地模板驱动引擎，支持 40+ 参数和 3 种预设";
    }
}