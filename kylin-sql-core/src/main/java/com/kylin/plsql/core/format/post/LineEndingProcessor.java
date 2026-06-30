package com.kylin.plsql.core.format.post;

import com.kylin.plsql.core.format.FormatOptions;

/** Post-processor that converts line endings to LF or CRLF. */
public class LineEndingProcessor implements PostProcessor {
    @Override
    public String process(String formatted, FormatOptions options) {
        String le = options.getLineEnding();
        if ("CRLF".equals(le)) {
            return formatted.replace("\n", "\r\n");
        }
        return formatted;
    }
}
