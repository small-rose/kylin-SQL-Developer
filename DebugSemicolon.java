import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.PlSqlFormatter;
import com.kylin.plsql.core.format.plsql.model.*;
import com.kylin.plsql.core.format.plsql.builder.ParseTreeModelBuilder;
import com.kylin.plsql.core.format.plsql.dialect.PlSqlDialectFactory;
import com.kylin.plsql.core.format.plsql.formatter.PlSqlFormatterEngine;
import com.kylin.plsql.core.format.plsql.formatter.layout.ConstraintGenerator;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import java.util.List;

public class DebugSemicolon {
    public static void main(String[] args) throws Exception {
        String sql = "SELECT 1 FROM DUAL;";

        FormatOptions opts = new FormatOptions();
        System.out.println("semicolonCompact=" + opts.isSemicolonCompact());

        var dialect = PlSqlDialectFactory.forName("Oracle");
        ParseTreeModelBuilder builder = new ParseTreeModelBuilder();
        PlSqlModel model = builder.build(sql, dialect);

        // Print tokens
        System.out.println("\n=== TOKENS ===");
        for (int i = 0; i < model.tokens.size(); i++) {
            TokenInfo ti = model.tokens.get(i);
            System.out.println("  [" + i + "] ch=" + ti.channel + " text='" + escape(ti.text) + "'");
        }

        // Generate constraints
        ConstraintGenerator gen = new ConstraintGenerator(opts, model.tokens);
        List<ConstraintSpec> specs = gen.generate(model.topLevelBlocks);

        System.out.println("\n=== CONSTRAINTS ===");
        for (ConstraintSpec c : specs) {
            int from = c.getFromTokenIdx();
            int to = c.getToTokenIdx();
            if (from >= 0 && from < model.tokens.size() && to >= 0 && to < model.tokens.size()) {
                String fromText = model.tokens.get(from).text;
                String toText = model.tokens.get(to).text;
                System.out.println("  [" + c.getNewlineMode() + "] gap(" + from + "->" + to + "): '"
                    + escape(fromText) + "' -> '" + escape(toText) + "'"
                    + " spaces=" + c.getPreferredSpaces() + "/" + c.getMinSpaces() + "/" + c.getMaxSpaces());
            }
        }

        // Run formatter and output result
        System.out.println("\n=== FORMATTED ===");
        FormatResult result = PlSqlFormatter.format(sql, opts);
        System.out.println("Result: '" + result.getFormattedText() + "'");
        System.out.println("Score=" + result.getQualityScore() + " fallback=" + result.isFallback());
    }

    static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
