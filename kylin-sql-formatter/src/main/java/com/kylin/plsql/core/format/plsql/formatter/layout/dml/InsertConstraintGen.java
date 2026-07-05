package com.kylin.plsql.core.format.plsql.formatter.layout.dml;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.InsertColumnFormat;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.formatter.layout.dql.SelectColumnConstraintGen;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.List;
import java.util.Map;

public class InsertConstraintGen {

    private InsertConstraintGen() {}

    public static void process(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int startIdx, int endIdx) {
        List<Integer> insertPositions = SelectColumnConstraintGen.findKeywords(tokens, startIdx, endIdx, "INSERT");
        for (int insPos : insertPositions) {
            int intoPos = SelectColumnConstraintGen.findNextKeyword(tokens, insPos + 1, endIdx, "INTO");
            if (intoPos < 0) continue;

            int lparen = findNextNonWs(tokens, intoPos + 1, endIdx);
            boolean hasParen = lparen >= 0 && "(".equals(tokens.get(lparen).text);

            int valuesPos = SelectColumnConstraintGen.findNextKeyword(tokens, intoPos + 1, endIdx, "VALUES");
            int selectPos = SelectColumnConstraintGen.findNextKeyword(tokens, intoPos + 1, endIdx, "SELECT");
            int rparenValues = -1;

            boolean insertAll = isInsertAll(tokens, insPos, endIdx);

            if (valuesPos >= 0) {
                int vlparen = SelectColumnConstraintGen.nextVisibleToken(tokens, valuesPos + 1);
                if (vlparen >= 0 && "(".equals(tokens.get(vlparen).text)) {
                    rparenValues = findMatchingParen(tokens, vlparen, endIdx);
                }
            }

            // INSERT column list formatting
            if (hasParen && !insertAll) {
                int rparen = findMatchingParen(tokens, lparen, valuesPos > 0 ? valuesPos : endIdx);
                if (rparen >= 0) {
                    boolean colNewline = opts.isDmlInsertColumnNewline();
                    InsertColumnFormat colFmt = opts.getInsertColumnFormat();
                    int colPerRow = opts.getInsertColumnsPerRow();
                    int indentSize = opts.getIndentSize();

                    int firstCol = SelectColumnConstraintGen.nextVisibleToken(tokens, lparen + 1);
                    if (firstCol >= 0 && firstCol < rparen) {
                        if (colNewline) {
                            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                                lparen, firstCol, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);
                        }
                        int lastCol = SelectColumnConstraintGen.prevVisibleToken(tokens, rparen - 1);
                        if (lastCol > lparen) {
                            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                                lastCol, rparen, ConstraintSpec.NewlineMode.REQUIRED, -1, 0.3);
                        }
                        formatCommaList(tokens, constraints, gapMap, lparen, rparen,
                            colFmt, colPerRow, indentSize);
                    }

                    // INSERT column list → VALUES / SELECT
                    if (opts.isDmlValuesNewline() && valuesPos >= 0) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            rparen, valuesPos, ConstraintSpec.NewlineMode.REQUIRED, 0, 0.3);
                    } else if (selectPos > rparen) {
                        int gapFrom = rparen;
                        int gapTo = SelectColumnConstraintGen.nextVisibleToken(tokens, gapFrom + 1);
                        if (gapTo < selectPos) {
                            SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                                gapFrom, gapTo, ConstraintSpec.NewlineMode.REQUIRED, 0, 0.4);
                        }
                    }
                }
            }

            // VALUES formatting
            if (valuesPos >= 0 && rparenValues >= 0) {
                if (opts.isDmlValuesNewline()) {
                    int prevGap = SelectColumnConstraintGen.prevVisibleToken(tokens, valuesPos - 1);
                    if (prevGap >= 0) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            prevGap, valuesPos, ConstraintSpec.NewlineMode.REQUIRED, 0, 0.3);
                    }
                }

                if (opts.isDmlValuesExpand()) {
                    int vFirst = SelectColumnConstraintGen.nextVisibleToken(tokens, rparenValues + 1);
                    if (vFirst < 0) vFirst = rparenValues + 1;
                    SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                        valuesPos, SelectColumnConstraintGen.nextVisibleToken(tokens, valuesPos + 1),
                        ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);
                    SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                        SelectColumnConstraintGen.prevVisibleToken(tokens, rparenValues - 1), rparenValues,
                        ConstraintSpec.NewlineMode.REQUIRED, -1, 0.3);
                } else {
                    int valPerRow = opts.getInsertValuesPerRow();
                    if (valPerRow > 0) {
                        formatCommaList(tokens, constraints, gapMap, rparenValues - 1, rparenValues,
                            InsertColumnFormat.ONE_PER_LINE, valPerRow, opts.getIndentSize());
                    }
                }

                // VALUES → ; or INTO clause
                int lastKw = SelectColumnConstraintGen.findNextKeyword(tokens, rparenValues + 1, endIdx, "INTO");
                if (lastKw < 0) lastKw = SelectColumnConstraintGen.findNextKeyword(tokens, rparenValues + 1, endIdx, "SELECT");
                if (lastKw < 0) lastKw = SelectColumnConstraintGen.findNextKeyword(tokens, rparenValues + 1, endIdx, "WHERE");
                int gapAfter = lastKw > 0 ? lastKw : endIdx;
                int afterVal = SelectColumnConstraintGen.nextVisibleToken(tokens, rparenValues + 1);
                if (afterVal > 0 && afterVal < tokens.size()) {
                    SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                        rparenValues, afterVal, ConstraintSpec.NewlineMode.REQUIRED, -1, 0.3);
                }
            }

            // RETURNING INTO
            if (!insertAll) {
                int returningPos = SelectColumnConstraintGen.findNextKeyword(tokens, insPos + 1, endIdx, "RETURNING");
                if (returningPos >= 0) {
                    int intoPosR = SelectColumnConstraintGen.findNextKeyword(tokens, returningPos + 1, endIdx, "INTO");
                    if (intoPosR >= 0 && opts.isDmlIntoAlign()) {
                        alignIntoVars(tokens, constraints, gapMap, intoPosR, endIdx, opts);
                    }
                }
            }
        }
    }

    private static boolean isInsertAll(List<TokenInfo> tokens, int insertIdx, int endIdx) {
        int next = SelectColumnConstraintGen.nextVisibleToken(tokens, insertIdx + 1);
        if (next < 0) return false;
        String u = tokens.get(next).upper;
        return "ALL".equals(u) || "FIRST".equals(u);
    }

    private static void formatCommaList(List<TokenInfo> tokens,
                                         List<ConstraintSpec> constraints,
                                         Map<String, ConstraintSpec> gapMap,
                                         int lparen, int rparen,
                                         InsertColumnFormat fmt, int perRow, int indentSize) {
        List<Integer> commas = new java.util.ArrayList<>();
        for (int i = lparen + 1; i < rparen; i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (",".equals(ti.text)) commas.add(i);
        }
        if (commas.isEmpty()) return;

        if (fmt == InsertColumnFormat.COMPACT) {
            for (int i = 0; i < commas.size(); i++) {
                SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                    commas.get(i), commas.get(i) + 1,
                    ConstraintSpec.NewlineMode.OPTIONAL, 0, 0.5);
            }
        } else {
            for (int i = 0; i < commas.size(); i++) {
                int nextTok = SelectColumnConstraintGen.nextVisibleToken(tokens, commas.get(i) + 1);
                if (nextTok > 0 && nextTok < rparen) {
                    boolean forceBreak = perRow > 0 && (i + 1) % perRow == 0;
                    if (forceBreak) {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            commas.get(i), nextTok,
                            ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);
                    } else {
                        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
                            commas.get(i), nextTok,
                            ConstraintSpec.NewlineMode.OPTIONAL, 0, 0.5);
                    }
                }
            }
        }
    }

    private static void alignIntoVars(List<TokenInfo> tokens,
                                       List<ConstraintSpec> constraints,
                                       Map<String, ConstraintSpec> gapMap,
                                       int intoIdx, int endIdx, FormatOptions opts) {
        int nextVar = SelectColumnConstraintGen.nextVisibleToken(tokens, intoIdx + 1);
        if (nextVar < 0) return;

        SelectColumnConstraintGen.addDqlGap(tokens, constraints, gapMap,
            intoIdx, nextVar, ConstraintSpec.NewlineMode.REQUIRED, 1, 0.3);

        String groupId = "DML_INTO_" + intoIdx;
        for (int i = nextVar; i < endIdx; i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (",".equals(ti.text)) {
                int nextTok = SelectColumnConstraintGen.nextVisibleToken(tokens, i + 1);
                if (nextTok > 0 && nextTok <= endIdx) {
                    ConstraintSpec c = new ConstraintSpec(i, nextTok)
                        .newline(ConstraintSpec.NewlineMode.OPTIONAL)
                        .alignGroup(groupId);
                    SelectColumnConstraintGen.addDqlConstraint(constraints, gapMap, c);
                }
            }
        }
    }

    private static int findNextNonWs(List<TokenInfo> tokens, int from, int endIdx) {
        for (int i = from; i <= endIdx && i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0) return i;
        }
        return -1;
    }

    private static int findMatchingParen(List<TokenInfo> tokens, int openIdx, int endIdx) {
        int depth = 0;
        for (int i = openIdx; i <= endIdx && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if ("(".equals(ti.text)) depth++;
            else if (")".equals(ti.text)) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
