package com.kylin.plsql.core.format.post;

import com.kylin.plsql.core.format.FormatOptions;

/** Post-processor that removes trailing whitespace from each line. */
public class TrailingWhitespaceProcessor implements PostProcessor {
    @Override
    public String process(String formatted, FormatOptions options) {
        if (!options.isTrailingWhitespaceTrim()) return formatted;
        return formatted.replaceAll("[ \\t]+\\n", "\n");
    }
}
