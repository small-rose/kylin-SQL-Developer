package com.kylin.plsql.core.format.plsql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.model.FormatResult;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RunDemo {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Paths.get(args[0]));
        FormatOptions opts = new FormatOptions();
        FormatResult result = PlSqlFormatter.format(source, opts);
        Files.writeString(Paths.get(args[1]), result.getFormattedText());
        System.out.println("OK score=" + result.getQualityScore()
            + " elapsed=" + result.getElapsedMs() + "ms"
            + " fallback=" + result.isFallback());
    }
}
