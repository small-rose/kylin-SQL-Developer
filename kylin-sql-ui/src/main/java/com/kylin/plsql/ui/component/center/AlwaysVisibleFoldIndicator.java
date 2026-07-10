package com.kylin.plsql.ui.component.center;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldManager;
import org.fife.ui.rtextarea.FoldIndicator;
import org.fife.ui.rtextarea.RTextArea;

import java.awt.*;

public class AlwaysVisibleFoldIndicator extends FoldIndicator {

    public AlwaysVisibleFoldIndicator(RTextArea textArea) {
        super(textArea);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (!(textArea instanceof RSyntaxTextArea rsta)) return;
        FoldManager fm = rsta.getFoldManager();
        if (fm == null || fm.getFoldCount() == 0) return;

        int w = getWidth();
        int lineX = w / 2;

        Rectangle clip = g.getClipBounds();
        Color fg = getForeground();
        Color armedFg = getArmedForeground();

        for (int i = 0; i < fm.getFoldCount(); i++) {
            paintFold(g, rsta, fm.getFold(i), clip, lineX, w, fg, armedFg);
        }
    }

    private void paintFold(Graphics g, RSyntaxTextArea rsta, Fold fold,
                           Rectangle clip, int lineX, int w, Color fg, Color armedFg) {
        if (fold == null) return;

        int y1;
        try {
            y1 = rsta.yForLine(fold.getStartLine());
        } catch (Exception e) {
            return;
        }
        if (y1 < 0) return;

        int lineHeight = rsta.getLineHeight();
        if (y1 > clip.y + clip.height) return;

        if (fold.isCollapsed()) {
            // Already drawn by super.paintComponent — skip arrow duplication
        } else {
            int y2;
            try {
                y2 = rsta.yForLine(fold.getEndLine());
            } catch (Exception e) {
                return;
            }

            // Down arrow at fold start
            int midY = y1 + lineHeight / 2;
            drawDownArrow(g, lineX, midY, fg);

            // Vertical bracket line
            if (y2 >= 0) {
                int endMidY = y2 + lineHeight / 2;
                if (endMidY >= clip.y) {
                    g.setColor(fg);
                    g.drawLine(lineX, midY + 3, lineX, endMidY);
                    g.drawLine(lineX, endMidY, w - 2, endMidY);
                }
            }

            for (int i = 0; i < fold.getChildCount(); i++) {
                paintFold(g, rsta, fold.getChild(i), clip, lineX, w, fg, armedFg);
            }
        }
    }

    private void drawDownArrow(Graphics g, int cx, int cy, Color color) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);
        int[] xPoints = {cx - 4, cx + 4, cx};
        int[] yPoints = {cy - 4, cy - 4, cy + 2};
        g2d.fillPolygon(xPoints, yPoints, 3);
        g2d.dispose();
    }
}
