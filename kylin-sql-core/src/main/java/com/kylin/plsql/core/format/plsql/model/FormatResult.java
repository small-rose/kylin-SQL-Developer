package com.kylin.plsql.core.format.plsql.model;

import java.util.List;

public class FormatResult {
    private final String formattedText;
    private final String originalSource;
    private final List<Diagnostic> diagnostics;
    private final int qualityScore;
    private final String dialect;
    private final List<String> fixLog;
    private final boolean fallback;
    private final long elapsedMs;

    public FormatResult(String formattedText, String originalSource,
                        List<Diagnostic> diagnostics, int qualityScore,
                        String dialect, List<String> fixLog,
                        boolean fallback, long elapsedMs) {
        this.formattedText = formattedText;
        this.originalSource = originalSource;
        this.diagnostics = diagnostics;
        this.qualityScore = qualityScore;
        this.dialect = dialect;
        this.fixLog = fixLog;
        this.fallback = fallback;
        this.elapsedMs = elapsedMs;
    }

    public String getFormattedText() { return formattedText; }
    public String getOriginalSource() { return originalSource; }
    public List<Diagnostic> getDiagnostics() { return diagnostics; }
    public int getQualityScore() { return qualityScore; }
    public String getDialect() { return dialect; }
    public List<String> getFixLog() { return fixLog; }
    public boolean isFallback() { return fallback; }
    public long getElapsedMs() { return elapsedMs; }

    public boolean hasDiagnostics() {
        return diagnostics != null && !diagnostics.isEmpty();
    }

    public String getEffectiveText() {
        return fallback ? originalSource : formattedText;
    }

    public String getMessageText() {
        if (fallback) {
            return "格式化回退到原始代码（质量评分 " + qualityScore + "/100，"
                 + diagnostics.size() + " 个诊断问题）";
        }
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "格式化完成，耗时 " + elapsedMs + "ms";
        }
        return "格式化完成，发现 " + diagnostics.size() + " 个问题"
             + "（评分 " + qualityScore + "/100）";
    }

    public String formatDiagnosticsForPanel() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ PL/SQL 格式化诊断 ═══\n");
        sb.append("方言: ").append(dialect).append("  |  ");
        sb.append("评分: ").append(qualityScore).append("/100  |  ");
        sb.append("耗时: ").append(elapsedMs).append("ms\n");
        if (fixLog != null && !fixLog.isEmpty()) {
            sb.append("自动修正:\n");
            for (String fix : fixLog) {
                sb.append("  · ").append(fix).append("\n");
            }
        }
        if (diagnostics != null && !diagnostics.isEmpty()) {
            sb.append("诊断信息:\n");
            for (Diagnostic d : diagnostics) {
            sb.append("  [").append(d.severity).append("] ")
              .append("第").append(d.line).append("行 ")
              .append(d.message).append("\n");
            }
        }
        sb.append("════════════════════════════════\n");
        return sb.toString();
    }
}
