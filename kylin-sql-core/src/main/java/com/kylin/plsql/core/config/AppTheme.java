package com.kylin.plsql.core.config;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum AppTheme {
    DARK, LIGHT, GREEN;

    private static final Map<AppTheme, Map<String, Color>> PALETTES = new HashMap<>();
    private static final Map<AppTheme, Map<String, String>> CONFIGS = new HashMap<>();

    static {
        // ── DARK (Darcula) ──
        Map<String, Color> d = new HashMap<>();
        d.put("bg.main", new Color(0x2B2B2B));
        d.put("bg.editor", new Color(0x1E1E1E));
        d.put("bg.panel", new Color(0x252526));
        d.put("bg.toolbar", new Color(0x2B2B2B));
        d.put("bg.output", new Color(0x1E1E1E));
        d.put("fg.main", new Color(0xD4D4D4));
        d.put("fg.secondary", new Color(0xCCCCCC));
        d.put("fg.muted", new Color(0x888888));
        d.put("fg.title", new Color(0xE0E0E0));
        d.put("fg.tab.active", new Color(0xE0E0E0));
        d.put("fg.tab.inactive", new Color(0x999999));
        d.put("selection.bg", new Color(0x264F78));
        d.put("selection.fg", new Color(0xFFFFFF));
        d.put("selection.listBg", new Color(0x094771));
        d.put("selection.listFg", new Color(0xFFFFFF));
        d.put("border.default", new Color(0x3C3C3C));
        d.put("border.light", new Color(0x4A4A4A));
        d.put("accent.green", new Color(0x4A9B4A));
        d.put("accent.tab", new Color(0x4A9B4A));
        d.put("editor.caret", new Color(0xD4D4D4));
        d.put("exec.success", new Color(0x5CB85C));
        d.put("exec.fail", new Color(0xD9534F));
        d.put("exec.highlight", new Color(0x0FFFFFFF, true));
        d.put("list.bg", new Color(0x252526));
        d.put("list.fg", new Color(0xCCCCCC));
        d.put("scroll.bg", new Color(0x252526));
        PALETTES.put(DARK, Collections.unmodifiableMap(d));
        Map<String, String> dc = new HashMap<>();
        dc.put("rsta.theme", "/org/fife/ui/rsyntaxtextarea/themes/dark.xml");
        dc.put("flatlaf", "DARK");
        CONFIGS.put(DARK, Collections.unmodifiableMap(dc));

        // ── LIGHT (DataGrip Light) ──
        Map<String, Color> l = new HashMap<>();
        l.put("bg.main", new Color(0xF2F2F2));
        l.put("bg.editor", new Color(0xFFFFFF));
        l.put("bg.panel", new Color(0xECECEC));
        l.put("bg.toolbar", new Color(0xF2F2F2));
        l.put("bg.output", new Color(0xFFFFFF));
        l.put("fg.main", new Color(0x333333));
        l.put("fg.secondary", new Color(0x555555));
        l.put("fg.muted", new Color(0x999999));
        l.put("fg.title", new Color(0x333333));
        l.put("fg.tab.active", new Color(0x333333));
        l.put("fg.tab.inactive", new Color(0x999999));
        l.put("selection.bg", new Color(0xC6E2FF));
        l.put("selection.fg", new Color(0x333333));
        l.put("selection.listBg", new Color(0xA5C8FF));
        l.put("selection.listFg", new Color(0x333333));
        l.put("border.default", new Color(0xD0D0D0));
        l.put("border.light", new Color(0xE0E0E0));
        l.put("accent.green", new Color(0x2D7D2D));
        l.put("accent.tab", new Color(0x2D7D2D));
        l.put("editor.caret", new Color(0x333333));
        l.put("exec.success", new Color(0x5CB85C));
        l.put("exec.fail", new Color(0xD9534F));
        l.put("exec.highlight", new Color(0x0FFFFF00, true));
        l.put("list.bg", new Color(0xF5F5F5));
        l.put("list.fg", new Color(0x333333));
        l.put("scroll.bg", new Color(0xECECEC));
        PALETTES.put(LIGHT, Collections.unmodifiableMap(l));
        Map<String, String> lc = new HashMap<>();
        lc.put("rsta.theme", "themes/light.xml");
        lc.put("flatlaf", "LIGHT");
        CONFIGS.put(LIGHT, Collections.unmodifiableMap(lc));

        // ── GREEN (豆沙绿) ──
        Map<String, Color> g = new HashMap<>();
        g.put("bg.main", new Color(0xC7EDCC));
        g.put("bg.editor", new Color(0xD4EDDA));
        g.put("bg.panel", new Color(0xB8D4BA));
        g.put("bg.toolbar", new Color(0xC7EDCC));
        g.put("bg.output", new Color(0xD4EDDA));
        g.put("fg.main", new Color(0x333333));
        g.put("fg.secondary", new Color(0x555555));
        g.put("fg.muted", new Color(0x6B8E6B));
        g.put("fg.title", new Color(0x333333));
        g.put("fg.tab.active", new Color(0x333333));
        g.put("fg.tab.inactive", new Color(0x6B8E6B));
        g.put("selection.bg", new Color(0xA8D8A8));
        g.put("selection.fg", new Color(0x333333));
        g.put("selection.listBg", new Color(0x8BC68B));
        g.put("selection.listFg", new Color(0x333333));
        g.put("border.default", new Color(0x9DBFA1));
        g.put("border.light", new Color(0xAED8B2));
        g.put("accent.green", new Color(0x3D8B3D));
        g.put("accent.tab", new Color(0x3D8B3D));
        g.put("editor.caret", new Color(0x333333));
        g.put("exec.success", new Color(0x3D8B3D));
        g.put("exec.fail", new Color(0xD9534F));
        g.put("exec.highlight", new Color(0x0F3D8B3D, true));
        g.put("list.bg", new Color(0xB8D4BA));
        g.put("list.fg", new Color(0x333333));
        g.put("scroll.bg", new Color(0xB8D4BA));
        PALETTES.put(GREEN, Collections.unmodifiableMap(g));
        Map<String, String> gc = new HashMap<>();
        gc.put("rsta.theme", "themes/green.xml");
        gc.put("flatlaf", "LIGHT");
        CONFIGS.put(GREEN, Collections.unmodifiableMap(gc));
    }

    public Color resolve(String key) {
        Map<String, Color> p = PALETTES.get(this);
        Color c = p.get(key);
        return c != null ? c : Color.MAGENTA;
    }

    public String config(String key) {
        Map<String, String> c = CONFIGS.get(this);
        String v = c.get(key);
        return v != null ? v : "";
    }
}
