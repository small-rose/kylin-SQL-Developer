package com.kylin.plsql.core.format.plsql.formatter.layout.solver;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.model.PlSqlBlock;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.FinalLayout;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import java.util.List;

public class ConstraintSolver {

    private final List<TokenInfo> tokens;
    private final FormatOptions opts;
    private final List<PlSqlBlock> topLevelBlocks;

    public ConstraintSolver(List<TokenInfo> tokens, FormatOptions opts,
                            List<PlSqlBlock> topLevelBlocks) {
        this.tokens = tokens;
        this.opts = opts;
        this.topLevelBlocks = topLevelBlocks;
    }

    public FinalLayout[] solve(List<ConstraintSpec> specs, int tokenCount) {
        PipelineContext ctx = new PipelineContext(opts, tokens, specs, topLevelBlocks);

        ctx.frames = new StructuralResolver().resolve(ctx);
        if (opts.getMaxLineWidth() > 0) {
            new LineWidthResolver().resolve(ctx);
        }
        ctx.covers = new AlignmentResolver(tokens).resolve(ctx);
        ctx.layout = new LayoutMerger().merge(ctx);
        return ctx.layout;
    }
}
