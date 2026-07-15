package com.kylin.plsql.ui.dialog.common;

import javax.swing.*;
import java.awt.*;

public class AboutDialog extends JDialog {

    private static final String FONT_NAME = "Microsoft YaHei";
    private static final Color PURPLE = new Color(0x7B1FA2);

    private final JLabel versionLabel = new JLabel("Version 1.0.0  (Build 2026.1)");
    private final JLabel copyrightLabel = new JLabel("\u00A9 2026 Kylin Team. All rights reserved.");

    public AboutDialog(Frame owner) {
        super(owner, "\u5173\u4E8E", true);
        setResizable(false);

        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PURPLE);

        JLabel titleLabel = new JLabel("Kylin SQL Developer");
        titleLabel.setFont(new Font(FONT_NAME, Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        versionLabel.setFont(new Font(FONT_NAME, Font.PLAIN, 12));
        versionLabel.setForeground(new Color(0xE0E0E0));
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel headerText = new JPanel();
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));
        headerText.setOpaque(false);
        headerText.add(titleLabel);
        headerText.add(Box.createVerticalStrut(2));
        headerText.add(versionLabel);

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        addInfoLine(infoPanel, "\u5185\u6838\u5F15\u64CE", "PL/SQL \u683C\u5F0F\u5316\u5F15\u64CE + ANTLR4 \u8BED\u6CD5\u89E3\u6790");
        addInfoLine(infoPanel, "\u652F\u6301\u6570\u636E\u5E93", "Oracle / MySQL / PostgreSQL / OceanBase");
        addInfoLine(infoPanel, "\u5185\u7F6E\u5DE5\u5177", "SQL \u683C\u5F0F\u5316 / \u6570\u636E\u751F\u6210\u5668 / \u6587\u672C\u6BD4\u8F83 / \u6B63\u5219\u6D4B\u8BD5 / \u5BF9\u8C61\u641C\u7D22");
        addInfoLine(infoPanel, "\u8FD0\u884C\u73AF\u5883", "Java " + System.getProperty("java.version")
                + " / " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));

        copyrightLabel.setFont(new Font(FONT_NAME, Font.PLAIN, 11));
        copyrightLabel.setForeground(new Color(0xCE93D8));
        copyrightLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(24, 40, 24, 40));
        center.add(headerText);
        center.add(Box.createVerticalStrut(16));
        center.add(new JSeparator());
        center.add(Box.createVerticalStrut(16));
        center.add(infoPanel);
        center.add(Box.createVerticalStrut(20));
        center.add(copyrightLabel);

        root.add(center, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void addInfoLine(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font(FONT_NAME, Font.BOLD, 12));
        lbl.setForeground(new Color(0xCE93D8));
        lbl.setPreferredSize(new Dimension(100, 24));
        lbl.setHorizontalAlignment(SwingConstants.RIGHT);

        JLabel val = new JLabel(value);
        val.setFont(new Font(FONT_NAME, Font.PLAIN, 12));
        val.setForeground(Color.WHITE);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createVerticalStrut(4));
    }
}
