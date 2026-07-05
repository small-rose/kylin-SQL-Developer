package com.kylin.plsql.core.format.plsql.formatter.layout.plan;

public class StructuralFrame {

    private boolean newline;
    private int indentLevel;
    private int spaces;
    private int savedLevel;

    public StructuralFrame() {
        this.newline = false;
        this.indentLevel = 0;
        this.spaces = 1;
        this.savedLevel = 0;
    }

    public boolean isNewline() { return newline; }
    public int getIndentLevel() { return indentLevel; }
    public int getSpaces() { return spaces; }
    public int getSavedLevel() { return savedLevel; }

    public void setNewline(boolean v) { this.newline = v; }
    public void setIndentLevel(int v) { this.indentLevel = v; }
    public void setSpaces(int v) { this.spaces = v; }
    public void setSavedLevel(int v) { this.savedLevel = v; }
}
