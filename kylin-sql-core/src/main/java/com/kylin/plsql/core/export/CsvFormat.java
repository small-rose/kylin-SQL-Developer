package com.kylin.plsql.core.export;
import javax.swing.table.TableModel;
import java.util.List;
public class CsvFormat implements ExportFormat {
    @Override public String getName() { return "CSV"; }
    @Override public String export(TableModel model, List<Integer> cols, ExportOptions opts) {
        StringBuilder sb = new StringBuilder();
        if (opts.isHeader()) {
            for (int i = 0; i < cols.size(); i++) { if (i > 0) sb.append(","); sb.append(esc(model.getColumnName(cols.get(i)))); }
            sb.append("\n");
        }
        for (int r = 0; r < model.getRowCount(); r++) {
            for (int i = 0; i < cols.size(); i++) { if (i > 0) sb.append(","); sb.append(formatValue(model.getValueAt(r, cols.get(i)), opts.getNullPlaceholder())); }
            sb.append("\n");
        }
        return sb.toString();
    }
    @Override public String formatValue(Object v, String np) { return v == null ? np : "\"" + v.toString().replace("\"", "\"\"") + "\""; }
    private static String esc(String s) { return (s.contains(",")||s.contains("\"")||s.contains("\n")) ? "\""+s.replace("\"","\"\"")+"\"" : s; }
}
