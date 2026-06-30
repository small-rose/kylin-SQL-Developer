package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/** Side-by-side and unified text comparison dialog with colored diff markers */
public class TextDiffDialog extends BaseToolDialog {
    private final JTextArea leftArea;
    private final JTextArea rightArea;
    private final JTextPane sideLeftPane;
    private final JTextPane sideRightPane;
    private final JTextPane unifiedPane;
    private final JCheckBox ignoreWsCb;
    private final JLabel statsLabel;
    private final JTabbedPane viewTabs;

    enum DiffType { EQUAL, INSERT, DELETE, MODIFY }

    static class DiffLine {
        final DiffType type;
        final String leftText;
        final String rightText;
        DiffLine(DiffType type, String leftText, String rightText) {
            this.type = type; this.leftText = leftText; this.rightText = rightText;
        }
    }

    public TextDiffDialog(Frame owner) {
        super(owner, "\u6587\u672C\u6BD4\u8F83");
        setSizeRatio(0.7);
        centerOnOwner();

        leftArea = new JTextArea();
        leftArea.setFont(monoFont());
        rightArea = new JTextArea();
        rightArea.setFont(monoFont());

        sideLeftPane = new JTextPane();
        sideLeftPane.setFont(monoFont());
        sideLeftPane.setEditable(false);
        sideRightPane = new JTextPane();
        sideRightPane.setFont(monoFont());
        sideRightPane.setEditable(false);

        unifiedPane = new JTextPane();
        unifiedPane.setFont(monoFont());
        unifiedPane.setEditable(false);

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

        JScrollPane sideLeftScroll = new JScrollPane(sideLeftPane);
        JScrollPane sideRightScroll = new JScrollPane(sideRightPane);
        JSplitPane sideBySideSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                sideLeftScroll, sideRightScroll);
        sideBySideSplit.setResizeWeight(0.5);
        sideBySideSplit.setContinuousLayout(true);
        viewTabs.addTab("\u5BF9\u7167\u89C6\u56FE", sideBySideSplit);

        JScrollPane unifiedScroll = new JScrollPane(unifiedPane);
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
        applyTheme();
    }

    private void runDiff() {
        String leftText = leftArea.getText();
        String rightText = rightArea.getText();
        String[] leftLines = leftText.split("\\r?\\n", -1);
        String[] rightLines = rightText.split("\\r?\\n", -1);

        List<DiffLine> diffs = computeDiff(leftLines, rightLines);

        int adds = 0, dels = 0, mods = 0;
        for (DiffLine d : diffs) {
            switch (d.type) {
                case INSERT: adds++; break;
                case DELETE: dels++; break;
                case MODIFY: mods++; break;
            }
        }
        statsLabel.setText("\u5DEE\u5F02: +" + adds + " -" + dels + " ~" + mods);

        renderSideBySide(diffs);
        renderUnified(diffs);
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

        java.util.Stack<DiffLine> stack = new java.util.Stack<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && linesEqual(left[i - 1], right[j - 1])) {
                stack.push(new DiffLine(DiffType.EQUAL, left[i - 1], right[j - 1]));
                i--; j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                stack.push(new DiffLine(DiffType.INSERT, "", right[j - 1]));
                j--;
            } else if (i > 0) {
                if (j > 0 && !linesEqual(left[i - 1], right[j - 1])) {
                    stack.push(new DiffLine(DiffType.MODIFY, left[i - 1], right[j - 1]));
                    i--; j--;
                } else {
                    stack.push(new DiffLine(DiffType.DELETE, left[i - 1], ""));
                    i--;
                }
            }
        }

        List<DiffLine> result = new ArrayList<>();
        while (!stack.isEmpty()) result.add(stack.pop());
        return result;
    }

    private void renderSideBySide(List<DiffLine> diffs) {
        sideLeftPane.setText("");
        sideRightPane.setText("");
        StyledDocument leftDoc = sideLeftPane.getStyledDocument();
        StyledDocument rightDoc = sideRightPane.getStyledDocument();

        for (DiffLine d : diffs) {
            appendSideLine(leftDoc, d.leftText, d.type, false);
            appendSideLine(rightDoc, d.rightText, d.type, true);
        }
    }

    private void appendSideLine(StyledDocument doc, String text, DiffType type, boolean isRight) {
        Color bg;
        if (type == DiffType.EQUAL) {
            bg = null;
        } else if (type == DiffType.DELETE && !isRight) {
            bg = new Color(0xFFD7D7);
        } else if (type == DiffType.INSERT && isRight) {
            bg = new Color(0xD7FFD7);
        } else if (type == DiffType.MODIFY) {
            bg = new Color(0xFFF5CC);
        } else {
            bg = null;
        }

        Style style = doc.addStyle("s" + System.nanoTime(), null);
        if (bg != null) {
            StyleConstants.setBackground(style, bg);
        }
        try {
            doc.insertString(doc.getLength(), text + "\n", style);
        } catch (BadLocationException ignored) {}
    }

    private void renderUnified(List<DiffLine> diffs) {
        unifiedPane.setText("");
        StyledDocument doc = unifiedPane.getStyledDocument();

        for (DiffLine d : diffs) {
            String prefix;
            Color bg;
            switch (d.type) {
                case EQUAL:
                    prefix = "  ";
                    bg = null;
                    break;
                case DELETE:
                    prefix = "- ";
                    bg = new Color(0xFFD7D7);
                    break;
                case INSERT:
                    prefix = "+ ";
                    bg = new Color(0xD7FFD7);
                    break;
                case MODIFY:
                    prefix = "~ ";
                    bg = new Color(0xFFF5CC);
                    break;
                default:
                    prefix = "  ";
                    bg = null;
            }
            String displayText = d.type == DiffType.MODIFY
                    ? d.leftText + " | " + d.rightText
                    : d.type == DiffType.EQUAL ? d.leftText
                    : d.type == DiffType.DELETE ? d.leftText
                    : d.rightText;

            Style style = doc.addStyle("u" + System.nanoTime(), null);
            if (bg != null) {
                StyleConstants.setBackground(style, bg);
            }
            try {
                doc.insertString(doc.getLength(), prefix + displayText + "\n", style);
            } catch (BadLocationException ignored) {}
        }
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        Color editorBg = theme.resolve("bg.editor");
        Color editorFg = theme.resolve("fg.main");
        leftArea.setBackground(editorBg);
        leftArea.setForeground(editorFg);
        rightArea.setBackground(editorBg);
        rightArea.setForeground(editorFg);
        sideLeftPane.setBackground(editorBg);
        sideLeftPane.setForeground(editorFg);
        sideRightPane.setBackground(editorBg);
        sideRightPane.setForeground(editorFg);
        unifiedPane.setBackground(editorBg);
        unifiedPane.setForeground(editorFg);
        statsLabel.setForeground(theme.resolve("fg.muted"));
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
