package com.kylin.plsql.ui.dialog;

import com.kylin.plsql.core.config.ThemeManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public abstract class BaseToolDialog extends JDialog {
    protected final Frame owner;
    protected final ThemeManager theme;

    public BaseToolDialog(Frame owner, String title) {
        super(owner, title, false);
        this.owner = owner;
        this.theme = ThemeManager.getInstance();
    }

    protected JPanel wrapTitled(String title, Component comp) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));
        panel.add(comp, BorderLayout.CENTER);
        return panel;
    }

    protected JScrollPane wrapScroll(Component comp) {
        return new JScrollPane(comp);
    }

    protected Font monoFont() {
        return new Font("Monospaced", Font.PLAIN, 12);
    }

    protected JButton btn(String text, java.awt.event.ActionListener listener) {
        JButton b = new JButton(text);
        b.addActionListener(listener);
        return b;
    }

    protected Color themeColor(String key) {
        return theme.resolve(key);
    }

    protected void centerOnOwner() {
        setLocationRelativeTo(owner);
    }

    protected void setSizeRatio(double ratio) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize((int) (screen.width * ratio), (int) (screen.height * ratio));
    }

    protected void applyTheme() {
        getContentPane().setBackground(theme.resolve("bg.main"));
    }
}
