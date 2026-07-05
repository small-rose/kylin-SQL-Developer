package com.kylin.plsql.core.format.plsql.formatter.layout.plan;

import com.kylin.plsql.core.format.plsql.formatter.layout.align.AlignmentCover;

public class FinalLayout {

    private boolean newline;
    private int indent;
    private int spaces;

    public FinalLayout() {
        this.newline = false;
        this.indent = 0;
        this.spaces = 1;
    }

    public boolean isNewline() { return newline; }
    public int getIndent() { return indent; }
    public int getSpaces() { return spaces; }

    public void setNewline(boolean v) { this.newline = v; }
    public void setIndent(int v) { this.indent = v; }
    public void setSpaces(int v) { this.spaces = v; }

    public static FinalLayout merge(
            StructuralFrame frame,
            AlignmentCover align,
            int indentSize) {

        FinalLayout out = new FinalLayout();
        out.newline = frame.isNewline();
        int baseIndent = frame.getIndentLevel() * indentSize;
        if (align != null && align.isActive()) {
            out.indent = Math.max(baseIndent, align.getAlignColumn());
        } else {
            out.indent = baseIndent;
        }
        if (align != null && align.isActive() && !out.newline) {
            out.spaces = Math.max(1, frame.getSpaces() + align.getSpaceAdjust());
        } else {
            out.spaces = frame.getSpaces();
        }
        return out;
    }

    public void applyBreak(int indentLevel, int indentSize) {
        this.newline = true;
        this.indent = indentLevel * indentSize;
    }
}
