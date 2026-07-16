package com.kylin.plsql.core.config;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.*;

public class FontManager {
    private static FontManager instance;
    private final Map<String, String> overrides = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private String[] allFonts;

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("font.editor",   "Monospaced,14");
        DEFAULTS.put("font.table",    "Monospaced,12");
        DEFAULTS.put("font.mono",     "Monospaced,12");
        DEFAULTS.put("font.ui",       "Segoe UI,12");
        DEFAULTS.put("font.ui.bold",  "Segoe UI,12");
        DEFAULTS.put("font.status",   "Segoe UI,11");
        DEFAULTS.put("font.tab",      "Segoe UI,10");
    }

    private static final Map<String, String> KEY_LABELS = new LinkedHashMap<>();
    static {
        KEY_LABELS.put("font.editor",   "代码编辑器");
        KEY_LABELS.put("font.table",    "结果表");
        KEY_LABELS.put("font.mono",     "等宽辅助");
        KEY_LABELS.put("font.ui",       "通用界面");
        KEY_LABELS.put("font.ui.bold",  "粗体标题");
        KEY_LABELS.put("font.status",   "状态栏");
        KEY_LABELS.put("font.tab",      "标签栏");
    }

    private static final Map<String, String> PREVIEW_TEXTS = new LinkedHashMap<>();
    static {
        PREVIEW_TEXTS.put("font.editor",   "-- 查询当月订单\nSELECT * FROM orders");
        PREVIEW_TEXTS.put("font.table",    "-- 用户名      状态\n张三    ACTIVE");
        PREVIEW_TEXTS.put("font.mono",     "AaBbCc 你好世界 12345");
        PREVIEW_TEXTS.put("font.ui",       "AaBbCc 你好世界 12345");
        PREVIEW_TEXTS.put("font.ui.bold",  "AaBbCc 你好世界 12345");
        PREVIEW_TEXTS.put("font.status",   "AaBbCc 你好世界 12345");
        PREVIEW_TEXTS.put("font.tab",      "AaBbCc 你好世界");
    }

    private static final String CHINESE_TEST = "\u4f60\u597d"; // 你好

    public static synchronized FontManager getInstance() {
        if (instance == null) instance = new FontManager();
        return instance;
    }

    public Font resolve(String key) {
        String val = overrides.getOrDefault(key, DEFAULTS.get(key));
        if (val == null) return new Font("Dialog", Font.PLAIN, 12);
        String[] parts = val.split(",");
        String name = parts[0].trim();
        int size = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 12;
        int style = "font.ui.bold".equals(key) ? Font.BOLD : Font.PLAIN;
        return new Font(name, style, size);
    }

    public String resolveValue(String key) {
        return overrides.getOrDefault(key, DEFAULTS.get(key));
    }

    public String[] getAllFonts() {
        if (allFonts == null) {
            allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
        }
        return allFonts;
    }

    public static String getFontLabel(String fontName) {
        Font f = new Font(fontName, Font.PLAIN, 12);
        boolean mono = f.getFontName().toLowerCase().contains("mono")
                || f.getFontName().toLowerCase().contains("console")
                || f.getFontName().toLowerCase().contains("courier");
        boolean cn = f.canDisplayUpTo(CHINESE_TEST) == -1;
        StringBuilder label = new StringBuilder(fontName);
        if (mono) label.append("  [\u7b49\u5bbd]");    // [等宽]
        else if (cn) label.append("  [\u4e2d\u6587]"); // [中文]
        else label.append("  [\u5b88\u5219]");          // [默认]
        return label.toString();
    }

    public static boolean isMonospace(String fontName) {
        Font f = new Font(fontName, Font.PLAIN, 12);
        return f.getFontName().toLowerCase().contains("mono")
                || f.getFontName().toLowerCase().contains("console")
                || f.getFontName().toLowerCase().contains("courier");
    }

    public static boolean supportsChinese(String fontName) {
        return new Font(fontName, Font.PLAIN, 12).canDisplayUpTo(CHINESE_TEST) == -1;
    }

    public Map<String, String> getOverrideMap() { return new HashMap<>(overrides); }

    public void setOverride(String key, String value) { overrides.put(key, value); }

    public void removeOverride(String key) { overrides.remove(key); }

    public void clearOverrides() { overrides.clear(); }

    public void loadFromConfig(ConfigManager config) {
        overrides.clear();
        var ws = config.loadWorkspace();
        if (ws.fontOverrides != null) overrides.putAll(ws.fontOverrides);
    }

    public void saveToConfig(ConfigManager config) {
        var ws = config.loadWorkspace();
        ws.fontOverrides = new HashMap<>(overrides);
        config.saveWorkspace(ws);
    }

    public void addListener(Runnable r) { listeners.add(r); }

    public void fireListeners() {
        for (Runnable r : listeners) r.run();
    }

    public static Set<String> getKeys() { return DEFAULTS.keySet(); }

    public static String getDefault(String key) { return DEFAULTS.get(key); }

    public static String getLabel(String key) { return KEY_LABELS.get(key); }

    public static String getPreviewText(String key) { return PREVIEW_TEXTS.get(key); }
}
