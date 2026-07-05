package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.model.*;
import com.kylin.plsql.core.format.plsql.formatter.layout.block.*;
import com.kylin.plsql.core.format.plsql.formatter.layout.comments.CommentConstraintGen;
import com.kylin.plsql.core.format.plsql.formatter.layout.ddl.DdlConstraintGen;
import com.kylin.plsql.core.format.plsql.formatter.layout.dml.DmlConstraintGen;
import com.kylin.plsql.core.format.plsql.formatter.layout.dql.DqlConstraintGen;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import java.util.*;

public class ConstraintGenerator {

    private final FormatOptions opts;
    private final List<TokenInfo> tokens;
    private final List<ConstraintSpec> constraints = new ArrayList<>();
    private final Map<String, ConstraintSpec> gapMap = new LinkedHashMap<>();
    private final Set<Integer> afterOperatorPositions = new HashSet<>();
    private final Set<Integer> beforeOperatorPositions = new HashSet<>();

    private static final Set<String> OPERATOR_PAIRS = Set.of(
        "||", "<<", ">>", "<=", ">=", "=>",
        "!=", "<>", "^=", "~=", ":="
    );

    private static final Set<String> PCT_ATTRIBUTES = Set.of(
        "TYPE", "ROWTYPE", "NOTFOUND", "FOUND", "ISOPEN", "ROWCOUNT",
        "BULK_ROWCOUNT", "BULK_EXCEPTIONS"
    );

    private static final Set<String> NOT_KEYWORDS = Set.of(
        "IN", "LIKE", "BETWEEN", "NULL", "EXISTS"
    );

    private BitSet offRegion;

    public ConstraintGenerator(FormatOptions opts, List<TokenInfo> tokens) {
        this.opts = opts;
        this.tokens = tokens;
    }

    public List<ConstraintSpec> generate(List<PlSqlBlock> topLevelBlocks) {
        constraints.clear();
        gapMap.clear();
        afterOperatorPositions.clear();
        beforeOperatorPositions.clear();
        offRegion = scanOffRegions();

        // 1) Structural constraints for PACKAGE/FUNCTION/PROCEDURE/ANON_BLOCK
        BodyBlockConstraintGen.addBodyConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1a) IF block constraints (THEN→stmt, ELSIF/ELSE, thenOnNewLine, elseOnNewLine)
        IfConstraintGen.addIfConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1b) LOOP block constraints (LOOP→stmt, loopOnNewLine, forLoopFormat)
        LoopConstraintGen.addLoopConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1c) CASE_BLOCK constraints (WHEN→stmt, ELSE, END)
        CaseConstraintGen.addCaseConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1d) TYPE_SPEC/TYPE_BODY constraints (AS→indent, member align, typeMemberAlign)
        TypeConstraintGen.addTypeConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1e) TRIGGER constraints (header formatting, DECLARE/BEGIN/EXCEPTION/END, triggerFormat)
        TriggerConstraintGen.addTriggerConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1f) Block refinements (blankLineBeforeBlock, isEndAlign, isDeclarationAlign)
        BlockConstraintGen.addBlockConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1g) DQL constraints (SELECT column list, FROM/JOIN, WHERE, IN, subqueries, CTE, SET ops)
        DqlConstraintGen.addDqlConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1h) DML constraints (INSERT, UPDATE, DELETE, MERGE, BULK COLLECT, USING)
        DmlConstraintGen.addDmlConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1i) DDL constraints (CREATE TABLE/INDEX/VIEW, ALTER, DROP, GRANT, FLASHBACK, ANALYZE)
        DdlConstraintGen.addDdlConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 1j) Comment placement constraints
        CommentConstraintGen.addCommentConstraints(opts, tokens, constraints, gapMap);

        // 2) Semicolons → newlines for all blocks
        addSemicolonGaps(topLevelBlocks);

        // 3) FORBIDDEN for multi-char operators + attribute references (skip channel-1)
        addForbiddenOperatorConstraints();

        // 3a) Comma position (TRAILING/LEADING) — general comma constraints
        CommaConstraintGen.addCommaConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 3b) Parenthesis spacing per §28
        ParenConstraintGen.addParenConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 3c) FORALL statement formatting
        ForallConstraintGen.addForallConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 3d) DBMS_SQL call formatting
        DbmsSqlConstraintGen.addDbmsSqlConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 3e) CASE expression formatting
        CaseExprConstraintGen.addCaseExprConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 3f) Named parameter (=>) alignment
        NamedParamConstraintGen.addNamedParamConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 3g) Parameter list formatting (parameterPerLine, parameterAlignMode)
        ParamConstraintGen.addParamConstraints(opts, tokens, constraints, gapMap, topLevelBlocks);

        // 3h) PACKAGE header FORBIDDEN between CREATE PACKAGE name IS
        addPackageHeaderForbidden();

        // 4) OPTIONAL gaps at every remaining visible-token gap
        addOptionalGaps();

        return new ArrayList<>(gapMap.values());
    }

    private String gapKey(int from, int to) {
        return from + ":" + to;
    }

    private int nextVisible(int fromIdx) {
        int i = fromIdx;
        while (i < tokens.size() && tokens.get(i).channel != 0) i++;
        return i;
    }

    private int prevVisible(int fromIdx) {
        int i = fromIdx;
        while (i >= 0 && tokens.get(i).channel != 0) i--;
        return i;
    }

    private BitSet scanOffRegions() {
        BitSet off = new BitSet(tokens.size());
        boolean inOff = false;
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) {
                String lower = ti.text.toLowerCase();
                if (lower.contains("@formatter:off")) {
                    inOff = true;
                    continue;
                }
                if (lower.contains("@formatter:on")) {
                    inOff = false;
                    continue;
                }
            }
            if (inOff) off.set(i);
        }
        return off;
    }

    private boolean bothOff(int from, int to) {
        return offRegion != null && offRegion.get(from) && offRegion.get(to);
    }

    private void addConstraint(ConstraintSpec c) {
        if (bothOff(c.getFromTokenIdx(), c.getToTokenIdx())) return;
        String key = gapKey(c.getFromTokenIdx(), c.getToTokenIdx());
        ConstraintSpec prev = gapMap.get(key);
        if (prev == null) {
            gapMap.put(key, c);
            constraints.add(c);
        } else {
            if (c.getNewlineMode() == ConstraintSpec.NewlineMode.FORBIDDEN) {
                prev.newline(ConstraintSpec.NewlineMode.FORBIDDEN);
                int min = Math.min(c.getMinSpaces(), prev.getMinSpaces());
                int max = Math.min(c.getMaxSpaces(), prev.getMaxSpaces());
                int pref = Math.min(c.getPreferredSpaces(), prev.getPreferredSpaces());
                prev.spaces(min, max, pref);
                return;
            }
            if (c.getNewlineMode() == ConstraintSpec.NewlineMode.REQUIRED
                && prev.getNewlineMode() == ConstraintSpec.NewlineMode.OPTIONAL) {
                prev.newline(ConstraintSpec.NewlineMode.REQUIRED);
            }
            if (c.getIndentDelta() != 0 && prev.getIndentDelta() == 0) {
                prev.indentDelta(c.getIndentDelta());
            }
            if (c.getBreakPenalty() < prev.getBreakPenalty()) prev.breakPenalty(c.getBreakPenalty());
        }
    }

    private void requireNewline(int from, int to, int indentDelta) {
        addConstraint(new ConstraintSpec(from, to)
            .newline(ConstraintSpec.NewlineMode.REQUIRED).indentDelta(indentDelta));
    }

    // ── PACKAGE header: keep CREATE PACKAGE name IS together ──
    private void addPackageHeaderForbidden() {
        // Force FORBIDDEN between specific header keyword pairs
        // CREATE→PACKAGE, PACKAGE→name, name→IS
        // We look for CREATE and then track forward a few tokens
        for (int i = 0; i + 1 < tokens.size(); i++) {
            TokenInfo a = tokens.get(i);
            if (a.channel != 0) continue;
            String au = a.upper;
            if ("CREATE".equals(au) || "PACKAGE".equals(au)
                || "BODY".equals(au) || "OR".equals(au) || "REPLACE".equals(au)) {
                int j = nextVisible(i + 1);
                if (j < 0 || j >= tokens.size()) continue;
                if (bothOff(i, j)) continue;
                String key = gapKey(i, j);
                if (gapMap.containsKey(key)) continue;
                ConstraintSpec c = new ConstraintSpec(i, j)
                    .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(1, 1, 1);
                gapMap.put(key, c);
                constraints.add(c);
            }
        }
        // IS after package/function/procedure header: keep with preceding token
        for (int i = 0; i + 1 < tokens.size(); i++) {
            TokenInfo a = tokens.get(i);
            if (a.channel != 0) continue;
            int j = nextVisible(i + 1);
            if (j < 0 || j >= tokens.size()) continue;
            if (bothOff(i, j)) continue;
            TokenInfo b = tokens.get(j);
            if ("IS".equals(b.upper) || "AS".equals(b.upper)) {
                String key = gapKey(i, j);
                if (gapMap.containsKey(key)) continue;
                ConstraintSpec c = new ConstraintSpec(i, j)
                    .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(1, 1, 1);
                gapMap.put(key, c);
                constraints.add(c);
            }
        }
        // PROCEDURE and FUNCTION as single word - keep with following name (but next is often `;` or `(`)
        for (int i = 0; i + 1 < tokens.size(); i++) {
            TokenInfo a = tokens.get(i);
            if (a.channel != 0) continue;
            String au = a.upper;
            if ("PROCEDURE".equals(au) || "FUNCTION".equals(au)) {
                int j = nextVisible(i + 1);
                if (j < 0 || j >= tokens.size()) continue;
                if (bothOff(i, j)) continue;
                String key = gapKey(i, j);
                if (gapMap.containsKey(key)) continue;
                ConstraintSpec c = new ConstraintSpec(i, j)
                    .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(1, 1, 1);
                gapMap.put(key, c);
                constraints.add(c);
            }
        }
    }

    private void addSemicolonGaps(List<PlSqlBlock> blocks) {
        for (PlSqlBlock block : blocks) {
            addSemicolonGapsInBlock(block);
            for (PlSqlBlock child : block.children) {
                addSemicolonGaps(Collections.singletonList(child));
            }
        }
    }

    private void addSemicolonGapsInBlock(PlSqlBlock block) {
        if (block.startTokenIdx < 0 || block.endTokenIdx < 0) return;
        int end = Math.min(block.endTokenIdx, tokens.size() - 1);
        for (int i = block.startTokenIdx + 1; i <= end && i + 1 < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0 && ";".equals(ti.text)) {
                int next = i + 1;
                while (next < tokens.size() && tokens.get(next).channel == 1) next++;
                if (next < tokens.size() && !";".equals(tokens.get(next).text)) {
                    String key = gapKey(i, next);
                    if (!gapMap.containsKey(key)) {
                        requireNewline(i, next, 0);
                    }
                }
            }
        }
    }

    private void addForbiddenOperatorConstraints() {
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo a = tokens.get(i);
            if (a.channel != 0) continue;

            int j = i + 1;
            while (j < tokens.size() && tokens.get(j).channel != 0) j++;
            if (j >= tokens.size()) continue;
            TokenInfo b = tokens.get(j);

            // 1) Multi-char operator pairs: two separate tokens like | + | => ||
            String pair = a.text + b.text;
            if (OPERATOR_PAIRS.contains(pair)) {
                addConstraint(new ConstraintSpec(i, j)
                    .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(0, 0, 0));

                if (":=".equals(pair) || "||".equals(pair)) {
                    afterOperatorPositions.add(j);
                    int prev = prevVisible(i - 1);
                    if (prev >= 0) {
                        beforeOperatorPositions.add(prev);
                    }
                }

                if ("||".equals(pair)) {
                    int prev = prevVisible(i - 1);
                    if (prev >= 0) {
                        boolean isChain = false;
                        int scanIdx = prev - 1;
                        while (scanIdx >= 0) {
                            TokenInfo t = tokens.get(scanIdx);
                            if (t.channel == 0) {
                                if (";".equals(t.text)) break;
                                if ("|".equals(t.text)) {
                                    isChain = true;
                                    break;
                                }
                            }
                            scanIdx--;
                        }
                        String key = gapKey(prev, i);
                        if (!gapMap.containsKey(key)) {
                            ConstraintSpec opt = new ConstraintSpec(prev, i)
                                .newline(ConstraintSpec.NewlineMode.OPTIONAL)
                                .indentDelta(isChain ? 0 : 1)
                                .breakPenalty(0.3);
                            gapMap.put(key, opt);
                            constraints.add(opt);
                        }
                    }
                }

                if (":=".equals(pair)) {
                    int prev = prevVisible(i - 1);
                    if (prev >= 0) {
                        addConstraint(new ConstraintSpec(prev, i)
                            .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(1, 1, 1));
                    }
                }
                continue;
            }

            if ("<=".equals(a.text) || ">=".equals(a.text)
                || "=>".equals(a.text) || "<>".equals(a.text) || "!=".equals(a.text)) {
                addConstraint(new ConstraintSpec(i, j)
                    .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(0, 0, 0));
                continue;
            }

            if (":=".equals(a.text)) {
                addConstraint(new ConstraintSpec(i, j)
                    .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(1, 1, 1));
                int prev = prevVisible(i - 1);
                if (prev >= 0) {
                    addConstraint(new ConstraintSpec(prev, i)
                        .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(1, 1, 1));
                }
                continue;
            }

            // Standalone comparison operators
            if ("=".equals(a.text) || ">".equals(a.text) || "<".equals(a.text)) {
                if (!isPartOfMultiCharOp(a, i)) {
                    addConstraint(new ConstraintSpec(i, j)
                        .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(1, 1, 1));
                }
                continue;
            }

            if ("NOT".equals(a.upper)) {
                String nu = b.upper;
                if (NOT_KEYWORDS.contains(nu) || nu.equals("IN")) {
                    addConstraint(new ConstraintSpec(i, j)
                        .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(1, 1, 1));
                }
                continue;
            }

            if ("%".equals(a.text)) {
                if (PCT_ATTRIBUTES.contains(b.upper)) {
                    addConstraint(new ConstraintSpec(i, j)
                        .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(0, 0, 0));
                }
                int prev = prevVisible(i - 1);
                if (prev >= 0 && isIdentifier(tokens.get(prev))) {
                    addConstraint(new ConstraintSpec(prev, i)
                        .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(0, 0, 0));
                }
                continue;
            }

            if (";".equals(a.text) && opts.isSemicolonCompact()) {
                int prev = prevVisible(i - 1);
                if (prev >= 0) {
                    addConstraint(new ConstraintSpec(prev, i)
                        .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(0, 0, 0));
                }
            }

            if (".".equals(b.text) && isIdentifier(a)) {
                addConstraint(new ConstraintSpec(i, j)
                    .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(0, 0, 0));
            }
            if (".".equals(a.text) && isIdentifier(b)) {
                addConstraint(new ConstraintSpec(i, j)
                    .newline(ConstraintSpec.NewlineMode.FORBIDDEN).spaces(0, 0, 0));
            }
        }
    }

    private boolean isPartOfMultiCharOp(TokenInfo a, int idx) {
        if (!"=".equals(a.text) && !">".equals(a.text) && !"<".equals(a.text)) return false;
        int prev = prevVisible(idx - 1);
        if (prev < 0) return false;
        String pt = tokens.get(prev).text;
        if ("=".equals(a.text)) {
            return ":".equals(pt) || "<".equals(pt) || ">".equals(pt);
        }
        if (">".equals(a.text) || "<".equals(a.text)) {
            return "=".equals(pt);
        }
        return false;
    }

    private static boolean isIdentifier(TokenInfo ti) {
        if (ti.channel != 0) return false;
        if (ti.isStringLiteral) return false;
        String t = ti.text;
        if (t.isEmpty()) return false;
        char fc = t.charAt(0);
        if (Character.isDigit(fc)) return false;
        if ("'\"".indexOf(fc) >= 0) return false;
        return true;
    }

    private void addOptionalGaps() {
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo a = tokens.get(i);
            if (a.channel != 0) continue;
            if (bothOff(i, i)) continue;

            int j = i + 1;
            while (j < tokens.size() && tokens.get(j).channel != 0) j++;
            if (j >= tokens.size()) continue;
            if (bothOff(i, j)) continue;

            String key = gapKey(i, j);
            if (gapMap.containsKey(key)) continue;

            TokenInfo b = tokens.get(j);
            double penalty = computeBreakPenalty(a, b, i);
            ConstraintSpec c = new ConstraintSpec(i, j)
                .newline(ConstraintSpec.NewlineMode.OPTIONAL)
                .breakPenalty(penalty);
            gapMap.put(key, c);
            constraints.add(c);
        }
    }

    private double computeBreakPenalty(TokenInfo prev, TokenInfo next, int fromIdx) {
        String pt = prev.text;
        if (",".equals(pt)) return 0.2;
        if (beforeOperatorPositions.contains(fromIdx)) return 7.0;
        if (afterOperatorPositions.contains(fromIdx)) return 5.0;
        if ("AND".equals(pt) || "OR".equals(pt)) return 0.3;
        String nu = next.upper;
        if (Set.of("FROM","WHERE","ORDER","GROUP","HAVING","SET","VALUES","INTO","ON").contains(nu)) return 0.4;
        if (Set.of("UNION","INTERSECT","MINUS").contains(nu)) return 0.3;
        return 1.0;
    }
}
