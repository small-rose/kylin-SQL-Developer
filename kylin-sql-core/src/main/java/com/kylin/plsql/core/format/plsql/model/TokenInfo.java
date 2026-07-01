package com.kylin.plsql.core.format.plsql.model;

public class TokenInfo {
    public final int index;
    public final int type;
    public final String text;
    public final String upper;
    public final int line;
    public final int column;
    public final int channel;

    public boolean isKeyword;
    public boolean isStringLiteral;

    public TokenInfo(int index, int type, String text, String upper,
                     int line, int column, int channel) {
        this.index = index;
        this.type = type;
        this.text = text;
        this.upper = upper;
        this.line = line;
        this.column = column;
        this.channel = channel;
    }
}
