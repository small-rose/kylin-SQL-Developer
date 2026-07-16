package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;
import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.EngineManager;
import com.kylin.plsql.core.format.SqlFormatterEngine;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlToolsDialog extends BaseToolDialog {
    private final JTextArea inputArea;
    private final JTextArea outputArea;
    private final RSyntaxTextArea fmtInputArea;
    private final RSyntaxTextArea[] fmtOutputAreas;
    private final List<SqlFormatterEngine> engines;
    private final JTabbedPane tabbedPane;
    private final JSplitPane[] splitPanes;
    private final JToggleButton layoutToggleBtn;
    private final JLabel descLabel;
    private final JCheckBox quoteCb;
    private final JButton toInBtn;
    private final JButton fromInBtn;
    private final JButton formatBtn;
    private final JPanel centerRow;
    private JPanel fmtOutputsPanel;
    private JScrollPane[] fmtOutputScrolls;
    private JCheckBox[] fmtToggleCbs;

    private static final String SAMPLE_KEY = "sampleText";

    public SqlToolsDialog(Frame owner, FormatOptions formatOptions, int initialTab) {
        super(owner, "SQL 工具");
        setSizeRatio(0.7);
        centerOnOwner();

        inputArea = new JTextArea(5, 40);
        inputArea.setFont(monoFont());

        outputArea = new JTextArea(5, 40);
        outputArea.setFont(monoFont());
        outputArea.setEditable(true);

        fmtInputArea = new RSyntaxTextArea(5, 40);
        fmtInputArea.setSyntaxEditingStyle("text/plsql");
        fmtInputArea.setCodeFoldingEnabled(true);

        engines = EngineManager.getEngines();
        fmtOutputAreas = new RSyntaxTextArea[engines.size()];
        for (int i = 0; i < fmtOutputAreas.length; i++) {
            fmtOutputAreas[i] = new RSyntaxTextArea(5, 40);
            fmtOutputAreas[i].setSyntaxEditingStyle("text/plsql");
            fmtOutputAreas[i].setCodeFoldingEnabled(true);
        }

        installSample(inputArea, "value1\nvalue2\nvalue3");
        installSample(outputArea, "-- 结果将显示在此处");
        installSample(fmtInputArea,
            "select a.id,b.name from users a\nleft join orders b on a.id=b.user_id\nwhere b.status='ACTIVE'\norder by b.create_time desc");

        tabbedPane = new JTabbedPane();

        JPanel inClausePanel = buildInClausePanel();
        JPanel formatPanel = buildFormatPanel();
        JSplitPane inSplit = (JSplitPane) inClausePanel.getComponent(0);
        JSplitPane fmtSplit = (JSplitPane) formatPanel.getComponent(0);
        splitPanes = new JSplitPane[]{inSplit, fmtSplit};

        tabbedPane.addTab("IN 子句转换", inClausePanel);
        tabbedPane.addTab("SQL 格式化", formatPanel);

        layoutToggleBtn = new JToggleButton("⇕ 垂直布局");
        layoutToggleBtn.addActionListener(e -> toggleLayout());

        descLabel = new JLabel();

        quoteCb = new JCheckBox("带引号");
        quoteCb.setSelected(true);
        toInBtn = new JButton("> IN 子句");
        toInBtn.addActionListener(e -> outputArea.setText(toInClause(inputArea.getText(), quoteCb.isSelected())));
        fromInBtn = new JButton("< 还原");
        fromInBtn.addActionListener(e -> outputArea.setText(fromInClause(inputArea.getText())));

        formatBtn = new JButton("格式化 (Ctrl+Enter)");
        formatBtn.addActionListener(e -> doFormat());
        InputMap im = formatBtn.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = formatBtn.getActionMap();
        im.put(KeyStroke.getKeyStroke("ctrl ENTER"), "fmt");
        am.put("fmt", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { doFormat(); }
        });

        JPanel southPanel = new JPanel(new BorderLayout(4, 0));
        centerRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        tabbedPane.addChangeListener(e -> onTabChanged());
        onTabChanged();
        southPanel.add(descLabel, BorderLayout.WEST);
        southPanel.add(centerRow, BorderLayout.CENTER);
        southPanel.add(layoutToggleBtn, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        applyTheme();
        tabbedPane.setSelectedIndex(initialTab);

        if (fmtOutputScrolls != null) rebuildOutputsGrid();
        SwingUtilities.invokeLater(() -> {
            tabbedPane.requestFocusInWindow();
            doFormat(true);
        });
    }

    private void installSample(JTextComponent comp, String sample) {
        comp.putClientProperty(SAMPLE_KEY, sample);
        comp.setText(sample);
        comp.setForeground(theme.resolve("fg.muted"));
        comp.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                String saved = (String) comp.getClientProperty(SAMPLE_KEY);
                if (saved != null && saved.equals(comp.getText())) {
                    comp.setText("");
                    comp.setForeground(theme.resolve("fg.main"));
                }
            }
            @Override
            public void focusLost(FocusEvent e) {
                if (comp.getText().isEmpty()) {
                    String saved = (String) comp.getClientProperty(SAMPLE_KEY);
                    if (saved != null) {
                        comp.setText(saved);
                        comp.setForeground(theme.resolve("fg.muted"));
                    }
                }
            }
        });
    }

    private JPanel buildInClausePanel() {
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("输入"));
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("结果"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputScroll, outputScroll);
        split.setResizeWeight(0.5);
        split.setContinuousLayout(true);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFormatPanel() {
        JScrollPane inputScroll = new JScrollPane(fmtInputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("输入 SQL"));

        fmtOutputScrolls = new JScrollPane[fmtOutputAreas.length];
        fmtToggleCbs = new JCheckBox[fmtOutputAreas.length];
        String saved = com.kylin.plsql.core.config.ConfigManager.getInstance().getPreference("SqlToolsDialog.fmtVisible", "");
        boolean[] visible = new boolean[fmtOutputAreas.length];
        if (saved.isEmpty()) {
            for (int i = 0; i < fmtOutputAreas.length; i++) visible[i] = true;
        } else {
            String[] parts = saved.split(",");
            for (String p : parts) {
                try { int idx = Integer.parseInt(p.trim()); if (idx >= 0 && idx < visible.length) visible[idx] = true; } catch (Exception ignored) {}
            }
        }

        fmtOutputsPanel = new JPanel();
        for (int i = 0; i < fmtOutputAreas.length; i++) {
            int idx = i;
            JScrollPane sp = new JScrollPane(fmtOutputAreas[i]);
            sp.setBorder(BorderFactory.createTitledBorder(engines.get(i).getDisplayName()));
            fmtOutputScrolls[i] = sp;
            fmtToggleCbs[i] = new JCheckBox(engines.get(i).getDisplayName(), visible[i]);
            fmtToggleCbs[i].addActionListener(e -> toggleEngineOutput(idx));
        }

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputScroll, fmtOutputsPanel);
        split.setResizeWeight(0.4);
        split.setContinuousLayout(true);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void doFormat() {
        doFormat(false);
    }

    private void doFormat(boolean force) {
        String input = fmtInputArea.getText();
        String saved = (String) fmtInputArea.getClientProperty(SAMPLE_KEY);
        if (!force && saved != null && saved.equals(input)) return;
        if (input == null || input.trim().isEmpty()) return;
        for (int i = 0; i < engines.size(); i++) {
            try {
                EngineManager.setCurrent(i);
                String result = EngineManager.format(input);
                fmtOutputAreas[i].setText(result);
                fmtOutputAreas[i].setForeground(theme.resolve("fg.main"));
            } catch (Exception ex) {
                fmtOutputAreas[i].setText("格式化失败: " + ex.getMessage());
            }
        }
    }

    private void onTabChanged() {
        updateDesc();
        updateToolbar();
        tabbedPane.requestFocusInWindow();
    }

    private void updateToolbar() {
        int idx = tabbedPane.getSelectedIndex();
        centerRow.removeAll();
        if (idx == 1) {
            centerRow.add(formatBtn);
            centerRow.add(new JLabel(" 显示:"));
            if (fmtToggleCbs != null) {
                for (JCheckBox cb : fmtToggleCbs) centerRow.add(cb);
            }
        } else {
            centerRow.add(quoteCb);
            centerRow.add(toInBtn);
            centerRow.add(fromInBtn);
        }
        centerRow.revalidate();
        centerRow.repaint();
    }

    private void toggleEngineOutput(int idx) {
        rebuildOutputsGrid();
        saveEngineVisibility();
    }

    private void rebuildOutputsGrid() {
        fmtOutputsPanel.removeAll();
        boolean horizontal = splitPanes[1].getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
        int visible = 0;
        for (int i = 0; i < fmtOutputScrolls.length; i++) {
            if (fmtToggleCbs[i].isSelected()) {
                fmtOutputsPanel.add(fmtOutputScrolls[i]);
                visible++;
            }
        }
        if (visible == 0) {
            fmtOutputsPanel.setLayout(new GridLayout(1, 1));
            fmtOutputsPanel.add(new JLabel("所有引擎已隐藏，请在工具栏勾选显示", SwingConstants.CENTER));
        } else {
            fmtOutputsPanel.setLayout(new GridLayout(horizontal ? visible : 1, horizontal ? 1 : visible, horizontal ? 0 : 4, horizontal ? 4 : 0));
        }
        fmtOutputsPanel.revalidate();
        fmtOutputsPanel.repaint();
    }

    private void saveEngineVisibility() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fmtToggleCbs.length; i++) {
            if (fmtToggleCbs[i].isSelected()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(i);
            }
        }
        com.kylin.plsql.core.config.ConfigManager.getInstance().setPreference("SqlToolsDialog.fmtVisible", sb.toString());
    }

    private void updateDesc() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx == 0) {
            descLabel.setText("功能说明：多行值与 IN 子句格式互相转换");
        } else {
            descLabel.setText("功能说明：SQL 格式化（3 引擎结果对比）");
        }
    }

    private void toggleLayout() {
        int idx = tabbedPane.getSelectedIndex();
        JSplitPane split = splitPanes[idx];
        boolean horizontal = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
        split.setOrientation(horizontal ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.4);
        // 按钮文字指示下次点击切换到的方向
        layoutToggleBtn.setText(split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? "⇕ 垂直布局" : "⇔ 水平布局");
        if (idx == 1 && fmtOutputScrolls != null) rebuildOutputsGrid();
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        Color editorBg = theme.resolve("bg.editor");
        Color fg = theme.resolve("fg.main");
        Color muted = theme.resolve("fg.muted");
        inputArea.setBackground(editorBg);
        inputArea.setForeground(fg);
        outputArea.setBackground(editorBg);
        outputArea.setForeground(fg);
        descLabel.setForeground(muted);
        applyRstaTheme(fmtInputArea);
        for (RSyntaxTextArea area : fmtOutputAreas) applyRstaTheme(area);
        if (splitPanes != null && splitPanes.length > 1) {
            splitPanes[1].setBackground(theme.resolve("bg.panel"));
        }
    }

    private void applyRstaTheme(RSyntaxTextArea area) {
        Color bg = theme.resolve("bg.panel");
        boolean dark = bg.getRed() + bg.getGreen() + bg.getBlue() < 382;
        String path = dark
                ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                Theme.load(is).apply(area);
            }
        } catch (Exception ignored) {
            area.setBackground(theme.resolve("bg.editor"));
            area.setForeground(theme.resolve("fg.main"));
        }
        String saved = (String) area.getClientProperty(SAMPLE_KEY);
        if (saved != null && saved.equals(area.getText())) {
            area.setForeground(theme.resolve("fg.muted"));
        }
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
