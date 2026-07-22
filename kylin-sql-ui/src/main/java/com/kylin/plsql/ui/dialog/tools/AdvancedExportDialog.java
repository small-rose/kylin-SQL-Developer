package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.export.ExportEngine;
import com.kylin.plsql.core.export.ExportOptions;
import com.kylin.plsql.ui.component.common.ToastManager;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

/** Advanced result set export dialog supporting INSERT, JSON, XML, Markdown formats */
public class AdvancedExportDialog extends BaseToolDialog {
    private final TableModel sourceModel;
    private final JTextArea outputArea;
    private final JComboBox<String> formatCombo;
    private final JComboBox<String> dialectCombo;
    private final JTextField tableNameField;
    private final JCheckBox headerCb;
    private final JComboBox<String> charsetCombo;
    private final List<Integer> selectedColumns;
    private final JTextField dateFormatField;
    private final JTextField nullPlaceholder;
    private final JSpinner maxBlobSize;
    private final JPanel colCheckPanel;
    private final String connName;
    private JComboBox<String> sourceCombo;
    private JComboBox<String> schemaCombo;
    private JComboBox<String> tableCombo;
    private JTextArea sqlTextArea;
    private JScrollPane sqlScroll;
    private JSpinner maxRowsSpinner;
    private JPanel configPanel; 

    public AdvancedExportDialog(Frame owner, TableModel sourceModel) {
        this(owner, sourceModel, null);
    }

    public AdvancedExportDialog(Frame owner, TableModel sourceModel, String connName) {
        super(owner, "高级导出");
        this.connName = connName;
        this.sourceModel = sourceModel;
        setSizeRatio(0.7);
        centerOnOwner();

        selectedColumns = new ArrayList<>();
        for (int i = 0; i < sourceModel.getColumnCount(); i++) {
            selectedColumns.add(i);
        }

        dialectCombo = new JComboBox<>(
                new String[]{"Oracle", "MySQL", "PostgreSQL", "ANSI SQL"});
        dialectCombo.addActionListener(e -> doExport());

        tableNameField = new JTextField("", 15);

        formatCombo = new JComboBox<>(
                new String[]{"INSERT", "CSV", "JSON", "XML", "Markdown"});
        formatCombo.addActionListener(e -> {
            boolean insert = "INSERT".equals(formatCombo.getSelectedItem());
            tableNameField.setEnabled(insert);
            dialectCombo.setEnabled(insert);
            doExport();
        });

        headerCb = new JCheckBox("包含列头");
        headerCb.setSelected(true);
        headerCb.addActionListener(e -> doExport());

        charsetCombo = new JComboBox<>(
                new String[]{"UTF-8", "GBK", "ISO-8859-1", "UTF-16"});

        dateFormatField = new JTextField("yyyy-MM-dd HH:mm:ss", 15);
        dateFormatField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { doExport(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { doExport(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { doExport(); }
        });

        nullPlaceholder = new JTextField("NULL", 10);
        nullPlaceholder.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { doExport(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { doExport(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { doExport(); }
        });

        maxBlobSize = new JSpinner(new SpinnerNumberModel(64, 1, 10240, 8));
        maxBlobSize.addChangeListener(e -> doExport());

        outputArea = new JTextArea();
        outputArea.setFont(monoFont());
        outputArea.setEditable(false);

        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("导出设置"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 5);

        // ── Row 0: 数据来源 + schema + 表 + 最大行 ──
        sourceCombo = new JComboBox<>(new String[]{"当前结果集", "表", "自定义SQL"});
        sourceCombo.addActionListener(e -> toggleSourceMode());
        c.gridy = 0; c.gridx = 0; c.weightx = 0;
        configPanel.add(new JLabel("数据来源:"), c);
        c.gridx = 1; c.weightx = 0.25;
        configPanel.add(sourceCombo, c);

        schemaCombo = new JComboBox<>();
        schemaCombo.setEditable(true);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(new JLabel("模式:"), c);
        c.gridx = 3; c.weightx = 0.3;
        configPanel.add(schemaCombo, c);

        tableCombo = new JComboBox<>();
        tableCombo.setEditable(true);
        c.gridx = 4; c.weightx = 0;
        configPanel.add(new JLabel("表:"), c);
        c.gridx = 5; c.weightx = 0.5;
        configPanel.add(tableCombo, c);

        c.gridx = 6; c.weightx = 0;
        configPanel.add(new JLabel("最大行:"), c);
        c.gridx = 7; c.weightx = 0.25;
        maxRowsSpinner = new JSpinner(new SpinnerNumberModel(10000, 0, 10000000, 1000));
        configPanel.add(maxRowsSpinner, c);

        // ── Row 1: SQL 文本域（仅在自定义SQL时可见）──
        sqlTextArea = new JTextArea(3, 30);
        sqlTextArea.setFont(monoFont());
        sqlScroll = new JScrollPane(sqlTextArea);
        c.gridy = 1; c.gridx = 0; c.gridwidth = 8; c.weightx = 1;
        configPanel.add(sqlScroll, c);
        sqlScroll.setVisible(false);

        // ── Row 2: 导出格式 + 表名 + 编码 ──
        c.gridy = 2; c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        configPanel.add(new JLabel("导出格式:"), c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(formatCombo, c);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(new JLabel("表名:"), c);
        c.gridx = 3; c.weightx = 0.5;
        configPanel.add(tableNameField, c);
        c.gridx = 4; c.weightx = 0;
        configPanel.add(new JLabel("编码:"), c);
        c.gridx = 5; c.weightx = 0.3;
        configPanel.add(charsetCombo, c);

        // ── Row 3: 方言 + 日期格式 + NULL值 ──
        c.gridy = 3; c.gridx = 0; c.weightx = 0;
        configPanel.add(new JLabel("方言:"), c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(dialectCombo, c);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(new JLabel("日期格式:"), c);
        c.gridx = 3; c.weightx = 0.5;
        configPanel.add(dateFormatField, c);
        c.gridx = 4; c.weightx = 0;
        configPanel.add(new JLabel("NULL值:"), c);
        c.gridx = 5; c.weightx = 0.3;
        configPanel.add(nullPlaceholder, c);

        // ── Row 4: BLOB上限 + 最大行 + 包含列头 ──
        c.gridy = 4; c.gridx = 0; c.weightx = 0;
        configPanel.add(new JLabel("BLOB上限:"), c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(maxBlobSize, c);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(headerCb, c);

        JLabel hintLabel = new JLabel(
                "选择要导出的列，切换格式自动预览 | INSERT 格式需填写表名 | 大数据量建议使用异步导出");
        hintLabel.setFont(FontManager.getInstance().resolve("font.dialog"));
        hintLabel.setOpaque(true);
        hintLabel.setBackground(theme.resolve("bg.panel"));
        hintLabel.setForeground(theme.resolve("fg.muted"));

        colCheckPanel = new JPanel();
        colCheckPanel.setLayout(new BoxLayout(colCheckPanel, BoxLayout.Y_AXIS));
        rebuildColumnCheckboxes();
        JScrollPane colScroll = new JScrollPane(colCheckPanel);

        JScrollPane outputScroll = new JScrollPane(outputArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("选择导出列", colScroll),
                wrapTitled("导出预览", outputScroll));
        splitPane.setResizeWeight(0.25);
        splitPane.setContinuousLayout(true);

        JButton syncBtn = new JButton("同步导出");
        syncBtn.addActionListener(e -> {
            if (sourceModel.getRowCount() > 1000) {
                int opt = JOptionPane.showOptionDialog(this,
                        "数据量较大（" + sourceModel.getRowCount() + " 行），是否改为异步导出？",
                        "确认", JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null,
                        new String[]{"异步", "同步", "取消"}, "异步");
                if (opt == 0) { doExportAsync(); return; }
                if (opt == 2) return;
            }
            String content = outputArea.getText();
            if (!content.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(content), null);
                ToastManager.show(this, "已复制到剪贴板");
            }
        });

        JButton asyncBtn = new JButton("异步导出");
        asyncBtn.addActionListener(e -> doExportAsync());

        JButton saveBtn = new JButton("保存文件...");
        saveBtn.addActionListener(e -> saveToFile());

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        southPanel.add(syncBtn);
        southPanel.add(asyncBtn);
        southPanel.add(saveBtn);

        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.add(configPanel, BorderLayout.NORTH);
        northWrapper.add(hintLabel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(northWrapper, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        applyTheme();
        javax.swing.SwingUtilities.invokeLater(this::doExport);
    }

    /** 切换数据来源模式。 */
    private void toggleSourceMode() {
        String src = (String) sourceCombo.getSelectedItem();
        boolean isTable = "表".equals(src);
        boolean isSql = "自定义SQL".equals(src);
        schemaCombo.setVisible(isTable);
        tableCombo.setVisible(isTable);
        for (Component c : configPanel.getComponents()) {
            if (c instanceof JLabel l && ("模式:".equals(l.getText()) || "表:".equals(l.getText()))) {
                l.setVisible(isTable);
            }
        }
        sqlScroll.setVisible(isSql);
        if (isSql && sqlTextArea.getText().isEmpty()) {
            sqlTextArea.setText("SELECT * FROM " + schemaCombo.getSelectedItem() + "." + tableCombo.getSelectedItem());
        }
    }

    private void rebuildColumnCheckboxes() {
        colCheckPanel.removeAll();
        for (int i = 0; i < sourceModel.getColumnCount(); i++) {
            JCheckBox cb = new JCheckBox(sourceModel.getColumnName(i));
            cb.setSelected(selectedColumns.contains(i));
            final int colIdx = i;
            cb.addActionListener(e -> {
                if (cb.isSelected()) {
                    if (!selectedColumns.contains(colIdx)) selectedColumns.add(colIdx);
                } else {
                    selectedColumns.remove((Integer) colIdx);
                }
                Collections.sort(selectedColumns);
                doExport();
            });
            colCheckPanel.add(cb);
        }
        colCheckPanel.revalidate();
        colCheckPanel.repaint();
    }

    private void doExport() {
        String format = (String) formatCombo.getSelectedItem();
        if (format == null) return;
        try {
            outputArea.setText(ExportEngine.export(sourceModel, selectedColumns, format, buildOptions()));
        } catch (Exception e) {
            outputArea.setText("导出错误: " + e.getMessage());
        }
    }

    private ExportOptions buildOptions() {
        return new ExportOptions()
            .setTableName(tableNameField.getText().trim())
            .setHeader(headerCb.isSelected())
            .setDateFormat(dateFormatField.getText().trim())
            .setNullPlaceholder(nullPlaceholder.getText())
            .setMaxBlobSize((Integer) maxBlobSize.getValue())
            .setCharset((String) charsetCombo.getSelectedItem())
            .setDialect((String) dialectCombo.getSelectedItem());
    }



    private void doExportAsync() {
        ExportTaskListDialog taskList = ExportTaskListDialog.getInstance(owner);
        ExportOptions opts = buildOptions();
        taskList.submitTask(sourceModel, (String) formatCombo.getSelectedItem(),
                new ArrayList<>(selectedColumns),
                opts.getTableName(), opts.isHeader(),
                Charset.forName(opts.getCharset()),
                opts.getDateFormat(), opts.getNullPlaceholder(),
                opts.getMaxBlobSize(), null);
        taskList.setVisible(true);
    }

    private void saveToFile() {
        JFileChooser chooser = new JFileChooser();
        String format = (String) formatCombo.getSelectedItem();
        String ext = "." + (format != null ? format.toLowerCase() : "txt");
        chooser.setSelectedFile(new File("export" + ext));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String charset = (String) charsetCombo.getSelectedItem();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                w.write(outputArea.getText());
                ToastManager.show(this, "已保存到: " + file.getName());
            } catch (Exception e) {
                ToastManager.showError(this, "保存失败: " + e.getMessage());
            }
        }
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        Color editorBg = theme.resolve("bg.editor");
        Color editorFg = theme.resolve("fg.main");
        outputArea.setBackground(editorBg);
        outputArea.setForeground(editorFg);
        tableNameField.setBackground(editorBg);
        tableNameField.setForeground(editorFg);
        dateFormatField.setBackground(editorBg);
        dateFormatField.setForeground(editorFg);
        nullPlaceholder.setBackground(editorBg);
        nullPlaceholder.setForeground(editorFg);
    }


}
