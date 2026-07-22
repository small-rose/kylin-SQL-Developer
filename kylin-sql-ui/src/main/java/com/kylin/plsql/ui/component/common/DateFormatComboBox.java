package com.kylin.plsql.ui.component.common;

import javax.swing.*;

/**
 * 日期格式下拉框，支持下拉选择和手动输入。<br>
 * 构造参数 shortList=false 显示全部 14 种常用格式（用于全局设置），true 显示 6 种快捷格式（用于列级设置）。
 */
public class DateFormatComboBox extends JComboBox<String> {
    private static final String[] FULL = {
        "yyyy-MM-dd",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy/MM/dd",
        "yyyy/MM/dd HH:mm:ss",
        "yyyyMMdd",
        "dd/MM/yyyy",
        "dd/MM/yyyy HH:mm:ss",
        "MM/dd/yyyy",
        "MM/dd/yyyy HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "HH:mm:ss",
        "HH:mm"
    };
    private static final String[] SHORT = {
        "yyyy-MM-dd",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy/MM/dd",
        "dd/MM/yyyy",
        "HH:mm:ss"
    };

    public DateFormatComboBox() {
        this(false);
    }

    public DateFormatComboBox(boolean shortList) {
        super(shortList ? SHORT : FULL);
        setEditable(true);
    }

    public String getFormat() {
        if (isEditable()) {
            Object editorItem = getEditor().getItem();
            if (editorItem != null) {
                String s = editorItem.toString().trim();
                if (!s.isEmpty()) return s;
            }
        }
        Object sel = getSelectedItem();
        return sel != null ? sel.toString().trim() : "yyyy-MM-dd HH:mm:ss";
    }
}
