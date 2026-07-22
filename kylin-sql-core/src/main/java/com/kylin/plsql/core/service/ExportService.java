package com.kylin.plsql.core.service;

import com.kylin.plsql.core.export.ExportEngine;
import com.kylin.plsql.core.export.ExportOptions;

import javax.swing.table.TableModel;
import java.util.List;

/** 导出服务，委托 ExportEngine 执行导出，无数据库差异，不区分方言。 */
public class ExportService {
    public String export(TableModel model, List<Integer> columns, String format, ExportOptions options) {
        return ExportEngine.export(model, columns, format, options);
    }

    public List<String> getSupportedFormats() {
        return ExportEngine.getSupportedFormats();
    }
}
