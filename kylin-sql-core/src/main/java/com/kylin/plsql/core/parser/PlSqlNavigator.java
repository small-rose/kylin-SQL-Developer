package com.kylin.plsql.core.parser;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.*;


public class PlSqlNavigator {

    public static class OutlineEntry {
        public final String name;
        public final String type;
        public final int line;

        public OutlineEntry(String name, String type, int line) {
            this.name = name;
            this.type = type;
            this.line = line;
        }

        public String displayLabel() {
            switch (type) {
                case "PACKAGE": return name + " (包)";
                case "PACKAGE_BODY": return name + " (包体)";
                case "FUNCTION": return name + " (函数)";
                case "PROCEDURE": return name + " (过程)";
                default: return name;
            }
        }
    }

    public static List<OutlineEntry> parse(String source) {
        List<OutlineEntry> entries = new ArrayList<>();
        if (source == null || source.isBlank()) return entries;

        try {
            var input = CharStreams.fromString(source);
            var lexer = new PlSqlLexer(input);
            var tokens = new CommonTokenStream(lexer);
            var parser = new PlSqlParser(tokens);
            parser.removeErrorListeners();

            ParseTree tree = parser.sql_script();

            ParseTreeWalker.DEFAULT.walk(new PlSqlParserBaseListener() {
                @Override
                public void enterCreate_package(PlSqlParser.Create_packageContext ctx) {
                    String raw = ctx.package_name(0).getText();
                    entries.add(new OutlineEntry(lastSegment(raw), "PACKAGE", ctx.start.getLine()));
                }

                @Override
                public void enterCreate_package_body(PlSqlParser.Create_package_bodyContext ctx) {
                    String raw = ctx.package_name(0).getText();
                    entries.add(new OutlineEntry(lastSegment(raw), "PACKAGE_BODY", ctx.start.getLine()));
                }

                @Override
                public void enterCreate_function_body(PlSqlParser.Create_function_bodyContext ctx) {
                    String raw = ctx.function_name().getText();
                    entries.add(new OutlineEntry(lastSegment(raw), "FUNCTION", ctx.start.getLine()));
                }

                @Override
                public void enterCreate_procedure_body(PlSqlParser.Create_procedure_bodyContext ctx) {
                    String raw = ctx.procedure_name().getText();
                    entries.add(new OutlineEntry(lastSegment(raw), "PROCEDURE", ctx.start.getLine()));
                }

                @Override
                public void enterFunction_body(PlSqlParser.Function_bodyContext ctx) {
                    String raw = ctx.identifier().getText();
                    entries.add(new OutlineEntry(raw, "FUNCTION", ctx.start.getLine()));
                }

                @Override
                public void enterProcedure_body(PlSqlParser.Procedure_bodyContext ctx) {
                    String raw = ctx.identifier().getText();
                    entries.add(new OutlineEntry(raw, "PROCEDURE", ctx.start.getLine()));
                }
            }, tree);
        } catch (Exception ignored) {
        }

        entries.sort(Comparator.comparingInt(e -> e.line));
        return entries;
    }

    private static String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
