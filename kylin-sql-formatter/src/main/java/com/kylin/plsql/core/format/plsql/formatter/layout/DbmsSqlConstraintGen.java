package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.block.BlockConstraintUtil;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class DbmsSqlConstraintGen {

    private static final Set<String> DBMS_SQL_FUNCS = Set.of(
        "OPEN_CURSOR", "TO_CURSOR_NUMBER", "PARSE", "BIND_VARIABLE",
        "DEFINE_COLUMN", "EXECUTE", "EXECUTE_AND_FETCH", "FETCH_ROWS",
        "COLUMN_VALUE", "CLOSE_CURSOR", "IS_OPEN", "NATIVE"
    );

    private DbmsSqlConstraintGen() {}

    public static void addDbmsSqlConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                               List<ConstraintSpec> constraints,
                                               Map<String, ConstraintSpec> gapMap,
                                               List<PlSqlBlock> topLevelBlocks) {
        if (!opts.isDbmsSqlFormat()) return;

        BlockConstraintUtil.iterateBlocks(topLevelBlocks, block -> {
            if (block.statements == null) return;
            for (Statement stmt : block.statements) {
                walkDbmsSqlStatement(opts, tokens, constraints, gapMap, stmt, block);
            }
        });
    }

    private static void walkDbmsSqlStatement(FormatOptions opts, List<TokenInfo> tokens,
                                               List<ConstraintSpec> constraints,
                                               Map<String, ConstraintSpec> gapMap,
                                               Statement stmt, PlSqlBlock block) {
        int endBound = block.endTokenIdx;
        int start = stmt.startTokenIdx;
        if (start < 0) start = block.startTokenIdx;
        int end = stmt.endTokenIdx;
        if (end < 0) end = block.endTokenIdx;

        // Scan for DBMS_SQL.xxx pattern
        for (int i = start; i <= end && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (!"DBMS_SQL".equals(ti.upper)) continue;

            // Check next visible token is a dot
            int dot = BlockConstraintUtil.nextVisible(tokens, i + 1, end);
            if (dot < 0 || !".".equals(tokens.get(dot).text)) continue;

            // Check next after dot is a known function
            int func = BlockConstraintUtil.nextVisible(tokens, dot + 1, end);
            if (func < 0) continue;
            String funcName = tokens.get(func).upper;
            if (!DBMS_SQL_FUNCS.contains(funcName) && !funcName.startsWith("BIND_VARIABLE")
                && !funcName.startsWith("DEFINE_COLUMN")) continue;

            // DBMS_SQL → . : FORBIDDEN
            BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                i, dot, ConstraintSpec.NewlineMode.FORBIDDEN, 0);

            // . → func : FORBIDDEN
            BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                dot, func, ConstraintSpec.NewlineMode.FORBIDDEN, 0);

            // Find matching parens
            int lparen = BlockConstraintUtil.nextVisible(tokens, func + 1, end);
            if (lparen < 0 || !"(".equals(tokens.get(lparen).text)) continue;
            int rparen = findMatchingParen(tokens, lparen, end);
            if (rparen < 0) continue;

            // Function → ( : FORBIDDEN (no space)
            BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                func, lparen, ConstraintSpec.NewlineMode.FORBIDDEN, 0);

            // Handle BIND_VARIABLE / DEFINE_COLUMN — each on separate line
            boolean bindOrCol = "BIND_VARIABLE".equals(funcName)
                || "DEFINE_COLUMN".equals(funcName)
                || funcName.startsWith("BIND_VARIABLE")
                || funcName.startsWith("DEFINE_COLUMN");

            if (bindOrCol) {
                boolean perLine = "BIND_VARIABLE".equals(funcName)
                    ? opts.isDbmsSqlBindPerLine() : opts.isDbmsSqlColumnPerLine();
                if (perLine) {
                    // Statement should be on its own line
                    int prevStmt = BlockConstraintUtil.prevVisible(tokens, start - 1, 0);
                    if (prevStmt >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            prevStmt, start, ConstraintSpec.NewlineMode.REQUIRED, 0);
                    }
                }
                // Comma → next param: FORBIDDEN (keep params compact on one line)
                for (int j = lparen + 1; j < rparen; j++) {
                    TokenInfo commaT = tokens.get(j);
                    if (commaT.channel != 0) continue;
                    if (!",".equals(commaT.text)) continue;
                    int nextParam = BlockConstraintUtil.nextVisible(tokens, j + 1, rparen);
                    if (nextParam >= 0) {
                        BlockConstraintUtil.addBlockGap(tokens, constraints, gapMap,
                            j, nextParam, ConstraintSpec.NewlineMode.FORBIDDEN, 0);
                    }
                }
            }
        }
    }

    private static int findMatchingParen(List<TokenInfo> tokens, int lparen, int endBound) {
        int depth = 1;
        int limit = Math.min(endBound, tokens.size() - 1);
        for (int i = lparen + 1; i <= limit; i++) {
            String t = tokens.get(i).text;
            if (tokens.get(i).channel != 0) continue;
            if ("(".equals(t)) depth++;
            else if (")".equals(t)) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
