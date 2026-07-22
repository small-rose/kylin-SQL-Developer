package com.kylin.plsql.core.export;
import javax.swing.table.TableModel;
import java.util.List;
public class XmlFormat implements ExportFormat {
    @Override public String getName() { return "XML"; }
    @Override public String export(TableModel model, List<Integer> cols, ExportOptions opts) {
        StringBuilder sb = new StringBuilder("<rows>\n");
        for (int r = 0; r < model.getRowCount(); r++) {
            sb.append("  <row>\n");
            for (int i = 0; i < cols.size(); i++) {
                String n = model.getColumnName(cols.get(i));
                sb.append("    <").append(esc(n)).append(">").append(formatValue(model.getValueAt(r, cols.get(i)), opts.getNullPlaceholder())).append("</").append(esc(n)).append(">\n");
            } sb.append("  </row>\n");
        } sb.append("</rows>"); return sb.toString();
    }
    @Override public String formatValue(Object v, String np) { return v == null ? np : esc(v.toString()); }
    private static String esc(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;"); }
}
