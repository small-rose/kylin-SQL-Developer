package com.kylin.plsql.core.service.model;

import javax.swing.table.DefaultTableModel;
import java.util.List;

/** 数据预览结果封装，包含列名、行数据和错误信息，提供 toTableModel() 转换为 Swing TableModel。 */
public class DataPreview {
    public final List<String> columns;
    public final List<List<Object>> rows;
    public final String error;

    public DataPreview(List<String> columns, List<List<Object>> rows) {
        this.columns = columns;
        this.rows = rows;
        this.error = null;
    }

    public DataPreview(String error) {
        this.columns = List.of();
        this.rows = List.of();
        this.error = error;
    }

    public boolean isSuccess() { return error == null; }

    public DefaultTableModel toTableModel() {
        return new DefaultTableModel(
            rows.stream().map(r -> r.toArray()).toArray(Object[][]::new),
            columns.toArray()
        );
    }
}
