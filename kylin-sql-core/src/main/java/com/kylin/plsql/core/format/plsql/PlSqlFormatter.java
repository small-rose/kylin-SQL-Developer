package com.kylin.plsql.core.format.plsql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.dialect.SqlDialect;
import com.kylin.plsql.core.format.plsql.builder.ParseTreeModelBuilder;
import com.kylin.plsql.core.format.plsql.dialect.PlSqlDialect;
import com.kylin.plsql.core.format.plsql.dialect.PlSqlDialectFactory;
import com.kylin.plsql.core.format.plsql.formatter.PlSqlFormatterEngine;
import com.kylin.plsql.core.format.plsql.model.*;
import com.kylin.plsql.core.format.plsql.qa.PlSqlQualityChecker;

import java.util.*;

public class PlSqlFormatter {

    public static FormatResult format(String source, FormatOptions options) {
        return format(source, options, null);
    }

    public static FormatResult format(String source, FormatOptions options,
                                       SqlDialect sqlDialect) {
        long startMs = System.currentTimeMillis();

        PlSqlDialect dialect = resolveDialect(options, sqlDialect);
        ParseTreeModelBuilder builder = new ParseTreeModelBuilder();
        PlSqlModel model = builder.build(source, dialect);

        List<Diagnostic> allDiagnostics = new ArrayList<>(model.diagnostics);
        List<String> fixLog = new ArrayList<>();
        String result;

        if (!model.isComplete) {
            // Critical parse errors — return original
            long elapsed = System.currentTimeMillis() - startMs;
            return new FormatResult(source, source, allDiagnostics, 0,
                dialect.getName(), fixLog, true, elapsed);
        }

        try {
            PlSqlFormatterEngine engine = new PlSqlFormatterEngine(options, model.tokens);
            result = engine.format(model);
            fixLog.addAll(engine.getFixLog());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            allDiagnostics.add(new Diagnostic(
                Diagnostic.Severity.ERROR, Diagnostic.Code.UNEXPECTED_KEYWORD,
                0, 0, "格式化引擎异常: " + e.getMessage(), ""));
            return new FormatResult(source, source, allDiagnostics, 0,
                dialect.getName(), fixLog, true, elapsed);
        }

        // Quality check
        PlSqlQualityChecker qa = new PlSqlQualityChecker();
        var qaReport = qa.check(source, result, model, fixLog);
        allDiagnostics.addAll(qaReport.diagnostics);

        boolean fallback = qaReport.fallback;
        String finalText = fallback ? source : result;

        long elapsed = System.currentTimeMillis() - startMs;

        if (fallback) {
            System.out.println("[PLSQL_FORMAT] QUALITY LOW: score="
                + qaReport.score + ", fallback to source. "
                + qaReport.diagnostics.size() + " diagnostics.");
        } else if (qaReport.score < 80) {
            System.out.println("[PLSQL_FORMAT] Quality score="
                + qaReport.score + ", fixes applied: " + qaReport.fixLog.size());
            for (String fix : qaReport.fixLog) {
                System.out.println("  FIXED: " + fix);
            }
        }

        return new FormatResult(result, source, allDiagnostics,
            qaReport.score, dialect.getName(), qaReport.fixLog,
            fallback, elapsed);
    }

    private static PlSqlDialect resolveDialect(FormatOptions options,
                                                SqlDialect sqlDialect) {
        String name;
        if (sqlDialect != null) {
            name = sqlDialect.getName();
        } else if (options != null) {
            name = options.getDialect();
        } else {
            name = "Oracle";
        }
        if (name == null || name.isEmpty()) name = "Oracle";
        return PlSqlDialectFactory.forName(name);
    }
}
