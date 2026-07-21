package com.kylin.plsql.core.parser;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.List;

/** 从 SQL 中推断主表名的工具类。使用 ANTLR 词法分析器跟踪括号深度。 */
public class SqlTableExtractor {

    /** 从 SQL 推断主表名，无法确定时返回 "table_name"。 */
    public static String guessTableName(String sql) {
        if (sql == null || sql.isBlank()) return "table_name";
        try {
            var input = CharStreams.fromString(sql);
            var lexer = new PlSqlLexer(input);
            var tokens = new CommonTokenStream(lexer);
            tokens.fill();
            List<Token> all = tokens.getTokens();

            if (all.isEmpty()) return "table_name";

            int firstType = all.get(0).getType();
            // 处理 INSERT / UPDATE / DELETE / MERGE
            if (firstType == PlSqlLexer.INSERT || firstType == PlSqlLexer.UPDATE
                || firstType == PlSqlLexer.DELETE || firstType == PlSqlLexer.MERGE) {
                for (int i = 0; i < all.size(); i++) {
                    if (all.get(i).getType() == PlSqlLexer.INTO) {
                        return extractTableToken(all, i + 1);
                    }
                    // UPDATE 没有 INTO，直接找标识符
                    if (firstType == PlSqlLexer.UPDATE && i > 0
                        && all.get(i - 1).getType() == PlSqlLexer.UPDATE) {
                        return extractTableToken(all, i);
                    }
                }
                return "table_name";
            }

            // 处理 SELECT — 在 depth=0 的位置找 FROM
            int depth = 0;
            for (int i = 0; i < all.size(); i++) {
                Token tok = all.get(i);
                String text = tok.getText();
                if ("(".equals(text)) depth++;
                else if (")".equals(text)) depth = Math.max(0, depth - 1);
                else if (depth == 0 && tok.getType() == PlSqlLexer.FROM) {
                    return extractTableToken(all, i + 1);
                }
            }
        } catch (Exception ignored) {}
        return "table_name";
    }

    /** 从 tokens 的 pos 位置开始，跳过 LPAREN/关键字，取第一个有效表标识符。 */
    private static String extractTableToken(List<Token> tokens, int pos) {
        for (int i = pos; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            int type = t.getType();
            // 跳过空白、注释
            if (type == PlSqlLexer.SPACES || type == PlSqlLexer.SINGLE_LINE_COMMENT
                || type == PlSqlLexer.MULTI_LINE_COMMENT || type == PlSqlLexer.REMARK_COMMENT) {
                continue;
            }
            // 跳过关键字（ON、JOIN、WHERE、GROUP、ORDER、HAVING、LIMIT、OFFSET 等）
            if (type == PlSqlLexer.ON || type == PlSqlLexer.JOIN || type == PlSqlLexer.WHERE
                || type == PlSqlLexer.GROUP || type == PlSqlLexer.ORDER || type == PlSqlLexer.HAVING
                || type == PlSqlLexer.LIMIT || type == PlSqlLexer.OFFSET) {
                return "table_name";
            }
            // 子查询，放弃
            if ("(".equals(t.getText())) return "table_name";
            // 找到标识符
            if (type == PlSqlLexer.REGULAR_ID || type == PlSqlLexer.DELIMITED_ID) {
                String name = t.getText().replace("\"", "").replace("`", "");
                // schema.table → 优先检查自身是否已含 dot
                int dot = name.indexOf('.');
                if (dot >= 0) return name;
                // 下个 token 可能为 dot（无论是否带空格）
                if (i + 1 < tokens.size() && ".".equals(tokens.get(i + 1).getText())) {
                    name += ".";
                    // 跳过 dot 后的空白/注释，取表名
                    for (int j = i + 2; j < tokens.size(); j++) {
                        Token t2 = tokens.get(j);
                        int t2t = t2.getType();
                        if (t2t == PlSqlLexer.REGULAR_ID || t2t == PlSqlLexer.DELIMITED_ID) {
                            return name + t2.getText().replace("\"", "").replace("`", "");
                        }
                        if (t2t != PlSqlLexer.SPACES && t2t != PlSqlLexer.SINGLE_LINE_COMMENT
                            && t2t != PlSqlLexer.MULTI_LINE_COMMENT) break;
                    }
                }
                return name;
            }
            // 遇到逗号/分号也没命中 → 无法提取
            if (",".equals(t.getText()) || ";".equals(t.getText())) return "table_name";
        }
        return "table_name";
    }
}
