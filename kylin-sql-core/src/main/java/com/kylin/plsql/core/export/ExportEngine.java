package com.kylin.plsql.core.export;

import javax.swing.table.TableModel;
import java.util.List;

/** 导出引擎门面——上层 UI 只需依赖此类。 */
public class ExportEngine {
    public static String export(TableModel model, List<Integer> columns,
                                 String formatName, ExportOptions opts) {
        return ExportFormats.get(formatName).export(model, columns, opts);
    }
    public static String formatValue(Object value, String formatName, String nullPlaceholder) {
        return ExportFormats.get(formatName).formatValue(value, nullPlaceholder);
    }
    public static List<String> getSupportedFormats() {
        return ExportFormats.getNames();
    }
}
