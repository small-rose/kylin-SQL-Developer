package com.kylin.plsql.core.parser;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Cross-file symbol index for PL/SQL identifiers from local and database sources. */
public class PlSqlSymbolIndex {

    public static class SymbolEntry {
        public final String objectName;
        public final String subObjectName;
        public final String type;
        public final int line;
        public final List<SymbolRef> references = new ArrayList<>();

        public SymbolEntry(String objectName, String subObjectName, String type, int line) {
            this.objectName = objectName;
            this.subObjectName = subObjectName;
            this.type = type;
            this.line = line;
        }
    }

    public static class SymbolRef {
        public final String context;
        public final int line;

        public SymbolRef(String context, int line) {
            this.context = context;
            this.line = line;
        }
    }

    private final Map<String, SymbolEntry> localIndex = new LinkedHashMap<>();
    private final Map<String, SymbolEntry> globalIndex = new LinkedHashMap<>();
    private boolean dbIndexed;

    public void indexLocal(String plsqlSource, List<PlSqlNavigator.OutlineEntry> entries) {
        localIndex.clear();
        if (entries == null || plsqlSource == null) return;

        String[] lines = plsqlSource.split("\n", -1);

        for (var entry : entries) {
            String key = entry.name.toUpperCase();
            var sym = new SymbolEntry("LOCAL", entry.name, entry.type, entry.line);

            // Find references to this symbol within the source
            if (entry.type.equals("FUNCTION") || entry.type.equals("PROCEDURE")) {
                int endLine = findEndLine(entries, entry, lines.length);
                for (int i = 0; i < lines.length; i++) {
                    if (i >= entry.line - 1 && i < endLine - 1) continue; // skip own body
                    if (lines[i].toUpperCase().contains(key)) {
                        sym.references.add(new SymbolRef("line " + (i + 1), i + 1));
                    }
                }
            }

            localIndex.put(key, sym);
        }
    }

    public void indexDatabase(Connection conn, String schema) throws SQLException {
        globalIndex.clear();
        dbIndexed = true;

        String sql = "SELECT NAME, TYPE, LINE, TEXT FROM ALL_SOURCE WHERE OWNER = ? ORDER BY NAME, TYPE, LINE";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema != null ? schema.toUpperCase() : conn.getSchema());
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("NAME");
                    String type = rs.getString("TYPE");
                    int line = rs.getInt("LINE");
                    String text = rs.getString("TEXT");

                    String key = name.toUpperCase();
                    if (!globalIndex.containsKey(key)) {
                        globalIndex.put(key, new SymbolEntry(name, null, type, line));
                    }
                }
            }
        }
    }

    public SymbolEntry find(String name) {
        String key = name.toUpperCase();
        var local = localIndex.get(key);
        if (local != null) return local;
        return globalIndex.get(key);
    }

    public Collection<SymbolEntry> getLocalSymbols() {
        return Collections.unmodifiableCollection(localIndex.values());
    }

    public boolean isDbIndexed() { return dbIndexed; }

    private int findEndLine(List<PlSqlNavigator.OutlineEntry> entries,
                             PlSqlNavigator.OutlineEntry entry, int maxLine) {
        int nextLine = maxLine;
        boolean foundSelf = false;
        for (var e : entries) {
            if (e.equals(entry)) { foundSelf = true; continue; }
            if (foundSelf && e.line > entry.line) { nextLine = e.line; break; }
        }
        return nextLine;
    }
}
