package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import com.kylin.plsql.ui.component.common.ToastManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/** Export task list dialog with retry support for failed exports */
public class ExportTaskListDialog extends BaseToolDialog {
    private static ExportTaskListDialog instance;

    private final TaskTableModel taskModel;
    private final JTable taskTable;
    private final List<ExportTask> tasks;

    static class ExportTask {
        String id;
        String name;
        String format;
        volatile String status;
        long startTime;
        long endTime;
        int totalRows;
        int exportedRows;
        String filePath;
        String errorMessage;
        transient SwingWorker<Void, Void> worker;

        // retry support
        List<Object[]> modelSnapshot;
        int columnCount;
        String[] columnNames;
        Class<?>[] columnClasses;
        String retryFormat;
        List<Integer> retryColumns;
        String retryTableName;
        boolean retryHeader;
        String retryCharsetName;
        String retryDateFormat;
        String retryNullPlaceholder;
        int retryMaxBlobSize;
    }

    static class TaskTableModel extends AbstractTableModel {
        private final List<ExportTask> data;

        TaskTableModel(List<ExportTask> data) { this.data = data; }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return 7; }

        @Override public String getColumnName(int col) {
            switch (col) {
                case 0: return "\u4EFB\u52A1\u540D";
                case 1: return "\u683C\u5F0F";
                case 2: return "\u72B6\u6001";
                case 3: return "\u5F00\u59CB\u65F6\u95F4";
                case 4: return "\u8017\u65F6";
                case 5: return "\u8FDB\u5EA6";
                case 6: return "\u64CD\u4F5C";
                default: return "";
            }
        }

        @Override public Object getValueAt(int row, int col) {
            ExportTask t = data.get(row);
            switch (col) {
                case 0: return t.name;
                case 1: return t.format;
                case 2: return t.status;
                case 3: return t.startTime > 0
                        ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(t.startTime))
                        : "--";
                case 4: return t.endTime > 0 && t.startTime > 0
                        ? String.format("%.1fs", (t.endTime - t.startTime) / 1000.0)
                        : "--";
                case 5: return t.totalRows > 0
                        ? t.exportedRows + "/" + t.totalRows
                        : "--";
                case 6: return t;
                default: return "";
            }
        }
    }

    private ExportTaskListDialog(Frame owner) {
        super(owner, "\u5BFC\u51FA\u4EFB\u52A1\u5217\u8868");
        tasks = new ArrayList<>();
        taskModel = new TaskTableModel(tasks);

        taskTable = new JTable(taskModel);
        taskTable.setRowHeight(28);
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        taskTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        taskTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        taskTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        taskTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        taskTable.getColumnModel().getColumn(6).setPreferredWidth(100);

        taskTable.getColumnModel().getColumn(6).setCellRenderer(new ButtonRenderer());
        taskTable.getColumnModel().getColumn(6).setCellEditor(new ButtonEditor());

        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        setSize(750, 380);
        centerOnOwner();

        JScrollPane scrollPane = new JScrollPane(taskTable);

        JButton clearBtn = new JButton("\u6E05\u9664\u5DF2\u5B8C\u6210");
        clearBtn.addActionListener(e -> clearCompleted());

        JButton closeBtn = new JButton("\u5173\u95ED");
        closeBtn.addActionListener(e -> setVisible(false));

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        southPanel.add(clearBtn);
        southPanel.add(closeBtn);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        applyTheme();
    }

    public static synchronized ExportTaskListDialog getInstance(Frame owner) {
        if (instance == null) instance = new ExportTaskListDialog(owner);
        return instance;
    }

    public void submitTask(javax.swing.table.TableModel model, String format,
                           List<Integer> columns, String tableName, boolean header,
                           Charset charset, String dateFormat, String nullPlaceholder,
                           int maxBlobSize) {
        ExportTask task = new ExportTask();
        task.id = java.util.UUID.randomUUID().toString().substring(0, 8);
        task.name = "\u5BFC\u51FA " + (tableName.isEmpty() ? "\u8868" : tableName)
                + " (" + format + ", " + model.getRowCount() + "\u884C)";
        task.format = format;
        task.status = "\u6392\u961F\u4E2D";
        task.startTime = System.currentTimeMillis();
        task.totalRows = model.getRowCount();

        tasks.add(task);
        int row = tasks.size() - 1;
        taskModel.fireTableRowsInserted(row, row);

        task.modelSnapshot = new ArrayList<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            Object[] rowData = new Object[model.getColumnCount()];
            for (int c = 0; c < model.getColumnCount(); c++) {
                rowData[c] = model.getValueAt(r, c);
            }
            task.modelSnapshot.add(rowData);
        }
        task.columnCount = model.getColumnCount();
        task.columnNames = new String[model.getColumnCount()];
        task.columnClasses = new Class<?>[model.getColumnCount()];
        for (int c = 0; c < model.getColumnCount(); c++) {
            task.columnNames[c] = model.getColumnName(c);
            task.columnClasses[c] = model.getColumnClass(c);
        }
        task.retryFormat = format;
        task.retryColumns = new ArrayList<>(columns);
        task.retryTableName = tableName;
        task.retryHeader = header;
        task.retryCharsetName = charset.name();
        task.retryDateFormat = dateFormat;
        task.retryNullPlaceholder = nullPlaceholder;
        task.retryMaxBlobSize = maxBlobSize;

        task.worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                task.status = "\u6267\u884C\u4E2D";
                taskModel.fireTableRowsUpdated(row, row);

                StringBuilder sb = new StringBuilder();
                try {
                    for (int r = 0; r < model.getRowCount() && !isCancelled(); r++) {
                        if ("INSERT".equals(format)) {
                            sb.append("INSERT INTO ").append(tableName.isEmpty() ? "EXPORT_TABLE" : tableName).append(" (");
                            for (int i = 0; i < columns.size(); i++) {
                                if (i > 0) sb.append(", ");
                                sb.append(model.getColumnName(columns.get(i)));
                            }
                            sb.append(") VALUES (");
                            for (int i = 0; i < columns.size(); i++) {
                                if (i > 0) sb.append(", ");
                                sb.append(formatValue(model.getValueAt(r, columns.get(i)), "INSERT"));
                            }
                            sb.append(");\n");
                        } else if ("CSV".equals(format)) {
                            if (header && r == 0) {
                                for (int i = 0; i < columns.size(); i++) {
                                    if (i > 0) sb.append(",");
                                    sb.append(model.getColumnName(columns.get(i)));
                                }
                                sb.append("\n");
                            }
                            for (int i = 0; i < columns.size(); i++) {
                                if (i > 0) sb.append(",");
                                sb.append("\"").append(model.getValueAt(r, columns.get(i))).append("\"");
                            }
                            sb.append("\n");
                        }
                        task.exportedRows = r + 1;
                        if (r % 100 == 0) {
                            taskModel.fireTableRowsUpdated(row, row);
                        }
                    }

                    File tempFile = File.createTempFile("export_", ".tmp");
                    try (Writer w = new OutputStreamWriter(new FileOutputStream(tempFile), charset)) {
                        w.write(sb.toString());
                    }
                    task.filePath = tempFile.getAbsolutePath();
                    task.endTime = System.currentTimeMillis();
                } catch (Exception e) {
                    task.errorMessage = e.getMessage();
                }
                return null;
            }

            private String formatValue(Object value, String format) {
                if (value == null) return nullPlaceholder;
                if (value instanceof Number) return value.toString();
                return "'" + value.toString().replace("'", "''") + "'";
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    task.status = "\u5DF2\u53D6\u6D88";
                } else if (task.errorMessage != null) {
                    task.status = "\u5931\u8D25";
                    ToastManager.showError(ExportTaskListDialog.this,
                            "\u5BFC\u51FA\u5931\u8D25: " + task.errorMessage);
                } else {
                    task.status = "\u5DF2\u5B8C\u6210";
                    task.endTime = System.currentTimeMillis();
                    ToastManager.show(ExportTaskListDialog.this,
                            "\u5BFC\u51FA\u5B8C\u6210: " + task.filePath);
                }
                taskModel.fireTableRowsUpdated(row, row);
            }
        };
        task.worker.execute();
    }

    @Override
    protected void applyTheme() {
        super.applyTheme();
        taskTable.setBackground(theme.resolve("list.bg"));
        taskTable.setForeground(theme.resolve("list.fg"));
    }

    private void cancelTask(int row) {
        ExportTask t = tasks.get(row);
        if (t.worker != null && !t.worker.isDone()) {
            t.worker.cancel(true);
            t.status = "\u5DF2\u53D6\u6D88";
            taskModel.fireTableRowsUpdated(row, row);
        }
    }

    private void retryTask(int row) {
        ExportTask failed = tasks.get(row);
        if (failed.modelSnapshot == null || failed.columnNames == null) {
            ToastManager.showError(this, "\u65E0\u6CD5\u91CD\u8BD5\uFF1A\u7F3A\u5C11\u4EFB\u52A1\u53C2\u6570");
            return;
        }
        tasks.remove(row);
        taskModel.fireTableRowsDeleted(row, row);

        TableModel retryModel = new AbstractTableModel() {
            @Override public int getRowCount() { return failed.modelSnapshot.size(); }
            @Override public int getColumnCount() { return failed.columnCount; }
            @Override public String getColumnName(int col) { return failed.columnNames[col]; }
            @Override public Class<?> getColumnClass(int col) { return failed.columnClasses[col]; }
            @Override public Object getValueAt(int r, int c) {
                return failed.modelSnapshot.get(r)[c];
            }
        };
        submitTask(retryModel, failed.retryFormat, failed.retryColumns,
                failed.retryTableName, failed.retryHeader,
                Charset.forName(failed.retryCharsetName),
                failed.retryDateFormat, failed.retryNullPlaceholder,
                failed.retryMaxBlobSize);
    }

    private void openFile(int row) {
        ExportTask t = tasks.get(row);
        if (t.filePath != null) {
            try {
                Desktop.getDesktop().open(new File(t.filePath));
            } catch (Exception e) {
                ToastManager.showError(this, "\u6253\u5F00\u6587\u4EF6\u5931\u8D25: " + e.getMessage());
            }
        }
    }

    private void clearCompleted() {
        tasks.removeIf(t -> "\u5DF2\u5B8C\u6210".equals(t.status) || "\u5931\u8D25".equals(t.status));
        taskModel.fireTableDataChanged();
    }

    private class ButtonRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            if (value instanceof ExportTask) {
                ExportTask t = (ExportTask) value;
                JButton btn = new JButton();
                if ("\u5DF2\u5B8C\u6210".equals(t.status)) btn.setText("\u6253\u5F00\u6587\u4EF6");
                else if ("\u5931\u8D25".equals(t.status)) btn.setText("\u91CD\u8BD5");
                else if ("\u6392\u961F\u4E2D".equals(t.status) || "\u6267\u884C\u4E2D".equals(t.status))
                    btn.setText("\u53D6\u6D88");
                else btn.setText("--");
                return btn;
            }
            return this;
        }
    }

    private class ButtonEditor extends DefaultCellEditor {
        ButtonEditor() {
            super(new JTextField());
            setClickCountToStart(1);
        }

        @Override public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int col) {
            if (value instanceof ExportTask) {
                ExportTask t = (ExportTask) value;
                JButton btn = new JButton();
                String text;
                Runnable action;
                if ("\u5DF2\u5B8C\u6210".equals(t.status)) {
                    text = "\u6253\u5F00\u6587\u4EF6";
                    action = () -> openFile(row);
                } else if ("\u5931\u8D25".equals(t.status)) {
                    text = "\u91CD\u8BD5";
                    action = () -> retryTask(row);
                } else if ("\u6392\u961F\u4E2D".equals(t.status) || "\u6267\u884C\u4E2D".equals(t.status)) {
                    text = "\u53D6\u6D88";
                    action = () -> cancelTask(row);
                } else {
                    text = "--";
                    action = () -> {};
                }
                btn.setText(text);
                btn.addActionListener(e -> {
                    action.run();
                    fireEditingStopped();
                });
                return btn;
            }
            return new JLabel("\u9519\u8BEF");
        }

        @Override public Object getCellEditorValue() {
            return "";
        }
    }
}
