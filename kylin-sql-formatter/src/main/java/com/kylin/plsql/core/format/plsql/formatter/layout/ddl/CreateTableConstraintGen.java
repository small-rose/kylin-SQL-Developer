package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class CreateTableConstraintGen {

    private CreateTableConstraintGen() {}

    static void walkCreateTable(FormatOptions opts, List<TokenInfo> tokens,
                                 List<ConstraintSpec> constraints,
                                 Map<String, ConstraintSpec> gapMap,
                                 int start, int end) {
        int openParen = DdlUtil.findChar(tokens, start, end, '(');
        if (openParen < 0) return;

        int closeParen = DdlUtil.findMatchingParen(tokens, openParen, end);

        if (closeParen > openParen) {
            formatColumnDefs(opts, tokens, constraints, gapMap, openParen, closeParen);

            int afterClose = DdlUtil.nextVisible(tokens, closeParen + 1, end);
            if (afterClose >= 0 && afterClose <= end) {
                String nextKw = tokens.get(afterClose).upper;
                if ("TABLESPACE".equals(nextKw) || "STORAGE".equals(nextKw)
                    || "PCTFREE".equals(nextKw) || "PCTUSED".equals(nextKw)
                    || "PCTINCREASE".equals(nextKw) || "LOGGING".equals(nextKw)
                    || "NOLOGGING".equals(nextKw) || "COMPRESS".equals(nextKw)
                    || "CACHE".equals(nextKw) || "NOCACHE".equals(nextKw)
                    || "MONITORING".equals(nextKw) || "ORGANIZATION".equals(nextKw)
                    || "ROW".equals(nextKw) || "PCTTHRESHOLD".equals(nextKw)) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, closeParen, afterClose,
                        ConstraintSpec.NewlineMode.REQUIRED, 0);
                }
            }
        }

        if (closeParen > 0 && closeParen < end) {
            walkTableOptions(opts, tokens, constraints, gapMap, closeParen, end);
        }

        int asIdx = DdlUtil.findKeyword(tokens, start, end, "AS");
        if (asIdx > 0) {
            int selectIdx = DdlUtil.nextVisibleKw(tokens, asIdx + 1, end, "SELECT");
            if (selectIdx > 0) {
                DdlUtil.addDdlGap(tokens, constraints, gapMap, asIdx, selectIdx,
                    ConstraintSpec.NewlineMode.REQUIRED, 1);
            }
        }
    }

    private static void formatColumnDefs(FormatOptions opts, List<TokenInfo> tokens,
                                          List<ConstraintSpec> constraints,
                                          Map<String, ConstraintSpec> gapMap,
                                          int openParen, int closeParen) {
        int firstCol = DdlUtil.nextVisible(tokens, openParen + 1, closeParen);
        if (firstCol < 0) return;
        DdlUtil.addDdlGap(tokens, constraints, gapMap, openParen, firstCol,
            ConstraintSpec.NewlineMode.REQUIRED, 1);

        int i = firstCol;
        boolean alignColumns = opts.isDdlColumnAlign();
        int colNameAlignId = 0;
        int colTypeAlignId = 0;
        if (alignColumns) {
            colNameAlignId = DdlUtil.nextAlignGroup(gapMap);
            colTypeAlignId = DdlUtil.nextAlignGroup(gapMap);
        }

        while (i <= closeParen) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) { i++; continue; }

            if (",".equals(ti.text)) {
                int next = DdlUtil.nextVisible(tokens, i + 1, closeParen);
                if (next >= 0 && next <= closeParen) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, i, next,
                        ConstraintSpec.NewlineMode.REQUIRED, 0);
                }
                i++;
                continue;
            }

            if ("(".equals(ti.text)) {
                int depth = 1;
                int j = i + 1;
                while (j <= closeParen && depth > 0) {
                    if ("(".equals(tokens.get(j).text)) depth++;
                    else if (")".equals(tokens.get(j).text)) depth--;
                    j++;
                }
                int rparen = j - 1;
                int beforeRparen = DdlUtil.prevVisible(tokens, rparen - 1, i);
                if (beforeRparen >= 0) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, beforeRparen, rparen,
                        ConstraintSpec.NewlineMode.REQUIRED, -1);
                }
                i = j;
                continue;
            }

            if (alignColumns && DdlUtil.isIdentifier(ti)) {
                int afterName = DdlUtil.nextVisible(tokens, i + 1, closeParen);
                if (afterName >= 0 && !"CONSTRAINT".equals(tokens.get(afterName).upper)) {
                    ConstraintSpec g = DdlUtil.gapConstraint(gapMap, i, afterName);
                    g.newline(ConstraintSpec.NewlineMode.OPTIONAL);
                    g.alignGroup("ddlColName_" + colNameAlignId);
                    g.indentDelta(0);
                    constraints.add(g);

                    int afterType = DdlUtil.nextVisible(tokens, afterName + 1, closeParen);
                    if (afterType >= 0 && !",".equals(tokens.get(afterType).text)
                        && !")".equals(tokens.get(afterType).text)
                        && !"CONSTRAINT".equals(tokens.get(afterType).upper)) {
                        ConstraintSpec g2 = DdlUtil.gapConstraint(gapMap, afterType, afterType);
                        g2.alignGroup("ddlColType_" + colTypeAlignId);
                        g2.newline(ConstraintSpec.NewlineMode.OPTIONAL);
                        constraints.add(g2);
                    }
                }
            }

            i++;
        }

        int beforeClose = DdlUtil.prevVisible(tokens, closeParen - 1, openParen);
        if (beforeClose >= 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, beforeClose, closeParen,
                ConstraintSpec.NewlineMode.REQUIRED, -1);
        }
    }

    private static void walkTableOptions(FormatOptions opts, List<TokenInfo> tokens,
                                           List<ConstraintSpec> constraints,
                                           Map<String, ConstraintSpec> gapMap,
                                           int afterParen, int end) {
        int i = afterParen;
        while (i <= end) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) { i++; continue; }
            String u = ti.upper;

            if ("TABLESPACE".equals(u)) {
                int nameIdx = DdlUtil.nextVisible(tokens, i + 1, end);
                if (nameIdx > i) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, i, nameIdx,
                        ConstraintSpec.NewlineMode.OPTIONAL, 0);
                }
                i = nameIdx + 1;
                continue;
            }

            if ("STORAGE".equals(u)) {
                int sp = DdlUtil.findChar(tokens, i + 1, end, '(');
                if (sp > i) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, i, sp,
                        ConstraintSpec.NewlineMode.OPTIONAL, 0);
                    int scp = DdlUtil.findMatchingParen(tokens, sp, end);
                    if (scp > sp) formatStorageParams(tokens, constraints, gapMap, sp, scp);
                    i = scp > 0 ? scp + 1 : i + 1;
                } else {
                    i++;
                }
                continue;
            }

            if ("ORGANIZATION".equals(u)) {
                int next = DdlUtil.nextVisible(tokens, i + 1, end);
                if (next > i) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, i, next,
                        ConstraintSpec.NewlineMode.OPTIONAL, 0);
                }
                i = next + 1;
                continue;
            }

            if ("PARTITION".equals(u) && i + 1 <= end
                && "BY".equals(tokens.get(DdlUtil.nextVisible(tokens, i + 1, end)).upper)) {
                walkPartitionClause(tokens, constraints, gapMap, i, end);
                break;
            }

            if ("LOB".equals(u)) {
                walkLobClause(tokens, constraints, gapMap, i, end);
                int lobEnd = DdlUtil.findLobEnd(tokens, i, end);
                i = lobEnd > 0 ? lobEnd + 1 : i + 1;
                continue;
            }

            if ("NESTED".equals(u) && i + 1 <= end
                && "TABLE".equals(tokens.get(DdlUtil.nextVisible(tokens, i + 1, end)).upper)) {
                walkNestedTableClause(tokens, constraints, gapMap, i, end);
                i = end + 1;
                break;
            }

            if ("VARRAY".equals(u)) {
                walkVarrayClause(tokens, constraints, gapMap, i, end);
                i = end + 1;
                break;
            }

            if (Set.of("PCTFREE","PCTUSED","PCTINCREASE","LOGGING","NOLOGGING",
                       "COMPRESS","CACHE","NOCACHE","MONITORING","ROW",
                       "PCTTHRESHOLD").contains(u)) {
                int next = DdlUtil.nextVisible(tokens, i + 1, end);
                String gid = "ddlStorageOpt_" + DdlUtil.nextAlignGroup(gapMap);
                if (next > i) {
                    ConstraintSpec g = DdlUtil.gapConstraint(gapMap, i, next);
                    g.newline(ConstraintSpec.NewlineMode.REQUIRED);
                    g.alignGroup(gid);
                    constraints.add(g);
                }
                i = next + 1;
                continue;
            }

            i++;
        }
    }

    static void formatStorageParams(List<TokenInfo> tokens,
                                      List<ConstraintSpec> constraints,
                                      Map<String, ConstraintSpec> gapMap,
                                      int openParen, int closeParen) {
        int first = DdlUtil.nextVisible(tokens, openParen + 1, closeParen);
        if (first < 0) return;
        String gid = "ddlStorage_" + DdlUtil.nextAlignGroup(gapMap);
        DdlUtil.addDdlGap(tokens, constraints, gapMap, openParen, first,
            ConstraintSpec.NewlineMode.REQUIRED, 1);

        int i = first;
        while (i <= closeParen) {
            if (tokens.get(i).channel != 0) { i++; continue; }
            if (")".equals(tokens.get(i).text)) {
                int prev = DdlUtil.prevVisible(tokens, i - 1, openParen);
                if (prev >= 0) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, prev, i,
                        ConstraintSpec.NewlineMode.REQUIRED, -1);
                }
                break;
            }
            i++;
        }

        int lastParamEnd = -1;
        for (int j = first; j <= closeParen; j++) {
            TokenInfo tj = tokens.get(j);
            if (tj.channel != 0) continue;
            if (Set.of("INITIAL","NEXT","MINEXTENTS","MAXEXTENTS","PCTINCREASE",
                       "FREELISTS","FREELIST","BUFFER_POOL","RECOVER").contains(tj.upper)) {
                if (lastParamEnd >= 0) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, lastParamEnd, j,
                        ConstraintSpec.NewlineMode.REQUIRED, 0);
                }
                int valIdx = DdlUtil.nextVisible(tokens, j + 1, closeParen);
                if (valIdx > j && valIdx <= closeParen
                    && !",".equals(tokens.get(valIdx).text)
                    && !")".equals(tokens.get(valIdx).text)) {
                    ConstraintSpec g = DdlUtil.gapConstraint(gapMap, j, valIdx);
                    g.newline(ConstraintSpec.NewlineMode.OPTIONAL);
                    g.alignGroup(gid);
                    constraints.add(g);
                    lastParamEnd = valIdx;
                } else {
                    lastParamEnd = j;
                }
            }
        }
    }

    private static void walkPartitionClause(List<TokenInfo> tokens,
                                              List<ConstraintSpec> constraints,
                                              Map<String, ConstraintSpec> gapMap,
                                              int partByIdx, int end) {
        int openParen = DdlUtil.findChar(tokens, partByIdx + 1, end, '(');
        if (openParen < 0) return;

        int closeParen = DdlUtil.findMatchingParen(tokens, openParen, end);
        if (closeParen <= openParen) return;

        DdlUtil.addDdlGap(tokens, constraints, gapMap, partByIdx,
            DdlUtil.nextVisible(tokens, partByIdx + 1, end),
            ConstraintSpec.NewlineMode.REQUIRED, 0);

        int firstPart = DdlUtil.nextVisible(tokens, openParen + 1, closeParen);
        if (firstPart < 0) return;
        DdlUtil.addDdlGap(tokens, constraints, gapMap, openParen, firstPart,
            ConstraintSpec.NewlineMode.REQUIRED, 1);

        String partGid = "ddlPartition_" + DdlUtil.nextAlignGroup(gapMap);
        int i = firstPart;
        while (i <= closeParen) {
            if (tokens.get(i).channel != 0) { i++; continue; }
            if ("PARTITION".equals(tokens.get(i).upper) && i > firstPart) {
                DdlUtil.addDdlGap(tokens, constraints, gapMap,
                    DdlUtil.prevVisible(tokens, i - 1, openParen), i,
                    ConstraintSpec.NewlineMode.REQUIRED, 0);
                ConstraintSpec g = DdlUtil.gapConstraint(gapMap, i,
                    DdlUtil.nextVisible(tokens, i + 1, closeParen));
                g.alignGroup(partGid);
                constraints.add(g);
            }
            if (")".equals(tokens.get(i).text)) {
                int prev = DdlUtil.prevVisible(tokens, i - 1, openParen);
                if (prev >= 0) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, prev, i,
                        ConstraintSpec.NewlineMode.REQUIRED, -1);
                }
                break;
            }
            i++;
        }
    }

    private static void walkLobClause(List<TokenInfo> tokens,
                                        List<ConstraintSpec> constraints,
                                        Map<String, ConstraintSpec> gapMap,
                                        int lobIdx, int end) {
        int openParen = DdlUtil.findChar(tokens, lobIdx + 1, end, '(');
        if (openParen < 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, lobIdx,
                DdlUtil.nextVisible(tokens, lobIdx + 1, end),
                ConstraintSpec.NewlineMode.REQUIRED, 0);
            return;
        }
        int closeParen = DdlUtil.findMatchingParen(tokens, openParen, end);
        if (closeParen > openParen) {
            int storeAsIdx = DdlUtil.nextVisibleKw(tokens, closeParen + 1, end, "STORE");
            if (storeAsIdx > 0) {
                int storeCp = DdlUtil.findChar(tokens, storeAsIdx + 1, end, '(');
                if (storeCp > 0) {
                    int storeCpEnd = DdlUtil.findMatchingParen(tokens, storeCp, end);
                    if (storeCpEnd > storeCp) {
                        String lobGid = "ddlLob_" + DdlUtil.nextAlignGroup(gapMap);
                        DdlUtil.formatParenOpts(tokens, constraints, gapMap, storeCp, storeCpEnd, lobGid, true);
                    }
                }
            }
        }
    }

    private static void walkNestedTableClause(List<TokenInfo> tokens,
                                                List<ConstraintSpec> constraints,
                                                Map<String, ConstraintSpec> gapMap,
                                                int nestedIdx, int end) {
        int storeAsIdx = DdlUtil.nextVisibleKw(tokens, nestedIdx + 1, end, "STORE");
        if (storeAsIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, storeAsIdx,
                DdlUtil.nextVisible(tokens, storeAsIdx + 1, end),
                ConstraintSpec.NewlineMode.OPTIONAL, 0);
        }
        int i = storeAsIdx > 0 ? storeAsIdx + 2 : nestedIdx + 2;
        while (i <= end) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) { i++; continue; }
            String u = ti.upper;
            if ("TABLESPACE".equals(u)) {
                int nameIdx = DdlUtil.nextVisible(tokens, i + 1, end);
                if (nameIdx > i) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, i, nameIdx,
                        ConstraintSpec.NewlineMode.OPTIONAL, 1);
                }
                i = nameIdx + 1;
            } else if ("STORAGE".equals(u)) {
                int sp = DdlUtil.findChar(tokens, i + 1, end, '(');
                if (sp > i) {
                    DdlUtil.addDdlGap(tokens, constraints, gapMap, i, sp,
                        ConstraintSpec.NewlineMode.OPTIONAL, 0);
                    int scp = DdlUtil.findMatchingParen(tokens, sp, end);
                    if (scp > sp) formatStorageParams(tokens, constraints, gapMap, sp, scp);
                    i = scp > 0 ? scp + 1 : i + 1;
                } else {
                    i++;
                }
            } else if ("NESTED".equals(u)) {
                break;
            } else {
                i++;
            }
        }
    }

    private static void walkVarrayClause(List<TokenInfo> tokens,
                                           List<ConstraintSpec> constraints,
                                           Map<String, ConstraintSpec> gapMap,
                                           int varrayIdx, int end) {
        int storeAsIdx = DdlUtil.nextVisibleKw(tokens, varrayIdx + 1, end, "STORE");
        if (storeAsIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, storeAsIdx,
                DdlUtil.nextVisible(tokens, storeAsIdx + 1, end),
                ConstraintSpec.NewlineMode.OPTIONAL, 0);
        }
    }
}
