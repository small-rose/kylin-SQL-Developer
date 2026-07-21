package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;
import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.parser.SqlTableExtractor;
import com.kylin.plsql.ui.component.common.ToastManager;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/** 结果集导出对话框，支持 INSERT / CSV / JSON / XML / Markdown 格式。 */
public class ExportDialog extends BaseToolDialog {
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
    private final JTextField filePathField;

    public ExportDialog(Frame owner, TableModel sourceModel, String sql) {
        super(owner, "导出结果集");
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

        String defaultTable = SqlTableExtractor.guessTableName(sql);
        tableNameField = new JTextField(defaultTable, 15);

        formatCombo = new JComboBox<>(
                new String[]{"INSERT", "CSV", "JSON", "XML", "Markdown"});
        formatCombo.addActionListener(e -> {
            boolean insert = "INSERT".equals(formatCombo.getSelectedItem());
            tableNameField.setEnabled(insert);
            dialectCombo.setEnabled(insert);
            updateFilePathExt();
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

        c.gridy = 0; c.gridx = 0; c.weightx = 0;
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

        c.gridy = 1; c.gridx = 0; c.weightx = 0;
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

        c.gridy = 2; c.gridx = 0; c.weightx = 0;
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

        JButton copyBtn = new JButton("复制");
        copyBtn.setToolTipText("复制预览内容到剪贴板");
        copyBtn.addActionListener(e -> {
            String content = outputArea.getText();
            if (!content.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(content), null);
                ToastManager.show(this, "已复制到剪贴板");
            }
        });

        final JButton syncBtn = new JButton("同步导出");
        syncBtn.setToolTipText("写入文件路径指定的文件");
        JButton asyncBtn = new JButton("异步导出");
        asyncBtn.addActionListener(e -> doExportAsync());

        filePathField = new JTextField(35);
        filePathField.setFont(FontManager.getInstance().resolve("font.dialog"));
        filePathField.setText(defaultFilePath((String) formatCombo.getSelectedItem()));
        JButton browseBtn = new JButton("浏览...");
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            String format = (String) formatCombo.getSelectedItem();
            String ext = "." + (format != null ? format.toLowerCase() : "txt");
            fc.setSelectedFile(new File("export" + ext));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                filePathField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        syncBtn.addActionListener(e -> {
            String path = filePathField.getText().trim();
            if (!path.isEmpty()) {
                saveToFile(path);
                revealInFileManager(path);
            }
        });

        JPanel southPanel = new JPanel(new BorderLayout(4, 0));
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        leftPanel.add(new JLabel("文件路径:"));
        leftPanel.add(filePathField);
        leftPanel.add(browseBtn);
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        rightPanel.add(copyBtn);
        rightPanel.add(syncBtn);
        rightPanel.add(asyncBtn);
        southPanel.add(leftPanel, BorderLayout.WEST);
        southPanel.add(rightPanel, BorderLayout.EAST);

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
            switch (format) {
                case "INSERT": outputArea.setText(exportInsert()); break;
                case "CSV":    outputArea.setText(exportCsv()); break;
                case "JSON":   outputArea.setText(exportJson()); break;
                case "XML":    outputArea.setText(exportXml()); break;
                case "Markdown": outputArea.setText(exportMarkdown()); break;
            }
        } catch (Exception e) {
            outputArea.setText("导出错误: " + e.getMessage());
        }
    }

    private String exportInsert() {
        String table = tableNameField.getText().trim();
        String dialect = (String) dialectCombo.getSelectedItem();
        if (table.isEmpty()) table = "EXPORT_TABLE";
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < sourceModel.getRowCount(); row++) {
            sb.append("INSERT INTO ").append(table).append(" (");
            for (int i = 0; i < selectedColumns.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(sourceModel.getColumnName(selectedColumns.get(i)));
            }
            sb.append(") VALUES (");
            for (int i = 0; i < selectedColumns.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatCellValue(sourceModel.getValueAt(row, selectedColumns.get(i)),
                        selectedColumns.get(i), "INSERT", dialect));
            }
            sb.append(");\n");
        }
        return sb.toString();
    }

    private String exportCsv() {
        StringBuilder sb = new StringBuilder();
        if (headerCb.isSelected()) {
            for (int i = 0; i < selectedColumns.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(escapeCsv(sourceModel.getColumnName(selectedColumns.get(i))));
            }
            sb.append("\n");
        }
        for (int row = 0; row < sourceModel.getRowCount(); row++) {
            for (int i = 0; i < selectedColumns.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(escapeCsv(formatCellValue(sourceModel.getValueAt(row, selectedColumns.get(i)),
                        selectedColumns.get(i), "CSV", null)));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String exportJson() {
        StringBuilder sb = new StringBuilder("[\n");
        for (int row = 0; row < sourceModel.getRowCount(); row++) {
            if (row > 0) sb.append(",\n");
            sb.append("  {");
            for (int i = 0; i < selectedColumns.size(); i++) {
                if (i > 0) sb.append(", ");
                String colName = sourceModel.getColumnName(selectedColumns.get(i));
                String val = formatCellValue(sourceModel.getValueAt(row, selectedColumns.get(i)),
                        selectedColumns.get(i), "JSON", null);
                sb.append("\"").append(escapeJson(colName)).append("\": ").append(val);
            }
            sb.append("}");
        }
        sb.append("\n]");
        return sb.toString();
    }

    private String exportXml() {
        StringBuilder sb = new StringBuilder("<rows>\n");
        for (int row = 0; row < sourceModel.getRowCount(); row++) {
            sb.append("  <row>\n");
            for (int i = 0; i < selectedColumns.size(); i++) {
                String colName = sourceModel.getColumnName(selectedColumns.get(i));
                String val = formatCellValue(sourceModel.getValueAt(row, selectedColumns.get(i)),
                        selectedColumns.get(i), "XML", null);
                sb.append("    <").append(escapeXml(colName)).append(">")
                        .append(val).append("</").append(escapeXml(colName)).append(">\n");
            }
            sb.append("  </row>\n");
        }
        sb.append("</rows>");
        return sb.toString();
    }

    private String exportMarkdown() {
        StringBuilder sb = new StringBuilder();
        if (headerCb.isSelected()) {
            sb.append("|");
            for (int i = 0; i < selectedColumns.size(); i++) {
                sb.append(" ").append(sourceModel.getColumnName(selectedColumns.get(i))).append(" |");
            }
            sb.append("\n|");
            for (int i = 0; i < selectedColumns.size(); i++) {
                sb.append(" --- |");
            }
            sb.append("\n");
        }
        for (int row = 0; row < sourceModel.getRowCount(); row++) {
            sb.append("|");
            for (int i = 0; i < selectedColumns.size(); i++) {
                String val = formatCellValue(sourceModel.getValueAt(row, selectedColumns.get(i)),
                        selectedColumns.get(i), "Markdown", null);
                sb.append(" ").append(val).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    String formatCellValue(Object value, int colIndex, String format, String dialect) {
        if (value == null) {
            if ("INSERT".equals(format)) return nullPlaceholder.getText();
            if ("JSON".equals(format)) return "null";
            return "";
        }

        String dateFmt = dateFormatField.getText().trim();
        if (dateFmt.isEmpty()) dateFmt = "yyyy-MM-dd HH:mm:ss";
        int maxBlob = (Integer) maxBlobSize.getValue();

        String typeName = "OTHER";
        try {
            Class<?> clazz = sourceModel.getColumnClass(colIndex);
            if (clazz != null) typeName = clazz.getSimpleName().toUpperCase();
        } catch (Exception ignored) {}

        boolean isNumber = value instanceof Number;
        boolean isDate = value instanceof java.util.Date || typeName.contains("DATE")
                || typeName.contains("TIME") || typeName.contains("TIMESTAMP");
        boolean isBlob = value instanceof byte[] || typeName.contains("BLOB")
                || typeName.contains("BINARY") || typeName.contains("RAW");

        if (isNumber) {
            return value.toString();
        }

        if (isDate) {
            String dateStr;
            if (value instanceof java.util.Date) {
                dateStr = new SimpleDateFormat(dateFmt).format((java.util.Date) value);
            } else {
                dateStr = value.toString();
            }
            if ("INSERT".equals(format) && dialect != null) {
                switch (dialect) {
                    case "Oracle":
                        if (dateFmt.contains("HH") || dateFmt.contains("mm") || dateFmt.contains("ss")) {
                            String oracleFmt = java.util.regex.Pattern.compile("\\w+").matcher(dateFmt)
                                    .replaceAll(m -> {
                                        String s = m.group();
                                        switch (s) {
                                            case "yyyy": return "YYYY";
                                            case "MM": return "MM";
                                            case "dd": return "DD";
                                            case "HH": return "HH24";
                                            case "mm": return "MI";
                                            case "ss": return "SS";
                                            default: return s;
                                        }
                                    });
                            return "TO_TIMESTAMP('" + dateStr + "','" + oracleFmt + "')";
                        }
                        return "TO_DATE('" + dateStr + "','YYYY-MM-DD')";
                    case "MySQL":
                        return "'" + dateStr + "'";
                    case "PostgreSQL":
                    case "ANSI SQL":
                        return (dateFmt.contains("HH") || dateFmt.contains("mm") || dateFmt.contains("ss"))
                                ? "TIMESTAMP '" + dateStr + "'"
                                : "DATE '" + dateStr + "'";
                }
            }
            return dateStr;
        }

        if (isBlob) {
            byte[] bytes = value instanceof byte[] ? (byte[]) value : value.toString().getBytes();
            int len = Math.min(bytes.length, maxBlob * 1024);
            StringBuilder hex = new StringBuilder(len * 2);
            for (int i = 0; i < len; i++) hex.append(String.format("%02X", bytes[i]));

            if ("INSERT".equals(format) && dialect != null) {
                switch (dialect) {
                    case "Oracle": return "HEXTORAW('" + hex + "')";
                    case "MySQL": return "X'" + hex + "'";
                    case "PostgreSQL": return "'\\x" + hex.toString().toLowerCase() + "'::bytea";
                    case "ANSI SQL": return "NULL /* BLOB 不支持直接 INSERT */";
                }
            }
            if ("CSV".equals(format)) return "\"" + hex + "\"";
            if ("JSON".equals(format)) return "\"" + hex + "\"";
            if ("Markdown".equals(format)) return hex.toString();
            return hex.toString();
        }

        String str = value.toString();
        switch (format) {
            case "INSERT":
                return "'" + str.replace("'", "''") + "'";
            case "CSV":
                return "\"" + str.replace("\"", "\"\"") + "\"";
            case "JSON":
                return "\"" + escapeJson(str) + "\"";
            case "XML":
                return escapeXml(str);
            case "Markdown":
                return str.replace("|", "\\|");
            default:
                return str;
        }
    }

    private void doExportAsync() {
        ExportTaskListDialog taskList = ExportTaskListDialog.getInstance(owner);
        String table = tableNameField.getText().trim();
        String format = (String) formatCombo.getSelectedItem();
        Charset charset = Charset.forName((String) charsetCombo.getSelectedItem());
        String dateFmt = dateFormatField.getText().trim();
        String nullVal = nullPlaceholder.getText();
        int maxBlob = (Integer) maxBlobSize.getValue();
        taskList.submitTask(sourceModel, format, new ArrayList<>(selectedColumns),
                table, headerCb.isSelected(), charset, dateFmt, nullVal, maxBlob,
                filePathField.getText().trim());
        taskList.setVisible(true);
    }

    private void saveToFile(String path) {
        File file = new File(path);
        String charset = (String) charsetCombo.getSelectedItem();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), charset)) {
            w.write(outputArea.getText());
            ToastManager.show(this, "已保存到: " + file.getName());
        } catch (Exception e) {
            ToastManager.showError(this, "保存失败: " + e.getMessage());
        }
    }

    /** 根据导出格式生成默认文件路径：Export_yyyyMMdd_HHmmss.ext。优先桌面，桌面不存在则用户目录。 */
    private static String defaultFilePath(String format) {
        String ext = switch (format != null ? format : "SQL") {
            case "CSV" -> "csv";
            case "JSON" -> "json";
            case "XML" -> "xml";
            case "Markdown" -> "md";
            default -> "sql";
        };
        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        if (!new java.io.File(desktop).exists()) desktop = System.getProperty("user.home");
        return desktop + File.separator + "Export_" + ts + "." + ext;
    }

    /** 切换导出格式时，更新文件路径的扩展名，保留目录和文件名前缀。 */
    private void updateFilePathExt() {
        String path = filePathField.getText().trim();
        if (path.isEmpty()) {
            filePathField.setText(defaultFilePath((String) formatCombo.getSelectedItem()));
            return;
        }
        String format = (String) formatCombo.getSelectedItem();
        String newExt = switch (format != null ? format : "SQL") {
            case "CSV" -> "csv";
            case "JSON" -> "json";
            case "XML" -> "xml";
            case "Markdown" -> "md";
            default -> "sql";
        };
        int dot = path.lastIndexOf('.');
        if (dot >= 0) path = path.substring(0, dot + 1) + newExt;
        else path = path + "." + newExt;
        filePathField.setText(path);
    }

    /** 在文件管理器中定位并选中文件（Windows / macOS / Linux）。 */
    private static void revealInFileManager(String filePath) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"explorer", "/select,", filePath});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", "-R", filePath});
            } else {
                File f = new File(filePath);
                if (f.getParentFile() != null) Desktop.getDesktop().open(f.getParentFile());
            }
        } catch (Exception e) {
            try { Desktop.getDesktop().open(new File(filePath).getParentFile()); } catch (Exception ignored) {}
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

    static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
