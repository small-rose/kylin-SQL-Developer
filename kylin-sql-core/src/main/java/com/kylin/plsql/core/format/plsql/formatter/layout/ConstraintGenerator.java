package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.model.*;
import com.kylin.plsql.core.format.enums.ExceptionAlign;
import java.util.*;

public class ConstraintGenerator {

    private final FormatOptions opts;
    private final List<TokenInfo> tokens;
    private final List<GapConstraint> constraints = new ArrayList<>();
    private final Map<String, GapConstraint> gapMap = new LinkedHashMap<>();

    private static final Set<String> OPERATOR_PAIRS = Set.of(
        "||", "<<", ">>", "<=", ">=", "=>",
        "!=", "<>", "^=", "~=", ":="
    );

    private static final Set<String> PCT_ATTRIBUTES = Set.of(
        "TYPE", "ROWTYPE", "NOTFOUND", "FOUND", "ISOPEN", "ROWCOUNT",
        "BULK_ROWCOUNT", "BULK_EXCEPTIONS"
    );

    public ConstraintGenerator(FormatOptions opts, List<TokenInfo> tokens) {
        this.opts = opts;
        this.tokens = tokens;
    }

    public List<GapConstraint> generate(List<PlSqlBlock> topLevelBlocks) {
        constraints.clear();
        gapMap.clear();

        // 1) Structural constraints from block tree (recursive)
        for (PlSqlBlock block : topLevelBlocks) {
            walkRecursive(block);
        }

        // 2) Semicolons → newlines for all blocks
        addSemicolonGaps(topLevelBlocks);

        // 3) FORBIDDEN for multi-char operators + attribute references
        addForbiddenOperatorConstraints();

        // 4) := alignment in declarations
        addAlignConstraints(topLevelBlocks);

        // 5) OPTIONAL gaps at every remaining adjacent-token position
        addOptionalGaps();

        return new ArrayList<>(gapMap.values());
    }

    private void walkRecursive(PlSqlBlock block) {
        walkBlock(block, 0);
        for (PlSqlBlock child : block.children) {
            walkRecursive(child);
        }
    }

    // ──────────────────────────────────────────────
    //  Gap constraint helpers
    // ──────────────────────────────────────────────

    private String gapKey(int from, int to) {
        return from + ":" + to;
    }

    /** Find the next visible (channel-0) token at or after fromIdx. */
    private int nextVisible(int fromIdx) {
        int i = fromIdx;
        while (i < tokens.size() && tokens.get(i).channel != 0) i++;
        return i;
    }

    /** Find the previous visible (channel-0) token at or before fromIdx. */
    private int prevVisible(int fromIdx) {
        int i = fromIdx;
        while (i >= 0 && tokens.get(i).channel != 0) i--;
        return i;
    }

    private void addConstraint(GapConstraint c) {
        String key = gapKey(c.fromTokenIdx, c.toTokenIdx);
        GapConstraint prev = gapMap.get(key);
        if (prev == null) {
            gapMap.put(key, c);
            constraints.add(c);
        } else {
            if (c.newlineMode == GapConstraint.NewlineMode.REQUIRED
                && prev.newlineMode != GapConstraint.NewlineMode.REQUIRED) {
                prev.newlineMode = GapConstraint.NewlineMode.REQUIRED;
            }
            if (c.indentDelta != 0 && prev.indentDelta == 0) {
                prev.indentDelta = c.indentDelta;
            }
            if (c.breakPenalty < prev.breakPenalty) prev.breakPenalty = c.breakPenalty;
        }
    }

    private void requireNewline(int from, int to, int indentDelta) {
        addConstraint(new GapConstraint(from, to)
            .forceNewline(true).indentDelta(indentDelta));
    }

    private void requireNewlineEndAlign(int from, int to) {
        addConstraint(new GapConstraint(from, to)
            .forceNewline(true).endAlign(true));
    }

    // ──────────────────────────────────────────────
    //  Semicolons → newlines for every block
    // ──────────────────────────────────────────────

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

    // ──────────────────────────────────────────────
    //  Structural constraints per block type
    // ──────────────────────────────────────────────

    private void walkBlock(PlSqlBlock block, int parentIndent) {
        if (block.startTokenIdx < 0) return;

        if (block.type == PlSqlBlockType.IF)            { walkIfBlock(block); return; }
        if (block.type == PlSqlBlockType.CASE_BLOCK)    { walkCaseBlock(block); return; }
        if (block.type == PlSqlBlockType.LOOP
            || block.type == PlSqlBlockType.FOR_LOOP
            || block.type == PlSqlBlockType.WHILE_LOOP) { walkLoopBlock(block); return; }

        boolean hasHeader = block.headerEndTokenIdx >= 0
            && block.headerEndTokenIdx != block.startTokenIdx;
        int hed = block.headerEndTokenIdx;
        if (hasHeader && hed >= 0) {
            int next = nextVisible(hed + 1);
            if (next < tokens.size()) {
                requireNewline(hed, next, 1);
            }
        }

        int ss = block.stmtStartIdx;
        if (ss > 0 && ss < tokens.size()) {
            int beginIdx = prevVisible(ss - 1);
            if (beginIdx >= 0 && "BEGIN".equals(tokens.get(beginIdx).upper)) {
                int next = nextVisible(beginIdx + 1);
                if (next < tokens.size()) {
                    requireNewline(beginIdx, next, 1);
                }
            }
        }

        int exceptIdx = block.exceptionSection != null
            ? block.exceptionSection.startTokenIdx : -1;
        if (exceptIdx >= 0) {
            int next = nextVisible(exceptIdx + 1);
            if (next < tokens.size()) {
                boolean outdent = opts.getExceptionAlign() == ExceptionAlign.OUTDENT;
                requireNewline(exceptIdx, next, outdent ? 0 : 1);
            }
            for (ExceptionSection.Handler h : block.exceptionSection.handlers) {
                if (h.whenTokenIdx >= 0) {
                    int wn = nextVisible(h.whenTokenIdx + 1);
                    if (wn < tokens.size()) {
                        requireNewline(h.whenTokenIdx, wn, 0);
                    }
                }
            }
        }

        addEndConstraint(block);

        // Pop indent at the block's closing ;
        int ei = block.endTokenIdx;
        if (ei >= 0 && ei < tokens.size() && ";".equals(tokens.get(ei).text)) {
            int en = nextVisible(ei + 1);
            if (en < tokens.size()) {
                requireNewline(ei, en, -1);
            }
        }
    }

    private void addEndConstraint(PlSqlBlock block) {
        if (block.endTokenIdx < 0) return;
        int endKw = findEndKeyword(block.endTokenIdx);
        if (endKw < 0) return;

        // Pop indent at the gap before END (from the last statement's ;)cd
        int prevSemi = findPrevSemicolon(endKw);
        if (prevSemi >= 0) {
            requireNewline(prevSemi, endKw, -1);
        }
    }

    /** Find the last ; before endKw (channel 0) */
    private int findPrevSemicolon(int endKw) {
        for (int i = endKw - 1; i >= 0; i--) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0 && ";".equals(ti.text)) return i;
        }
        return -1;
    }

    private void walkIfBlock(PlSqlBlock block) {
        for (int bi = 0; bi < block.ifBranches.size(); bi++) {
            IfBranch branch = block.ifBranches.get(bi);
            if (branch.thenTokenIdx >= 0) {
                int tn = nextVisible(branch.thenTokenIdx + 1);
                if (tn < tokens.size()) {
                    requireNewline(branch.thenTokenIdx, tn, 1);
                }
            }
            if (bi > 0 && branch.conditionStartIdx > 0) {
                int kwPos = prevVisible(branch.conditionStartIdx - 1);
                if (kwPos >= 0) {
                    int kn = nextVisible(kwPos + 1);
                    if (kn < tokens.size()) {
                        requireNewline(kwPos, kn, -1);
                    }
                }
            }
        }
        if (block.elseStmtStartIdx >= 0) {
            int en = nextVisible(block.elseStmtStartIdx + 1);
            if (en < tokens.size()) {
                requireNewline(block.elseStmtStartIdx, en, -1);
            }
        }
        addEndConstraint(block);
    }

    private void walkLoopBlock(PlSqlBlock block) {
        int headerEnd = findLoopHeaderEnd(block);
        if (headerEnd >= 0) {
            int hn = nextVisible(headerEnd + 1);
            if (hn < tokens.size()) {
                requireNewline(headerEnd, hn, 1);
            }
        }
        addEndConstraint(block);
    }

    private void walkCaseBlock(PlSqlBlock block) {
        for (var cw : block.caseWhens) {
            if (cw.thenTokenIdx >= 0) {
                int tn = nextVisible(cw.thenTokenIdx + 1);
                if (tn < tokens.size()) {
                    requireNewline(cw.thenTokenIdx, tn, 1);
                }
            }
        }
        if (block.elseStmtStartIdx >= 0) {
            int en = nextVisible(block.elseStmtStartIdx + 1);
            if (en < tokens.size()) {
                requireNewline(block.elseStmtStartIdx, en, 0);
            }
        }
        addEndConstraint(block);
    }

    // ──────────────────────────────────────────────
    //  FORBIDDEN constraints: operators + %/. refs
    // ──────────────────────────────────────────────

    private void addForbiddenOperatorConstraints() {
        for (int i = 0; i < tokens.size() - 1; i++) {
            TokenInfo a = tokens.get(i);
            TokenInfo b = tokens.get(i + 1);
            if (a.channel != 0 || b.channel != 0) continue;

            String pair = a.text + b.text;

            // Multi-char operators (||, :=, <=, …)
            if (OPERATOR_PAIRS.contains(pair)) {
                GapConstraint c = new GapConstraint(i, i + 1)
                    .forceNewline(false).spaces(0, 0, 0);
                addConstraint(c);
                continue;
            }

            // % followed by TYPE / ROWTYPE / … table.column%TYPE
            if ("%".equals(a.text) && PCT_ATTRIBUTES.contains(b.upper)) {
                GapConstraint c = new GapConstraint(i, i + 1)
                    .forceNewline(false).spaces(0, 0, 0);
                addConstraint(c);
                continue;
            }

            // . followed by identifier  table . column → table.column
            if (".".equals(a.text) && isIdentifier(b)) {
                GapConstraint c = new GapConstraint(i, i + 1)
                    .forceNewline(false).spaces(0, 0, 0);
                addConstraint(c);
                continue;
            }

            // identifier . identifier (no space before .)
            if (".".equals(b.text) && isIdentifier(a)) {
                GapConstraint c = new GapConstraint(i, i + 1)
                    .forceNewline(false).spaces(0, 0, 0);
                addConstraint(c);
            }
        }
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

    // ──────────────────────────────────────────────
    //  := alignment in DECLARE sections
    // ──────────────────────────────────────────────

    private void addAlignConstraints(List<PlSqlBlock> blocks) {
        for (PlSqlBlock block : blocks) {
            addAlignInBlock(block);
            for (PlSqlBlock child : block.children) {
                addAlignConstraints(Collections.singletonList(child));
            }
        }
    }

    private void addAlignInBlock(PlSqlBlock block) {
        if (block.declarations.isEmpty()) return;
        int assignCount = 0;
        String groupId = "assign_" + block.startTokenIdx;

        // First pass: scan for := and emit FORBIDDEN + alignment markers
        for (Declaration decl : block.declarations) {
            int colonIdx = findAssignColon(decl.startTokenIdx, decl.endTokenIdx);
            if (colonIdx < 0) continue;
            assignCount++;

            // FORBIDDEN between : and =
            addConstraint(new GapConstraint(colonIdx, colonIdx + 1)
                .forceNewline(false).spaces(0, 0, 0));

            // Alignment constraint on the gap before ":"
            if (colonIdx > 0) {
                GapConstraint ac = new GapConstraint(colonIdx - 1, colonIdx);
                ac.alignGroupId = groupId;
                addConstraint(ac);
            }
        }

        // Second pass: rewrite structural constraints with alignGroupId
        // (already done above via addConstraint)
    }

    /** Find the position of the colon in := within [from, to]. */
    private int findAssignColon(int from, int to) {
        for (int i = from; i <= to && i + 1 < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if (!":".equals(ti.text)) continue;
            TokenInfo tj = tokens.get(i + 1);
            if (tj.channel == 0 && "=".equals(tj.text)) return i;
        }
        return -1;
    }

    // ──────────────────────────────────────────────
    //  OPTIONAL constraints for line-width DP
    // ──────────────────────────────────────────────

    private void addOptionalGaps() {
        for (int i = 0; i < tokens.size() - 1; i++) {
            TokenInfo a = tokens.get(i);
            TokenInfo b = tokens.get(i + 1);
            if (a.channel != 0 || b.channel != 0) continue;

            String key = gapKey(i, i + 1);
            if (gapMap.containsKey(key)) continue;

            double penalty = computeBreakPenalty(a, b);
            GapConstraint c = new GapConstraint(i, i + 1)
                .breakPenalty(penalty);
            c.newlineMode = GapConstraint.NewlineMode.OPTIONAL;
            gapMap.put(key, c);
            constraints.add(c);
        }
    }

    private static double computeBreakPenalty(TokenInfo prev, TokenInfo next) {
        String pt = prev.text;
        String nt = next.text;
        // Prefer breaking at commas
        if (",".equals(pt)) return 0.2;
        // After keywords like AND/OR
        if ("AND".equals(pt) || "OR".equals(pt)) return 0.3;
        // Before keywords like FROM, WHERE, ORDER, GROUP, …
        String nu = next.upper;
        if (Set.of("FROM","WHERE","ORDER","GROUP","HAVING","SET","VALUES","INTO","ON").contains(nu)) return 0.4;
        // Before union operators
        if (Set.of("UNION","INTERSECT","MINUS").contains(nu)) return 0.3;
        return 1.0;
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private int findEndKeyword(int endTokenIdx) {
        if (endTokenIdx < 0 || endTokenIdx >= tokens.size()) return -1;
        for (int i = endTokenIdx; i >= 0; i--) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if ("END".equals(ti.upper)) return i;
        }
        for (int i = endTokenIdx; i >= 0; i--) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0) return i;
        }
        return endTokenIdx;
    }

    private int findLoopHeaderEnd(PlSqlBlock block) {
        if (block.headerEndTokenIdx >= 0) return block.headerEndTokenIdx;
        int limit = block.endTokenIdx > 0 ? block.endTokenIdx : tokens.size() - 1;
        for (int i = block.startTokenIdx + 1; i <= limit && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if ("LOOP".equals(ti.upper)) return i;
        }
        return block.startTokenIdx;
    }
}
