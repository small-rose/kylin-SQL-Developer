package com.kylin.plsql.core.format.plsql.formatter.layout.solver;

import java.util.List;
import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.model.PlSqlBlock;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.StructuralFrame;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.FinalLayout;
import com.kylin.plsql.core.format.plsql.formatter.layout.align.AlignmentCover;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;

public class PipelineContext {

    public final FormatOptions opts;
    public final List<TokenInfo> tokens;
    public final int tokenCount;
    public final int indentSize;
    public final List<ConstraintSpec> specs;
    public final List<PlSqlBlock> topLevelBlocks;

    public StructuralFrame[] frames;
    public AlignmentCover[] covers;
    public FinalLayout[] layout;

    public int[] tokenWidth;
    public int consecutiveBreaks;

    public PipelineContext(FormatOptions opts, List<TokenInfo> tokens,
                           List<ConstraintSpec> specs,
                           List<PlSqlBlock> topLevelBlocks) {
        this.opts = opts;
        this.tokens = tokens;
        this.tokenCount = tokens.size();
        this.indentSize = opts.getIndentSize() > 0 ? opts.getIndentSize() : 4;
        this.specs = specs;
        this.topLevelBlocks = topLevelBlocks;

        this.tokenWidth = new int[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokenWidth[i] = tokens.get(i).text.length();
        }
    }

    public boolean hasOpts() { return opts != null; }
}
