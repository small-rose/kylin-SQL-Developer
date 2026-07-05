package com.kylin.plsql.core.format.plsql.model;

import java.util.ArrayList;
import java.util.List;

public class PlSqlModel {
    public final String rawSource;
    public final List<TokenInfo> tokens;
    public final List<PlSqlBlock> topLevelBlocks;
    public final List<Diagnostic> diagnostics;
    public final String dialect;
    public final boolean isComplete;

    public PlSqlModel(String rawSource, List<TokenInfo> tokens,
                      List<PlSqlBlock> topLevelBlocks,
                      List<Diagnostic> diagnostics, String dialect,
                      boolean isComplete) {
        this.rawSource = rawSource;
        this.tokens = tokens;
        this.topLevelBlocks = topLevelBlocks;
        this.diagnostics = diagnostics;
        this.dialect = dialect;
        this.isComplete = isComplete;
    }

    public List<Diagnostic> getErrors() {
        List<Diagnostic> result = new ArrayList<>();
        for (Diagnostic d : diagnostics) {
            if (d.severity == Diagnostic.Severity.ERROR) result.add(d);
        }
        return result;
    }

    public List<Diagnostic> getWarnings() {
        List<Diagnostic> result = new ArrayList<>();
        for (Diagnostic d : diagnostics) {
            if (d.severity == Diagnostic.Severity.WARNING) result.add(d);
        }
        return result;
    }
}
