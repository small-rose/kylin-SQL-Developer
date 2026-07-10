package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real-time regular expression testing dialog with match highlighting */
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
        {"邮箱", "^[\\w.-]+@[\\w.-]+\\.\\w{2,}$"},
        {"手机号", "^1[3-9]\\d{9}$"},
        {"URL", "^https?://[\\w.-]+(/\\S*)?$"},
        {"IP 地址", "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"},
        {"匹配中文", "[\u4e00-\u9fa5]+"},
        {"身份证号", "^\\d{17}[\\dXx]$"},
        {"日期 (YYYY-MM-DD)", "^\\d{4}-\\d{2}-\\d{2}$"},
        {"时间 (HH:MM:SS)", "^\\d{2}:\\d{2}:\\d{2}$"},
        {"邮编", "^\\d{6}$"},
        {"HTML 标签", "<[^>]+>"},
        {"空白行", "^\\s*$"},
        {"颜色十六进制", "^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$"},
        {"用户名", "^\\w{3,20}$"},
        {"文件扩展名", "\\.\\w+$"},
        {"数字", "^-?\\d+(\\.\\d+)?$"},
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
            return col == 0 ? "组号" : "值";
        }

        @Override public Object getValueAt(int row, int col) {
            return data.get(row)[col];
        }
    }

    public RegexTesterDialog(Frame owner) {
        super(owner, "正则测试器");
        setSizeRatio(0.7);
        centerOnOwner();

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

        caseCb = new JCheckBox("忽略大小写");
        multilineCb = new JCheckBox("多行模式");
        dotallCb = new JCheckBox("DOTALL 模式");

        JButton testBtn = new JButton("测试");
        testBtn.addActionListener(e -> runTest());

        groupModel = new GroupTableModel();
        groupTable = new JTable(groupModel);

        JPanel northPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 5);
        c.gridx = 0; c.gridy = 0; c.gridwidth = 7; c.weightx = 1;
        northPanel.add(new JLabel("正则表达式:"), c);
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
        modeTabs.addTab("匹配", matchPanel);

        JPanel replacePanel = new JPanel(new BorderLayout(4, 4));
        JPanel replaceTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        replaceTop.add(new JLabel("替换为:"));
        replaceTop.add(replaceField);
        JButton replaceAllBtn = new JButton("替换全部");
        replaceAllBtn.addActionListener(e -> runReplace());
        replaceTop.add(replaceAllBtn);
        replacePanel.add(replaceTop, BorderLayout.NORTH);
        replacePanel.add(new JScrollPane(replaceResultArea), BorderLayout.CENTER);
        modeTabs.addTab("替换", replacePanel);

        JPanel centerInner = new JPanel(new BorderLayout());
        centerInner.add(wrapTitled("测试文本", testScroll), BorderLayout.NORTH);
        centerInner.add(modeTabs, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                centerInner,
                wrapTitled("常用正则", buildFavoritesPanel()));
        mainSplit.setResizeWeight(0.75);
        mainSplit.setContinuousLayout(true);

        setLayout(new BorderLayout());
        add(northPanel, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        applyTheme();
    }

    private JComponent buildFavoritesPanel() {
        for (String[] fav : FAVORITES) {
            favoriteModel.addElement(fav[0] + "  —  " + fav[1]);
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
                sb.append("匹配 #").append(count).append(": [")
                        .append(m.start()).append("-").append(m.end()).append("] ")
                        .append(m.group()).append("\n");
                for (int i = 0; i <= m.groupCount(); i++) {
                    groupModel.setGroupValue(i, m.group(i));
                }
            }
            if (count == 0) {
                sb.append("未找到匹配");
            } else {
                sb.append("\n共 ").append(count).append(" 处匹配");
            }
            resultArea.setText(sb.toString());
        } catch (Exception ex) {
            resultArea.setText("正则错误: " + ex.getMessage());
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
            replaceResultArea.setText("替换错误: " + ex.getMessage());
        }
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        Color editorBg = theme.resolve("bg.editor");
        Color editorFg = theme.resolve("fg.main");
        testArea.setBackground(editorBg);
        testArea.setForeground(editorFg);
        resultArea.setBackground(editorBg);
        resultArea.setForeground(editorFg);
        replaceResultArea.setBackground(editorBg);
        replaceResultArea.setForeground(editorFg);
        regexField.setBackground(editorBg);
        regexField.setForeground(editorFg);
        replaceField.setBackground(editorBg);
        replaceField.setForeground(editorFg);
        favoriteList.setBackground(theme.resolve("list.bg"));
        favoriteList.setForeground(theme.resolve("list.fg"));
        groupTable.setBackground(theme.resolve("list.bg"));
        groupTable.setForeground(theme.resolve("list.fg"));
    }

    private void favoriteSelected() {
        int idx = favoriteList.getSelectedIndex();
        if (idx >= 0 && idx < FAVORITES.length) {
            regexField.setText(FAVORITES[idx][1]);
            runTest();
        }
    }
}
