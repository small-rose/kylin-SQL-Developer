package com.kylin.plsql.core.export;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ExportFormats {
    private static final List<ExportFormat> FORMATS = new CopyOnWriteArrayList<>();
    private static final Map<String, ExportFormat> BY_NAME = new ConcurrentHashMap<>();
    static {
        register(new InsertFormat());
        register(new CsvFormat());
        register(new JsonFormat());
        register(new XmlFormat());
        register(new MarkdownFormat());
    }
    public static void register(ExportFormat f) { FORMATS.add(f); BY_NAME.put(f.getName(), f); }
    public static ExportFormat get(String name) {
        ExportFormat f = BY_NAME.get(name); if (f == null) throw new IllegalArgumentException("不支持的格式: " + name); return f;
    }
    public static List<String> getNames() { List<String> n = new ArrayList<>(); for (ExportFormat f : FORMATS) n.add(f.getName()); return n; }
}
