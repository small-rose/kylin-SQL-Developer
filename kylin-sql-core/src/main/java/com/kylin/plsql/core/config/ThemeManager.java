package com.kylin.plsql.core.config;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Singleton theme manager with color override support and listener notification. */
public class ThemeManager {
    private static ThemeManager instance;
    private AppTheme currentTheme = AppTheme.DARK;
    private final Map<String, Color> overrides = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private ThemeManager() {}

    public static synchronized ThemeManager getInstance() {
        if (instance == null) instance = new ThemeManager();
        return instance;
    }

    public Color resolve(String key) {
        if (overrides.containsKey(key)) return overrides.get(key);
        return currentTheme.resolve(key);
    }

    public AppTheme getCurrentTheme() { return currentTheme; }

    // ── Override API ──

    public void setOverride(String key, Color color) {
        overrides.put(key, color);
    }

    public void removeOverride(String key) {
        overrides.remove(key);
    }

    public void clearOverrides() {
        overrides.clear();
    }

    public Map<String, String> getOverrideHexMap() {
        Map<String, String> hex = new HashMap<>();
        for (var e : overrides.entrySet()) {
            hex.put(e.getKey(), colorToHex(e.getValue()));
        }
        return hex;
    }

    public void loadOverrideHexMap(Map<String, String> hexMap) {
        overrides.clear();
        if (hexMap != null) {
            for (var e : hexMap.entrySet()) {
                Color c = hexToColor(e.getValue());
                if (c != null) overrides.put(e.getKey(), c);
            }
        }
    }

    // ── Theme switching ──

    public void switchTo(AppTheme theme) {
        if (theme == currentTheme) return;
        this.currentTheme = theme;
        SwingHelper.runOnEDT(() -> {
            for (Runnable r : listeners) r.run();
        });
    }

    public void addListener(Runnable r) { listeners.add(r); }

    // ── Persistence ──

    public void loadFromConfig(ConfigManager config) {
        var ws = config.loadWorkspace();
        if (ws.theme != null) {
            try { currentTheme = AppTheme.valueOf(ws.theme); }
            catch (IllegalArgumentException ignored) {}
        }
        loadOverrideHexMap(ws.colorOverrides);
    }

    public void saveToConfig(ConfigManager config) {
        var ws = config.loadWorkspace();
        ws.theme = currentTheme.name();
        ws.colorOverrides = getOverrideHexMap();
        config.saveWorkspace(ws);
    }

    // ── Helpers ──

    private static String colorToHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color hexToColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) {
                return new Color(Integer.parseInt(hex, 16));
            } else if (hex.length() == 8) {
                int argb = (int) Long.parseLong(hex, 16);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                return new Color(r, g, b, a);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static class SwingHelper {
        static void runOnEDT(Runnable r) {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) { r.run(); }
            else { javax.swing.SwingUtilities.invokeLater(r); }
        }
    }
}
