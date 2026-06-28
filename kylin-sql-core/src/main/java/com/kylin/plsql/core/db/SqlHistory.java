package com.kylin.plsql.core.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SqlHistory {
    private static final int MAX_SIZE = 200;
    private final List<String> entries = new ArrayList<>();

    public void add(String sql) {
        if (sql == null || sql.isBlank()) return;
        int idx = entries.indexOf(sql);
        if (idx >= 0) entries.remove(idx);
        entries.add(sql);
        if (entries.size() > MAX_SIZE) entries.remove(0);
    }

    public List<String> getAll() {
        return Collections.unmodifiableList(entries);
    }

    public List<String> getRecent(int n) {
        int size = entries.size();
        int from = Math.max(0, size - n);
        return entries.subList(from, size);
    }

    public void clear() { entries.clear(); }
}
