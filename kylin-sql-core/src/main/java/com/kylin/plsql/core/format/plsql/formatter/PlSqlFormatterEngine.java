package com.kylin.plsql.core.format.plsql.formatter;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.ConstraintGenerator;
import com.kylin.plsql.core.format.plsql.formatter.layout.ConstraintSolver;
import com.kylin.plsql.core.format.plsql.formatter.layout.GapConstraint;
import com.kylin.plsql.core.format.plsql.formatter.layout.GapResult;
import com.kylin.plsql.core.format.plsql.formatter.layout.StringAssembler;
import com.kylin.plsql.core.format.plsql.formatter.post.PlSqlPostProcessor;
import com.kylin.plsql.core.format.plsql.model.*;
import com.kylin.plsql.core.format.SqlFormatter;
import java.util.*;

public class PlSqlFormatterEngine {

    private final FormatOptions opts;
    private final List<TokenInfo> tokens;
    private final List<String> fixLog;

    public PlSqlFormatterEngine(FormatOptions opts, List<TokenInfo> tokens) {
        this.opts = opts;
        this.tokens = tokens;
        this.fixLog = new ArrayList<>();
    }

    public List<String> getFixLog() { return fixLog; }

    public String format(PlSqlModel model) {
        // Generate constraints from block tree
        ConstraintGenerator gen = new ConstraintGenerator(opts, tokens);
        List<GapConstraint> constraints = gen.generate(model.topLevelBlocks);

        // Solve constraints
        ConstraintSolver solver = new ConstraintSolver(opts, tokens);
        GapResult[] gaps = solver.solve(constraints);

        // Assemble base string
        StringAssembler assembler = new StringAssembler(opts, tokens);
        String base = assembler.assemble(gaps);

        // Apply statement-level formatting (DML, concat, etc.)
        String result = applyStatementFormatting(base, model);

        // Apply post-processors
        result = applyPostProcessors(result);
        return result;
    }

    private String applyStatementFormatting(String base, PlSqlModel model) {
        // Walk top-level blocks and format DML/concat statements
        StringBuilder out = new StringBuilder();
        String[] lines = base.split("\n", -1);
        Set<Integer> processedLines = new HashSet<>();

        for (PlSqlBlock block : model.topLevelBlocks) {
            applyFormattingRecursive(block, model.rawSource, lines, processedLines, out);
        }

        // Copy remaining unprocessed lines
        for (int i = 0; i < lines.length; i++) {
            if (!processedLines.contains(i)) {
                out.append(lines[i]).append('\n');
            }
        }

        return out.toString().trim();
    }

    private void applyFormattingRecursive(PlSqlBlock block, String rawSource,
                                           String[] lines, Set<Integer> processedLines,
                                           StringBuilder out) {
        if (block.type == PlSqlBlockType.IF) {
            for (IfBranch branch : block.ifBranches) {
                for (Statement stmt : branch.statements) {
                    processStatement(stmt, lines, processedLines, out);
                }
            }
        }
        for (Statement stmt : block.statements) {
            processStatement(stmt, lines, processedLines, out);
        }
        for (ExceptionSection.Handler handler : getHandlers(block)) {
            for (Statement stmt : handler.statements) {
                processStatement(stmt, lines, processedLines, out);
            }
        }
        for (PlSqlBlock child : block.children) {
            applyFormattingRecursive(child, rawSource, lines, processedLines, out);
        }
    }

    private List<ExceptionSection.Handler> getHandlers(PlSqlBlock block) {
        if (block.exceptionSection != null) return block.exceptionSection.handlers;
        return Collections.emptyList();
    }

    private void processStatement(Statement stmt, String[] lines,
                                   Set<Integer> processedLines, StringBuilder out) {
        if (stmt.type == Statement.Type.BLOCK_STMT && stmt.innerBlock != null) return;
        if (stmt.type == Statement.Type.DML) {
            String rawText = extractText(stmt.startTokenIdx, stmt.endTokenIdx);
            if (!rawText.isEmpty()) {
                try {
                    String formatted = SqlFormatter.format(rawText, opts);
                    out.append(formatted);
                    if (!formatted.endsWith("\n")) out.append('\n');
                } catch (Exception e) {
                    out.append(rawText);
                    if (!rawText.endsWith(";")) out.append(';');
                    out.append('\n');
                }
            }
        }
    }

    private String applyPostProcessors(String result) {
        return PlSqlPostProcessor.process(result, opts);
    }

    private String extractText(int from, int to) {
        StringBuilder sb = new StringBuilder();
        boolean needSpace = false;
        String lastText = null;
        for (int i = from; i <= to && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) {
                if (ti.text.startsWith("--") || ti.text.startsWith("/*")) {
                    sb.append(ti.text);
                }
                continue;
            }
            if (needSpace && !";".equals(ti.text) && !".".equals(ti.text)
                && !"," .equals(ti.text) && !")".equals(ti.text) && !"(".equals(ti.text)
                && !"%".equals(ti.text)
                && !isMultiCharPair(lastText, ti.text)) sb.append(' ');
            sb.append(ti.text);
            needSpace = !".".equals(ti.text) && !"(".equals(ti.text) && !"%".equals(ti.text);
            lastText = ti.text;
        }
        return sb.toString();
    }

    private static boolean isMultiCharPair(String prev, String curr) {
        if (prev == null) return false;
        String pair = prev + curr;
        return pair.equals("||") || pair.equals("<<") || pair.equals(">>")
            || pair.equals("<=") || pair.equals(">=") || pair.equals("=>")
            || pair.equals("!=") || pair.equals("<>") || pair.equals("^=") || pair.equals("~=");
    }

}
