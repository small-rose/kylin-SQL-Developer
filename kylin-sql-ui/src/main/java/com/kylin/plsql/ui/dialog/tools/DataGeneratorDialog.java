package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import com.kylin.plsql.ui.component.common.ToastManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;

/** Test data generator dialog based on table structure metadata */
public class DataGeneratorDialog extends BaseToolDialog {
    private final JComboBox<String> connCombo;
    private final JComboBox<String> schemaCombo;
    private final JComboBox<String> tableCombo;
    private final JSpinner rowCountSpinner;
    private final JComboBox<String> dialectCombo;
    private List<ColumnDef> columns;
    private final ColumnTableModel colModel;
    private final JTable columnTable;
    private final RSyntaxTextArea outputArea;
    private final JSplitPane splitPane;
    private final JToggleButton layoutToggleBtn;
    private final Function<String, Connection> connProvider;

    public DataGeneratorDialog(Frame owner, Function<String, Connection> connProvider) {
        super(owner, "数据生成器");
        this.connProvider = connProvider;
        setSizeRatio(0.7);
        centerOnOwner();

        connCombo = new JComboBox<>();
        connCombo.addActionListener(e -> loadSchemas());

        schemaCombo = new JComboBox<>();
        schemaCombo.addActionListener(e -> { if (schemaCombo.getItemCount() > 0) loadTables(); });

        tableCombo = new JComboBox<>();
        tableCombo.addActionListener(e -> { if (tableCombo.getItemCount() > 0) loadColumns(); });

        rowCountSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 10));

        dialectCombo = new JComboBox<>(new String[]{"Oracle", "MySQL", "PostgreSQL", "ANSI SQL"});

        columns = new ArrayList<>();
        colModel = new ColumnTableModel();
        columnTable = new JTable(colModel);

        outputArea = new RSyntaxTextArea();
        outputArea.setSyntaxEditingStyle("text/plsql");
        outputArea.setEditable(false);

        JButton loadColBtn = new JButton("加载列");
        loadColBtn.addActionListener(e -> loadColumns());

        JButton genBtn = new JButton("生成数据");
        genBtn.addActionListener(e -> generate());

        JPanel northPanel = new JPanel(new GridBagLayout());
        northPanel.setBorder(BorderFactory.createTitledBorder("数据源"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 5);

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        northPanel.add(new JLabel("连接:"), c);
        c.gridx = 1; c.weightx = 0.3;
        northPanel.add(connCombo, c);
        c.gridx = 2; c.weightx = 0;
        northPanel.add(new JLabel("Schema:"), c);
        c.gridx = 3; c.weightx = 0.3;
        northPanel.add(schemaCombo, c);
        c.gridx = 4; c.weightx = 0;
        northPanel.add(new JLabel("表:"), c);
        c.gridx = 5; c.weightx = 0.3;
        northPanel.add(tableCombo, c);
        c.gridx = 6; c.weightx = 0;
        northPanel.add(new JLabel("行数:"), c);
        c.gridx = 7; c.weightx = 0.1;
        northPanel.add(rowCountSpinner, c);
        c.gridx = 8; c.weightx = 0;
        northPanel.add(new JLabel("方言:"), c);
        c.gridx = 9; c.weightx = 0.2;
        northPanel.add(dialectCombo, c);

        c.gridy = 1; c.gridx = 0; c.gridwidth = 10;
        c.weightx = 1;
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        btnRow.add(loadColBtn);
        btnRow.add(genBtn);
        northPanel.add(btnRow, c);

        JScrollPane colScroll = new JScrollPane(columnTable);
        JScrollPane outputScroll = new JScrollPane(outputArea);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("列定义", colScroll),
                wrapTitled("数据预览", outputScroll));
        splitPane.setResizeWeight(0.35);
        splitPane.setContinuousLayout(true);

        layoutToggleBtn = new JToggleButton("⇔ 垂直布局");
        layoutToggleBtn.addActionListener(e -> toggleLayout());

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        southPanel.add(layoutToggleBtn);

        setLayout(new BorderLayout());
        add(northPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        applyTheme();
    }

    public void populateConnections(List<String> connNames) {
        connCombo.removeAllItems();
        for (String name : connNames) connCombo.addItem(name);
        if (!connNames.isEmpty()) loadSchemas();
    }

    private void loadSchemas() {
        schemaCombo.removeAllItems();
        String connName = (String) connCombo.getSelectedItem();
        if (connName == null) return;
        try (Connection conn = connProvider.apply(connName)) {
            if (conn == null) return;
            DatabaseMetaData meta = conn.getMetaData();
            Set<String> schemas = new LinkedHashSet<>();
            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) schemas.add(rs.getString("TABLE_SCHEM"));
            }
            for (String s : schemas) schemaCombo.addItem(s);
        } catch (Exception e) {
            ToastManager.showError(this, "加载 Schema 失败: " + e.getMessage());
        }
    }

    private void loadTables() {
        tableCombo.removeAllItems();
        String connName = (String) connCombo.getSelectedItem();
        String schema = (String) schemaCombo.getSelectedItem();
        if (connName == null || schema == null) return;
        try (Connection conn = connProvider.apply(connName)) {
            if (conn == null) return;
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) tableCombo.addItem(rs.getString("TABLE_NAME"));
            }
        } catch (Exception e) {
            ToastManager.showError(this, "加载表失败: " + e.getMessage());
        }
    }

    private void loadColumns() {
        columns.clear();
        String connName = (String) connCombo.getSelectedItem();
        String schema = (String) schemaCombo.getSelectedItem();
        String table = (String) tableCombo.getSelectedItem();
        if (connName == null || schema == null || table == null) return;
        try (Connection conn = connProvider.apply(connName)) {
            if (conn == null) return;
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, schema, table, "%")) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String colType = rs.getString("TYPE_NAME");
                    String rule = inferRule(colType);
                    columns.add(new ColumnDef(colName, colType, rule));
                }
            }
        } catch (Exception e) {
            ToastManager.showError(this, "加载列失败: " + e.getMessage());
        }
        colModel.fireTableDataChanged();
    }

    private void generate() {
        if (columns.isEmpty()) {
            ToastManager.show(this, "请先加载列");
            return;
        }
        int count = (Integer) rowCountSpinner.getValue();
        String dialect = (String) dialectCombo.getSelectedItem();
        outputArea.setText("");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                StringBuilder sb = new StringBuilder();
                String table = (String) tableCombo.getSelectedItem();
                if (table == null) table = "TEST_DATA";

                sb.append("-- ").append("生成 ").append(count).append(" 行测试数据");
                sb.append(", ").append("方言: ").append(dialect).append("\n\n");

                Random rnd = new Random();
                for (int row = 0; row < count; row++) {
                    sb.append("INSERT INTO ").append(table).append(" (");
                    for (int i = 0; i < columns.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(columns.get(i).name);
                    }
                    sb.append(") VALUES (");
                    for (int i = 0; i < columns.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(generateValue(columns.get(i), rnd, row, dialect));
                    }
                    sb.append(");\n");
                    if (row % 100 == 0) setProgress((row * 100) / count);
                }
                return sb.toString();
            }

            @Override
            protected void done() {
                try {
                    outputArea.setText(get());
                } catch (Exception e) {
                    ToastManager.showError(DataGeneratorDialog.this,
                            "生成失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    String generateValue(ColumnDef col, Random rnd, int row, String dialect) {
        String type = col.type.toUpperCase();
        if (type.contains("VARCHAR") || type.contains("CHAR") || type.contains("CLOB")
                || type.contains("NCLOB") || type.contains("LONG") || type.contains("TEXT")) {
            return "'" + randomString(10) + "'";
        }
        if (type.contains("INT") || type.contains("NUMERIC") || type.contains("FLOAT")
                || type.contains("DOUBLE") || type.contains("DECIMAL") || type.contains("NUMBER")) {
            return String.valueOf(rnd.nextInt(1000));
        }
        if (type.contains("DATE")) {
            String dateStr = randomDate(rnd);
            if ("Oracle".equals(dialect)) {
                return "TO_DATE('" + dateStr + "','YYYY-MM-DD')";
            } else if ("MySQL".equals(dialect)) {
                return "'" + dateStr + "'";
            } else {
                return "DATE '" + dateStr + "'";
            }
        }
        if (type.contains("TIMESTAMP")) {
            String tsStr = randomTimestamp(rnd);
            if ("Oracle".equals(dialect)) {
                return "TO_TIMESTAMP('" + tsStr + "','YYYY-MM-DD HH24:MI:SS')";
            } else if ("MySQL".equals(dialect)) {
                return "'" + tsStr + "'";
            } else {
                return "TIMESTAMP '" + tsStr + "'";
            }
        }
        if (type.contains("BLOB") || type.contains("RAW") || type.contains("BINARY")
                || type.contains("BYTEA")) {
            String hex = randomHex(8);
            if ("Oracle".equals(dialect)) {
                return "HEXTORAW('" + hex + "')";
            } else if ("MySQL".equals(dialect)) {
                return "X'" + hex + "'";
            } else if ("PostgreSQL".equals(dialect)) {
                return "'\\x" + hex.toLowerCase() + "'::bytea";
            } else {
                return "NULL /* BLOB 不支持直接 INSERT */";
            }
        }
        if (type.contains("BOOL")) {
            return rnd.nextBoolean() ? "1" : "0";
        }
        return "'" + randomString(5) + "'";
    }

    private String inferRule(String type) {
        String up = type.toUpperCase();
        if (up.contains("VARCHAR") || up.contains("CHAR") || up.contains("CLOB")
                || up.contains("NCLOB") || up.contains("LONG") || up.contains("TEXT")) {
            return "random_string(10)";
        }
        if (up.contains("INT") || up.contains("NUMERIC") || up.contains("FLOAT")
                || up.contains("DOUBLE") || up.contains("DECIMAL") || up.contains("NUMBER")) {
            return "random_int(1, 1000)";
        }
        if (up.contains("DATE")) return "random_date('2024-01-01', '2025-12-31')";
        if (up.contains("TIMESTAMP")) return "random_timestamp('2024-01-01', '2025-12-31')";
        if (up.contains("BLOB") || up.contains("RAW") || up.contains("BINARY")
                || up.contains("BYTEA")) return "random_hex(8)";
        if (up.contains("BOOL")) return "random_boolean()";
        return "random_string(5)";
    }

    private static String randomString(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(len);
        Random rnd = new Random();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    private static String randomDate(Random rnd) {
        long start = new GregorianCalendar(2024, Calendar.JANUARY, 1).getTimeInMillis();
        long end = new GregorianCalendar(2025, Calendar.DECEMBER, 31).getTimeInMillis();
        long diff = start + (long) (rnd.nextDouble() * (end - start));
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date(diff));
    }

    private static String randomTimestamp(Random rnd) {
        long start = new GregorianCalendar(2024, Calendar.JANUARY, 1).getTimeInMillis();
        long end = new GregorianCalendar(2025, Calendar.DECEMBER, 31).getTimeInMillis();
        long diff = start + (long) (rnd.nextDouble() * (end - start));
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(diff));
    }

    private static String randomHex(int bytes) {
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (int i = 0; i < bytes; i++) sb.append(String.format("%02X", rnd.nextInt(256)));
        return sb.toString();
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        outputArea.setBackground(theme.resolve("bg.editor"));
        outputArea.setForeground(theme.resolve("fg.main"));
        columnTable.setBackground(theme.resolve("list.bg"));
        columnTable.setForeground(theme.resolve("list.fg"));
        splitPane.setBackground(theme.resolve("bg.panel"));
    }

    private void toggleLayout() {
        boolean horizontal = splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
        splitPane.setOrientation(horizontal
                ? JSplitPane.VERTICAL_SPLIT
                : JSplitPane.HORIZONTAL_SPLIT);
        layoutToggleBtn.setText(horizontal ? "⇕ 水平布局" : "⇔ 垂直布局");
    }

    static class ColumnDef {
        final String name;
        final String type;
        String rule;

        ColumnDef(String name, String type, String rule) {
            this.name = name;
            this.type = type;
            this.rule = rule;
        }
    }

    static class ColumnTableModel extends AbstractTableModel {
        private final List<ColumnDef> data = new ArrayList<>();

        void setData(List<ColumnDef> cols) {
            data.clear();
            data.addAll(cols);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return 3; }

        @Override public String getColumnName(int col) {
            switch (col) {
                case 0: return "列名";
                case 1: return "类型";
                case 2: return "生成规则";
                default: return "";
            }
        }

        @Override public Object getValueAt(int row, int col) {
            ColumnDef d = data.get(row);
            switch (col) {
                case 0: return d.name;
                case 1: return d.type;
                case 2: return d.rule;
                default: return "";
            }
        }

        @Override public boolean isCellEditable(int row, int col) {
            return col == 2;
        }

        @Override public void setValueAt(Object val, int row, int col) {
            if (col == 2) data.get(row).rule = val.toString();
        }
    }
}
