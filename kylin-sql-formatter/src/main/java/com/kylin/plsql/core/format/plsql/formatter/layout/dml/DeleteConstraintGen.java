package com.kylin.plsql.core.format.plsql.formatter.layout.dml;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.DmlDeleteFromHandling;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.formatter.layout.dql.SelectColumnConstraintGen;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;

public class DeleteConstraintGen {

    private DeleteConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        List<Integer> deletePositions = SelectColumnConstraintGen.findKeywords(tokens, startIdx, endIdx, "DELETE");
        for (int delPos : deletePositions) {
            int fromPos = SelectColumnConstraintGen.findNextKeyword(tokens, delPos + 1, endIdx, "FROM");
            DmlDeleteFromHandling handling = opts.getDmlDeleteFromHandling();

            if (fromPos >= 0 && handling == DmlDeleteFromHandling.REMOVE) {
                // FORBIDDEN between DELETE and FROM to make "DELETE FROM" as one visual block
                // We don't actually remove the token; we just keep it compact
            }

            int wherePos = SelectColumnConstraintGen.findNextKeyword(tokens, delPos + 1, endIdx, "WHERE");
            if (wherePos > 0) {
                int prevGap = fromPos > 0 ? fromPos : delPos;
                SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                    prevGap, wherePos, ConstraintSpec.NewlineMode.OPTIONAL, 0, 0.3);
            }
        }
    }
}
