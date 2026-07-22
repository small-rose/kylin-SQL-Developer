package com.kylin.plsql.ui.dialog.tools;

import com.kylin.plsql.core.export.ExportEngine;
import com.kylin.plsql.core.export.ExportOptions;
import com.kylin.plsql.core.export.ExportTask;

import javax.swing.SwingWorker;
import javax.swing.table.TableModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.function.Consumer;

/** 统一异步导出 Worker。 */
public class ExportWorker extends SwingWorker<Void, String> {
    private final TableModel model;
    private final List<Integer> columns;
    private final String formatName;
    private final ExportOptions options;
    private final String filePath;
    private final ExportTask task;
    private final Consumer<String> onProgress;
    private final Consumer<ExportTask> onDone;

    public ExportWorker(TableModel model, List<Integer> columns, String formatName,
                        ExportOptions options, String filePath, ExportTask task,
                        Consumer<String> onProgress, Consumer<ExportTask> onDone) {
        this.model = model; this.columns = columns; this.formatName = formatName;
        this.options = options; this.filePath = filePath; this.task = task;
        this.onProgress = onProgress; this.onDone = onDone;
    }

    @Override protected Void doInBackground() {
        try {
            String result = ExportEngine.export(model, columns, formatName, options);
            File outFile = filePath != null && !filePath.isEmpty()
                    ? new File(filePath) : File.createTempFile("export_", ".tmp");
            String charset = options.getCharset() != null ? options.getCharset() : "UTF-8";
            try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), charset)) {
                w.write(result);
            }
            if (task != null) {
                task.setFilePath(outFile.getAbsolutePath());
                task.setStatus("已完成");
                task.setEndTime(System.currentTimeMillis());
                if (onDone != null) onDone.accept(task);
            }
        } catch (Exception e) {
            if (task != null) {
                task.setErrorMessage(e.getMessage());
                task.setStatus("失败");
                if (onDone != null) onDone.accept(task);
            }
        }
        return null;
    }

    @Override protected void process(List<String> chunks) {
        if (onProgress != null) for (String msg : chunks) onProgress.accept(msg);
    }
}
