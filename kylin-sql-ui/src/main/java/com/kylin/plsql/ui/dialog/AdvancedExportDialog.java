package com.kylin.plsql.ui.dialog;

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

    public AdvancedExportDialog(Frame owner, TableModel sourceModel) {
        super(owner, "\u9AD8\u7EA7\u5BFC\u51FA");
        this.sourceModel = sourceModel;
        setSizeRatio(0.7);

        selectedColumns = new ArrayList<>();
        for (int i = 0; i < sourceModel.getColumnCount(); i++) {
            selectedColumns.add(i);
        }

        dialectCombo = new JComboBox<>(
                new String[]{"Oracle", "MySQL", "PostgreSQL", "ANSI SQL"});
        dialectCombo.addActionListener(e -> doExport());

        tableNameField = new JTextField("\u8868\u540D", 15);

        formatCombo = new JComboBox<>(
                new String[]{"INSERT", "CSV", "JSON", "XML", "Markdown"});
        formatCombo.addActionListener(e -> {
            boolean insert = "INSERT".equals(formatCombo.getSelectedItem());
            tableNameField.setEnabled(insert);
            dialectCombo.setEnabled(insert);
            doExport();
        });

        headerCb = new JCheckBox("\u5305\u542B\u5217\u5934");
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
        configPanel.setBorder(BorderFactory.createTitledBorder("\u5BFC\u51FA\u8BBE\u7F6E"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 5);

        c.gridy = 0; c.gridx = 0; c.weightx = 0;
        configPanel.add(new JLabel("\u5BFC\u51FA\u683C\u5F0F:"), c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(formatCombo, c);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(new JLabel("\u8868\u540D:"), c);
        c.gridx = 3; c.weightx = 0.5;
        configPanel.add(tableNameField, c);
        c.gridx = 4; c.weightx = 0;
        configPanel.add(headerCb, c);
        c.gridx = 5; c.weightx = 0;
        configPanel.add(new JLabel("\u7F16\u7801:"), c);
        c.gridx = 6; c.weightx = 0.3;
        configPanel.add(charsetCombo, c);

        c.gridy = 1; c.gridx = 0; c.weightx = 0;
        configPanel.add(new JLabel("\u65B9\u8A00:"), c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(dialectCombo, c);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(new JLabel("\u65E5\u671F\u683C\u5F0F:"), c);
        c.gridx = 3; c.weightx = 0.5;
        configPanel.add(dateFormatField, c);
        c.gridx = 4; c.weightx = 0;
        configPanel.add(new JLabel("NULL\u503C:"), c);
        c.gridx = 5; c.weightx = 0.3;
        configPanel.add(nullPlaceholder, c);

        c.gridy = 2; c.gridx = 0; c.weightx = 0;
        configPanel.add(new JLabel("BLOB\u4E0A\u9650:"), c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(maxBlobSize, c);

        JLabel hintLabel = new JLabel(
                "\u9009\u62E9\u8981\u5BFC\u51FA\u7684\u5217\uFF0C\u5207\u6362\u683C\u5F0F\u81EA\u52A8\u9884\u89C8 | INSERT \u683C\u5F0F\u9700\u586B\u5199\u8868\u540D | \u5927\u6570\u636E\u91CF\u5EFA\u8BAE\u4F7F\u7528\u5F02\u6B65\u5BFC\u51FA");
        hintLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        hintLabel.setOpaque(true);
        hintLabel.setBackground(theme.resolve("bg.panel"));
        hintLabel.setForeground(theme.resolve("fg.muted"));

        colCheckPanel = new JPanel();
        colCheckPanel.setLayout(new BoxLayout(colCheckPanel, BoxLayout.Y_AXIS));
        rebuildColumnCheckboxes();
        JScrollPane colScroll = new JScrollPane(colCheckPanel);

        JScrollPane outputScroll = new JScrollPane(outputArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("\u9009\u62E9\u5BFC\u51FA\u5217", colScroll),
                wrapTitled("\u5BFC\u51FA\u9884\u89C8", outputScroll));
        splitPane.setResizeWeight(0.25);
        splitPane.setContinuousLayout(true);

        JButton syncBtn = new JButton("\u540C\u6B65\u5BFC\u51FA");
        syncBtn.addActionListener(e -> {
            if (sourceModel.getRowCount() > 1000) {
                int opt = JOptionPane.showOptionDialog(this,
                        "\u6570\u636E\u91CF\u8F83\u5927\uFF08" + sourceModel.getRowCount() + " \u884C\uFF09\uFF0C\u662F\u5426\u6539\u4E3A\u5F02\u6B65\u5BFC\u51FA\uFF1F",
                        "\u786E\u8BA4", JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null,
                        new String[]{"\u5F02\u6B65", "\u540C\u6B65", "\u53D6\u6D88"}, "\u5F02\u6B65");
                if (opt == 0) { doExportAsync(); return; }
                if (opt == 2) return;
            }
            String content = outputArea.getText();
            if (!content.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(content), null);
                ToastManager.show(this, "\u5DF2\u590D\u5236\u5230\u526A\u8D34\u677F");
            }
        });

        JButton asyncBtn = new JButton("\u5F02\u6B65\u5BFC\u51FA");
        asyncBtn.addActionListener(e -> doExportAsync());

        JButton saveBtn = new JButton("\u4FDD\u5B58\u6587\u4EF6...");
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
            outputArea.setText("\u5BFC\u51FA\u9519\u8BEF: " + e.getMessage());
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
                    case "ANSI SQL": return "NULL /* BLOB \u4E0D\u652F\u6301\u76F4\u63A5 INSERT */";
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
                table, headerCb.isSelected(), charset, dateFmt, nullVal, maxBlob);
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
                ToastManager.show(this, "\u5DF2\u4FDD\u5B58\u5230: " + file.getName());
            } catch (Exception e) {
                ToastManager.showError(this, "\u4FDD\u5B58\u5931\u8D25: " + e.getMessage());
            }
        }
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
