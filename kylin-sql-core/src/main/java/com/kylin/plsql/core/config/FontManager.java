package com.kylin.plsql.core.config;

import java.awt.Color;
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
        putDefault("font.default",           "Microsoft YaHei UI,12");
        putDefault("font.top",               null);  // → font.default
        putDefault("font.left",              null);  // → font.default
        putDefault("font.left.title",        null);  // → font.default
        putDefault("font.editor",            "Monospaced,14");
        putDefault("font.editor.comment",    "Monospaced,13");
        putDefault("font.editor.lineNum",    null);  // → font.editor
        putDefault("font.right",             null);  // → font.default
        putDefault("font.right.title",       null);  // → font.default
        putDefault("font.bottom",            null);  // → font.default
        putDefault("font.bottom.title",      null);  // → font.default
        putDefault("font.bottom.result",     "Monospaced,12");
        putDefault("font.bottom.message",    "Monospaced,12");
        putDefault("font.bottom.result.header", null); // → font.default
        putDefault("font.status",            null);  // → font.default
        putDefault("font.dialog",            null);  // → font.default
        putDefault("font.dialog.title",      null);  // → font.default
    }

    private static void putDefault(String key, String val) { DEFAULTS.put(key, val); }

    private static final Map<String, String> KEY_LABELS = new LinkedHashMap<>();
    static {
        KEY_LABELS.put("font.default",           "通用界面（兜底）");
        KEY_LABELS.put("font.top",               "顶部");
        KEY_LABELS.put("font.left",              "左侧面板");
        KEY_LABELS.put("font.left.title",        "左侧面板标题");
        KEY_LABELS.put("font.editor",            "代码编辑器");
        KEY_LABELS.put("font.editor.comment",    "代码注释");
        KEY_LABELS.put("font.editor.lineNum",    "行号栏");
        KEY_LABELS.put("font.right",             "右侧面板");
        KEY_LABELS.put("font.right.title",       "右侧面板标题");
        KEY_LABELS.put("font.bottom",            "底部面板");
        KEY_LABELS.put("font.bottom.title",      "底部面板标题");
        KEY_LABELS.put("font.bottom.result",     "结果表（等宽）");
        KEY_LABELS.put("font.bottom.message",    "消息面板");
        KEY_LABELS.put("font.bottom.result.header", "结果表表头");
        KEY_LABELS.put("font.status",            "状态栏");
        KEY_LABELS.put("font.dialog",            "对话框");
        KEY_LABELS.put("font.dialog.title",      "对话框标题");
    }

    private static final Map<String, String> PREVIEW_TEXTS = new LinkedHashMap<>();
    static {
        PREVIEW_TEXTS.put("font.default",           "AaBbCc 你好世界 12345");
        PREVIEW_TEXTS.put("font.top",               "文件 编辑 视图 执行 帮助");
        PREVIEW_TEXTS.put("font.left",              "HR\n  表\n    EMPLOYEES\n  视图\n视图");
        PREVIEW_TEXTS.put("font.left.title",        "已保存的连接");
        PREVIEW_TEXTS.put("font.editor",            "SELECT * FROM orders");
        PREVIEW_TEXTS.put("font.editor.comment",    "-- 查询当月订单");
        PREVIEW_TEXTS.put("font.editor.lineNum",    "  1\n  2\n  3");
        PREVIEW_TEXTS.put("font.right",             "Outline 面板内容");
        PREVIEW_TEXTS.put("font.right.title",       "属性 / 导航");
        PREVIEW_TEXTS.put("font.bottom",            "SQL检查 / Services 标签");
        PREVIEW_TEXTS.put("font.bottom.title",      "输入 SQL：");
        PREVIEW_TEXTS.put("font.bottom.result",     "12345 张三 ACTIVE");
        PREVIEW_TEXTS.put("font.bottom.message",    "━━━ 执行脚本: init.sql\n第 1-50 批 (50 条) 提交 → OK\n完毕: 150 成功, 0 失败");
        PREVIEW_TEXTS.put("font.bottom.result.header", "ID  姓名  状态");
        PREVIEW_TEXTS.put("font.status",            "就绪 | 连接: localhost | UTF-8");
        PREVIEW_TEXTS.put("font.dialog",            "设置对话框内容");
        PREVIEW_TEXTS.put("font.dialog.title",      "关于 Kylin SQL Developer");
    }

    private static final String CHINESE_TEST = "\u4f60\u597d";

    public static synchronized FontManager getInstance() {
        if (instance == null) instance = new FontManager();
        return instance;
    }

    /** Resolve font for key, with cascading fallback: override → default → font.default → Dialog,PLAIN,12.
     *  值格式: "FontName,Size" / "FontName,Size,Style" / "FontName,Size,Style,#RRGGBB" / "FontName,Size,#RRGGBB" */
    public Font resolve(String key) {
        String val = overrides.get(key);
        if (val == null) val = DEFAULTS.get(key);
        if (val == null && !"font.default".equals(key)) {
            val = overrides.get("font.default");
            if (val == null) val = DEFAULTS.get("font.default");
        }
        if (val == null) return new Font("Dialog", Font.PLAIN, 12);
        String[] parts = val.split(",");
        String name = parts[0].trim();
        int size = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 12;
        int style = Font.PLAIN;
        if (parts.length > 2) {
            String third = parts[2].trim();
            if (!third.startsWith("#")) {
                style = switch (third.toUpperCase()) {
                    case "BOLD" -> Font.BOLD;
                    case "ITALIC" -> Font.ITALIC;
                    case "BOLDITALIC" -> Font.BOLD | Font.ITALIC;
                    default -> Font.PLAIN;
                };
            }
        }
        return new Font(name, style, size);
    }

    public Color resolveColor(String key) {
        String val = overrides.get(key);
        if (val == null) val = DEFAULTS.get(key);
        if (val == null && !"font.default".equals(key)) {
            val = overrides.get("font.default");
            if (val == null) val = DEFAULTS.get("font.default");
        }
        if (val == null) return null;
        int hash = val.lastIndexOf(",#");
        if (hash < 0) return null;
        try {
            return Color.decode(val.substring(hash + 1));
        } catch (Exception e) {
            return null;
        }
    }

    public String resolveValue(String key) {
        String val = overrides.get(key);
        if (val != null) return val;
        val = DEFAULTS.get(key);
        if (val != null) return val;
        if (!"font.default".equals(key)) {
            val = overrides.get("font.default");
            if (val != null) return val;
            val = DEFAULTS.get("font.default");
            if (val != null) return val;
        }
        return null;
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
        String lower = f.getFamily().toLowerCase();
        boolean mono = lower.contains("mono") || lower.contains("console")
                || lower.contains("courier") || lower.contains("consola")
                || lower.contains("dejavu") || lower.contains("dialoginput");
        boolean cn = f.canDisplayUpTo(CHINESE_TEST) == -1;
        StringBuilder label = new StringBuilder(fontName);
        if (mono) label.append("  [\u7b49\u5bbd]");
        if (cn) label.append("  [\u4e2d\u6587]");
        return label.toString();
    }

    public Map<String, String> getOverrideMap() { return new HashMap<>(overrides); }

    public void setOverride(String key, String value) { overrides.put(key, value); }

    /** 简版 setter（无样式、无颜色），向后兼容。 */
    public void setOverride(String key, String fontName, int size, Color color) {
        setOverride(key, fontName, size, "PLAIN", color);
    }

    /** 完整 setter：字体名、大小、样式（""/"PLAIN"/"BOLD"/"ITALIC"/"BOLDITALIC"）、颜色。 */
    public void setOverride(String key, String fontName, int size, String style, Color color) {
        StringBuilder sb = new StringBuilder(fontName).append(",").append(size);
        if (style != null && !style.isEmpty() && !"PLAIN".equalsIgnoreCase(style)) {
            sb.append(",").append(style.toUpperCase());
        }
        if (color != null) {
            sb.append(",#").append(String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()));
        }
        overrides.put(key, sb.toString());
    }

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
