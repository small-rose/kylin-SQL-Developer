package com.kylin.plsql.core.format.plsql.model;

public class Diagnostic {
    public enum Severity { ERROR, WARNING, INFO }

    public enum Code {
        IF_UNCLOSED,
        LOOP_UNCLOSED,
        CASE_UNCLOSED,
        BLOCK_UNCLOSED,
        EXTRA_END_IF,
        EXTRA_END_LOOP,
        EXTRA_END_CASE,
        EXTRA_END,
        MISMATCHED_END,
        PAREN_UNCLOSED,
        STRING_UNCLOSED,
        UNEXPECTED_KEYWORD,
        INDENT_MISMATCH,
        STRING_INTEGRITY,
        BLOCK_BALANCE
    }

    public final Severity severity;
    public final Code code;
    public final int line;
    public final int column;
    public final String message;
    public final String suggestion;

    public Diagnostic(Severity severity, Code code, int line, int column,
                      String message, String suggestion) {
        this.severity = severity;
        this.code = code;
        this.line = line;
        this.column = column;
        this.message = message;
        this.suggestion = suggestion;
    }
}
