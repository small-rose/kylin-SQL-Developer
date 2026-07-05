package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class AlterTableConstraintGen {

    private AlterTableConstraintGen() {}

    static void walkAlterTable(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int start, int end) {
        String[] ops = {"ADD","MODIFY","DROP","RENAME","SET","ENABLE","DISABLE",
                        "MOVE","SPLIT","MERGE","SHRINK","TRUNCATE"};

        int tableKw = DdlUtil.findKeyword(tokens, start, end, "TABLE");
        if (tableKw < 0) return;
        int nameEnd = tableKw + 1;
        while (nameEnd <= end && tokens.get(nameEnd).channel != 0) nameEnd++;

        List<Integer> opPositions = new ArrayList<>();
        for (String op : ops) {
            int idx = DdlUtil.findKeyword(tokens, nameEnd, end, op);
            while (idx > 0) {
                opPositions.add(idx);
                idx = DdlUtil.findKeyword(tokens, idx + 1, end, op);
            }
        }
        Collections.sort(opPositions);

        String opGid = opts.isDdlAlterAddDropPerLine() ? "ddlAlterOp_" + DdlUtil.nextAlignGroup(gapMap) : null;

        int prevOpEnd = nameEnd;
        for (int opIdx : opPositions) {
            if (opIdx < prevOpEnd) continue;

            if (opIdx > nameEnd) {
                DdlUtil.addDdlGap(tokens, constraints, gapMap,
                    DdlUtil.prevVisible(tokens, opIdx - 1, start), opIdx,
                    ConstraintSpec.NewlineMode.REQUIRED, 0);
            }

            int opEnd = findAlterOpEnd(tokens, opIdx, end, opPositions);

            String opName = tokens.get(opIdx).upper;
            switch (opName) {
                case "ADD":
                case "MODIFY":
                    int opOpenParen = DdlUtil.findChar(tokens, opIdx + 1, opEnd, '(');
                    if (opOpenParen > 0) {
                        int opCloseParen = DdlUtil.findMatchingParen(tokens, opOpenParen, opEnd);
                        if (opCloseParen > opOpenParen) {
                            String colGid = opts.isDdlAlterColumnPerLine()
                                ? "ddlAlterCol_" + DdlUtil.nextAlignGroup(gapMap) : null;
                            DdlUtil.formatParenOpts(tokens, constraints, gapMap,
                                opOpenParen, opCloseParen, colGid, true);
                        }
                    } else {
                        int constraintIdx = DdlUtil.findKeyword(tokens, opIdx + 1, opEnd, "CONSTRAINT");
                        if (constraintIdx > 0) {
                            DdlUtil.addDdlGap(tokens, constraints, gapMap,
                                DdlUtil.prevVisible(tokens, constraintIdx - 1, start), constraintIdx,
                                ConstraintSpec.NewlineMode.OPTIONAL, 0);
                            formatConstraintClause(tokens, constraints, gapMap, constraintIdx, opEnd);
                        }
                    }
                    break;
                case "DROP":
                    int dropOpenParen = DdlUtil.findChar(tokens, opIdx + 1, opEnd, '(');
                    if (dropOpenParen > 0) {
                        int dropCloseParen = DdlUtil.findMatchingParen(tokens, dropOpenParen, opEnd);
                        if (dropCloseParen > dropOpenParen) {
                            DdlUtil.formatParenOpts(tokens, constraints, gapMap,
                                dropOpenParen, dropCloseParen, null, false);
                        }
                    }
                    break;
                case "ENABLE":
                case "DISABLE":
                    int constraintKwd = DdlUtil.findKeyword(tokens, opIdx + 1, opEnd, "CONSTRAINT");
                    if (constraintKwd > 0) {
                        DdlUtil.addDdlGap(tokens, constraints, gapMap, opIdx, constraintKwd,
                            ConstraintSpec.NewlineMode.OPTIONAL, 0);
                    }
                    if (opName.equals("ENABLE")) {
                        int rowMovement = DdlUtil.findKeyword(tokens, opIdx + 1, opEnd, "ROW");
                        if (rowMovement > 0) {
                            DdlUtil.addDdlGap(tokens, constraints, gapMap, opIdx, rowMovement,
                                ConstraintSpec.NewlineMode.OPTIONAL, 0);
                        }
                    }
                    break;
                case "MOVE":
                case "SPLIT":
                    walkMoveSplitAlter(tokens, constraints, gapMap, opIdx, opEnd);
                    break;
                default:
                    break;
            }

            prevOpEnd = opEnd;
        }
    }

    private static int findAlterOpEnd(List<TokenInfo> tokens, int opIdx, int end,
                                        List<Integer> otherOps) {
        int nextOp = end;
        for (int o : otherOps) {
            if (o > opIdx && o < nextOp) nextOp = o;
        }
        for (int i = opIdx + 1; i <= end && i < nextOp; i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (",".equals(ti.text)) {
                return i;
            }
        }
        return nextOp;
    }

    private static void walkMoveSplitAlter(List<TokenInfo> tokens,
                                             List<ConstraintSpec> constraints,
                                             Map<String, ConstraintSpec> gapMap,
                                             int opIdx, int opEnd) {
        String gid = "ddlMoveOpt_" + DdlUtil.nextAlignGroup(gapMap);
        int i = opIdx + 1;
        while (i <= opEnd) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) { i++; continue; }
            if (Set.of("TABLESPACE","STORAGE","COMPRESS","ONLINE",
                       "UPDATE","INTO","PARTITION","SUBPARTITION").contains(ti.upper)) {
                int next = DdlUtil.nextVisible(tokens, i + 1, opEnd);
                if (next > i) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap,
                        DdlUtil.prevVisible(tokens, i - 1, opIdx), i,
                        ConstraintSpec.NewlineMode.REQUIRED, 0);
                    ConstraintSpec g = DdlUtil.gapConstraint(gapMap, i, next);
                    g.alignGroup(gid);
                    constraints.add(g);
                }
                if ("STORAGE".equals(ti.upper)) {
                    int sp = DdlUtil.findChar(tokens, i + 1, opEnd, '(');
                    if (sp > 0) {
                        int scp = DdlUtil.findMatchingParen(tokens, sp, opEnd);
                        if (scp > sp) CreateTableConstraintGen.formatStorageParams(tokens, constraints, gapMap, sp, scp);
                        i = scp > 0 ? scp + 1 : i + 1;
                        continue;
                    }
                }
                if ("INTO".equals(ti.upper)) {
                    int ip = DdlUtil.findChar(tokens, i + 1, opEnd, '(');
                    if (ip > 0) {
                        int icp = DdlUtil.findMatchingParen(tokens, ip, opEnd);
                        if (icp > ip) DdlUtil.formatParenOpts(tokens, constraints, gapMap, ip, icp, null, true);
                        i = icp > 0 ? icp + 1 : i + 1;
                        continue;
                    }
                }
            }
            i++;
        }
    }

    private static void formatConstraintClause(List<TokenInfo> tokens,
                                                 List<ConstraintSpec> constraints,
                                                 Map<String, ConstraintSpec> gapMap,
                                                 int constraintIdx, int end) {
        int next = DdlUtil.nextVisible(tokens, constraintIdx + 1, end);
        if (next > constraintIdx) {
            ConstraintSpec g = DdlUtil.gapConstraint(gapMap, constraintIdx, next);
            g.alignGroup("ddlConstrName_" + DdlUtil.nextAlignGroup(gapMap));
            constraints.add(g);
        }
    }

    static void walkAlterIndex(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int start, int end) {
        int indexKw = DdlUtil.findKeyword(tokens, start, end, "INDEX");
        if (indexKw < 0) return;

        int nameEnd = DdlUtil.nextVisible(tokens, indexKw + 1, end);
        if (nameEnd < 0) return;
        int afterName = DdlUtil.nextVisible(tokens, nameEnd + 1, end);
        if (afterName < 0 || afterName > end) return;

        DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, afterName,
            ConstraintSpec.NewlineMode.REQUIRED, 0);

        String gid = "ddlIdxOpt_" + DdlUtil.nextAlignGroup(gapMap);
        DdlUtil.formatOptionsPerLine(tokens, constraints, gapMap, afterName, end, gid,
            Set.of("RENAME","REBUILD","TABLESPACE","STORAGE","ONLINE","MONITORING",
                   "UNUSABLE","PARALLEL","NOPARALLEL","LOGGING","NOLOGGING","PCTFREE",
                   "COMPUTE","INITRANS","MAXTRANS"));
    }
}
