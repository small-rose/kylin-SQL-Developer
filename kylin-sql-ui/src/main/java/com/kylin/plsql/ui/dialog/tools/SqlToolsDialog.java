package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import com.kylin.plsql.ui.component.common.ToastManager;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SQL utility dialog: string escaping, IN clause conversion, unescaping */
public class SqlToolsDialog extends BaseToolDialog {
    private final JTextArea inputArea;
    private final JTextArea outputArea;
    private final JTabbedPane tabbedPane;
    private final JSplitPane[] splitPanes;
    private final JToggleButton layoutToggleBtn;
    private final JLabel descLabel;

    public SqlToolsDialog(Frame owner) {
        super(owner, "SQL \u5DE5\u5177");
        setSizeRatio(0.7);
        centerOnOwner();

        inputArea = new JTextArea();
        inputArea.setFont(monoFont());

        outputArea = new JTextArea();
        outputArea.setFont(monoFont());
        outputArea.setEditable(false);

        tabbedPane = new JTabbedPane();

        JPanel escapePanel = buildEscapePanel();
        JPanel inClausePanel = buildInClausePanel();
        splitPanes = new JSplitPane[]{
                (JSplitPane) escapePanel.getComponent(0),
                (JSplitPane) inClausePanel.getComponent(0)
        };

        tabbedPane.addTab("\u5B57\u7B26\u8F6C\u4E49", escapePanel);
        tabbedPane.addTab("IN \u5B50\u53E5\u8F6C\u6362", inClausePanel);
        tabbedPane.addChangeListener(e -> updateDesc());

        layoutToggleBtn = new JToggleButton("\u21D4 \u5782\u76F4\u5E03\u5C40");
        layoutToggleBtn.addActionListener(e -> toggleLayout());

        descLabel = new JLabel();
        updateDesc();

        JButton escapeBtn = new JButton("SQL \u8F6C\u4E49");
        escapeBtn.addActionListener(e -> outputArea.setText(escapeSql(inputArea.getText())));
        JButton unescapeBtn = new JButton("SQL \u53CD\u8F6C\u4E49");
        unescapeBtn.addActionListener(e -> outputArea.setText(unescapeSql(inputArea.getText())));
        JCheckBox quoteCb = new JCheckBox("\u5E26\u5F15\u53F7");
        quoteCb.setSelected(true);
        JButton toInBtn = new JButton("> IN \u5B50\u53E5");
        toInBtn.addActionListener(e -> outputArea.setText(toInClause(inputArea.getText(), quoteCb.isSelected())));
        JButton fromInBtn = new JButton("< \u8FD8\u539F");
        fromInBtn.addActionListener(e -> outputArea.setText(fromInClause(inputArea.getText())));

        JPanel southPanel = new JPanel(new BorderLayout(4, 0));
        JPanel centerRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        centerRow.add(escapeBtn);
        centerRow.add(unescapeBtn);
        centerRow.add(quoteCb);
        centerRow.add(toInBtn);
        centerRow.add(fromInBtn);
        southPanel.add(descLabel, BorderLayout.WEST);
        southPanel.add(centerRow, BorderLayout.CENTER);
        southPanel.add(layoutToggleBtn, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        applyTheme();
    }

    private JPanel buildEscapePanel() {
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("\u8F93\u5165"));
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("\u7ED3\u679C"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                inputScroll, outputScroll);
        split.setResizeWeight(0.5);
        split.setContinuousLayout(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInClausePanel() {
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("\u8F93\u5165"));
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("\u7ED3\u679C"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                inputScroll, outputScroll);
        split.setResizeWeight(0.5);
        split.setContinuousLayout(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void updateDesc() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx == 0) {
            descLabel.setText("\u529F\u80FD\u8BF4\u660E\uFF1ASQL \u5B57\u7B26\u4E2D\u7684\u5355\u5F15\u53F7\u8F6C\u4E49\u4E0E\u53CD\u8F6C\u4E49");
        } else {
            descLabel.setText("\u529F\u80FD\u8BF4\u660E\uFF1A\u591A\u884C\u503C\u4E0E IN \u5B50\u53E5\u683C\u5F0F\u4E92\u76F8\u8F6C\u6362");
        }
    }

    private void toggleLayout() {
        int idx = tabbedPane.getSelectedIndex();
        JSplitPane split = splitPanes[idx];
        boolean horizontal = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
        split.setOrientation(horizontal
                ? JSplitPane.VERTICAL_SPLIT
                : JSplitPane.HORIZONTAL_SPLIT);
        layoutToggleBtn.setText(horizontal ? "\u21D5 \u6C34\u5E73\u5E03\u5C40" : "\u21D4 \u5782\u76F4\u5E03\u5C40");
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        inputArea.setBackground(theme.resolve("bg.editor"));
        inputArea.setForeground(theme.resolve("fg.main"));
        outputArea.setBackground(theme.resolve("bg.editor"));
        outputArea.setForeground(theme.resolve("fg.main"));
        descLabel.setForeground(theme.resolve("fg.muted"));
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
