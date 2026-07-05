package com.kylin.plsql.core.format.plsql.formatter.layout.block;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

/**
 * PACKAGE_SPEC / PACKAGE_BODY / FUNCTION / PROCEDURE / ANON_BLOCK 的结构约束。
 * 处理 header→body 缩进、BEGIN/EXCEPTION/END 定位、semicolon pop。
 * 遵循 ??ConstraintGen 模式，通过 iterateBlocks + block.type 过滤分发。
 */
public final class BodyBlockConstraintGen {

    private static final Set<PlSqlBlockType> BODY_TYPES = Set.of(
        PlSqlBlockType.PACKAGE_SPEC, PlSqlBlockType.PACKAGE_BODY,
        PlSqlBlockType.FUNCTION, PlSqlBlockType.PROCEDURE,
        PlSqlBlockType.ANON_BLOCK
    );

    private BodyBlockConstraintGen() {}

    public static void addBodyConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                           List<ConstraintSpec> constraints,
                                           Map<String, ConstraintSpec> gapMap,
                                           List<PlSqlBlock> topLevelBlocks) {
        BlockConstraintUtil.iterateBlocks(topLevelBlocks, block -> {
            if (!BODY_TYPES.contains(block.type)) return;
            walkBodyBlock(opts, tokens, constraints, gapMap, block);
        });
    }

    private static void walkBodyBlock(FormatOptions opts, List<TokenInfo> tokens,
                                       List<ConstraintSpec> constraints,
                                       Map<String, ConstraintSpec> gapMap,
                                       PlSqlBlock block) {
        int endBound = block.endTokenIdx;

        // Header (IS/AS/RETURN) → first statement.
        // PACKAGE_SPEC/PACKAGE_BODY: indent +1 (body content starts one level deeper)
        // FUNCTION/PROCEDURE/ANON_BLOCK: indent 0 (demo.txt style — IS and first
        //   declaration/statement are at the same level; continuation depth is
        //   handled by ParamConstraintGen).
        boolean hasHeader = block.headerEndTokenIdx >= 0
            && block.headerEndTokenIdx != block.startTokenIdx;
        int hed = block.headerEndTokenIdx;
        if (hasHeader && hed >= 0) {
        int delta = (block.type == PlSqlBlockType.PACKAGE_SPEC
                  || block.type == PlSqlBlockType.PACKAGE_BODY
                  || block.type == PlSqlBlockType.FUNCTION
                  || block.type == PlSqlBlockType.PROCEDURE) ? 1 : 0;
            int next = BlockConstraintUtil.nextVisible(tokens, hed + 1, endBound);
            if (next < tokens.size()) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    hed, next,
                    ConstraintSpec.NewlineMode.REQUIRED, delta);
            }
        }

        // BEGIN → body: indent +1 (statements inside BEGIN body)
        // For blocks with IS/AS header, the declaration area (IS→last_decl;)
        // is at body level via IS→body push. The ;last_decl → BEGIN gap pops
        // back to header level, then BEGIN → stmt pushes back to body level.
        int ss = block.stmtStartIdx;
        if (ss > 0 && ss < tokens.size()) {
            int beginIdx = BlockConstraintUtil.prevVisible(tokens, ss - 1, block.startTokenIdx);
            if (beginIdx >= 0 && "BEGIN".equals(tokens.get(beginIdx).upper)) {
                if (hasHeader) {
                    // Pop from body level (declarations) back to header level
                    int prevSemi = BlockConstraintUtil.findPrevSemicolon(tokens, beginIdx);
                    if (prevSemi >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            prevSemi, beginIdx,
                            ConstraintSpec.NewlineMode.REQUIRED, -1);
                    }
                }
                if (opts.isBeginOutdent()) {
                    int prevSemi = BlockConstraintUtil.findPrevSemicolon(tokens, beginIdx);
                    if (prevSemi >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            prevSemi, beginIdx,
                            ConstraintSpec.NewlineMode.REQUIRED, -1);
                    }
                }
                int next = BlockConstraintUtil.nextVisible(tokens, beginIdx + 1, endBound);
                if (next < tokens.size()) {
                    BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                        beginIdx, next,
                        ConstraintSpec.NewlineMode.REQUIRED, 1);
                }
            }
        }

        // EXCEPTION section (reuse existing utility)
        BlockConstraintUtil.addExceptionSectionConstraints(opts, tokens, constraints, gapMap, block);

        // END → outdent
        BlockConstraintUtil.addEndConstraint(tokens, constraints, gapMap, block);

        // Semicolon pop at block's closing ; — always delta=0.
        // EXCEPTION boundary pop (if present) is handled inside
        // addExceptionSectionConstraints.
        applySemicolonPop(tokens, constraints, gapMap, block);
    }

    private static void applySemicolonPop(List<TokenInfo> tokens,
                                            List<ConstraintSpec> constraints,
                                            Map<String, ConstraintSpec> gapMap,
                                            PlSqlBlock block) {
        int delta = 0;
        int ei = block.endTokenIdx;
        if (ei >= 0 && ei < tokens.size() && ";".equals(tokens.get(ei).text)) {
            int en = BlockConstraintUtil.nextVisible(tokens, ei + 1, tokens.size() - 1);
            if (en < tokens.size()) {
                BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                    ei, en,
                    ConstraintSpec.NewlineMode.REQUIRED, delta);
                // If the next token after ; is /, also force a newline after /
                String slashText = tokens.get(en).text;
                if ("/".equals(slashText) || "$".equals(slashText)) {
                    int afterSlash = BlockConstraintUtil.nextVisible(tokens, en + 1, tokens.size() - 1);
                    if (afterSlash >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            en, afterSlash,
                            ConstraintSpec.NewlineMode.REQUIRED, delta);
                    }
                }
            }
        }
    }
}
