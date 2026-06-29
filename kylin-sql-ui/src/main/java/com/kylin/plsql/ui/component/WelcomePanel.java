package com.kylin.plsql.ui.component;

import com.kylin.plsql.core.config.ThemeManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class WelcomePanel extends JPanel {
    private static final Font TITLE_FONT = UIManager.getFont("TitledBorder.font").deriveFont(Font.PLAIN);
    private final ThemeManager theme = ThemeManager.getInstance();
    private final JPanel shortcutsPanel = new JPanel(new GridBagLayout());
    private final JPanel tipsPanel = new JPanel(new GridBagLayout());

    public WelcomePanel(Runnable onNewFile, Runnable onOpenFile) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        JLabel title = new JLabel("Kylin SQL Developer");
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 18f));
        title.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        topPanel.add(title, BorderLayout.NORTH);
        topPanel.add(new JSeparator(JSeparator.HORIZONTAL), BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        String[][] shortcuts = {
            {"Ctrl + N", "\u65B0\u5EFA SQL \u6587\u4EF6"},
            {"Ctrl + O", "\u6253\u5F00 SQL \u6587\u4EF6"},
            {"Ctrl + S", "\u4FDD\u5B58"},
            {"Ctrl + Shift + S", "\u53E6\u5B58\u4E3A"},
            {"Ctrl + W", "\u5173\u95ED\u5F53\u524D\u6807\u7B7E"},
            {"F8", "\u6267\u884C SQL / \u9009\u4E2D\u4EE3\u7801"},
            {"Ctrl + Shift + F", "\u683C\u5F0F\u5316 SQL"},
            {"Ctrl + E", "\u6267\u884C\u8BA1\u5212"},
            {"Ctrl + Alt + H", "\u8C03\u7528\u5C42\u7EA7"},
            {"Ctrl + F", "\u67E5\u627E"},
            {"Ctrl + H", "\u66FF\u6362"},
        };

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(1, 0, 1, 20);
        gc.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < shortcuts.length; i++) {
            gc.gridx = 0; gc.gridy = i;
            JLabel key = new JLabel(shortcuts[i][0]);
            key.setFont(new Font("Monospaced", Font.BOLD, 12));
            key.setForeground(theme.resolve("accent.green"));
            shortcutsPanel.add(key, gc);
            gc.gridx = 1;
            shortcutsPanel.add(new JLabel(shortcuts[i][1]), gc);
        }
        shortcutsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "\u5FEB\u6377\u952E",
                TitledBorder.LEFT, TitledBorder.TOP, TITLE_FONT));

        String[] tips = {
            "\u25CF  \u5DE6\u4FA7\u5BF9\u8C61\u6D4F\u89C8\u5668\u53F3\u952E\u751F\u6210 SELECT/INSERT/UPDATE/DELETE/DDL",
            "\u25CF  \u8FDE\u63A5/Schema \u4E0B\u62C9\u5728\u6BCF\u4E2A\u7F16\u8F91\u5668\u6807\u7B7E\u9875\u9876\u90E8\u5DE5\u5177\u680F",
            "\u25CF  \u5149\u6807\u5B9A\u4F4D\u5230 SQL \u8BED\u53E5\u4E0A\u6309 F8 \u81EA\u52A8\u8BC6\u522B\u5F53\u524D SQL \u5757",
            "\u25CF  \u5149\u6807\u79FB\u52A8\u65F6\u81EA\u52A8\u9AD8\u4EAE\u5F53\u524D SQL \u8BED\u53E5\u8303\u56F4",
            "\u25CF  \u53F3\u4FA7\u5927\u7EB2\u9762\u677F\u663E\u793A\u5305/\u8FC7\u7A0B/\u51FD\u6570\uFF0C\u5355\u51FB\u8DF3\u8F6C\u5B9A\u4E49",
            "\u25CF  \u53F3\u952E \u201C\u6570\u636E\u9884\u89C8\u201D \u5FEB\u901F\u67E5\u770B\u8868\u524D 100 \u884C",
            "\u25CF  \u7ED3\u679C\u96C6\u5DE5\u5177\u680F\u652F\u6301 CSV \u5BFC\u51FA / \u590D\u5236",
            "\u25CF  \u8FDE\u63A5\u53EF\u8BBE\u7F6E SQL \u6267\u884C\u8D85\u65F6\u65F6\u95F4 (0=\u4E0D\u9650)",
        };

        GridBagConstraints tc = new GridBagConstraints();
        tc.gridx = 0; tc.anchor = GridBagConstraints.WEST; tc.insets = new Insets(1, 0, 1, 0);
        for (int i = 0; i < tips.length; i++) {
            tc.gridy = i;
            tipsPanel.add(new JLabel(tips[i]), tc);
        }
        tipsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "\u4F7F\u7528\u6280\u5DE7",
                TitledBorder.LEFT, TitledBorder.TOP, TITLE_FONT));

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        centerPanel.add(shortcutsPanel);
        centerPanel.add(tipsPanel);
        add(centerPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        btnPanel.setOpaque(false);
        JButton newBtn = new JButton("\u65B0\u5EFA SQL \u6587\u4EF6");
        newBtn.addActionListener(e -> onNewFile.run());
        JButton openBtn = new JButton("\u6253\u5F00 SQL \u6587\u4EF6");
        openBtn.addActionListener(e -> onOpenFile.run());
        btnPanel.add(newBtn);
        btnPanel.add(openBtn);
        add(btnPanel, BorderLayout.SOUTH);

        applyTheme();
    }

    public void applyTheme() {
        Color bg = theme.resolve("bg.main");
        setBackground(bg);
        shortcutsPanel.setBackground(bg);
        tipsPanel.setBackground(bg);
        repaint();
    }
}
