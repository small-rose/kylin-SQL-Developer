package com.kylin.plsql.core.format.post;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.CommaPosition;

/** Post-processor that converts trailing commas to leading if configured. */
public class CommaPositionProcessor implements PostProcessor {
    @Override
    public String process(String formatted, FormatOptions options) {
        if (options.getCommaPosition() != CommaPosition.LEADING) return formatted;
        return formatted.replaceAll(",\\n", "\n,");
    }
}
