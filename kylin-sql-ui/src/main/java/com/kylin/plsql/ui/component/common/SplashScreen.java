package com.kylin.plsql.ui.component.common;

import com.kylin.plsql.core.config.FontManager;

import javax.swing.*;
import java.awt.*;

public class SplashScreen extends JWindow {
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar bar = new JProgressBar();

    public SplashScreen() {
        setSize(560, 350);
        setLocationRelativeTo(null);

        bar.setIndeterminate(false);
        bar.setStringPainted(true);
        bar.setForeground(new Color(0x4CAF50));

        statusLabel.setFont(FontManager.getInstance().resolve("font.top"));
        statusLabel.setForeground(Color.WHITE);

        java.net.URL imgUrl = getClass().getResource("/logo/splash1.png");
        Image splashImage = imgUrl != null ? new ImageIcon(imgUrl).getImage() : null;

        JPanel content = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                if (splashImage != null) {
                    g2.drawImage(splashImage, 0, 0, getWidth(), getHeight(), null);
                } else {
                    GradientPaint gp = new GradientPaint(0, 0, new Color(0x1a1a2e), 0, getHeight(), new Color(0x16213e));
                    g2.setPaint(gp);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(22, 0, 25, 0));
        bottom.add(bar, BorderLayout.NORTH);
        bottom.add(statusLabel, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
    }

    public void setProgress(int percent, String status) {
        String text = status + " (" + percent + "%)";
        if (SwingUtilities.isEventDispatchThread()) {
            bar.setValue(percent);
            statusLabel.setText(text);
        } else {
            SwingUtilities.invokeLater(() -> {
                bar.setValue(percent);
                statusLabel.setText(text);
            });
        }
    }

    public void setStatus(String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText(text);
        } else {
            SwingUtilities.invokeLater(() -> statusLabel.setText(text));
        }
    }

    public void close() {
        dispose();
    }
}
