package com.kylin.plsql.ui.component.left;

/** 列信息 POJO，包含列名、数据类型、大小、注释。 */
public class ColumnInfo {
    public final String name;
    public final String dataType;
    public final String sizeStr;
    public final String comment;

    public ColumnInfo(String name, String dataType, String sizeStr, String comment) {
        this.name = name;
        this.dataType = dataType;
        this.sizeStr = sizeStr;
        this.comment = comment;
    }

    @Override
    public String toString() {
        return name;
    }

    public String toDisplayHtml(String nameColor, String grayColor) {
        String suffix = dataType;
        if (sizeStr != null) suffix += "(" + sizeStr + ")";
        return "<html><span style='color:" + nameColor + "'>" + esc(name) + "</span>"
            + " <span style='color:" + grayColor + "'>" + esc(suffix) + "</span></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
