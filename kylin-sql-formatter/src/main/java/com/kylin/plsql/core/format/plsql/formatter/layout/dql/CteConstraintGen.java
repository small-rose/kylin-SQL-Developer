package com.kylin.plsql.core.format.plsql.formatter.layout.dql;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.CteFormat;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class CteConstraintGen {

    private CteConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        List<Integer> withPositions = SelectColumnConstraintGen.findKeywords(tokens, startIdx, endIdx, "WITH");
        for (int withPos : withPositions) {
            int selectPos = SelectColumnConstraintGen.findNextKeyword(tokens, withPos + 1, endIdx, "SELECT");
            if (selectPos < 0) continue;

            CteFormat fmt = opts.getCteFormat();
            int indentSize = opts.getIndentSize();

            int withNext = SelectColumnConstraintGen.nextVisibleToken(tokens, withPos + 1);
            if (withNext < tokens.size()) {
                SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                    withPos, withNext, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.4);
            }

            if (fmt == CteFormat.COMPACT) {
                for (int i = withPos + 1; i < selectPos; i++) {
                    TokenInfo ti = tokens.get(i);
                    if (ti.channel != 0) continue;
                    if (",".equals(ti.text)) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            i, i + 1,
                            ConstraintSpec.NewlineMode.OPTIONAL, 0, 0.3);
                    }
                }
                return;
            }

            List<Integer> cteCommaPositions = new ArrayList<>();
            for (int i = withPos + 1; i < selectPos; i++) {
                TokenInfo ti = tokens.get(i);
                if (ti.channel != 0) continue;
                if (",".equals(ti.text)) {
                    cteCommaPositions.add(i);
                }
            }

            for (int commaIdx : cteCommaPositions) {
                int nextCte = SelectColumnConstraintGen.nextVisibleToken(tokens, commaIdx + 1);
                if (nextCte < selectPos && nextCte > 0) {
                    SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                        commaIdx, nextCte, ConstraintSpec.NewlineMode.REQUIRED, 0, 0.3);
                }
            }

            if (fmt == CteFormat.ALIGN && !cteCommaPositions.isEmpty()) {
                String alignGroupId = "CTE_NAME_" + withPos;
                for (int i = withPos + 1; i < selectPos; i++) {
                    TokenInfo ti = tokens.get(i);
                    if (ti.channel != 0) continue;
                    int asPos = SelectColumnConstraintGen.findNextKeyword(tokens, i, selectPos, "AS");
                    if (asPos == i) continue;
                    if (asPos < 0) continue;
                    ConstraintSpec c = new ConstraintSpec(asPos - 1, asPos);
                    c.alignGroup(alignGroupId);
                    SelectColumnConstraintGen.addDqlConstraint(constraints, gapMap, c);
                    break;
                }
            }
        }
    }
}
