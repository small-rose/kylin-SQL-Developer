package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.export.ExportEngine;
import com.kylin.plsql.core.export.ExportOptions;
import com.kylin.plsql.ui.component.common.IconUtil;
import com.kylin.plsql.ui.component.common.ToastManager;
import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Export task list dialog with retry support for failed exports */
public class ExportTaskListDialog extends BaseToolDialog {
    private static ExportTaskListDialog instance;

    private final TaskTableModel taskModel;
    private final JTable taskTable;
    private final List<TaskEntry> tasks;

    static class TaskEntry {
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
        private final List<TaskEntry> data;

        TaskTableModel(List<TaskEntry> data) { this.data = data; }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return 6; }

        @Override public String getColumnName(int col) {
            switch (col) {
                case 0: return "任务名";
                case 1: return "格式";
                case 2: return "状态";
                case 3: return "开始时间";
                case 4: return "耗时";
                case 5: return "进度";
                default: return "";
            }
        }

        @Override public Object getValueAt(int row, int col) {
            TaskEntry t = data.get(row);
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
                default: return "";
            }
        }
    }

    private ExportTaskListDialog(Frame owner) {
        super(owner, "导出任务列表");
        tasks = new ArrayList<>();
        taskModel = new TaskTableModel(tasks);

        // 从存储加载上次的任务
        var cm = ConfigManager.getInstance();
        for (var h : cm.loadExportHistory()) {
            TaskEntry t = new TaskEntry();
            t.name = h.name; t.format = h.format; t.status = h.status;
            t.startTime = h.startTime; t.endTime = h.endTime;
            t.totalRows = h.totalRows; t.filePath = h.filePath;
            t.errorMessage = h.errorMessage;
            tasks.add(t);
        }

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { saveHistory(); }
        });

        taskTable = new JTable(taskModel);
        taskTable.setRowHeight(28);
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        taskTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        taskTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        taskTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        taskTable.getColumnModel().getColumn(5).setPreferredWidth(80);

        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton openFileBtn = new JButton("打开文件");
        openFileBtn.setIcon(IconUtil.loadButtonIcon("file-play", null));
        JButton openLocationBtn = new JButton("打开位置");
        openLocationBtn.setIcon(IconUtil.loadButtonIcon("folder-open-dot", null));
        JButton retryBtn = new JButton("重试");
        retryBtn.setIcon(IconUtil.loadButtonIcon("retry", null));
        JButton delBtn = new JButton("删除");
        delBtn.setIcon(IconUtil.loadButtonIcon("trash-2", null));
        openFileBtn.setEnabled(false);
        openLocationBtn.setEnabled(false);
        retryBtn.setEnabled(false);
        delBtn.setEnabled(false);
        openFileBtn.addActionListener(e -> actionOnSelected(0));
        openLocationBtn.addActionListener(e -> actionOnSelected(1));
        retryBtn.addActionListener(e -> actionOnSelected(2));
        delBtn.addActionListener(e -> actionOnSelected(3));

        taskTable.getSelectionModel().addListSelectionListener(e -> {
            int row = taskTable.getSelectedRow();
            if (row < 0 || row >= tasks.size()) {
                openFileBtn.setEnabled(false); openLocationBtn.setEnabled(false);
                retryBtn.setEnabled(false); delBtn.setEnabled(false);
                return;
            }
            TaskEntry t = tasks.get(row);
            boolean done = "已完成".equals(t.status);
            boolean fail = "失败".equals(t.status);
            openFileBtn.setEnabled(done);
            openLocationBtn.setEnabled(done);
            retryBtn.setEnabled(fail);
            delBtn.setEnabled(done || fail);
        });

        setSize(750, 380);
        centerOnOwner();

        JScrollPane scrollPane = new JScrollPane(taskTable);

        JButton clearBtn = new JButton("清除已完成");
        clearBtn.addActionListener(e -> clearCompleted());

        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> setVisible(false));

        JPanel southPanel = new JPanel(new BorderLayout(8, 0));
        JPanel leftBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        leftBar.add(openFileBtn); leftBar.add(openLocationBtn);
        leftBar.add(retryBtn); leftBar.add(delBtn);
        southPanel.add(leftBar, BorderLayout.WEST);
        JPanel rightBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        rightBar.add(clearBtn); rightBar.add(closeBtn);
        southPanel.add(rightBar, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        applyTheme();
    }

    public static synchronized ExportTaskListDialog getInstance(Frame owner) {
        if (instance == null) instance = new ExportTaskListDialog(owner);
        return instance;
    }

    /** 将已完成/失败的任务持久化到存储。 */
    public void saveHistory() {
        var items = new java.util.ArrayList<ConfigManager.ExportHistoryItem>();
        for (TaskEntry t : tasks) {
            if (!"已完成".equals(t.status) && !"失败".equals(t.status)) continue;
            var h = new ConfigManager.ExportHistoryItem();
            h.name = t.name; h.format = t.format; h.status = t.status;
            h.startTime = t.startTime; h.endTime = t.endTime;
            h.totalRows = t.totalRows; h.filePath = t.filePath;
            h.errorMessage = t.errorMessage;
            items.add(h);
        }
        ConfigManager.getInstance().saveExportHistory(items);
    }

    /** 外部添加一个已配置好的任务（由 ExportWorker 使用）。 */
    public void addTask(TaskEntry task) {
        tasks.add(task);
        taskModel.fireTableRowsInserted(tasks.size() - 1, tasks.size() - 1);
    }

    public void submitTask(javax.swing.table.TableModel model, String format,
                           List<Integer> columns, String tableName, boolean header,
                           Charset charset, String dateFormat, String nullPlaceholder,
                           int maxBlobSize, String filePath) {
        TaskEntry task = new TaskEntry();
        task.id = java.util.UUID.randomUUID().toString().substring(0, 8);
        task.name = "导出 " + (tableName.isEmpty() ? "表" : tableName)
                + " (" + format + ", " + model.getRowCount() + "行)";
        task.format = format;
        task.status = "排队中";
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
                task.status = "执行中";
                final int r0 = row;
                SwingUtilities.invokeLater(() -> taskModel.fireTableRowsUpdated(r0, r0));

                StringBuilder sb = new StringBuilder();
                try {
                    ExportOptions opts = new ExportOptions()
                        .setTableName(tableName.isEmpty() ? "EXPORT_TABLE" : tableName)
                        .setHeader(header)
                        .setDateFormat(dateFormat)
                        .setNullPlaceholder(nullPlaceholder)
                        .setMaxBlobSize(maxBlobSize)
                        .setCharset(charset.name());
                    sb.append(ExportEngine.export(model, columns, format, opts));
                    task.exportedRows = model.getRowCount();

                    File outFile = filePath != null && !filePath.isEmpty()
                        ? new File(filePath) : File.createTempFile("export_", ".tmp");
                    task.filePath = outFile.getAbsolutePath();
                    try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), charset)) {
                        w.write(sb.toString());
                    }
                    task.endTime = System.currentTimeMillis();
                } catch (Exception e) {
                    task.errorMessage = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    task.status = "已取消";
                } else if (task.errorMessage != null) {
                    task.status = "失败";
                    ToastManager.showError(ExportTaskListDialog.this,
                            "导出失败: " + task.errorMessage);
                } else {
                    task.status = "已完成";
                    task.endTime = System.currentTimeMillis();
                    ToastManager.show(ExportTaskListDialog.this,
                            "导出完成: " + task.filePath);
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
        TaskEntry t = tasks.get(row);
        if (t.worker != null && !t.worker.isDone()) {
            t.worker.cancel(true);
            t.status = "已取消";
            taskModel.fireTableRowsUpdated(row, row);
        }
    }

    private void retryTask(int row) {
        TaskEntry failed = tasks.get(row);
        if (failed.modelSnapshot == null || failed.columnNames == null) {
            ToastManager.showError(this, "无法重试：缺少任务参数");
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
                failed.retryMaxBlobSize, null);
    }

    private void openFile(int row) {
        TaskEntry t = tasks.get(row);
        if (t.filePath != null) {
            try {
                Desktop.getDesktop().open(new File(t.filePath));
            } catch (Exception e) {
                ToastManager.showError(this, "打开文件失败: " + e.getMessage());
            }
        }
    }

    private void openFileLocation(int row) {
        TaskEntry t = tasks.get(row);
        if (t.filePath != null) {
            try {
                File file = new File(t.filePath);
                if (file.exists()) Desktop.getDesktop().open(file.getParentFile());
            } catch (Exception e) {
                ToastManager.showError(this, "打开位置失败: " + e.getMessage());
            }
        }
    }

    private void clearCompleted() {
        tasks.removeIf(t -> "已完成".equals(t.status) || "失败".equals(t.status));
        taskModel.fireTableDataChanged();
    }

    /** 根据底部操作按钮类型，对选中行执行对应操作。idx: 0=打开文件,1=打开位置,2=重试,3=删除 */
    private void actionOnSelected(int idx) {
        int row = taskTable.getSelectedRow();
        if (row < 0 || row >= tasks.size()) return;
        switch (idx) {
            case 0 -> openFile(row);
            case 1 -> openFileLocation(row);
            case 2 -> retryTask(row);
            case 3 -> deleteTask(row);
        }
    }

    private void deleteTask(int row) {
        tasks.remove(row);
        taskModel.fireTableRowsDeleted(row, row);
    }
}
