import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.PlSqlFormatter;
import com.kylin.plsql.core.format.plsql.model.*;
import com.kylin.plsql.core.format.plsql.builder.ParseTreeModelBuilder;
import com.kylin.plsql.core.format.plsql.dialect.PlSqlDialectFactory;
import com.kylin.plsql.core.format.plsql.formatter.PlSqlFormatterEngine;
import com.kylin.plsql.core.format.plsql.formatter.layout.ConstraintGenerator;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.formatter.layout.solver.ConstraintSolver;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.FinalLayout;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DebugDdlConstraints {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Paths.get(args[0]));

        // Build model
        var dialect = PlSqlDialectFactory.forName("Oracle");
        ParseTreeModelBuilder builder = new ParseTreeModelBuilder();
        PlSqlModel model = builder.build(source, dialect);

        System.out.println("=== TOP-LEVEL BLOCKS ===");
        for (PlSqlBlock b : model.topLevelBlocks) {
            System.out.println("  block type=" + b.type + " start=" + b.startTokenIdx
                + " end=" + b.endTokenIdx + " text="
                + (b.startTokenIdx >= 0 ? model.tokens.get(b.startTokenIdx).text : "?"));
            for (PlSqlBlock c : b.children) {
                System.out.println("    child type=" + c.type + " start=" + c.startTokenIdx
                    + " end=" + c.endTokenIdx + " text="
                    + (c.startTokenIdx >= 0 ? model.tokens.get(c.startTokenIdx).text : "?"));
            }
        }

        FormatOptions opts = new FormatOptions();
        System.out.println("\n=== CONSTRAINTS (from gapMap) ===");
        ConstraintGenerator gen = new ConstraintGenerator(opts, model.tokens);
        List<ConstraintSpec> specs = gen.generate(model.topLevelBlocks);

        System.out.println("Total constraints: " + specs.size());
        // Filter to DDL-relevant constraints (from/to in the CREATE TABLE range)
        for (ConstraintSpec c : specs) {
            int from = c.getFromTokenIdx();
            int to = c.getToTokenIdx();
            if (from >= 0 && from < model.tokens.size() && to >= 0 && to < model.tokens.size()) {
                String fromText = model.tokens.get(from).text;
                String toText = model.tokens.get(to).text;
                String mode = c.getNewlineMode().toString();
                if (fromText.equals("(") || fromText.equals(")") || fromText.equals(",")
                    || toText.equals("(") || toText.equals(")") || toText.equals(",")
                    || fromText.toUpperCase().equals("CONSTRAINT")
                    || toText.toUpperCase().equals("CONSTRAINT")
                    || fromText.toUpperCase().equals("STORAGE")
                    || toText.toUpperCase().equals("STORAGE")) {
                    System.out.println("  [" + mode + "] gap(" + from + "->" + to + "): '"
                        + escape(fromText) + "' -> '" + escape(toText) + "'"
                        + " indent=" + c.getIndentDelta());
                }
            }
        }

        // Also print ALL gaps
        System.out.println("\n=== ALL CONSTRAINTS ===");
        for (ConstraintSpec c : specs) {
            int from = c.getFromTokenIdx();
            int to = c.getToTokenIdx();
            if (from >= 0 && from < model.tokens.size() && to >= 0 && to < model.tokens.size()) {
                String fromText = model.tokens.get(from).text;
                String toText = model.tokens.get(to).text;
                String mode = c.getNewlineMode().toString();
                System.out.println("  [" + mode + "] gap(" + from + "->" + to + "): '"
                    + escape(fromText) + "' -> '" + escape(toText) + "'"
                    + " indent=" + c.getIndentDelta()
                    + " penalty=" + c.getBreakPenalty()
                    + (c.getAlignGroupId() != null ? " align=" + c.getAlignGroupId() : ""));
            }
        }

        // Run final formatting and output result
        System.out.println("\n=== FORMATTED OUTPUT ===");
        PlSqlFormatterEngine engine = new PlSqlFormatterEngine(opts, model.tokens);
        String result = engine.format(model, source);
        System.out.println(result);

        // Run quality check
        com.kylin.plsql.core.format.plsql.qa.PlSqlQualityChecker qa = 
            new com.kylin.plsql.core.format.plsql.qa.PlSqlQualityChecker();
        var report = qa.check(source, result, model, engine.getFixLog());
        System.out.println("\nScore=" + report.score + " fallback=" + report.fallback);
    }

    static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
