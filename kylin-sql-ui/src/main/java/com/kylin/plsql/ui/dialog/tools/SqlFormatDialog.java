package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.EngineManager;
import com.kylin.plsql.core.format.SqlFormatterEngine;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;

/** SQL formatting dialog with syntax-highlighted input/output and format options */
public class SqlFormatDialog extends BaseToolDialog {
    private final RSyntaxTextArea inputArea;
    private final RSyntaxTextArea outputArea;
    private final FormatOptions formatOptions;
    private final JSplitPane splitPane;
    private final JToggleButton layoutToggleBtn;
    private final JComboBox<SqlFormatterEngine> engineCombo;

    public SqlFormatDialog(Frame owner, FormatOptions formatOptions) {
        super(owner, "SQL 格式化");
        this.formatOptions = formatOptions;
        setSizeRatio(0.7);
        centerOnOwner();

        inputArea = new RSyntaxTextArea();
        inputArea.setSyntaxEditingStyle("text/plsql");
        inputArea.setCodeFoldingEnabled(true);

        outputArea = new RSyntaxTextArea();
        outputArea.setSyntaxEditingStyle("text/plsql");
        outputArea.setEditable(false);
        outputArea.setCodeFoldingEnabled(true);
        applyOutputTheme();

        JScrollPane inputScroll = new JScrollPane(inputArea);
        JScrollPane outputScroll = new JScrollPane(outputArea);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("输入 SQL", inputScroll),
                wrapTitled("格式化结果", outputScroll));
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);

        layoutToggleBtn = new JToggleButton("⇔ 垂直布局");
        layoutToggleBtn.addActionListener(e -> toggleLayout());

        engineCombo = new JComboBox<>();
        for (SqlFormatterEngine e : EngineManager.getEngines()) {
            engineCombo.addItem(e);
        }
        engineCombo.setSelectedItem(EngineManager.getCurrent());
        engineCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SqlFormatterEngine e) {
                    setText(e.getDisplayName());
                }
                return c;
            }
        });
        engineCombo.addActionListener(e -> {
            EngineManager.setCurrent(engineCombo.getSelectedIndex());
        });
        engineCombo.setToolTipText("选择格式化引擎");

        JButton formatBtn = new JButton("格式化 (Ctrl+Enter)");
        formatBtn.addActionListener(e -> doFormat());
        InputMap im = formatBtn.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = formatBtn.getActionMap();
        im.put(KeyStroke.getKeyStroke("ctrl ENTER"), "format");
        am.put("format", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { doFormat(); }
        });

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        southPanel.add(new JLabel("引擎:"));
        southPanel.add(engineCombo);
        southPanel.add(layoutToggleBtn);
        southPanel.add(formatBtn);

        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        applyTheme();
    }

    private void doFormat() {
        String input = inputArea.getText();
        if (input == null || input.trim().isEmpty()) return;
        try {
            String result = EngineManager.format(input);
            outputArea.setText(result);
        } catch (Exception ex) {
            outputArea.setText("格式化失败: " + ex.getMessage());
        }
    }

    private void applyOutputTheme() {
        Color bg = theme.resolve("bg.panel");
        boolean dark = bg.getRed() + bg.getGreen() + bg.getBlue() < 382;
        String themePath = dark
                ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try (InputStream is = getClass().getResourceAsStream(themePath)) {
            if (is != null) {
                Theme.load(is).apply(outputArea);
            }
        } catch (Exception ignored) {
            outputArea.setBackground(theme.resolve("bg.editor"));
            outputArea.setForeground(theme.resolve("fg.main"));
        }
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        inputArea.setBackground(theme.resolve("bg.editor"));
        inputArea.setForeground(theme.resolve("fg.main"));
        splitPane.setBackground(theme.resolve("bg.panel"));
        layoutToggleBtn.setBackground(theme.resolve("bg.toolbar"));
        applyOutputTheme();
    }

    private void toggleLayout() {
        boolean horizontal = splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
        splitPane.setOrientation(horizontal
                ? JSplitPane.VERTICAL_SPLIT
                : JSplitPane.HORIZONTAL_SPLIT);
        layoutToggleBtn.setText(horizontal ? "⇕ 水平布局" : "⇔ 垂直布局");
    }
}
