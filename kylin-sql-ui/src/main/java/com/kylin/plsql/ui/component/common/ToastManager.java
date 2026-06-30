package com.kylin.plsql.ui.component.common;

import com.kylin.plsql.core.config.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/** Non-modal toast notification manager for transient messages. */
public class ToastManager {
    private static ToastManager instance;
    private final Map<Window, JWindow> activeToasts = new HashMap<>();

    private ToastManager() {}

    public static synchronized ToastManager getInstance() {
        if (instance == null) instance = new ToastManager();
        return instance;
    }

    public static void show(Component anchor, String msg) {
        getInstance().showToast(anchor, msg, 2000, false);
    }

    public static void show(Window owner, String msg) {
        getInstance().showToast(owner, msg, 2000, false);
    }

    public static void show(Component anchor, String msg, int durationMs) {
        getInstance().showToast(anchor, msg, durationMs, false);
    }

    public static void showError(Component anchor, String msg) {
        getInstance().showToast(anchor, msg, 4000, true);
    }

    private void showToast(Component anchor, String msg, int durationMs, boolean error) {
        Window owner = (anchor instanceof Window) ? (Window) anchor
                : SwingUtilities.getWindowAncestor(anchor);
        if (owner == null) return;

        JWindow existing = activeToasts.get(owner);
        if (existing != null) {
            existing.dispose();
            activeToasts.remove(owner);
        }

        Color bg = ThemeManager.getInstance().resolve("bg.panel");
        boolean dark = bg.getRed() + bg.getGreen() + bg.getBlue() < 382;

        JWindow toast = new JWindow(owner);
        JLabel label = new JLabel(msg);
        label.setOpaque(true);
        if (error) {
            label.setBackground(new Color(0xC62828));
            label.setForeground(Color.WHITE);
        } else {
            label.setBackground(dark ? new Color(0xE0E0E0) : new Color(0x444444));
            label.setForeground(dark ? new Color(0x222222) : Color.WHITE);
        }
        label.setFont(new Font("Dialog", Font.PLAIN, 12));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(dark ? new Color(0xAAAAAA) : new Color(0x666666)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        toast.add(label);
        toast.pack();

        Point p = owner.getLocation();
        toast.setLocation(p.x + owner.getWidth() - toast.getWidth() - 24,
                p.y + owner.getHeight() - toast.getHeight() - 40);
        toast.setVisible(true);
        activeToasts.put(owner, toast);

        new Timer(durationMs, e -> {
            toast.dispose();
            activeToasts.remove(owner);
        }).start();
    }
}
