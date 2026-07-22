package com.kylin.plsql.core.export;

/**
 * 日期格式化工具类，统一处理 java.util.Date 和 java.time 系列类型的格式化。<br>
 * 支持 java.util.Date / java.sql.Date / java.sql.Timestamp / java.time.LocalDateTime / java.time.LocalDate / java.time.LocalTime。
 */


import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Map;

public class DateFormatterUtil {

    public static boolean isDateTimeType(Object value) {
        if (value instanceof java.util.Date) return true;
        if (value instanceof Temporal) return true;
        return false;
    }

    public static String format(Object value, String pattern) {
        if (value instanceof java.util.Date) {
            return new SimpleDateFormat(pattern).format((java.util.Date) value);
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ofPattern(pattern));
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ofPattern(pattern));
        }
        if (value instanceof LocalTime) {
            return ((LocalTime) value).format(DateTimeFormatter.ofPattern(pattern));
        }
        return value != null ? value.toString() : "";
    }

    public static String resolveFormat(int colIdx, Map<Integer, String> columnFormats, String globalFormat) {
        if (columnFormats != null) {
            String colFmt = columnFormats.get(colIdx);
            if (colFmt != null && !colFmt.isEmpty()) return colFmt;
        }
        return globalFormat;
    }
}
