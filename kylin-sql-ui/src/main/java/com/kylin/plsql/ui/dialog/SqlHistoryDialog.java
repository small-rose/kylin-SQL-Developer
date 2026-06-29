package com.kylin.plsql.ui.dialog;

import com.kylin.plsql.ui.component.common.ToastManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SqlHistoryDialog extends BaseToolDialog {
    private final DefaultListModel<String> listModel;
    private final JList<String> historyList;
    private final RSyntaxTextArea previewArea;
    private final JLabel countLabel;
    private final JTextField filterField;
    private final Consumer<String> callback;
    private List<String> allEntries;

    public SqlHistoryDialog(Frame owner, List<String> history, Consumer<String> onSelect) {
        super(owner, "SQL \u5386\u53F2");
        this.callback = onSelect;
        this.allEntries = new ArrayList<>(history);
        setSizeRatio(0.7);
        centerOnOwner();

        listModel = new DefaultListModel<>();
        for (String s : history) listModel.addElement(s);

        historyList = new JList<>(listModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showPreview();
        });
        historyList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) selectAndClose();
            }
        });

        previewArea = new RSyntaxTextArea();
        previewArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        previewArea.setEditable(false);
        applyPreviewTheme();

        countLabel = new JLabel("\u5171 " + listModel.size() + " \u6761");

        filterField = new JTextField(30);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterList(); }
            @Override public void removeUpdate(DocumentEvent e) { filterList(); }
            @Override public void changedUpdate(DocumentEvent e) { filterList(); }
        });

        JPanel northPanel = new JPanel(new BorderLayout(8, 0));
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        filterRow.add(new JLabel("\u641C\u7D22:"));
        filterRow.add(filterField);
        northPanel.add(filterRow, BorderLayout.WEST);
        northPanel.add(countLabel, BorderLayout.EAST);

        JScrollPane listScroll = new JScrollPane(historyList);
        JScrollPane previewScroll = new JScrollPane(previewArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("\u5386\u53F2\u8BB0\u5F55", listScroll),
                wrapTitled("\u9884\u89C8", previewScroll));
        splitPane.setResizeWeight(0.35);
        splitPane.setContinuousLayout(true);

        JButton useBtn = new JButton("\u4F7F\u7528\u9009\u4E2D\u7684 SQL");
        useBtn.addActionListener(e -> selectAndClose());

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        southPanel.add(useBtn);

        setLayout(new BorderLayout());
        add(northPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        applyTheme();
    }

    private void filterList() {
        String keyword = filterField.getText().toLowerCase();
        listModel.clear();
        for (String s : allEntries) {
            if (keyword.isEmpty() || s.toLowerCase().contains(keyword)) {
                listModel.addElement(s);
            }
        }
        countLabel.setText("\u5171 " + listModel.size() + " \u6761");
    }

    private void showPreview() {
        String selected = historyList.getSelectedValue();
        if (selected != null) previewArea.setText(selected);
    }

    private void selectAndClose() {
        String selected = historyList.getSelectedValue();
        if (selected != null) {
            callback.accept(selected);
            dispose();
        } else {
            ToastManager.show(this, "\u8BF7\u9009\u62E9\u4E00\u6761 SQL");
        }
    }

    private void applyPreviewTheme() {
        Color bg = theme.resolve("bg.panel");
        boolean dark = bg.getRed() + bg.getGreen() + bg.getBlue() < 382;
        String themePath = dark
                ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try (java.io.InputStream is = getClass().getResourceAsStream(themePath)) {
            if (is != null) {
                org.fife.ui.rsyntaxtextarea.Theme.load(is).apply(previewArea);
            }
        } catch (Exception ignored) {
            previewArea.setBackground(theme.resolve("bg.editor"));
            previewArea.setForeground(theme.resolve("fg.main"));
        }
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        historyList.setBackground(theme.resolve("list.bg"));
        historyList.setForeground(theme.resolve("list.fg"));
        filterField.setBackground(theme.resolve("bg.editor"));
        filterField.setForeground(theme.resolve("fg.main"));
        countLabel.setForeground(theme.resolve("fg.muted"));
        applyPreviewTheme();
    }
}
