package com.kylin.plsql.core.format.plsql.formatter.layout.dql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SetOpConstraintGen {

    private static final Set<String> SET_OPS = Set.of("UNION", "INTERSECT", "MINUS");

    private SetOpConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        boolean newline = opts.isSetOperatorNewline();
        boolean align = opts.isSetOperatorColumnAlign();
        int indentSize = opts.getIndentSize();

        for (int i = startIdx; i <= endIdx && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            String u = ti.upper;

            if ("ALL".equals(u)) {
                int prev = SelectColumnConstraintGen.prevVisibleToken(tokens, i - 1);
                if (prev >= 0 && SET_OPS.contains(tokens.get(prev).upper)) {
                    continue;
                }
            }

            if (!SET_OPS.contains(u)) continue;

            int prevKw = SelectColumnConstraintGen.prevVisibleToken(tokens, i - 1);
            if (prevKw < 0) continue;

            if (newline) {
                SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                    prevKw, i, ConstraintSpec.NewlineMode.REQUIRED, 0, 0.3);
            }

            int nextKw = SelectColumnConstraintGen.nextVisibleToken(tokens, i + 1);
            if (nextKw > 0 && nextKw < tokens.size()) {
                SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                    i, nextKw, ConstraintSpec.NewlineMode.REQUIRED, align ? indentSize : 0, 0.3);
            }
        }
    }
}
