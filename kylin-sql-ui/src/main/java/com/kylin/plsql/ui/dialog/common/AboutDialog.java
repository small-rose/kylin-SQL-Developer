package com.kylin.plsql.ui.dialog.common;

import javax.swing.*;
import com.kylin.plsql.core.config.FontManager;
import java.awt.*;

public class AboutDialog extends JDialog {

    private static final String FONT_NAME = "Microsoft YaHei";

    private final JLabel versionLabel = new JLabel("Version 1.0.0  (Build 2026.1)");
    private final JLabel copyrightLabel = new JLabel("\u00A9 2026 Kylin Team. All rights reserved.");

    public AboutDialog(Frame owner) {
        super(owner, "\u5173\u4E8E", true);
        setResizable(false);

        initUI();
        setSize(480, 380);
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout());

        java.net.URL logoUrl = getClass().getResource("/logo/kylin_192x192.png");
        ImageIcon logoIcon = logoUrl != null ? new ImageIcon(logoUrl) : null;
        JLabel logoLabel = new JLabel();
        if (logoIcon != null) {
            Image scaled = logoIcon.getImage().getScaledInstance(56, 56, Image.SCALE_SMOOTH);
            logoLabel.setIcon(new ImageIcon(scaled));
        }

        JLabel titleLabel = new JLabel("Kylin SQL Developer");
        titleLabel.setFont(FontManager.getInstance().resolve("font.dialog.title"));
        titleLabel.setForeground(new Color(0x1A1A1A));

        versionLabel.setFont(FontManager.getInstance().resolve("font.dialog"));
        versionLabel.setForeground(new Color(0x666666));

        // 标题区域（LOGO + 文字水平并排，整体居中）
        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);
        textCol.add(Box.createVerticalGlue());
        textCol.add(titleLabel);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(versionLabel);
        textCol.add(Box.createVerticalGlue());

        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 48, 0));
        headerRow.setOpaque(false);
        headerRow.add(logoLabel);
        headerRow.add(textCol);

        // 信息区域（浅色背景，文字使用深色系保证可读性）
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        addInfoLine(infoPanel, "\u5185\u6838\u5F15\u64CE", "PL/SQL \u683C\u5F0F\u5316\u5F15\u64CE + ANTLR4 \u8BED\u6CD5\u89E3\u6790");
        addInfoLine(infoPanel, "\u652F\u6301\u6570\u636E\u5E93", "Oracle / MySQL / PostgreSQL / OceanBase");
        addInfoLine(infoPanel, "\u5185\u7F6E\u5DE5\u5177", "SQL \u683C\u5F0F\u5316 / \u6570\u636E\u751F\u6210\u5668 / \u6587\u672C\u6BD4\u8F83 / \u6B63\u5219\u6D4B\u8BD5 / \u5BF9\u8C61\u641C\u7D22");
        addInfoLine(infoPanel, "\u8FD0\u884C\u73AF\u5883", "Java " + System.getProperty("java.version")
                + " / " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));

        copyrightLabel.setFont(FontManager.getInstance().resolve("font.dialog"));
        copyrightLabel.setForeground(new Color(0x888888));
        copyrightLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(48, 40, 12, 40));
        center.add(headerRow);
        center.add(Box.createVerticalStrut(4));
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
        lbl.setFont(FontManager.getInstance().resolve("font.dialog.title"));
        lbl.setForeground(new Color(0x7B1FA2));
        lbl.setPreferredSize(new Dimension(100, 24));
        lbl.setHorizontalAlignment(SwingConstants.RIGHT);

        JLabel val = new JLabel(value);
        val.setFont(FontManager.getInstance().resolve("font.dialog"));
        val.setForeground(new Color(0x333333));

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createVerticalStrut(4));
    }
}
