package com.kylin.plsql.core.parser;

import java.util.*;
import java.util.regex.Pattern;

/** Builds PL/SQL call hierarchy tree from outline entries. */
public class PlSqlCallHierarchy {

    public static class CallNode {
        public final String name;
        public final int line;
        public final List<CallNode> callees = new ArrayList<>();

        public CallNode(String name, int line) {
            this.name = name;
            this.line = line;
        }
    }

    private static final Pattern CALL_PATTERN = Pattern.compile(
        "(?i)\\b(?:call|exec(?:ute)?)\\s+(\\w+(?:\\.\\w+)?)"
    );
    private static final Pattern INVOKE_PATTERN = Pattern.compile(
        "(?i)\\b(\\w+)\\s*\\("
    );

    public static CallNode buildCallTree(List<PlSqlNavigator.OutlineEntry> entries, String source) {
        if (entries == null || entries.isEmpty()) return new CallNode("ROOT", 0);

        var root = new CallNode("ROOT", 0);
        Map<String, CallNode> nodeMap = new LinkedHashMap<>();

        for (var entry : entries) {
            if (!"FUNCTION".equals(entry.type) && !"PROCEDURE".equals(entry.type)) continue;
            var node = new CallNode(entry.name, entry.line);
            nodeMap.put(entry.name, node);
            root.callees.add(node);
        }

        String[] lines = source.split("\n", -1);
        for (var entry : entries) {
            if (!"FUNCTION".equals(entry.type) && !"PROCEDURE".equals(entry.type)) continue;
            var caller = nodeMap.get(entry.name);
            if (caller == null) continue;

            int endLine = findBodyEnd(entries, entry, lines.length);
            for (int i = entry.line; i < endLine && i < lines.length; i++) {
                String line = lines[i];
                var m = INVOKE_PATTERN.matcher(line);
                while (m.find()) {
                    String calleeName = m.group(1);
                    CalleeType ct = classifyToken(line, m.start(), lines[i]);
                    if (ct == CalleeType.FLOW) continue;
                    var callee = nodeMap.get(calleeName);
                    if (callee != null && !callee.equals(caller)) {
                        if (caller.callees.stream().noneMatch(c -> c.name.equals(calleeName))) {
                            caller.callees.add(callee);
                        }
                    }
                }
            }
        }

        return root;
    }

    private enum CalleeType { FLOW, CALL }

    private static CalleeType classifyToken(String line, int pos, String fullLine) {
        String before = line.substring(0, pos).trim().toUpperCase();
        if (before.endsWith("IF") || before.endsWith("ELSIF") || before.endsWith("WHEN")
            || before.endsWith("LOOP") || before.endsWith("WHILE") || before.endsWith("THEN")
            || before.endsWith("CASE")) {
            return CalleeType.FLOW;
        }
        return CalleeType.CALL;
    }

    private static int findBodyEnd(List<PlSqlNavigator.OutlineEntry> entries,
                                    PlSqlNavigator.OutlineEntry entry, int maxLine) {
        int nextLine = maxLine;
        boolean foundSelf = false;
        for (var e : entries) {
            if (e.equals(entry)) {
                foundSelf = true;
                continue;
            }
            if (foundSelf && e.line > entry.line) {
                nextLine = e.line;
                break;
            }
        }
        return nextLine;
    }
}
