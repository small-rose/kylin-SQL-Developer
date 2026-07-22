package com.kylin.plsql.core.export;

import javax.swing.table.TableModel;
import java.util.List;

public interface ExportFormat {
    String getName();
    String export(TableModel model, List<Integer> columns, ExportOptions opts);
    String formatValue(Object value, String nullPlaceholder);
    default boolean requiresTableName() { return false; }
    default boolean requiresDialect() { return false; }
}
