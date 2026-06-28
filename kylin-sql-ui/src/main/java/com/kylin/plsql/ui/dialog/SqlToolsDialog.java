package com.kylin.plsql.ui.dialog;

import com.kylin.plsql.ui.component.ToastManager;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlToolsDialog extends BaseToolDialog {
    private final JTextArea inputArea;
    private final JTextArea outputArea;
    private final JTabbedPane tabbedPane;

    public SqlToolsDialog(Frame owner) {
        super(owner, "SQL \u5DE5\u5177");
        setSizeRatio(0.7);

        inputArea = new JTextArea(4, 40);
        inputArea.setFont(monoFont());

        outputArea = new JTextArea(4, 40);
        outputArea.setFont(monoFont());
        outputArea.setEditable(false);

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("\u5B57\u7B26\u8F6C\u4E49", buildEscapePanel());
        tabbedPane.addTab("IN \u5B50\u53E5\u8F6C\u6362", buildInClausePanel());

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel buildEscapePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("\u8F93\u5165"));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        JButton escapeBtn = new JButton("SQL \u8F6C\u4E49");
        escapeBtn.addActionListener(e -> outputArea.setText(escapeSql(inputArea.getText())));
        JButton unescapeBtn = new JButton("SQL \u53CD\u8F6C\u4E49");
        unescapeBtn.addActionListener(e -> outputArea.setText(unescapeSql(inputArea.getText())));
        btnPanel.add(escapeBtn);
        btnPanel.add(unescapeBtn);

        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("\u7ED3\u679C"));

        panel.add(inputScroll, BorderLayout.NORTH);
        panel.add(btnPanel, BorderLayout.CENTER);
        panel.add(outputScroll, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildInClausePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JTextArea inInput = new JTextArea(4, 40);
        inInput.setFont(monoFont());
        JTextArea inOutput = new JTextArea(4, 40);
        inOutput.setFont(monoFont());
        inOutput.setEditable(false);

        JScrollPane inputScroll = new JScrollPane(inInput);
        inputScroll.setBorder(BorderFactory.createTitledBorder("\u8F93\u5165"));

        JCheckBox quoteCb = new JCheckBox("\u5E26\u5F15\u53F7");
        quoteCb.setSelected(true);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        btnPanel.add(quoteCb);

        JButton toInBtn = new JButton("> IN \u5B50\u53E5");
        toInBtn.addActionListener(e -> inOutput.setText(toInClause(inInput.getText(), quoteCb.isSelected())));
        JButton fromInBtn = new JButton("< \u8FD8\u539F");
        fromInBtn.addActionListener(e -> inOutput.setText(fromInClause(inInput.getText())));
        btnPanel.add(toInBtn);
        btnPanel.add(fromInBtn);

        JScrollPane outputScroll = new JScrollPane(inOutput);
        outputScroll.setBorder(BorderFactory.createTitledBorder("\u7ED3\u679C"));

        panel.add(inputScroll, BorderLayout.NORTH);
        panel.add(btnPanel, BorderLayout.CENTER);
        panel.add(outputScroll, BorderLayout.SOUTH);
        return panel;
    }

    static String escapeSql(String input) {
        if (input == null || input.isEmpty()) return "";
        return input.replace("'", "''");
    }

    static String unescapeSql(String input) {
        if (input == null || input.isEmpty()) return "";
        return input.replace("''", "'");
    }

    static String toInClause(String input, boolean quoted) {
        if (input == null || input.isEmpty()) return "";
        String[] lines = input.split("\\r?\\n");
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (i > 0 && sb.charAt(sb.length() - 1) != '(') sb.append(", ");
            if (quoted) sb.append("'").append(line.replace("'", "''")).append("'");
            else sb.append(line);
        }
        sb.append(")");
        return sb.toString();
    }

    static String fromInClause(String input) {
        if (input == null || input.isEmpty()) return "";
        String trimmed = input.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("'([^']*)'|([^,]+)").matcher(trimmed);
        while (m.find()) {
            if (sb.length() > 0) sb.append("\n");
            if (m.group(1) != null) sb.append(m.group(1).replace("''", "'"));
            else sb.append(m.group(2).trim());
        }
        return sb.toString();
    }
}
