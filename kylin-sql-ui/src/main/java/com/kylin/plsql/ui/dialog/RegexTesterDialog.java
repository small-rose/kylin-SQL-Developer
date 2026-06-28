package com.kylin.plsql.ui.dialog;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTesterDialog extends BaseToolDialog {
    private final JTextField regexField;
    private final JTextArea testArea;
    private final JTextArea resultArea;
    private final JTextField replaceField;
    private final JTextArea replaceResultArea;
    private final JCheckBox caseCb;
    private final JCheckBox multilineCb;
    private final JCheckBox dotallCb;
    private final DefaultListModel<String> favoriteModel = new DefaultListModel<>();
    private final JList<String> favoriteList = new JList<>(favoriteModel);
    private final JTabbedPane modeTabs;
    private final GroupTableModel groupModel;
    private final JTable groupTable;

    private static final String[][] FAVORITES = {
        {"\u90AE\u7BB1", "^[\\w.-]+@[\\w.-]+\\.\\w{2,}$"},
        {"\u624B\u673A\u53F7", "^1[3-9]\\d{9}$"},
        {"URL", "^https?://[\\w.-]+(/\\S*)?$"},
        {"IP \u5730\u5740", "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"},
        {"\u5339\u914D\u4E2D\u6587", "[\\u4e00-\\u9fa5]+"},
        {"\u8EAB\u4EFD\u8BC1\u53F7", "^\\d{17}[\\dXx]$"},
        {"\u65E5\u671F (YYYY-MM-DD)", "^\\d{4}-\\d{2}-\\d{2}$"},
        {"\u65F6\u95F4 (HH:MM:SS)", "^\\d{2}:\\d{2}:\\d{2}$"},
        {"\u90AE\u7F16", "^\\d{6}$"},
        {"HTML \u6807\u7B7E", "<[^>]+>"},
        {"\u7A7A\u767D\u884C", "^\\s*$"},
        {"\u989C\u8272\u5341\u516D\u8FDB\u5236", "^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$"},
        {"\u7528\u6237\u540D", "^\\w{3,20}$"},
        {"\u6587\u4EF6\u6269\u5C55\u540D", "\\.\\w+$"},
        {"\u6570\u5B57", "^-?\\d+(\\.\\d+)?$"},
    };

    static class GroupTableModel extends AbstractTableModel {
        private final List<String[]> data = new ArrayList<>();

        void setGroups(int count) {
            data.clear();
            for (int i = 0; i <= count; i++) {
                data.add(new String[]{String.valueOf(i), ""});
            }
            fireTableDataChanged();
        }

        void setGroupValue(int group, String value) {
            if (group >= 0 && group < data.size()) {
                data.set(group, new String[]{String.valueOf(group), value});
                fireTableRowsUpdated(group, group);
            }
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return 2; }

        @Override public String getColumnName(int col) {
            return col == 0 ? "\u7EC4\u53F7" : "\u503C";
        }

        @Override public Object getValueAt(int row, int col) {
            return data.get(row)[col];
        }
    }

    public RegexTesterDialog(Frame owner) {
        super(owner, "\u6B63\u5219\u6D4B\u8BD5\u5668");
        setSizeRatio(0.7);

        regexField = new JTextField();
        testArea = new JTextArea(6, 30);
        testArea.setFont(monoFont());
        resultArea = new JTextArea();
        resultArea.setFont(monoFont());
        resultArea.setEditable(false);

        replaceField = new JTextField();
        replaceResultArea = new JTextArea();
        replaceResultArea.setFont(monoFont());
        replaceResultArea.setEditable(false);

        caseCb = new JCheckBox("\u5FFD\u7565\u5927\u5C0F\u5199");
        multilineCb = new JCheckBox("\u591A\u884C\u6A21\u5F0F");
        dotallCb = new JCheckBox("DOTALL \u6A21\u5F0F");

        JButton testBtn = new JButton("\u6D4B\u8BD5");
        testBtn.addActionListener(e -> runTest());

        groupModel = new GroupTableModel();
        groupTable = new JTable(groupModel);

        JPanel northPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 5);
        c.gridx = 0; c.gridy = 0; c.gridwidth = 7; c.weightx = 1;
        northPanel.add(new JLabel("\u6B63\u5219\u8868\u8FBE\u5F0F:"), c);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 6; c.weightx = 1;
        northPanel.add(regexField, c);
        c.gridx = 6; c.gridy = 1; c.gridwidth = 1; c.weightx = 0;
        northPanel.add(testBtn, c);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 1;
        northPanel.add(caseCb, c);
        c.gridx = 1;
        northPanel.add(multilineCb, c);
        c.gridx = 2;
        northPanel.add(dotallCb, c);

        JScrollPane testScroll = new JScrollPane(testArea);
        JScrollPane resultScroll = new JScrollPane(resultArea);
        JScrollPane groupScroll = new JScrollPane(groupTable);
        groupScroll.setPreferredSize(new Dimension(0, 120));

        modeTabs = new JTabbedPane();

        JPanel matchPanel = new JPanel(new BorderLayout());
        JSplitPane matchSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                resultScroll, groupScroll);
        matchSplit.setResizeWeight(0.7);
        matchSplit.setContinuousLayout(true);
        matchPanel.add(matchSplit, BorderLayout.CENTER);
        modeTabs.addTab("\u5339\u914D", matchPanel);

        JPanel replacePanel = new JPanel(new BorderLayout(4, 4));
        JPanel replaceTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        replaceTop.add(new JLabel("\u66FF\u6362\u4E3A:"));
        replaceTop.add(replaceField);
        JButton replaceAllBtn = new JButton("\u66FF\u6362\u5168\u90E8");
        replaceAllBtn.addActionListener(e -> runReplace());
        replaceTop.add(replaceAllBtn);
        replacePanel.add(replaceTop, BorderLayout.NORTH);
        replacePanel.add(new JScrollPane(replaceResultArea), BorderLayout.CENTER);
        modeTabs.addTab("\u66FF\u6362", replacePanel);

        JPanel centerInner = new JPanel(new BorderLayout());
        centerInner.add(wrapTitled("\u6D4B\u8BD5\u6587\u672C", testScroll), BorderLayout.NORTH);
        centerInner.add(modeTabs, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                centerInner,
                wrapTitled("\u5E38\u7528\u6B63\u5219", buildFavoritesPanel()));
        mainSplit.setResizeWeight(0.75);
        mainSplit.setContinuousLayout(true);

        setLayout(new BorderLayout());
        add(northPanel, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
    }

    private JComponent buildFavoritesPanel() {
        for (String[] fav : FAVORITES) {
            favoriteModel.addElement(fav[0] + "  \u2014  " + fav[1]);
        }
        favoriteList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) favoriteSelected();
            }
        });
        return new JScrollPane(favoriteList);
    }

    private void runTest() {
        String regex = regexField.getText();
        String text = testArea.getText();
        if (regex.isEmpty() || text.isEmpty()) return;

        try {
            int flags = 0;
            if (caseCb.isSelected()) flags |= Pattern.CASE_INSENSITIVE;
            if (multilineCb.isSelected()) flags |= Pattern.MULTILINE;
            if (dotallCb.isSelected()) flags |= Pattern.DOTALL;

            Pattern p = Pattern.compile(regex, flags);
            Matcher m = p.matcher(text);

            StringBuilder sb = new StringBuilder();
            int count = 0;
            groupModel.setGroups(m.groupCount());

            while (m.find()) {
                count++;
                sb.append("\u5339\u914D #").append(count).append(": [")
                        .append(m.start()).append("-").append(m.end()).append("] ")
                        .append(m.group()).append("\n");
                for (int i = 0; i <= m.groupCount(); i++) {
                    groupModel.setGroupValue(i, m.group(i));
                }
            }
            if (count == 0) {
                sb.append("\u672A\u627E\u5230\u5339\u914D");
            } else {
                sb.append("\n\u5171 ").append(count).append(" \u5904\u5339\u914D");
            }
            resultArea.setText(sb.toString());
        } catch (Exception ex) {
            resultArea.setText("\u6B63\u5219\u9519\u8BEF: " + ex.getMessage());
        }
    }

    private void runReplace() {
        String regex = regexField.getText();
        String text = testArea.getText();
        String replacement = replaceField.getText();
        if (regex.isEmpty() || text.isEmpty()) return;

        try {
            int flags = 0;
            if (caseCb.isSelected()) flags |= Pattern.CASE_INSENSITIVE;
            if (multilineCb.isSelected()) flags |= Pattern.MULTILINE;
            if (dotallCb.isSelected()) flags |= Pattern.DOTALL;

            Pattern p = Pattern.compile(regex, flags);
            String result = p.matcher(text).replaceAll(replacement);
            replaceResultArea.setText(result);
        } catch (Exception ex) {
            replaceResultArea.setText("\u66FF\u6362\u9519\u8BEF: " + ex.getMessage());
        }
    }

    private void favoriteSelected() {
        int idx = favoriteList.getSelectedIndex();
        if (idx >= 0 && idx < FAVORITES.length) {
            regexField.setText(FAVORITES[idx][1]);
            runTest();
        }
    }
}
