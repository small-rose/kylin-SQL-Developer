package com.kylin.plsql.core.format.plsql.formatter.layout.block;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.enums.ExceptionAlign;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.*;
import java.util.*;

public final class BlockConstraintUtil {

    private BlockConstraintUtil() {}

    public static int nextVisible(List<TokenInfo> tokens, int from, int endBound) {
        int limit = Math.min(endBound, tokens.size() - 1);
        for (int i = from; i <= limit; i++) {
            if (tokens.get(i).channel == 0) return i;
        }
        return -1;
    }

    public static int prevVisible(List<TokenInfo> tokens, int from, int endBound) {
        for (int i = from; i >= endBound && i >= 0; i--) {
            if (tokens.get(i).channel == 0) return i;
        }
        return -1;
    }

    public static int findKeyword(List<TokenInfo> tokens, int start, int end, String keyword) {
        int limit = Math.min(end, tokens.size() - 1);
        for (int i = start; i <= limit; i++) {
            if (tokens.get(i).channel == 0 && keyword.equals(tokens.get(i).upper)) return i;
        }
        return -1;
    }

    public static int findEndKeyword(List<TokenInfo> tokens, int endTokenIdx) {
        if (endTokenIdx < 0 || endTokenIdx >= tokens.size()) return -1;
        for (int i = endTokenIdx; i >= 0; i--) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) continue;
            if ("END".equals(ti.upper)) return i;
        }
        return -1;
    }

    public static int findPrevSemicolon(List<TokenInfo> tokens, int fromIdx) {
        for (int i = fromIdx - 1; i >= 0; i--) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0 && ";".equals(ti.text)) return i;
        }
        return -1;
    }

    public static boolean isIdentifier(TokenInfo ti) {
        if (ti.channel != 0) return false;
        if (ti.isStringLiteral) return false;
        String t = ti.text;
        if (t.isEmpty()) return false;
        char fc = t.charAt(0);
        if (Character.isDigit(fc)) return false;
        if ("'\"".indexOf(fc) >= 0) return false;
        return true;
    }

    public static void addBlockGap(List<TokenInfo> tokens, List<ConstraintSpec> constraints,
                                    Map<String, ConstraintSpec> gapMap,
                                    int from, int to,
                                    ConstraintSpec.NewlineMode mode, int indentDelta) {
        if (from < 0 || to < 0 || from >= tokens.size() || to >= tokens.size()) return;
        String key = from + ":" + to;
        ConstraintSpec existing = gapMap.get(key);
        if (existing != null) {
            if (mode == ConstraintSpec.NewlineMode.REQUIRED
                && existing.getNewlineMode() != ConstraintSpec.NewlineMode.REQUIRED) {
                existing.newline(ConstraintSpec.NewlineMode.REQUIRED);
            }
            if (indentDelta != 0 && existing.getIndentDelta() == 0) {
                existing.indentDelta(indentDelta);
            }
            return;
        }
        ConstraintSpec c = new ConstraintSpec(from, to);
        c.newline(mode);
        c.indentDelta(indentDelta);
        gapMap.put(key, c);
        constraints.add(c);
    }

    public static void addForbiddenGap(List<TokenInfo> tokens, List<ConstraintSpec> constraints,
                                        Map<String, ConstraintSpec> gapMap,
                                        int from, int to, int spaces) {
        if (from < 0 || to < 0 || from >= tokens.size() || to >= tokens.size()) return;
        String key = from + ":" + to;
        if (gapMap.containsKey(key)) return;
        ConstraintSpec c = new ConstraintSpec(from, to)
            .newline(ConstraintSpec.NewlineMode.FORBIDDEN)
            .spaces(spaces, spaces, spaces);
        gapMap.put(key, c);
        constraints.add(c);
    }

    public static ConstraintSpec gapConstraint(Map<String, ConstraintSpec> gapMap,
                                               int from, int to) {
        String key = from + ":" + to;
        ConstraintSpec existing = gapMap.get(key);
        if (existing != null) return existing;
        ConstraintSpec c = new ConstraintSpec(from, to);
        gapMap.put(key, c);
        return c;
    }

    public static void addEndConstraint(List<TokenInfo> tokens, List<ConstraintSpec> constraints,
                                          Map<String, ConstraintSpec> gapMap,
                                          PlSqlBlock block) {
        if (block.endTokenIdx < 0) return;
        int endKw = findEndKeyword(tokens, block.endTokenIdx);
        if (endKw < 0) return;
        int prevSemi = findPrevSemicolon(tokens, endKw);
        if (prevSemi >= 0) {
            addBlockGap(tokens, constraints, gapMap, prevSemi, endKw,
                ConstraintSpec.NewlineMode.REQUIRED, -1);
        }
    }

    public static void addExceptionSectionConstraints(FormatOptions opts,
                                                        List<TokenInfo> tokens,
                                                        List<ConstraintSpec> constraints,
                                                        Map<String, ConstraintSpec> gapMap,
                                                        PlSqlBlock block) {
        if (block.exceptionSection == null) return;
        int exceptIdx = block.exceptionSection.startTokenIdx;
        int endBound = block.endTokenIdx;
        if (exceptIdx < 0) return;

        // Pop before EXCEPTION: ;last_begin_body_stmt → EXCEPTION with delta=-1
        // This undoes the BEGIN→stmt push so EXCEPTION/WHEN are at header level
        int prevSemi = findPrevSemicolon(tokens, exceptIdx);
        if (prevSemi >= 0) {
            addBlockGap(tokens, constraints, gapMap, prevSemi, exceptIdx,
                ConstraintSpec.NewlineMode.REQUIRED, -1);
        }

        int next = nextVisible(tokens, exceptIdx + 1, endBound);
        if (next >= 0) {
            boolean outdent = opts.getExceptionAlign() == ExceptionAlign.OUTDENT;
            addBlockGap(tokens, constraints, gapMap, exceptIdx, next,
                ConstraintSpec.NewlineMode.REQUIRED, 0);
        }
        for (ExceptionSection.Handler h : block.exceptionSection.handlers) {
            if (h.whenTokenIdx >= 0) {
                int wn = nextVisible(tokens, h.whenTokenIdx + 1, endBound);
                if (wn >= 0) {
                    addForbiddenGap(tokens, constraints, gapMap,
                        h.whenTokenIdx, wn, 1);
                }
            }
            if (h.thenTokenIdx >= 0) {
                int tn = nextVisible(tokens, h.thenTokenIdx + 1, endBound);
                if (tn >= 0) {
                    addBlockGap(tokens, constraints, gapMap,
                        h.thenTokenIdx, tn,
                        ConstraintSpec.NewlineMode.REQUIRED, 1);
                }
            }
        }
    }

    public static void iterateBlocks(List<PlSqlBlock> topLevelBlocks,
                                      BlockHandler handler) {
        for (PlSqlBlock block : topLevelBlocks) {
            visitBlockDeep(block, handler);
        }
    }

    private static void visitBlockDeep(PlSqlBlock block, BlockHandler handler) {
        handler.handle(block);
        for (PlSqlBlock child : block.children) {
            visitBlockDeep(child, handler);
        }
    }

    public interface BlockHandler {
        void handle(PlSqlBlock block);
    }
}
