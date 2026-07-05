package com.kylin.plsql.core.format.plsql.formatter.layout.block;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class TypeConstraintGen {

    private static final Set<String> MEMBER_KEYWORDS = Set.of(
        "MEMBER", "STATIC", "CONSTRUCTOR", "MAP", "ORDER"
    );

    private TypeConstraintGen() {}

    public static void addTypeConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                           List<ConstraintSpec> constraints,
                                           Map<String, ConstraintSpec> gapMap,
                                           List<PlSqlBlock> topLevelBlocks) {
        BlockConstraintUtil.iterateBlocks(topLevelBlocks, block -> {
            if (block.type != PlSqlBlockType.TYPE_SPEC
                && block.type != PlSqlBlockType.TYPE_BODY) return;
            walkTypeBlock(opts, tokens, constraints, gapMap, block);
        });
    }

    private static void walkTypeBlock(FormatOptions opts, List<TokenInfo> tokens,
                                       List<ConstraintSpec> constraints,
                                       Map<String, ConstraintSpec> gapMap,
                                       PlSqlBlock block) {
        int endBound = block.endTokenIdx;
        int start = block.startTokenIdx;

        // AS/IS keyword → indent +1
        int asIdx = BlockConstraintUtil.findKeyword(tokens, start, endBound, "AS");
        if (asIdx < 0) asIdx = BlockConstraintUtil.findKeyword(tokens, start, endBound, "IS");
        if (asIdx >= 0) {
            int next = BlockConstraintUtil.nextVisible(tokens, asIdx + 1, endBound);
            if (next >= 0) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    asIdx, next,
                    ConstraintSpec.NewlineMode.REQUIRED, 1);
            }
        }

        // TYPE_BODY: BEGIN section
        if (block.type == PlSqlBlockType.TYPE_BODY && block.stmtStartIdx >= 0) {
            int beginIdx = BlockConstraintUtil.findKeyword(tokens, start, endBound, "BEGIN");
            if (beginIdx >= 0) {
                int next = BlockConstraintUtil.nextVisible(tokens, beginIdx + 1, endBound);
                if (next >= 0) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        beginIdx, next,
                        ConstraintSpec.NewlineMode.REQUIRED, 1);
                }
            }
        }

        // TYPE_SPEC: member field alignment
        if (opts.isTypeMemberAlign() && block.type == PlSqlBlockType.TYPE_SPEC) {
            alignTypeFields(tokens, constraints, gapMap, start, endBound);
        }

        // EXCEPTION section (TYPE_BODY)
        BlockConstraintUtil.addExceptionSectionConstraints(opts, tokens, constraints, gapMap, block);

        // END → outdent
        BlockConstraintUtil.addEndConstraint(tokens, constraints, gapMap, block);
    }

    private static void alignTypeFields(List<TokenInfo> tokens,
                                          List<ConstraintSpec> constraints,
                                          Map<String, ConstraintSpec> gapMap,
                                          int start, int end) {
        int i = start;
        while (i <= end) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) { i++; continue; }

            // MEMBER/STATIC/CONSTRUCTOR/MAP/ORDER function declarations
            if (MEMBER_KEYWORDS.contains(ti.upper)) {
                int funcKw = BlockConstraintUtil.findKeyword(tokens, i, end, "FUNCTION");
                if (funcKw < 0) funcKw = BlockConstraintUtil.findKeyword(tokens, i, end, "PROCEDURE");
                if (funcKw > 0) {
                    int fn = BlockConstraintUtil.nextVisible(tokens, funcKw + 1, end);
                    if (fn >= 0) {
                        ConstraintSpec g = BlockConstraintUtil.gapConstraint(gapMap, funcKw, fn);
                        g.alignGroup("TYPE_MEMBER_FUNC");
                        constraints.add(g);
                    }
                    int retIdx = BlockConstraintUtil.findKeyword(tokens, funcKw, end, "RETURN");
                    if (retIdx > funcKw) {
                        int retVal = BlockConstraintUtil.nextVisible(tokens, retIdx + 1, end);
                        if (retVal >= 0 && !";".equals(tokens.get(retVal).text)) {
                            ConstraintSpec g2 = BlockConstraintUtil.gapConstraint(gapMap, retIdx, retVal);
                            g2.alignGroup("TYPE_MEMBER_RETURN");
                            constraints.add(g2);
                        }
                    }
                    i = funcKw + 1;
                } else {
                    i++;
                }
                continue;
            }

            // Field declarations: name → type alignment
            if (BlockConstraintUtil.isIdentifier(ti) && i > 0) {
                int prev = BlockConstraintUtil.prevVisible(tokens, i - 1, start);
                if (prev >= 0 && (",".equals(tokens.get(prev).text)
                    || "(".equals(tokens.get(prev).text) || prev == (i - 1))) {
                    int next = BlockConstraintUtil.nextVisible(tokens, i + 1, end);
                    if (next >= 0 && !",".equals(tokens.get(next).text)
                        && !")".equals(tokens.get(next).text)
                        && !"FUNCTION".equals(tokens.get(next).upper)
                        && !"PROCEDURE".equals(tokens.get(next).upper)
                        && !"MEMBER".equals(tokens.get(next).upper)
                        && !"STATIC".equals(tokens.get(next).upper)) {
                        ConstraintSpec g = BlockConstraintUtil.gapConstraint(gapMap, i, next);
                        g.alignGroup("TYPE_FIELD");
                        constraints.add(g);
                    }
                }
            }

            i++;
        }
    }
}
