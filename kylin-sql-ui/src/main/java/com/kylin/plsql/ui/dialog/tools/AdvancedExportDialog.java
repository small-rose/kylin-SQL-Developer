package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.export.ExportOptions;
import com.kylin.plsql.core.service.DataQueryService;
import com.kylin.plsql.core.service.ExportService;
import com.kylin.plsql.core.service.SchemaService;
import com.kylin.plsql.core.service.ServiceFactory;
import com.kylin.plsql.core.service.model.DataPreview;
import com.kylin.plsql.ui.component.common.DateFormatComboBox;
import com.kylin.plsql.ui.component.common.ToastManager;
import com.kylin.plsql.ui.dialog.common.BaseToolDialog;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancedExportDialog extends BaseToolDialog {
    private static final Logger log = LoggerFactory.getLogger(AdvancedExportDialog.class);
    private final ServiceFactory serviceFactory;
    private final String connName;
    private TableModel sourceModel;
    private final List<Integer> selectedColumns;

    private final RSyntaxTextArea outputArea;
    private final ConnectionManager cm;
    private final JComboBox<String> formatCombo;
    private final JTextField tableNameField;
    private final JCheckBox headerCb;
    private final JComboBox<String> charsetCombo;
    private final DateFormatComboBox dateFormatCb;
    private final JTextField nullPlaceholder;
    private final JSpinner maxBlobSize;
    private final JSpinner maxRowsSpinner;
    private final JPanel colCheckPanel;

    private List<MetadataCache.CachedColumn> columnMetaList;
    private final java.util.Map<Integer, DateFormatComboBox> columnDateFields = new java.util.HashMap<>();
    private final JTextField filePathField;

    private JComboBox<String> sourceCombo;
    private JComboBox<String> connCombo;
    private JComboBox<String> schemaCombo;
    private JComboBox<String> tableCombo;
    private JTextArea sqlTextArea;
    private JScrollPane sqlScroll;
    private JLabel schemaLabel;
    private JLabel tableLabel;
    private JLabel connLabel;
    private JPanel configPanel;

    public AdvancedExportDialog(Frame owner, TableModel sourceModel, String connName, ServiceFactory serviceFactory, ConnectionManager cm) {
        super(owner, "\u9AD8\u7EA7\u5BFC\u51FA");
        this.sourceModel = sourceModel;
        this.connName = connName;
        this.serviceFactory = serviceFactory;
        this.cm = cm;
        setSizeRatio(0.7);
        centerOnOwner();

        selectedColumns = new ArrayList<>();
        for (int i = 0; i < sourceModel.getColumnCount(); i++) {
            selectedColumns.add(i);
        }

        tableNameField = new JTextField("", 15);

        formatCombo = new JComboBox<>(
                new String[]{"INSERT", "CSV", "JSON", "XML", "Markdown"});
        formatCombo.addActionListener(e -> {
            updateFilePathExtension();
            doExport();
        });

        headerCb = new JCheckBox("\u5305\u542B\u5217\u5934");
        headerCb.setSelected(true);
        headerCb.addActionListener(e -> doExport());

        charsetCombo = new JComboBox<>(
                new String[]{"UTF-8", "GBK", "ISO-8859-1", "UTF-16"});

        dateFormatCb = new DateFormatComboBox();
        dateFormatCb.setSelectedItem("yyyy-MM-dd HH:mm:ss");
        dateFormatCb.addActionListener(e -> doExport());
        dateFormatCb.getEditor().getEditorComponent().addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) { doExport(); }
        });

        nullPlaceholder = new JTextField("NULL", 10);
        nullPlaceholder.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { doExport(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { doExport(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { doExport(); }
        });

        maxBlobSize = new JSpinner(new SpinnerNumberModel(64, 1, 10240, 8));
        maxBlobSize.addChangeListener(e -> doExport());

        maxRowsSpinner = new JSpinner(new SpinnerNumberModel(10000, 0, 10000000, 1000));
        maxRowsSpinner.setEditor(new JSpinner.NumberEditor(maxRowsSpinner, "#,##0"));
        maxRowsSpinner.addChangeListener(e -> doExport());

        filePathField = new JTextField(45);
        filePathField.setFont(monoFont());
        filePathField.setText(defaultExportPath());

        outputArea = new RSyntaxTextArea();
        outputArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        outputArea.setEditable(false);
        outputArea.setCodeFoldingEnabled(false);
        outputArea.setAntiAliasingEnabled(true);

        configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("\u5BFC\u51FA\u8BBE\u7F6E"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 5);

        // ── Row 0: data source | max rows | BLOB size ──
        sourceCombo = new JComboBox<>(new String[]{"\u8868", "\u81EA\u5B9A\u4E49SQL"});
        sourceCombo.addActionListener(e -> toggleSourceMode());
        c.gridy = 0; c.gridx = 0; c.weightx = 0;
        configPanel.add(new JLabel("\u6570\u636E\u6765\u6E90:"), c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(sourceCombo, c);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(new JLabel("\u6700\u5927\u884C:"), c);
        c.gridx = 3; c.weightx = 0.3;
        configPanel.add(maxRowsSpinner, c);
        c.gridx = 4; c.weightx = 0;
        configPanel.add(new JLabel("BLOB\u4E0A\u9650:"), c);
        c.gridx = 5; c.weightx = 0.3;
        configPanel.add(maxBlobSize, c);

        // ── Row 1: connection | schema | table (or SQL scroll) ──
        connCombo = new JComboBox<>();
        connCombo.addActionListener(e -> {
            if (connCombo.hasFocus() || connCombo.getSelectedItem() != null) onConnSelected();
        });
        connLabel = new JLabel("\u8FDE\u63A5:");
        schemaCombo = new JComboBox<>();
        schemaCombo.addActionListener(e -> {
            if (schemaCombo.hasFocus() || schemaCombo.getSelectedItem() != null) onSchemaSelected();
        });
        schemaLabel = new JLabel("\u6A21\u5F0F:");
        tableCombo = new JComboBox<>();
        tableCombo.addActionListener(e -> {
            if (tableCombo.getSelectedItem() != null
                && schemaCombo.getSelectedItem() != null
                && connCombo.getSelectedItem() != null) {
                onTableSelected();
            }
        });
        tableLabel = new JLabel("\u8868\u540D:");
        c.gridy = 1; c.gridx = 0; c.weightx = 0;
        configPanel.add(connLabel, c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(connCombo, c);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(schemaLabel, c);
        c.gridx = 3; c.weightx = 0.3;
        configPanel.add(schemaCombo, c);
        c.gridx = 4; c.weightx = 0;
        configPanel.add(tableLabel, c);
        c.gridx = 5; c.weightx = 0.3;
        configPanel.add(tableCombo, c);

        // SQL text area (replaces row 1 in custom SQL mode)
        sqlTextArea = new JTextArea(3, 30);
        sqlTextArea.setFont(monoFont());
        javax.swing.Timer sqlTimer = new javax.swing.Timer(500, e -> onSqlChanged());
        sqlTimer.setRepeats(false);
        sqlTextArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { sqlTimer.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { sqlTimer.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { sqlTimer.restart(); }
        });
        sqlScroll = new JScrollPane(sqlTextArea);
        c.gridx = 0; c.gridwidth = 6; c.weightx = 1;
        configPanel.add(sqlScroll, c);
        sqlScroll.setVisible(false);
        c.gridwidth = 1;

        // ── Row 2: export format | charset | [包含列头] ──
        c.gridy = 2; c.gridx = 0; c.weightx = 0;
        configPanel.add(new JLabel("\u5BFC\u51FA\u683C\u5F0F:"), c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(formatCombo, c);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(new JLabel("\u7F16\u7801:"), c);
        c.gridx = 3; c.weightx = 0.3;
        configPanel.add(charsetCombo, c);
        c.gridx = 4; c.gridwidth = 2; c.weightx = 0;
        c.anchor = GridBagConstraints.WEST;
        configPanel.add(headerCb, c);
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;

        // ── Row 3: date format | null value ──
        c.gridy = 3; c.gridx = 0; c.weightx = 0;
        configPanel.add(new JLabel("\u65E5\u671F\u683C\u5F0F:"), c);
        c.gridx = 1; c.weightx = 0.3;
        configPanel.add(dateFormatCb, c);
        c.gridx = 2; c.weightx = 0;
        configPanel.add(new JLabel("NULL\u503C:"), c);
        c.gridx = 3; c.weightx = 0.5;
        configPanel.add(nullPlaceholder, c);

        // ── Build hint + split + buttons ──
        JLabel hintLabel = new JLabel(
                "\u9009\u62E9\u8981\u5BFC\u51FA\u7684\u5217\uFF0C\u5207\u6362\u683C\u5F0F\u81EA\u52A8\u9884\u89C8 | \u5927\u6570\u636E\u91CF\u5EFA\u8BAE\u4F7F\u7528\u5F02\u6B65\u5BFC\u51FA");
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
                wrapTitled("\u9009\u62E9\u5BFC\u51FA\u5217", colScroll),
                wrapTitled("\u5BFC\u51FA\u9884\u89C8", outputScroll));
        splitPane.setResizeWeight(0.25);
        splitPane.setContinuousLayout(true);

        JButton syncBtn = new JButton("\u540C\u6B65\u5BFC\u51FA");
        syncBtn.addActionListener(e -> {
            int rowCount = sourceModel.getRowCount();
            if (rowCount > 1000) {
                int opt = JOptionPane.showOptionDialog(this,
                        "\u6570\u636E\u91CF\u8F83\u5927\uFF08" + rowCount + " \u884C\uFF09\uFF0C\u662F\u5426\u6539\u4E3A\u5F02\u6B65\u5BFC\u51FA\uFF1F",
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

        JLabel filePathLabel = new JLabel("\u5BFC\u51FA\u8DEF\u5F84:");
        JButton browseBtn = new JButton("\u6D4F\u89C8...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File(filePathField.getText()));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                filePathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        JButton saveBtn = new JButton("\u4FDD\u5B58\u6587\u4EF6");
        saveBtn.addActionListener(e -> saveToFile());

        JPanel leftSouth = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftSouth.add(filePathLabel);
        leftSouth.add(filePathField);
        leftSouth.add(browseBtn);

        JPanel rightSouth = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        rightSouth.add(syncBtn);
        rightSouth.add(asyncBtn);
        rightSouth.add(saveBtn);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(leftSouth, BorderLayout.WEST);
        southPanel.add(rightSouth, BorderLayout.EAST);

        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.add(configPanel, BorderLayout.NORTH);
        northWrapper.add(hintLabel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(northWrapper, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        initConnections();
        applyTheme();
        javax.swing.SwingUtilities.invokeLater(this::doExport);
    }

    // ── Data source toggle ──

    private boolean isCustomSqlMode() {
        return "\u81EA\u5B9A\u4E49SQL".equals(sourceCombo.getSelectedItem());
    }

    private void toggleSourceMode() {
        boolean isTable = !isCustomSqlMode();
        boolean isCustom = isCustomSqlMode();

        connCombo.setVisible(isTable);
        connLabel.setVisible(isTable);
        schemaCombo.setVisible(isTable);
        schemaLabel.setVisible(isTable);
        tableCombo.setVisible(isTable);
        tableLabel.setVisible(isTable);
        sqlScroll.setVisible(isCustom);

        if (isTable) {
            initConnections();
            maybeAutoSelectConn();
        } else if (isCustom && sqlTextArea.getText().isEmpty()) {
            Object selSchema = schemaCombo.getSelectedItem();
            Object selTable = tableCombo.getSelectedItem();
            if (selSchema != null && selTable != null) {
                sqlTextArea.setText("SELECT * FROM " + selSchema + "." + selTable);
            }
        }
    }

    private void initConnections() {
        connCombo.removeAllItems();
        var connections = com.kylin.plsql.core.config.ConfigManager.getInstance().loadConnections();
        for (var ci : connections) {
            connCombo.addItem(ci.getName());
        }
    }

    private void maybeAutoSelectConn() {
        if (connName != null && !connName.isEmpty()) {
            for (int i = 0; i < connCombo.getItemCount(); i++) {
                if (connName.equals(connCombo.getItemAt(i))) {
                    connCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    // ── Connection cascade ──

    private void ensureConnected(String name) {
        if (cm.isConnected(name)) return;
        var connections = ConfigManager.getInstance().loadConnections();
        for (var ci : connections) {
            if (ci.getName().equals(name)) {
                try { cm.connect(ci); } catch (Exception e) {
                    log.warn("自动连接 '{}' 失败: {}", name, e.getMessage());
                }
                return;
            }
        }
    }

    private String getDbTypeForConn(String name) {
        var connections = com.kylin.plsql.core.config.ConfigManager.getInstance().loadConnections();
        for (var ci : connections) {
            if (ci.getName().equals(name)) return ci.getDbType();
        }
        return "oracle";
    }

    private void onConnSelected() {
        String name = (String) connCombo.getSelectedItem();
        if (name == null || name.isEmpty()) return;
        schemaCombo.removeAllItems();
        tableCombo.removeAllItems();

        String dbType = getDbTypeForConn(name);
        SchemaService schemaSvc = serviceFactory.getSchemaService(dbType);
        var schemas = schemaSvc.getSchemas(name);
        for (String s : schemas) schemaCombo.addItem(s);
        if (schemas.size() == 1) schemaCombo.setSelectedIndex(0);
    }

    private void onSchemaSelected() {
        String schema = (String) schemaCombo.getSelectedItem();
        String conn = (String) connCombo.getSelectedItem();
        if (schema == null || schema.isEmpty() || conn == null || conn.isEmpty()) return;
        tableCombo.removeAllItems();

        String dbType = getDbTypeForConn(conn);
        SchemaService schemaSvc = serviceFactory.getSchemaService(dbType);
        var tables = schemaSvc.getTables(conn, schema);
        for (String t : tables) tableCombo.addItem(t);
        if (tables.size() == 1) tableCombo.setSelectedIndex(0);
    }

    private void onTableSelected() {
        String schema = (String) schemaCombo.getSelectedItem();
        String table = (String) tableCombo.getSelectedItem();
        String conn = (String) connCombo.getSelectedItem();
        if (schema == null || table == null || conn == null) return;

        tableNameField.setText(table);
        String dbType = getDbTypeForConn(conn);
        DataQueryService querySvc = serviceFactory.getDataQueryService(dbType);
        SchemaService schemaSvc = serviceFactory.getSchemaService(dbType);

        // Load columns and preview
        var cols = schemaSvc.getColumns(conn, schema, table);
        columnMetaList = MetadataCache.getInstance().getColumns(conn, schema, table);
        if (columnMetaList == null || columnMetaList.isEmpty()) {
            columnMetaList = new java.util.ArrayList<>();
        }
        ensureConnected(conn);
        int previewLimit = Math.max(1, (Integer) maxRowsSpinner.getValue());
        DataPreview preview = querySvc.preview(conn, schema, table, Math.min(previewLimit, 10));

        // Replace sourceModel with preview data
        if (preview.columns != null && !preview.columns.isEmpty()) {
            sourceModel = preview.toTableModel();
        } else if (!cols.isEmpty()) {
            String[] colNames = cols.stream().map(c -> c.name).toArray(String[]::new);
            sourceModel = new DefaultTableModel(colNames, 0);
        }
        rebuildColumnCheckboxes();
        doExport();
    }

    private void onSqlChanged() {
        if (!isCustomSqlMode()) return;
        String sql = sqlTextArea.getText().trim();
        if (sql.isEmpty()) return;
        String conn = (String) connCombo.getSelectedItem();
        if (conn == null || conn.isEmpty()) {
            outputArea.setText("\u8BF7\u5148\u9009\u62E9\u8FDE\u63A5");
            return;
        }
        ensureConnected(conn);
        String dbType = getDbTypeForConn(conn);
        DataQueryService querySvc = serviceFactory.getDataQueryService(dbType);
        DataPreview preview = querySvc.executeQuery(conn, sql);
        if (preview.isSuccess()) {
            if (preview.columns.isEmpty()) {
                outputArea.setText("\u67E5\u8BE2\u6267\u884C\u5B8C\u6210\uFF0C\u65E0\u8FD4\u56DE\u6570\u636E");
            } else {
                sourceModel = preview.toTableModel();
                selectedColumns.clear();
                rebuildColumnCheckboxes();
                doExport();
                outputArea.setText("\u2713 \u67E5\u8BE2\u5B8C\u6210\uFF0C\u8FD4\u56DE " + preview.rows.size() + " \u884C\uFF0C" + preview.columns.size() + " \u5217\n\n" + outputArea.getText());
            }
        } else {
            outputArea.setText("\u2717 \u67E5\u8BE2\u5931\u8D25: " + preview.error);
        }
    }

    // ── File path helpers ──

    private String getExtensionForFormat(String format) {
        if (format == null) return ".txt";
        return switch (format) {
            case "INSERT" -> ".sql";
            case "CSV" -> ".csv";
            case "JSON" -> ".json";
            case "XML" -> ".xml";
            case "Markdown" -> ".md";
            default -> ".txt";
        };
    }

    private String defaultExportPath() {
        return System.getProperty("user.home") + File.separator + "export" + getExtensionForFormat((String) formatCombo.getSelectedItem());
    }

    private void updateFilePathExtension() {
        String current = filePathField.getText().trim();
        if (current.isEmpty()) {
            filePathField.setText(defaultExportPath());
            return;
        }
        String ext = getExtensionForFormat((String) formatCombo.getSelectedItem());
        int dot = current.lastIndexOf('.');
        int sep = Math.max(current.lastIndexOf(File.separatorChar), current.lastIndexOf('/'));
        if (dot > sep) {
            filePathField.setText(current.substring(0, dot) + ext);
        } else {
            filePathField.setText(current + ext);
        }
    }

    // ── Column checkbox panel ──

    private boolean isDateType(String type) {
        if (type == null) return false;
        String t = type.toUpperCase();
        return t.contains("DATE") || t.contains("TIMESTAMP") || t.equals("TIME");
    }

    private String guessDateFormat(String type, int size) {
        if (type == null) return null;
        String t = type.toUpperCase();
        if (t.contains("TIMESTAMP")) {
            if (size >= 3) return "yyyy-MM-dd HH:mm:ss.SSS";
            return "yyyy-MM-dd HH:mm:ss";
        }
        if (t.contains("DATE") || t.equals("DATETIME")) return "yyyy-MM-dd HH:mm:ss";
        if (t.equals("TIME")) return "HH:mm:ss";
        return null;
    }

    private void rebuildColumnCheckboxes() {
        colCheckPanel.removeAll();
        selectedColumns.clear();
        columnDateFields.clear();
        String globalFormat = dateFormatCb.getFormat();
        for (int i = 0; i < sourceModel.getColumnCount(); i++) {
            selectedColumns.add(i);
            String colName = sourceModel.getColumnName(i);
            String type = null;
            int size = 0;
            String comment = null;
            boolean isDate = false;
            if (columnMetaList != null && i < columnMetaList.size()) {
                var cm = columnMetaList.get(i);
                type = cm.type;
                size = cm.size;
                comment = cm.comment;
                isDate = isDateType(cm.type);
            }

            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setOpaque(false);
            row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 26));

            // Checkbox with name
            StringBuilder label = new StringBuilder(colName);
            if (type != null) {
                label.append(" (").append(type);
                if (size > 0) label.append(",").append(size);
                label.append(")");
            }
            String escaped = label.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            JCheckBox cb = new JCheckBox("<html><nobr>" + escaped + "</nobr></html>");
            cb.setSelected(true);
            if (comment != null && !comment.isEmpty()) {
                cb.setToolTipText("<html><b>" + colName + "</b><br>" + comment + "</html>");
            }
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
            row.add(cb, BorderLayout.WEST);

            // Date format combo (only for date type columns)
            if (isDate) {
                String fmt = guessDateFormat(type, size);
                if (fmt == null) fmt = globalFormat;
                DateFormatComboBox fmtCb = new DateFormatComboBox(true);
                fmtCb.setSelectedItem(fmt);
                fmtCb.setToolTipText("\u9009\u62E9\u6216\u8F93\u5165\u65E5\u671F\u683C\u5F0F\uFF0C\u7559\u7A7A\u5219\u4F7F\u7528\u5168\u5C40\u683C\u5F0F");
                fmtCb.addActionListener(e -> doExport());
                java.awt.event.KeyAdapter ka = new java.awt.event.KeyAdapter() {
                    @Override public void keyReleased(java.awt.event.KeyEvent e) { doExport(); }
                };
                fmtCb.getEditor().getEditorComponent().addKeyListener(ka);
                columnDateFields.put(i, fmtCb);
                row.add(fmtCb, BorderLayout.EAST);
            }

            colCheckPanel.add(row);
        }
        colCheckPanel.revalidate();
        colCheckPanel.repaint();
    }

    // ── Export ──

    private void doExport() {
        String format = (String) formatCombo.getSelectedItem();
        if (format == null) return;
        outputArea.setSyntaxEditingStyle(
            "INSERT".equals(format) ? SyntaxConstants.SYNTAX_STYLE_SQL
            : "JSON".equals(format) ? SyntaxConstants.SYNTAX_STYLE_JSON
            : "XML".equals(format) ? SyntaxConstants.SYNTAX_STYLE_XML
            : SyntaxConstants.SYNTAX_STYLE_NONE);
        try {
            ExportService exportSvc = serviceFactory.getExportService();
            outputArea.setText(exportSvc.export(sourceModel, selectedColumns, format, buildOptions()));
        } catch (Exception e) {
            outputArea.setText("\u5BFC\u51FA\u9519\u8BEF: " + e.getMessage());
        }
    }

    private String detectDialect() {
        String conn = (String) connCombo.getSelectedItem();
        if (conn == null || conn.isEmpty()) return "ANSI SQL";
        String dbType = getDbTypeForConn(conn);
        return switch (dbType) {
            case "oracle", "oceanbase-oracle" -> "Oracle";
            case "mysql", "mariadb", "oceanbase-mysql" -> "MySQL";
            case "postgresql" -> "PostgreSQL";
            default -> "ANSI SQL";
        };
    }

    private ExportOptions buildOptions() {
        java.util.Map<Integer, String> perColumnFormats = new java.util.HashMap<>();
        for (var e : columnDateFields.entrySet()) {
            String fmt = e.getValue().getFormat();
            if (!fmt.isEmpty()) perColumnFormats.put(e.getKey(), fmt);
        }
        return new ExportOptions()
            .setTableName(tableNameField.getText().trim())
            .setHeader(headerCb.isSelected())
            .setDateFormat(dateFormatCb.getFormat())
            .setColumnDateFormats(perColumnFormats)
            .setNullPlaceholder(nullPlaceholder.getText())
            .setMaxBlobSize((Integer) maxBlobSize.getValue())
            .setCharset((String) charsetCombo.getSelectedItem())
            .setDialect(detectDialect());
    }

    private void doExportAsync() {
        ExportTaskListDialog taskList = ExportTaskListDialog.getInstance(owner);
        ExportOptions opts = buildOptions();
        taskList.submitTask(sourceModel, (String) formatCombo.getSelectedItem(),
                new ArrayList<>(selectedColumns),
                opts.getTableName(), opts.isHeader(),
                Charset.forName(opts.getCharset()),
                opts.getDateFormat(), opts.getNullPlaceholder(),
                opts.getMaxBlobSize(), opts.getDialect());
        taskList.setVisible(true);
    }

    private void saveToFile() {
        String path = filePathField.getText().trim();
        if (path.isEmpty()) {
            ToastManager.showError(this, "\u8BF7\u5148\u8F93\u5165\u5BFC\u51FA\u8DEF\u5F84");
            return;
        }
        File file = new File(path);
        String charset = (String) charsetCombo.getSelectedItem();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), charset)) {
            w.write(outputArea.getText());
            ToastManager.show(this, "\u5DF2\u4FDD\u5B58\u5230: " + file.getAbsolutePath());
        } catch (Exception e) {
            ToastManager.showError(this, "\u4FDD\u5B58\u5931\u8D25: " + e.getMessage());
        }
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        Color editorBg = theme.resolve("bg.editor");
        Color editorFg = theme.resolve("fg.main");
        outputArea.setBackground(editorBg);
        outputArea.setForeground(editorFg);
        outputArea.setCaretColor(editorFg);
        outputArea.setSelectionColor(theme.resolve("bg.selection"));
        tableNameField.setBackground(editorBg);
        tableNameField.setForeground(editorFg);
        nullPlaceholder.setBackground(editorBg);
        nullPlaceholder.setForeground(editorFg);
        filePathField.setBackground(editorBg);
        filePathField.setForeground(editorFg);
    }
}
