package com.kylin.plsql.core.export;

import java.util.Collections;
import java.util.Map;

public class ExportOptions {
    private String tableName = "EXPORT_TABLE";
    private boolean header = true;
    private String dateFormat = "yyyy-MM-dd HH:mm:ss";
    private Map<Integer, String> columnDateFormats = Collections.emptyMap();
    private String nullPlaceholder = "NULL";
    private int maxBlobSize = 64;
    private String charset = "UTF-8";
    private String dialect = "Oracle";
    public String getTableName() { return tableName; }
    public ExportOptions setTableName(String v) { this.tableName = v; return this; }
    public boolean isHeader() { return header; }
    public ExportOptions setHeader(boolean v) { this.header = v; return this; }
    public String getDateFormat() { return dateFormat; }
    public ExportOptions setDateFormat(String v) { this.dateFormat = v; return this; }
    public Map<Integer, String> getColumnDateFormats() { return columnDateFormats; }
    public ExportOptions setColumnDateFormats(Map<Integer, String> v) { this.columnDateFormats = v != null ? v : Collections.emptyMap(); return this; }
    public String getNullPlaceholder() { return nullPlaceholder; }
    public ExportOptions setNullPlaceholder(String v) { this.nullPlaceholder = v; return this; }
    public int getMaxBlobSize() { return maxBlobSize; }
    public ExportOptions setMaxBlobSize(int v) { this.maxBlobSize = v; return this; }
    public String getCharset() { return charset; }
    public ExportOptions setCharset(String v) { this.charset = v; return this; }
    public String getDialect() { return dialect; }
    public ExportOptions setDialect(String v) { this.dialect = v; return this; }
}
