package com.kylin.plsql.ui.dialog;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class TextDiffDialog extends BaseToolDialog {
    private final JTextArea leftArea;
    private final JTextArea rightArea;
    private final JTextArea resultArea;
    private final JCheckBox ignoreWsCb;
    private final JLabel statsLabel;
    private final JTabbedPane viewTabs;

    enum DiffType { EQUAL, INSERT, DELETE, MODIFY }

    static class DiffLine {
        final DiffType type;
        final String text;
        DiffLine(DiffType type, String text) { this.type = type; this.text = text; }
    }

    public TextDiffDialog(Frame owner) {
        super(owner, "\u6587\u672C\u6BD4\u8F83");
        setSizeRatio(0.7);

        leftArea = new JTextArea();
        leftArea.setFont(monoFont());
        rightArea = new JTextArea();
        rightArea.setFont(monoFont());

        resultArea = new JTextArea();
        resultArea.setFont(monoFont());
        resultArea.setEditable(false);

        ignoreWsCb = new JCheckBox("\u5FFD\u7565\u7A7A\u767D");

        statsLabel = new JLabel(" ");

        JButton openLeftBtn = new JButton("\u6253\u5F00\u5DE6\u4FA7\u6587\u4EF6");
        openLeftBtn.addActionListener(e -> loadFile(leftArea));
        JButton openRightBtn = new JButton("\u6253\u5F00\u53F3\u4FA7\u6587\u4EF6");
        openRightBtn.addActionListener(e -> loadFile(rightArea));
        JButton diffBtn = new JButton("\u6BD4\u8F83");
        diffBtn.addActionListener(e -> runDiff());

        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        northPanel.add(openLeftBtn);
        northPanel.add(openRightBtn);
        northPanel.add(ignoreWsCb);
        northPanel.add(diffBtn);

        JScrollPane leftScroll = new JScrollPane(leftArea);
        JScrollPane rightScroll = new JScrollPane(rightArea);

        JSplitPane inputSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("\u5DE6\u4FA7\u6587\u672C", leftScroll),
                wrapTitled("\u53F3\u4FA7\u6587\u672C", rightScroll));
        inputSplit.setResizeWeight(0.5);
        inputSplit.setContinuousLayout(true);

        viewTabs = new JTabbedPane();
        JScrollPane sideBySideScroll = new JScrollPane(resultArea);
        JScrollPane unifiedScroll = new JScrollPane(resultArea);
        viewTabs.addTab("\u5BF9\u7167\u89C6\u56FE", sideBySideScroll);
        viewTabs.addTab("\u7EDF\u4E00\u89C6\u56FE", unifiedScroll);

        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.add(statsLabel, BorderLayout.NORTH);
        resultPanel.add(viewTabs, BorderLayout.CENTER);

        JSplitPane outerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                inputSplit, resultPanel);
        outerSplit.setResizeWeight(0.55);
        outerSplit.setContinuousLayout(true);

        setLayout(new BorderLayout());
        add(northPanel, BorderLayout.NORTH);
        add(outerSplit, BorderLayout.CENTER);
    }

    private void runDiff() {
        String leftText = leftArea.getText();
        String rightText = rightArea.getText();
        String[] leftLines = leftText.split("\\r?\\n");
        String[] rightLines = rightText.split("\\r?\\n");

        List<DiffLine> diffs = computeDiff(leftLines, rightLines);

        int adds = 0, dels = 0, mods = 0;
        StringBuilder sb = new StringBuilder();
        for (DiffLine d : diffs) {
            switch (d.type) {
                case EQUAL: sb.append("= ").append(d.text).append("\n"); break;
                case DELETE: sb.append("- ").append(d.text).append("\n"); dels++; break;
                case INSERT: sb.append("+ ").append(d.text).append("\n"); adds++; break;
                case MODIFY: sb.append("~ ").append(d.text).append("\n"); mods++; break;
            }
        }
        resultArea.setText(sb.toString());
        statsLabel.setText("\u5DEE\u5F02: +" + adds + " -" + dels + " ~" + mods);
    }

    private boolean linesEqual(String a, String b) {
        if (ignoreWsCb.isSelected()) {
            return a.trim().equals(b.trim());
        }
        return a.equals(b);
    }

    private List<DiffLine> computeDiff(String[] left, String[] right) {
        int m = left.length, n = right.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (linesEqual(left[i - 1], right[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        List<DiffLine> result = new ArrayList<>();
        int i = m, j = n;
        java.util.Stack<DiffLine> stack = new java.util.Stack<>();
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && linesEqual(left[i - 1], right[j - 1])) {
                stack.push(new DiffLine(DiffType.EQUAL, left[i - 1]));
                i--; j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                stack.push(new DiffLine(DiffType.INSERT, right[j - 1]));
                j--;
            } else if (i > 0) {
                if (j > 0 && !linesEqual(left[i - 1], right[j - 1])) {
                    stack.push(new DiffLine(DiffType.MODIFY, left[i - 1] + " | " + right[j - 1]));
                    i--; j--;
                } else {
                    stack.push(new DiffLine(DiffType.DELETE, left[i - 1]));
                    i--;
                }
            }
        }
        while (!stack.isEmpty()) result.add(stack.pop());
        return result;
    }

    private void loadFile(JTextArea target) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("\u6587\u672C\u6587\u4EF6 (*.sql, *.txt, *.java, *.xml)",
                "sql", "txt", "java", "xml", "properties", "md"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                target.setText(sb.toString());
            } catch (Exception ex) {
                com.kylin.plsql.ui.component.common.ToastManager.showError(this,
                        "\u6253\u5F00\u6587\u4EF6\u5931\u8D25: " + ex.getMessage());
            }
        }
    }
}
