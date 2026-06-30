package com.kylin.plsql.core.format.post;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.BlankLineHandling;

/** Post-processor that handles blank line collapsing or stripping. */
public class BlankLineProcessor implements PostProcessor {
    @Override
    public String process(String formatted, FormatOptions options) {
        BlankLineHandling h = options.getBlankLineHandling();
        if (h == BlankLineHandling.PRESERVE) return formatted;
        String r = formatted;
        if (h == BlankLineHandling.COLLAPSE || h == BlankLineHandling.STRIP) {
            r = r.replaceAll("\n[ \\t]*\n[ \\t]*\n", "\n\n");
        }
        if (h == BlankLineHandling.STRIP) {
            r = r.replaceAll("\n[ \\t]*\n", "\n");
        }
        return r;
    }
}
