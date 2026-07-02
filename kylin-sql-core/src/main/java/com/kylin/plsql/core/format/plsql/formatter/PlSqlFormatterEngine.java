package com.kylin.plsql.core.format.plsql.formatter;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.ConstraintGenerator;
import com.kylin.plsql.core.format.plsql.formatter.layout.ConstraintSolver;
import com.kylin.plsql.core.format.plsql.formatter.layout.GapConstraint;
import com.kylin.plsql.core.format.plsql.formatter.layout.GapResult;
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
        // Generate constraints from block tree
        ConstraintGenerator gen = new ConstraintGenerator(opts, tokens);
        List<GapConstraint> constraints = gen.generate(model.topLevelBlocks);

        // Solve constraints
        ConstraintSolver solver = new ConstraintSolver(opts, tokens);
        GapResult[] gaps = solver.solve(constraints);

        // Assemble base string
        StringAssembler assembler = new StringAssembler(opts, tokens);
        String base = assembler.assemble(gaps);

        // Apply post-processors
        String result = PlSqlPostProcessor.process(base, opts);
        return result;
    }

}
