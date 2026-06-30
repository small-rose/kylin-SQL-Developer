package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.SqlFormatter;
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

    public SqlFormatDialog(Frame owner, FormatOptions formatOptions) {
        super(owner, "SQL \u683C\u5F0F\u5316");
        this.formatOptions = formatOptions;
        setSizeRatio(0.7);
        centerOnOwner();

        inputArea = new RSyntaxTextArea();
        inputArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        inputArea.setCodeFoldingEnabled(true);

        outputArea = new RSyntaxTextArea();
        outputArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        outputArea.setEditable(false);
        outputArea.setCodeFoldingEnabled(true);
        applyOutputTheme();

        JScrollPane inputScroll = new JScrollPane(inputArea);
        JScrollPane outputScroll = new JScrollPane(outputArea);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("\u8F93\u5165 SQL", inputScroll),
                wrapTitled("\u683C\u5F0F\u5316\u7ED3\u679C", outputScroll));
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);

        layoutToggleBtn = new JToggleButton("\u21D4 \u5782\u76F4\u5E03\u5C40");
        layoutToggleBtn.addActionListener(e -> toggleLayout());

        JButton formatBtn = new JButton("\u683C\u5F0F\u5316 (Ctrl+Enter)");
        formatBtn.addActionListener(e -> doFormat());
        InputMap im = formatBtn.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = formatBtn.getActionMap();
        im.put(KeyStroke.getKeyStroke("ctrl ENTER"), "format");
        am.put("format", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { doFormat(); }
        });

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
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
        String result = SqlFormatter.format(input, formatOptions);
        outputArea.setText(result);
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
        layoutToggleBtn.setText(horizontal ? "\u21D5 \u6C34\u5E73\u5E03\u5C40" : "\u21D4 \u5782\u76F4\u5E03\u5C40");
    }
}
