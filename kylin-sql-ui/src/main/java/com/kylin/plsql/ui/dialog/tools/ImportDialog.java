package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.service.ImportService;
import com.kylin.plsql.core.service.SchemaService;
import com.kylin.plsql.core.service.ServiceFactory;
import com.kylin.plsql.ui.component.common.ToastManager;
import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class ImportDialog extends BaseToolDialog {
    private final String connName;
    private final ConnectionManager cm;
    private final ServiceFactory serviceFactory;
    private final ImportService importService = new ImportService();
    private final ImportService.ImportConfig importCfg = new ImportService.ImportConfig();

    private final JComboBox<String> schemaCombo = new JComboBox<>();
    private final JComboBox<String> tableCombo = new JComboBox<>();
    private final JTextField filePathField = new JTextField(30);
    private final JComboBox<String> charsetCombo = new JComboBox<>(new String[]{"UTF-8", "GBK", "ISO-8859-1"});
    private final JComboBox<String> delimiterCombo = new JComboBox<>(new String[]{",", "\t", "|", ";"});
    private final JCheckBox headerCb = new JCheckBox("首行为列头", true);
    private final DefaultTableModel previewModel = new DefaultTableModel();
    private final JTable previewTable = new JTable(previewModel);
    private List<String> fileHeaders;
    private List<Integer> columnMapping = new ArrayList<>();

    public ImportDialog(Frame owner, String connName, ServiceFactory serviceFactory, ConnectionManager cm) {
        super(owner, "导入数据");
        this.connName = connName;
        this.serviceFactory = serviceFactory;
        this.cm = cm;
        setSizeRatio(0.6);
        centerOnOwner();

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // target table panel
        JPanel targetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        schemaCombo.setEditable(true);
        tableCombo.setEditable(true);
        schemaCombo.addActionListener(e -> {
            if (schemaCombo.hasFocus() || schemaCombo.getSelectedItem() != null) loadTables();
        });
        targetPanel.add(new JLabel("目标表:"));
        targetPanel.add(new JLabel("模式"));
        targetPanel.add(schemaCombo);
        targetPanel.add(new JLabel("表名"));
        targetPanel.add(tableCombo);

        JButton browseBtn = new JButton("浏览...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                filePathField.setText(chooser.getSelectedFile().getAbsolutePath());
                onFileSelected();
            }
        });
        targetPanel.add(new JLabel("文件:"));
        targetPanel.add(filePathField);
        targetPanel.add(browseBtn);

        // options panel
        JPanel optsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        optsPanel.add(new JLabel("编码:"));
        optsPanel.add(charsetCombo);
        optsPanel.add(new JLabel("分隔符:"));
        delimiterCombo.setEditable(true);
        optsPanel.add(delimiterCombo);
        optsPanel.add(headerCb);

        // north: combine target + options + format hint
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(targetPanel, BorderLayout.NORTH);

        JPanel optsAndHint = new JPanel(new BorderLayout());
        optsAndHint.add(optsPanel, BorderLayout.NORTH);
        JLabel formatHint = new JLabel("支持格式: CSV (逗号/制表符/竖线/分号分隔), JSON");
        formatHint.setFont(formatHint.getFont().deriveFont(11f));
        formatHint.setForeground(theme.resolve("fg.muted"));
        formatHint.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        optsAndHint.add(formatHint, BorderLayout.CENTER);
        northPanel.add(optsAndHint, BorderLayout.SOUTH);

        // preview (fills remaining space)
        JScrollPane previewScroll = new JScrollPane(previewTable);

        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(previewScroll, BorderLayout.CENTER);

        // buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> dispose());
        JButton importBtn = new JButton("导入");
        importBtn.addActionListener(e -> doImport());
        btnPanel.add(cancelBtn);
        btnPanel.add(importBtn);

        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        loadSchemas();
    }

    private void loadSchemas() {
        if (connName == null) return;
        String dbType = getDbTypeForConn(connName);
        SchemaService svc = serviceFactory.getSchemaService(dbType);
        var schemas = svc.getSchemas(connName);
        for (String s : schemas) schemaCombo.addItem(s);
        if (schemas.size() == 1) schemaCombo.setSelectedIndex(0);
    }

    private void loadTables() {
        String schema = (String) schemaCombo.getSelectedItem();
        if (schema == null || schema.isEmpty()) return;
        tableCombo.removeAllItems();
        String dbType = getDbTypeForConn(connName);
        SchemaService svc = serviceFactory.getSchemaService(dbType);
        var tables = svc.getTables(connName, schema);
        for (String t : tables) tableCombo.addItem(t);
        if (tables.size() == 1) tableCombo.setSelectedIndex(0);
    }

    private void onFileSelected() {
        String path = filePathField.getText().trim();
        if (path.isEmpty()) return;
        File file = new File(path);
        if (!file.exists()) return;

        String name = file.getName().toLowerCase();

        try {
            if (name.endsWith(".json")) {
                fileHeaders = importService.parseJsonHeader(file);
                var previewData = importService.previewJson(file, 10);
                buildPreviewTable(previewData);
                columnMapping.clear();
                for (int i = 0; i < (fileHeaders != null ? fileHeaders.size() : 0); i++) {
                    columnMapping.add(i);
                }
            } else {
                importCfg.charset = Charset.forName((String) charsetCombo.getSelectedItem());
                importCfg.delimiter = ((String) delimiterCombo.getSelectedItem()).charAt(0);
                importCfg.hasHeader = headerCb.isSelected();

                fileHeaders = importService.parseHeader(file, importCfg);
                var previewData = importService.preview(file, importCfg, 10);
                buildPreviewTable(previewData);

                columnMapping.clear();
                for (int i = 0; i < (fileHeaders != null ? fileHeaders.size() : 0); i++) {
                    columnMapping.add(i);
                }
            }
        } catch (Exception ex) {
            ToastManager.showError(this, "文件解析失败: " + ex.getMessage());
        }
    }

    private void buildPreviewTable(List<List<String>> previewData) {
        previewModel.setColumnCount(0);
        previewModel.setRowCount(0);
        if (fileHeaders != null && !fileHeaders.isEmpty()) {
            for (String h : fileHeaders) previewModel.addColumn(h);
        } else if (!previewData.isEmpty()) {
            int maxCols = previewData.get(0).size();
            for (int i = 0; i < maxCols; i++) previewModel.addColumn("Col" + (i + 1));
        }
        for (List<String> row : previewData) {
            previewModel.addRow(row.toArray());
        }
    }

    private void doImport() {
        String schema = (String) schemaCombo.getSelectedItem();
        String table = (String) tableCombo.getSelectedItem();
        String path = filePathField.getText().trim();
        if (schema == null || table == null || path.isEmpty()) {
            ToastManager.showError(this, "请选择目标表和文件");
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            ToastManager.showError(this, "文件不存在");
            return;
        }

        importCfg.charset = Charset.forName((String) charsetCombo.getSelectedItem());
        importCfg.delimiter = ((String) delimiterCombo.getSelectedItem()).charAt(0);
        importCfg.hasHeader = headerCb.isSelected();

        try (java.sql.Connection conn = cm.getConnection(connName)) {
            ImportService.ImportResult result;
            if (file.getName().toLowerCase().endsWith(".json")) {
                var jsonRows = importService.parseJsonRows(file);
                result = importService.execute(conn, schema, table, jsonRows, fileHeaders);
            } else {
                result = importService.execute(conn, schema, table,
                        file, importCfg, fileHeaders, columnMapping);
            }
            if (result.error != null) {
                ToastManager.showError(this, "导入失败: " + result.error);
            } else {
                ToastManager.show(this, "导入完成: 成功 " + result.successRows + " 行" +
                        (result.failRows > 0 ? ", 失败 " + result.failRows + " 行" : ""));
                dispose();
            }
        } catch (Exception ex) {
            ToastManager.showError(this, "导入失败: " + ex.getMessage());
        }
    }

    private String getDbTypeForConn(String connName) {
        var connections = com.kylin.plsql.core.config.ConfigManager.getInstance().loadConnections();
        for (var ci : connections) {
            if (ci.getName().equals(connName)) return ci.getDbType();
        }
        return "oracle";
    }
}
