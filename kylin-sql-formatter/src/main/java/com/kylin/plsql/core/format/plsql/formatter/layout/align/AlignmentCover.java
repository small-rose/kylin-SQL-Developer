package com.kylin.plsql.core.format.plsql.formatter.layout.align;

public class AlignmentCover {

    private int alignColumn;
    private int spaceAdjust;

    public AlignmentCover() {
        this.alignColumn = -1;
        this.spaceAdjust = 0;
    }

    public boolean isActive() { return alignColumn >= 0; }
    public int getAlignColumn() { return alignColumn; }
    public int getSpaceAdjust() { return spaceAdjust; }

    public void setAlignColumn(int col) { this.alignColumn = col; }
    public void setSpaceAdjust(int adj) { this.spaceAdjust = adj; }
}
