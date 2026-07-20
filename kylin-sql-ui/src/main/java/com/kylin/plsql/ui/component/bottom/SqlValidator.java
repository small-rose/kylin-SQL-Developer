package com.kylin.plsql.ui.component.bottom;

import com.kylin.plsql.core.format.plsql.builder.ParseTreeModelBuilder;
import com.kylin.plsql.core.format.plsql.dialect.OraclePlSqlDialect;
import com.kylin.plsql.core.format.plsql.dialect.PlSqlDialect;
import com.kylin.plsql.core.format.plsql.model.Diagnostic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** SQL 语法检查器：中文标点容忍 + ANTLR 解析 + 一键修复。 */
public class SqlValidator {
    private static final Logger log = LoggerFactory.getLogger(SqlValidator.class);
    private static final PlSqlDialect DIALECT = new OraclePlSqlDialect();

    public static class SqlError {
        public final int line;
        public final int column;
        public final String message;

        public SqlError(int line, int column, String message) {
            this.line = line;
            this.column = column;
            this.message = message;
        }
    }

    /** 检查 SQL 语法，原样返回诊断信息（不清洗）。 */
    public static List<SqlError> validate(String sql) {
        List<SqlError> errors = new ArrayList<>();
        if (sql == null || sql.isBlank()) return errors;

        String cleaned = fixPunctuation(sql);
        try {
            ParseTreeModelBuilder builder = new ParseTreeModelBuilder();
            var model = builder.build(cleaned, DIALECT);

            for (var d : model.diagnostics) {
                if (d.line == 0 && d.column == 0) {
                    errors.add(new SqlError(0, 0, d.message));
                } else {
                    errors.add(new SqlError(d.line, d.column, d.message));
                }
            }
        } catch (Exception e) {
            log.warn("SQL 解析异常", e);
            errors.add(new SqlError(0, 0, "解析失败: " + e.getMessage()));
        }

        // 额外检查：|| 中间有空格（| |）
        errors.addAll(checkBarWithSpaces(sql));

        return errors;
    }

    /** 扫描 SQL 中 `| |` 模式（竖线空格竖线），可能为 || 误输入。 */
    private static List<SqlError> checkBarWithSpaces(String sql) {
        List<SqlError> errors = new ArrayList<>();
        int len = sql.length();
        int line = 1, col = 1;
        int i = 0;
        while (i < len) {
            char c = sql.charAt(i);

            // 跳过字符串
            if (c == '\'') {
                do { if (sql.charAt(i) == '\n') { line++; col = 1; } else col++; i++; }
                while (i < len && sql.charAt(i) != '\'');
                if (i < len) { col++; i++; }
                continue;
            }
            if (c == '"') {
                do { if (sql.charAt(i) == '\n') { line++; col = 1; } else col++; i++; }
                while (i < len && sql.charAt(i) != '"');
                if (i < len) { col++; i++; }
                continue;
            }
            // 跳过注释
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                while (i < len && sql.charAt(i) != '\n') { col++; i++; }
                continue;
            }
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                while (i < len) {
                    if (sql.charAt(i) == '*' && i + 1 < len && sql.charAt(i + 1) == '/') { col += 2; i += 2; break; }
                    if (sql.charAt(i) == '\n') { line++; col = 1; } else col++; i++;
                }
                continue;
            }

            // 检测 | 空格 |
            if (c == '|' && i + 2 < len && sql.charAt(i + 1) == ' ' && sql.charAt(i + 2) == '|') {
                errors.add(new SqlError(line, col, "警告: || 之间存在空格，请确认是否为字符串连接操作符"));
                col++; i++;
                continue;
            }

            if (c == '\n') { line++; col = 1; } else col++;
            i++;
        }
        return errors;
    }

    /**
     * 智能清洗中文标点 → ASCII 标点。
     * 保留字符串('...')、双引号标识符("...")、注释内的内容不变。
     */
    public static String fixPunctuation(String sql) {
        if (sql == null || sql.isEmpty()) return sql;
        StringBuilder sb = new StringBuilder(sql.length());
        int len = sql.length();
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            if (c == '\'') {
                sb.append(c); i++;
                while (i < len) { sb.append(sql.charAt(i)); if (sql.charAt(i) == '\'') { i++; break; } i++; }
                continue;
            }

            if (c == '"') {
                sb.append(c); i++;
                while (i < len) { sb.append(sql.charAt(i)); if (sql.charAt(i) == '"') { i++; break; } i++; }
                continue;
            }

            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                while (i < len) { sb.append(sql.charAt(i)); if (sql.charAt(i) == '\n') { i++; break; } i++; }
                continue;
            }

            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                while (i < len) {
                    sb.append(sql.charAt(i));
                    if (sql.charAt(i) == '*' && i + 1 < len && sql.charAt(i + 1) == '/') {
                        sb.append('/'); i += 2; break;
                    }
                    i++;
                }
                continue;
            }

            switch (c) {
                case '\uFF0C' -> sb.append(',');   // 全角逗号
                case '\uFF1B' -> sb.append(';');   // 全角分号
                case '\uFF08' -> sb.append('(');   // 全角左括号
                case '\uFF09' -> sb.append(')');   // 全角右括号
                case '\uFF1A' -> sb.append(':');   // 全角冒号
                case '\uFF1F' -> sb.append('?');   // 全角问号
                case '\uFF01' -> sb.append('!');   // 全角感叹号
                case '\uFF1D' -> sb.append('=');   // 全角等号
                case '\u2018' -> sb.append('\'');  // 左单引号
                case '\u2019' -> sb.append('\'');  // 右单引号
                case '\u201C' -> sb.append('"');   // 左双引号
                case '\u201D' -> sb.append('"');   // 右双引号
                case '\u3010' -> sb.append('[');   // 左黑括号
                case '\u3011' -> sb.append(']');   // 右黑括号
                case '\u3001' -> sb.append(',');   // 顿号
                case '\uFF5E' -> sb.append('~');   // 全角波浪号
                default -> sb.append(c);
            }
            i++;
        }

        return sb.toString();
    }
}
