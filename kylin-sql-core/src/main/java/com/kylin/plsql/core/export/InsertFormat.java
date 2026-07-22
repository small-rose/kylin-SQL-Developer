package com.kylin.plsql.core.export;
import javax.swing.table.TableModel;
import java.util.List;
public class InsertFormat implements ExportFormat {
    @Override public String getName() { return "INSERT"; }
    @Override public boolean requiresTableName() { return true; }
    @Override public boolean requiresDialect() { return true; }
    @Override public String export(TableModel model, List<Integer> cols, ExportOptions opts) {
        String table = opts.getTableName(); if (table.isEmpty()) table = "EXPORT_TABLE";
        String d = opts.getDialect(); StringBuilder sb = new StringBuilder();
        for (int r = 0; r < model.getRowCount(); r++) {
            sb.append("INSERT INTO ").append(table).append(" (");
            for (int i = 0; i < cols.size(); i++) { if (i > 0) sb.append(", "); sb.append(model.getColumnName(cols.get(i))); }
            sb.append(") VALUES (");
            for (int i = 0; i < cols.size(); i++) { if (i > 0) sb.append(", "); sb.append(fmt(model.getValueAt(r, cols.get(i)), opts, d, cols.get(i))); }
            sb.append(");\n");
        }
        return sb.toString();
    }
    @Override public String formatValue(Object v, String np) { return v == null ? np : "'" + v.toString().replace("'", "''") + "'"; }
    private String fmt(Object v, ExportOptions o, String d, int colIdx) {
        if (v == null) return o.getNullPlaceholder();
        if (v instanceof Number) return v.toString();
        if (DateFormatterUtil.isDateTimeType(v)) {
            String f = DateFormatterUtil.resolveFormat(colIdx, o.getColumnDateFormats(), o.getDateFormat());
            String ds = DateFormatterUtil.format(v, f);
            if (d != null) {
                if ("Oracle".equals(d)) {
                    if (f.contains("HH")||f.contains("mm")||f.contains("ss")) return "TO_TIMESTAMP('"+ds+"','"+f.replace("yyyy","YYYY").replace("MM","MM").replace("dd","DD").replace("HH","HH24").replace("mm","MI").replace("ss","SS")+"')";
                    return "TO_DATE('"+ds+"','YYYY-MM-DD')";
                } else if ("MySQL".equals(d)) return "'"+ds+"'";
                else if ("PostgreSQL".equals(d)||"ANSI SQL".equals(d)) return (f.contains("HH")||f.contains("mm")||f.contains("ss"))?"TIMESTAMP '"+ds+"'":"DATE '"+ds+"'";
            }
            return "'"+ds+"'";
        }
        if (v instanceof byte[]) {
            byte[] bytes = (byte[])v;
            int len = Math.min(bytes.length, o.getMaxBlobSize()*1024);
            StringBuilder h = new StringBuilder(len*2); for (int i=0;i<len;i++) h.append(String.format("%02X",bytes[i]));
            if (d!=null) { switch(d) { case "Oracle": return "HEXTORAW('"+h+"')"; case "MySQL": return "X'"+h+"'"; case "PostgreSQL": return "'\\x"+h.toString().toLowerCase()+"'::bytea"; case "ANSI SQL": return "NULL /* BLOB unsupported */"; } }
            return "'"+h+"'";
        }
        return formatValue(v, o.getNullPlaceholder());
    }
}
