package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import com.kylin.plsql.ui.component.common.ToastManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Cross-schema database object search dialog */
public class ObjectSearchDialog extends BaseToolDialog {
    private final JTextField searchField;
    private final JComboBox<String> connCombo;
    private final JComboBox<String> typeCombo;
    private final JCheckBox allSchemaCb;
    private final DefaultListModel<SearchResult> resultModel;
    private final JList<SearchResult> resultList;
    private final JLabel detailLabel;
    private final DetailTableModel detailModel;
    private final JTable detailTable;
    private final Function<String, Connection> connProvider;
    private final BiConsumer<String, String> onNavigate;

    static class SearchResult {
        final String name;
        final String type;
        final String schema;

        SearchResult(String name, String type, String schema) {
            this.name = name; this.type = type; this.schema = schema;
        }

        @Override public String toString() {
            return schema + "." + name + " (" + type + ")";
        }
    }

    static class DetailTableModel extends AbstractTableModel {
        private final List<String[]> rows = new ArrayList<>();

        void setData(List<String[]> data) {
            rows.clear();
            rows.addAll(data);
            fireTableDataChanged();
        }

        void clear() { rows.clear(); fireTableDataChanged(); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int col) {
            return col == 0 ? "\u5C5E\u6027" : "\u503C";
        }
        @Override public Object getValueAt(int row, int col) {
            return rows.get(row)[col];
        }
    }

    public ObjectSearchDialog(Frame owner,
                              Function<String, Connection> connProvider,
                              BiConsumer<String, String> onNavigate) {
        super(owner, "\u5BF9\u8C61\u641C\u7D22");
        this.connProvider = connProvider;
        this.onNavigate = onNavigate;
        setSizeRatio(0.7);
        centerOnOwner();

        searchField = new JTextField();
        connCombo = new JComboBox<>();
        typeCombo = new JComboBox<>(new String[]{"ALL", "TABLE", "VIEW", "PROCEDURE", "FUNCTION", "PACKAGE"});
        allSchemaCb = new JCheckBox("\u641C\u7D22\u6240\u6709 Schema");

        JButton searchBtn = new JButton("\u641C\u7D22");
        searchBtn.addActionListener(e -> doSearch());

        JPanel northPanel = new JPanel(new GridBagLayout());
        northPanel.setBorder(BorderFactory.createTitledBorder("\u641C\u7D22\u6761\u4EF6"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 5);

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        northPanel.add(new JLabel("\u5173\u952E\u8BCD:"), c);
        c.gridx = 1; c.weightx = 1; c.gridwidth = 3;
        northPanel.add(searchField, c);
        c.gridx = 4; c.weightx = 0; c.gridwidth = 1;
        northPanel.add(searchBtn, c);

        c.gridy = 1; c.gridx = 0; c.weightx = 0;
        northPanel.add(new JLabel("\u8FDE\u63A5:"), c);
        c.gridx = 1; c.weightx = 0.3;
        northPanel.add(connCombo, c);
        c.gridx = 2; c.weightx = 0;
        northPanel.add(new JLabel("\u7C7B\u578B:"), c);
        c.gridx = 3; c.weightx = 0.3;
        northPanel.add(typeCombo, c);
        c.gridx = 4; c.weightx = 0;
        northPanel.add(allSchemaCb, c);

        resultModel = new DefaultListModel<>();
        resultList = new JList<>(resultModel);
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetails();
        });
        resultList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    SearchResult sr = resultList.getSelectedValue();
                    if (sr != null && onNavigate != null) {
                        onNavigate.accept((String) connCombo.getSelectedItem(), sr.schema + "." + sr.name);
                    }
                }
            }
        });

        detailLabel = new JLabel("\u672A\u9009\u4E2D\u5BF9\u8C61");
        detailModel = new DetailTableModel();
        detailTable = new JTable(detailModel);

        JScrollPane resultScroll = new JScrollPane(resultList);
        JScrollPane detailScroll = new JScrollPane(detailTable);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(detailLabel, BorderLayout.NORTH);
        rightPanel.add(detailScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled("\u641C\u7D22\u7ED3\u679C", resultScroll),
                rightPanel);
        splitPane.setResizeWeight(0.35);
        splitPane.setContinuousLayout(true);

        setLayout(new BorderLayout());
        add(northPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        applyTheme();
    }

    public void populateConnections(List<String> connNames) {
        connCombo.removeAllItems();
        for (String name : connNames) connCombo.addItem(name);
    }

    private void doSearch() {
        String keyword = searchField.getText().trim();
        String connName = (String) connCombo.getSelectedItem();
        String type = (String) typeCombo.getSelectedItem();
        boolean allSchema = allSchemaCb.isSelected();
        if (keyword.isEmpty() || connName == null) return;

        resultModel.clear();
        detailModel.clear();
        detailLabel.setText("\u672A\u9009\u4E2D\u5BF9\u8C61");

        SwingWorker<List<SearchResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SearchResult> doInBackground() {
                List<SearchResult> results = new ArrayList<>();
                try (Connection conn = connProvider.apply(connName)) {
                    if (conn == null) return results;
                    DatabaseMetaData meta = conn.getMetaData();
                    String pattern = "%" + keyword.toUpperCase() + "%";

                    if (type.equals("ALL") || type.equals("TABLE") || type.equals("VIEW")) {
                        try (ResultSet rs = meta.getTables(null,
                                allSchema ? null : getUserName(conn), pattern,
                                type.equals("ALL") ? null : new String[]{type})) {
                            while (rs.next()) {
                                results.add(new SearchResult(
                                        rs.getString("TABLE_NAME"),
                                        rs.getString("TABLE_TYPE"),
                                        rs.getString("TABLE_SCHEM")));
                            }
                        }
                    }

                    if (type.equals("ALL") || type.equals("PROCEDURE") || type.equals("FUNCTION")) {
                        try (ResultSet rs = meta.getProcedures(null,
                                allSchema ? null : getUserName(conn), pattern)) {
                            while (rs.next()) {
                                String procType = rs.getString("PROCEDURE_TYPE");
                                results.add(new SearchResult(
                                        rs.getString("PROCEDURE_NAME"),
                                        procType != null ? "PROCEDURE" : "FUNCTION",
                                        rs.getString("PROCEDURE_SCHEM")));
                            }
                        }
                    }
                } catch (Exception e) {
                    // swallow for background
                }
                return results;
            }

            @Override
            protected void done() {
                try {
                    List<SearchResult> results = get();
                    for (SearchResult sr : results) resultModel.addElement(sr);
                    if (results.isEmpty()) {
                        ToastManager.show(ObjectSearchDialog.this,
                                "\u672A\u627E\u5230\u5339\u914D\u5BF9\u8C61");
                    }
                } catch (Exception e) {
                    ToastManager.showError(ObjectSearchDialog.this,
                            "\u641C\u7D22\u5931\u8D25: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void showDetails() {
        SearchResult sr = resultList.getSelectedValue();
        if (sr == null) {
            detailLabel.setText("\u672A\u9009\u4E2D\u5BF9\u8C61");
            detailModel.clear();
            return;
        }
        detailLabel.setText(sr.schema + "." + sr.name + " (" + sr.type + ")");

        String connName = (String) connCombo.getSelectedItem();
        List<String[]> details = new ArrayList<>();
        try (Connection conn = connProvider.apply(connName)) {
            if (conn == null) return;
            DatabaseMetaData meta = conn.getMetaData();

            if (sr.type.contains("TABLE")) {
                try (ResultSet rs = meta.getColumns(null, sr.schema, sr.name, "%")) {
                    while (rs.next()) {
                        details.add(new String[]{
                                rs.getString("COLUMN_NAME"),
                                rs.getString("TYPE_NAME") + "(" + rs.getInt("COLUMN_SIZE") + ")"
                        });
                    }
                }
            } else if (sr.type.contains("PROCEDURE") || sr.type.contains("FUNCTION")) {
                try (ResultSet rs = meta.getProcedureColumns(null, sr.schema, sr.name, "%")) {
                    while (rs.next()) {
                        details.add(new String[]{
                                rs.getString("COLUMN_NAME"),
                                rs.getString("TYPE_NAME") + " (" + rs.getString("COLUMN_TYPE") + ")"
                        });
                    }
                }
            }
        } catch (Exception e) {
            details.add(new String[]{"\u9519\u8BEF", e.getMessage()});
        }
        detailModel.setData(details);
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        Color editorBg = theme.resolve("bg.editor");
        Color editorFg = theme.resolve("fg.main");
        searchField.setBackground(editorBg);
        searchField.setForeground(editorFg);
        resultList.setBackground(theme.resolve("list.bg"));
        resultList.setForeground(theme.resolve("list.fg"));
        detailTable.setBackground(theme.resolve("list.bg"));
        detailTable.setForeground(theme.resolve("list.fg"));
        detailLabel.setForeground(theme.resolve("fg.muted"));
    }

    private String getUserName(Connection conn) {
        try {
            return conn.getMetaData().getUserName();
        } catch (Exception e) {
            return null;
        }
    }
}
