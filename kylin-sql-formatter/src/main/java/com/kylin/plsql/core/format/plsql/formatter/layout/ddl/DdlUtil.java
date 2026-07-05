package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class DdlUtil {

    private DdlUtil() {}

    public static String firstKeyword(List<TokenInfo> tokens, int start, int end) {
        for (int i = start; i <= end && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0) return ti.upper;
        }
        return null;
    }

    public static String secondKeyword(List<TokenInfo> tokens, int start, int end) {
        boolean foundFirst = false;
        for (int i = start; i <= end && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) continue;
            if (!foundFirst) { foundFirst = true; continue; }
            return ti.upper;
        }
        return null;
    }

    public static String nextKeyword(List<TokenInfo> tokens, int from, int end) {
        for (int i = from; i <= end && i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 0) return ti.upper;
        }
        return null;
    }

    public static int nextVisible(List<TokenInfo> tokens, int from, int end) {
        for (int i = from; i <= end && i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0) return i;
        }
        return -1;
    }

    public static int prevVisible(List<TokenInfo> tokens, int from, int end) {
        for (int i = from; i >= end && i >= 0; i--) {
            if (tokens.get(i).channel == 0) return i;
        }
        return -1;
    }

    public static int nextVisibleKw(List<TokenInfo> tokens, int from, int end, String keyword) {
        for (int i = from; i <= end && i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0 && keyword.equals(tokens.get(i).upper)) return i;
        }
        return -1;
    }

    public static int findKeyword(List<TokenInfo> tokens, int start, int end, String keyword) {
        for (int i = start; i <= end && i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0 && keyword.equals(tokens.get(i).upper)) return i;
        }
        return -1;
    }

    public static int findAnyKeyword(List<TokenInfo> tokens, int start, int end, String... keywords) {
        Set<String> kwSet = new HashSet<>(Arrays.asList(keywords));
        for (int i = start; i <= end && i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0 && kwSet.contains(tokens.get(i).upper)) return i;
        }
        return -1;
    }

    public static int findChar(List<TokenInfo> tokens, int start, int end, char ch) {
        String s = String.valueOf(ch);
        for (int i = start; i <= end && i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0 && s.equals(tokens.get(i).text)) return i;
        }
        return -1;
    }

    public static int findLastChar(List<TokenInfo> tokens, int start, int end, char ch) {
        String s = String.valueOf(ch);
        int last = -1;
        for (int i = start; i <= end && i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0 && s.equals(tokens.get(i).text)) last = i;
        }
        return last;
    }

    public static int findMatchingParen(List<TokenInfo> tokens, int openParen, int end) {
        int depth = 1;
        for (int i = openParen + 1; i <= end && i < tokens.size(); i++) {
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

    public static int findLobEnd(List<TokenInfo> tokens, int lobIdx, int end) {
        int lastParen = -1;
        for (int i = lobIdx; i <= end && i < tokens.size(); i++) {
            if (tokens.get(i).channel == 0 && ")".equals(tokens.get(i).text)) {
                lastParen = i;
            }
        }
        return lastParen > 0 ? lastParen : end;
    }

    public static int findMViewNameEnd(List<TokenInfo> tokens, int start, int end) {
        int viewKw = findKeyword(tokens, start, end, "VIEW");
        if (viewKw < 0) return -1;
        return nextVisible(tokens, viewKw + 1, end);
    }

    public static int nextAlignGroup(Map<String, ConstraintSpec> gapMap) {
        return gapMap.size();
    }

    public static void addDdlGap(List<TokenInfo> tokens, List<ConstraintSpec> constraints,
                                   Map<String, ConstraintSpec> gapMap,
                                   int from, int to,
                                   ConstraintSpec.NewlineMode mode, int indentDelta) {
        if (from < 0 || to < 0 || from >= tokens.size() || to >= tokens.size()) return;
        String key = from + ":" + to;
        if (gapMap.containsKey(key)) {
            ConstraintSpec existing = gapMap.get(key);
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

    public static ConstraintSpec gapConstraint(Map<String, ConstraintSpec> gapMap,
                                                int from, int to) {
        String key = from + ":" + to;
        ConstraintSpec existing = gapMap.get(key);
        if (existing != null) return existing;
        ConstraintSpec c = new ConstraintSpec(from, to);
        gapMap.put(key, c);
        return c;
    }

    public static void forceSimpleLayout(List<TokenInfo> tokens,
                                           List<ConstraintSpec> constraints,
                                           Map<String, ConstraintSpec> gapMap,
                                           int start, int end) {
        for (int i = start; i < end && i < tokens.size() - 1; i++) {
            TokenInfo a = tokens.get(i);
            TokenInfo b = tokens.get(i + 1);
            if (a.channel != 0 || b.channel != 0) continue;
            addDdlGap(tokens, constraints, gapMap, i, i + 1,
                ConstraintSpec.NewlineMode.FORBIDDEN, 0);
        }
    }

    public static void formatParenOpts(List<TokenInfo> tokens,
                                         List<ConstraintSpec> constraints,
                                         Map<String, ConstraintSpec> gapMap,
                                         int openParen, int closeParen,
                                         String alignGroupId, boolean indent) {
        int first = nextVisible(tokens, openParen + 1, closeParen);
        if (first < 0) return;
        if (indent) {
            addDdlGap(tokens, constraints, gapMap, openParen, first,
                ConstraintSpec.NewlineMode.REQUIRED, 1);
        }
        int i = first;
        while (i <= closeParen) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) { i++; continue; }
            if (",".equals(ti.text)) {
                int next = nextVisible(tokens, i + 1, closeParen);
                if (next >= 0 && next <= closeParen) {
                    addDdlGap(tokens, constraints, gapMap, i, next,
                        ConstraintSpec.NewlineMode.REQUIRED, 0);
                    if (alignGroupId != null) {
                        ConstraintSpec ag = gapConstraint(gapMap, next, nextVisible(tokens, next + 1, closeParen));
                        ag.alignGroup(alignGroupId);
                        constraints.add(ag);
                    }
                }
                i++;
                continue;
            }
            if (")".equals(ti.text)) {
                int prev = prevVisible(tokens, i - 1, openParen);
                if (prev >= 0 && indent) {
                    addDdlGap(tokens, constraints, gapMap, prev, i,
                        ConstraintSpec.NewlineMode.REQUIRED, -1);
                }
                break;
            }
            i++;
        }
    }

    public static int formatOptionsPerLine(List<TokenInfo> tokens,
                                             List<ConstraintSpec> constraints,
                                             Map<String, ConstraintSpec> gapMap,
                                             int start, int end,
                                             String alignGroupId,
                                             Set<String> optionKeywords) {
        int i = start;
        while (i <= end) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel != 0) { i++; continue; }
            String u = ti.upper;
            if (optionKeywords.contains(u)) {
                int next = nextVisible(tokens, i + 1, end);
                if (next > i && next <= end) {
                    if (i > start) {
                        addDdlGap(tokens, constraints, gapMap, prevVisible(tokens, i - 1, start), i,
                            ConstraintSpec.NewlineMode.REQUIRED, 0);
                    }
                    if (alignGroupId != null) {
                        ConstraintSpec g = gapConstraint(gapMap, i, next);
                        g.alignGroup(alignGroupId);
                        constraints.add(g);
                    }
                }
                i = next + 1;
                continue;
            }
            i++;
        }
        return end;
    }
}
