package com.kylin.plsql.core.format.post;

import com.kylin.plsql.core.format.FormatOptions;

@FunctionalInterface
/** Functional interface for post-formatting processing pipeline. */
public interface PostProcessor {
    String process(String formatted, FormatOptions options);
}
