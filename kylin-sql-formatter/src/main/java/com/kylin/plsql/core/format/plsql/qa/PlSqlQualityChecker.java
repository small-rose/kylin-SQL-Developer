package com.kylin.plsql.core.format.plsql.qa;

import com.kylin.plsql.core.format.plsql.model.*;

import java.util.*;

public class PlSqlQualityChecker {

    public QualityReport check(String source, String result,
                                PlSqlModel model, List<String> fixLog) {
        List<Diagnostic> qaDiagnostics = new ArrayList<>();
        List<String> qaFixLog = new ArrayList<>(fixLog != null ? fixLog : new ArrayList<>());

        int score = 100;

        // 1. String integrity: count single quotes
        int srcQuotes = countChar(source, '\'');
        int resQuotes = countChar(result, '\'');
        int quoteDiff = Math.abs(srcQuotes - resQuotes);
        if (quoteDiff > 0) {
            if (quoteDiff % 2 == 1) {
                qaDiagnostics.add(new Diagnostic(
                    Diagnostic.Severity.ERROR, Diagnostic.Code.STRING_INTEGRITY,
                    0, 0, "字符串引号数量不一致（源 " + srcQuotes + " 个, 输出 "
                    + resQuotes + " 个, 差异 " + quoteDiff + "）", "检查字符串字面量是否被修改"));
                score -= 50;
            } else {
                qaDiagnostics.add(new Diagnostic(
                    Diagnostic.Severity.WARNING, Diagnostic.Code.STRING_INTEGRITY,
                    0, 0, "字符串引号数量轻微差异（" + quoteDiff + "）", ""));
                score -= 20;
            }
        }

        // 2. Parenthesis balance
        int srcParen = countChar(source, '(') - countChar(source, ')');
        int resParen = countChar(result, '(') - countChar(result, ')');
        if (srcParen != 0 || resParen != 0) {
            qaDiagnostics.add(new Diagnostic(
                Diagnostic.Severity.WARNING, Diagnostic.Code.PAREN_UNCLOSED,
                0, 0, "括号不平衡（源码 " + srcParen + ", 输出 " + resParen + "）", "检查括号匹配"));
            score -= 20;
        }

        // 3. Block balance
        int srcBegin = countKeyword(source, "BEGIN");
        int resBegin = countKeyword(result, "BEGIN");
        int srcEnd = countKeyword(source, "END");
        int resEnd = countKeyword(result, "END");
        if (srcBegin != resBegin || srcEnd != resEnd) {
            qaDiagnostics.add(new Diagnostic(
                Diagnostic.Severity.WARNING, Diagnostic.Code.BLOCK_BALANCE,
                0, 0, "BEGIN/END 数量变化（源 " + srcBegin + "/" + srcEnd
                + ", 输出 " + resBegin + "/" + resEnd + "）", ""));
            score -= 20;
        }

        // 4. Indent mismatch: check if any line has excessive indentation
        //    compared to max nesting depth from model
        if (model != null) {
            int maxNesting = getMaxNestingDepth(model);
            int maxAllowedIndent = (Math.max(maxNesting, 4) + 4) * 4;
            if (maxAllowedIndent < 24) maxAllowedIndent = 24;
            String[] resultLines = result.split("\n");
            int lineNum = 0;
            for (String line : resultLines) {
                lineNum++;
                int leadingSpaces = 0;
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) == ' ') leadingSpaces++;
                    else break;
                }
                if (leadingSpaces > maxAllowedIndent) {
                    qaDiagnostics.add(new Diagnostic(
                        Diagnostic.Severity.WARNING, Diagnostic.Code.INDENT_MISMATCH,
                        lineNum, 0, "缩进深度超出预期（" + leadingSpaces
                        + " 空格, 最大允许 " + maxAllowedIndent + "）", ""));
                    score -= 20;
                    break;
                }
            }
        }

        // 5. Model diagnostics from parser
        if (model != null && model.diagnostics != null) {
            for (Diagnostic d : model.diagnostics) {
                switch (d.severity) {
                    case ERROR -> score -= 50;
                    case WARNING -> score -= 20;
                    case INFO -> score -= 5;
                }
            }
            qaDiagnostics.addAll(model.diagnostics);
        }

        score = Math.max(0, Math.min(100, score));
        return new QualityReport(score, qaDiagnostics, qaFixLog);
    }

    private int getMaxNestingDepth(PlSqlModel model) {
        int maxDepth = 0;
        for (PlSqlBlock top : model.topLevelBlocks) {
            maxDepth = Math.max(maxDepth, calcDepth(top, 0));
        }
        return maxDepth;
    }

    private int calcDepth(PlSqlBlock block, int depth) {
        int max = depth;
        for (PlSqlBlock child : block.children) {
            max = Math.max(max, calcDepth(child, depth + 1));
        }
        return max;
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    private int countKeyword(String s, String kw) {
        int count = 0;
        int idx = 0;
        String u = s.toUpperCase();
        while ((idx = u.indexOf(kw, idx)) >= 0) {
            count++;
            idx += kw.length();
        }
        return count;
    }

    public static class QualityReport {
        public final int score;
        public final List<Diagnostic> diagnostics;
        public final List<String> fixLog;
        public final boolean fallback;

        public QualityReport(int score, List<Diagnostic> diagnostics,
                             List<String> fixLog) {
            this.score = score;
            this.diagnostics = diagnostics;
            this.fixLog = fixLog;
            this.fallback = score < 60;
        }
    }
}
