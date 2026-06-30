package com.kylin.plsql.core.format.engine;

import com.kylin.plsql.core.format.FormatOptions;

/** Tracks line width and column count for maxLineWidth/columnsPerRow enforcement. */
public class LineWidthTracker {
    private int currentLineWidth;
    private int columnCount;
    private final int maxLineWidth;
    private final int maxColumns;

    public LineWidthTracker(FormatOptions options, FormatContext ctx) {
        this.maxLineWidth = options.getMaxLineWidth();
        this.maxColumns = ctx.getMaxColumns();
    }

    public void reset() { currentLineWidth = 0; columnCount = 0; }

    public void addToken(String text) {
        currentLineWidth += text.length() + 1;
        columnCount++;
    }

    public void setLineWidth(int width) {
        this.currentLineWidth = width;
    }

    public boolean shouldBreak(FormatContext ctx) {
        if (maxLineWidth > 0 && currentLineWidth > maxLineWidth) return true;
        int mxc = ctx.getMaxColumns();
        if (mxc > 0 && columnCount > mxc) return true;
        return false;
    }
}
