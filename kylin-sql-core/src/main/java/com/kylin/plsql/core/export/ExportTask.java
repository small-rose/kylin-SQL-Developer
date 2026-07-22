package com.kylin.plsql.core.export;
import javax.swing.*;
import java.util.List;
import java.util.UUID;

public class ExportTask {
    private String id = UUID.randomUUID().toString().substring(0, 8);
    private String name; private String format; private volatile String status = "排队中";
    private long startTime; private long endTime; private int totalRows; private int exportedRows;
    private String filePath; private String errorMessage;
    private transient SwingWorker<Void, Void> worker;
    private List<Object[]> modelSnapshot; private int columnCount; private String[] columnNames;
    private Class<?>[] columnClasses; private String retryFormat;
    private List<Integer> retryColumns; private String retryTableName; private boolean retryHeader;
    private String retryCharsetName; private String retryDateFormat; private String retryNullPlaceholder; private int retryMaxBlobSize;

    public String getId() { return id; } public void setId(String v) { id = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getFormat() { return format; } public void setFormat(String v) { format = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public long getStartTime() { return startTime; } public void setStartTime(long v) { startTime = v; }
    public long getEndTime() { return endTime; } public void setEndTime(long v) { endTime = v; }
    public int getTotalRows() { return totalRows; } public void setTotalRows(int v) { totalRows = v; }
    public int getExportedRows() { return exportedRows; } public void setExportedRows(int v) { exportedRows = v; }
    public String getFilePath() { return filePath; } public void setFilePath(String v) { filePath = v; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String v) { errorMessage = v; }
    public SwingWorker<Void, Void> getWorker() { return worker; } public void setWorker(SwingWorker<Void, Void> v) { worker = v; }
    public List<Object[]> getModelSnapshot() { return modelSnapshot; } public void setModelSnapshot(List<Object[]> v) { modelSnapshot = v; }
    public int getColumnCount() { return columnCount; } public void setColumnCount(int v) { columnCount = v; }
    public String[] getColumnNames() { return columnNames; } public void setColumnNames(String[] v) { columnNames = v; }
    public Class<?>[] getColumnClasses() { return columnClasses; } public void setColumnClasses(Class<?>[] v) { columnClasses = v; }
    public String getRetryFormat() { return retryFormat; } public void setRetryFormat(String v) { retryFormat = v; }
    public List<Integer> getRetryColumns() { return retryColumns; } public void setRetryColumns(List<Integer> v) { retryColumns = v; }
    public String getRetryTableName() { return retryTableName; } public void setRetryTableName(String v) { retryTableName = v; }
    public boolean isRetryHeader() { return retryHeader; } public void setRetryHeader(boolean v) { retryHeader = v; }
    public String getRetryCharsetName() { return retryCharsetName; } public void setRetryCharsetName(String v) { retryCharsetName = v; }
    public String getRetryDateFormat() { return retryDateFormat; } public void setRetryDateFormat(String v) { retryDateFormat = v; }
    public String getRetryNullPlaceholder() { return retryNullPlaceholder; } public void setRetryNullPlaceholder(String v) { retryNullPlaceholder = v; }
    public int getRetryMaxBlobSize() { return retryMaxBlobSize; } public void setRetryMaxBlobSize(int v) { retryMaxBlobSize = v; }
}
