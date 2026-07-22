package com.kylin.plsql.core.export;
import javax.swing.table.TableModel;
import java.util.List;
public class JsonFormat implements ExportFormat {
    @Override public String getName() { return "JSON"; }
    @Override public String export(TableModel model, List<Integer> cols, ExportOptions opts) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int r = 0; r < model.getRowCount(); r++) {
            if (r > 0) sb.append(",\n"); sb.append("  {");
            for (int i = 0; i < cols.size(); i++) { if (i > 0) sb.append(", ");
                sb.append("\"").append(esc(model.getColumnName(cols.get(i)))).append("\": ").append(formatValue(model.getValueAt(r, cols.get(i)), opts.getNullPlaceholder())); }
            sb.append("}");
        } sb.append("\n]"); return sb.toString();
    }
    @Override public String formatValue(Object v, String np) {
        if (v == null) return "null"; if (v instanceof Number) return v.toString();
        return "\"" + v.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t") + "\"";
    }
    private static String esc(String s) { return s.replace("\\","\\\\").replace("\"","\\\""); }
}
