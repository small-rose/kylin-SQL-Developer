package com.kylin.plsql.core.format.plsql.formatter;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.ConstraintGenerator;
import com.kylin.plsql.core.format.plsql.formatter.layout.solver.ConstraintSolver;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.FinalLayout;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.formatter.layout.StringAssembler;
import com.kylin.plsql.core.format.plsql.formatter.post.PlSqlPostProcessor;
import com.kylin.plsql.core.format.plsql.model.PlSqlModel;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.ArrayList;

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
        return format(model, null);
    }

    public String format(PlSqlModel model, String source) {
        ConstraintGenerator gen = new ConstraintGenerator(opts, tokens);
        List<ConstraintSpec> specs = gen.generate(model.topLevelBlocks);

        ConstraintSolver solver = new ConstraintSolver(tokens, opts, model.topLevelBlocks);
        FinalLayout[] layout = solver.solve(specs, tokens.size());

        StringAssembler assembler = new StringAssembler(opts, tokens, source);
        String base = assembler.assemble(layout);

        String result = PlSqlPostProcessor.process(base, opts);
        return result;
    }

}
