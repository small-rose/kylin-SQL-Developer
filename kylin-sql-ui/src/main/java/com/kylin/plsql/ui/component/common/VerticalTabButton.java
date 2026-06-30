package com.kylin.plsql.ui.component.common;

import com.kylin.plsql.core.config.ThemeManager;

import javax.swing.*;
import java.awt.*;

/** Vertical text tab button used in left/right side panels. */
public class VerticalTabButton extends JButton {
    private static final Font TAB_FONT = new Font("Segoe UI", Font.PLAIN, 11);
    private static final int TAB_STRIP_WIDTH = 28;
    private boolean active;
    private Dimension fixedPrefSize;

    private final ThemeManager theme = ThemeManager.getInstance();

    public VerticalTabButton(String text) {
        super(text);
        setFont(TAB_FONT);
        setFocusable(false);
        setContentAreaFilled(false);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        setAlignmentX(LEFT_ALIGNMENT);
        setOpaque(true);
        putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        recalcPrefSize();
    }

    private void recalcPrefSize() {
        FontMetrics fm = Toolkit.getDefaultToolkit().getFontMetrics(TAB_FONT);
        int extra = "FILES".equals(getText()) ? 50 : 40;
        fixedPrefSize = new Dimension(TAB_STRIP_WIDTH, fm.stringWidth(getText()) + extra);
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() { return fixedPrefSize; }

    @Override
    public Dimension getMinimumSize() { return fixedPrefSize; }
    @Override
    public Dimension getMaximumSize() { return fixedPrefSize; }

    @Override
    public boolean isOpaque() { return true; }

    @Override
    public void updateUI() {
        super.updateUI();
        setFont(TAB_FONT);
        setOpaque(true);
        setContentAreaFilled(false);
        recalcPrefSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();
        g2.setColor(active ? theme.resolve("selection.listBg") : theme.resolve("bg.main"));
        g2.fillRect(0, 0, w, h);

        if (active) {
            g2.setColor(theme.resolve("accent.green"));
            g2.fillRect(0, 0, 3, h);
        }

        g2.setColor(theme.resolve("fg.secondary"));
        g2.setFont(TAB_FONT);
        g2.rotate(-Math.PI / 2);
        FontMetrics fm = g2.getFontMetrics();
        String text = getText();
        int tx = -(h + fm.stringWidth(text)) / 2;
        int ty = (w + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, tx, ty);
        g2.dispose();
    }
}
