package com.kylin.plsql.core.export;
import javax.swing.table.TableModel;
import java.util.List;
public class MarkdownFormat implements ExportFormat {
    @Override public String getName() { return "Markdown"; }
    @Override public String export(TableModel model, List<Integer> cols, ExportOptions opts) {
        StringBuilder sb = new StringBuilder();
        if (opts.isHeader()) {
            sb.append("|"); for (int i = 0; i < cols.size(); i++) sb.append(" ").append(model.getColumnName(cols.get(i))).append(" |");
            sb.append("\n|"); for (int i = 0; i < cols.size(); i++) sb.append(" --- |"); sb.append("\n");
        }
        for (int r = 0; r < model.getRowCount(); r++) {
            sb.append("|"); for (int i = 0; i < cols.size(); i++) sb.append(" ").append(formatValue(model.getValueAt(r, cols.get(i)), opts.getNullPlaceholder())).append(" |");
            sb.append("\n");
        } return sb.toString();
    }
    @Override public String formatValue(Object v, String np) { return v == null ? np : v.toString().replace("|","\\|"); }
}
