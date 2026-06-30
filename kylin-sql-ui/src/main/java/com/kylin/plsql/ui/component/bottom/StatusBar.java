package com.kylin.plsql.ui.component.bottom;

import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.ui.component.center.SqlEditorPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.function.Consumer;

/** Status bar showing connection state, cursor position, encoding, lock, memory bar. */
public class StatusBar extends JPanel {
    private final JLabel dotLabel;
    private final JLabel connLabel;
    private final JLabel statusLabel;
    private final JLabel msgLabel;
    private final JLabel posLabel;
    private final JLabel encodingLabel;
    private final JLabel lockLabel;
    private final JLabel typeLabel;
    private final MemoryBar memoryBar;
    private final JLabel syncLabel;
    private final JProgressBar syncProgress;
    private final JPanel syncPanel;

    private int currentLine = 1;
    private int currentCol = 1;

    private Consumer<String> onEncodingChange;
    private Consumer<Boolean> onLockToggle;
    private Timer statusTextTimer;

    public void setOnEncodingChange(Consumer<String> fn) { this.onEncodingChange = fn; }
    public void setOnLockToggle(Consumer<Boolean> fn) { this.onLockToggle = fn; }

    public StatusBar() {
        super(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        dotLabel = new JLabel("\u25CF");
        dotLabel.setFont(dotLabel.getFont().deriveFont(14f));
        dotLabel.setForeground(new Color(0x5CB85C));
        dotLabel.setBorder(new EmptyBorder(2, 8, 2, 2));

        connLabel = new JLabel("\u5C31\u7EEA");
        connLabel.setBorder(new EmptyBorder(2, 0, 2, 4));
        connLabel.setFont(connLabel.getFont().deriveFont(11f));

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(new EmptyBorder(2, 4, 2, 8));
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(dotLabel);
        leftPanel.add(connLabel);
        leftPanel.add(statusLabel);

        msgLabel = new JLabel(" ");
        msgLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        msgLabel.setFont(msgLabel.getFont().deriveFont(11f));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setOpaque(false);

        posLabel = clickableSection("", this::showNavigateDialog);
        encodingLabel = clickableSection("UTF-8", this::showEncodingPopup);
        lockLabel = clickableSection("\uD83D\uDD13", e -> toggleLock());
        typeLabel = section("SQL");
        memoryBar = new MemoryBar();

        syncLabel = new JLabel(" ");
        syncLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        syncLabel.setFont(syncLabel.getFont().deriveFont(11f));
        syncProgress = new JProgressBar(0, 100);
        syncProgress.setPreferredSize(new Dimension(60, 14));
        syncProgress.setStringPainted(true);
        syncProgress.setFont(syncProgress.getFont().deriveFont(9f));
        syncProgress.setVisible(false);
        syncPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        syncPanel.setOpaque(false);
        syncPanel.add(syncLabel);
        syncPanel.add(syncProgress);

        rightPanel.add(syncPanel);
        rightPanel.add(sep());

        rightPanel.add(posLabel);
        rightPanel.add(sep());
        rightPanel.add(encodingLabel);
        rightPanel.add(sep());
        rightPanel.add(lockLabel);
        rightPanel.add(sep());
        rightPanel.add(typeLabel);
        rightPanel.add(sep());
        rightPanel.add(memoryBar);

        add(leftPanel, BorderLayout.WEST);
        add(msgLabel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        applyTheme();
    }

    // ── Section helpers ──

    private static JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setBorder(new EmptyBorder(2, 6, 2, 6));
        l.setFont(l.getFont().deriveFont(11f));
        return l;
    }

    private static JLabel clickableSection(String text, java.awt.event.ActionListener onClick) {
        JLabel l = new JLabel(text);
        l.setBorder(new EmptyBorder(2, 6, 2, 6));
        l.setFont(l.getFont().deriveFont(11f));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClick != null) onClick.actionPerformed(null);
            }
        });
        return l;
    }

    private static JLabel sep() {
        JLabel l = new JLabel("\u2502");
        l.setFont(l.getFont().deriveFont(10f));
        l.setBorder(new EmptyBorder(2, 1, 2, 1));
        return l;
    }

    // ── Setters ──

    public void setConnection(String connName) {
        setConnection(connName, true);
    }

    public void setConnection(String connName, boolean connected) {
        if (connName == null || connName.isEmpty()) {
            dotLabel.setVisible(false);
            connLabel.setText("");
        } else {
            dotLabel.setVisible(true);
            dotLabel.setForeground(connected ? new Color(0x5CB85C) : new Color(0xD9534F));
            connLabel.setText(connName);
        }
    }

    public void setStatusText(String text) {
        if (text == null || text.isEmpty()) {
            statusLabel.setText(" ");
            return;
        }
        if (statusTextTimer != null && statusTextTimer.isRunning()) {
            statusTextTimer.stop();
        }
        statusLabel.setText(text);
        statusTextTimer = new Timer(3000, e -> statusLabel.setText(" "));
        statusTextTimer.setRepeats(false);
        statusTextTimer.start();
    }

    public void setMessage(String msg) {
        msgLabel.setText(msg != null ? msg : " ");
    }

    public void setPosition(int line, int col) {
        currentLine = line;
        currentCol = col;
        posLabel.setText("Ln " + line + ", Col " + col);
    }

    public void setEncoding(String enc) {
        encodingLabel.setText(enc != null ? enc : "UTF-8");
    }

    public void setFileType(String type) {
        typeLabel.setText(type != null ? type : "");
    }

    public void setLocked(boolean locked) {
        lockLabel.setText(locked ? "\uD83D\uDD12" : "\uD83D\uDD13");
        lockLabel.setForeground(locked ? new Color(0xD9534F) : new Color(0x5CB85C));
    }

    public void setSyncProgress(String text, int percent) {
        syncLabel.setText(text);
        syncProgress.setValue(percent);
        syncProgress.setString(percent + "%");
        syncProgress.setVisible(true);
    }

    public void hideSyncProgress() {
        syncProgress.setVisible(false);
        syncLabel.setText(" ");
    }

    // ── Click handlers ──

    private void showNavigateDialog(java.awt.event.ActionEvent e) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, "\u8DF3\u8F6C\u5230\u884C", ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();

        ThemeManager tm = ThemeManager.getInstance();
        Color bg = tm.resolve("bg.panel");
        Color fg = tm.resolve("fg.main");
        dlg.getContentPane().setBackground(bg);

        g.insets = new Insets(8, 10, 4, 10);
        g.anchor = GridBagConstraints.WEST;

        JLabel lineLabel = new JLabel("\u884C:");
        lineLabel.setForeground(fg);
        JLabel colLabel = new JLabel("\u5217:");
        colLabel.setForeground(fg);

        SpinnerNumberModel lineModel = new SpinnerNumberModel(currentLine, 1, Integer.MAX_VALUE, 1);
        SpinnerNumberModel colModel = new SpinnerNumberModel(currentCol, 1, Integer.MAX_VALUE, 1);
        JSpinner lineSpinner = new JSpinner(lineModel);
        JSpinner colSpinner = new JSpinner(colModel);
        lineSpinner.setPreferredSize(new Dimension(120, 26));
        colSpinner.setPreferredSize(new Dimension(120, 26));

        g.gridx = 0; g.gridy = 0;
        dlg.add(lineLabel, g);
        g.gridx = 1;
        dlg.add(lineSpinner, g);
        g.gridx = 0; g.gridy = 1;
        g.insets = new Insets(4, 10, 10, 10);
        dlg.add(colLabel, g);
        g.gridx = 1;
        dlg.add(colSpinner, g);

        JButton okBtn = new JButton("\u8DF3\u8F6C");
        okBtn.setBackground(tm.resolve("accent.green"));
        okBtn.setForeground(Color.WHITE);
        okBtn.setOpaque(true);
        okBtn.setBorderPainted(false);
        okBtn.setFocusPainted(false);
        okBtn.setFont(okBtn.getFont().deriveFont(Font.BOLD));
        okBtn.addActionListener(ev -> {
            int l = (Integer) lineSpinner.getValue();
            int c = (Integer) colSpinner.getValue();
            navigateToLineCol(l, c);
            dlg.dispose();
        });

        JButton cancelBtn = new JButton("\u53D6\u6D88");
        cancelBtn.addActionListener(ev -> dlg.dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        g.gridx = 0; g.gridy = 2; g.gridwidth = 2;
        g.insets = new Insets(0, 10, 10, 10);
        g.anchor = GridBagConstraints.CENTER;
        dlg.add(btnPanel, g);

        // Enter → jump, Escape → cancel
        dlg.getRootPane().setDefaultButton(okBtn);
        dlg.getRootPane().registerKeyboardAction(
            e2 -> dlg.dispose(),
            KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        dlg.pack();
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    private void navigateToLineCol(int line, int col) {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (!(w instanceof JFrame frame)) return;
        Component active = ((JTabbedPane) findComponent(frame, JTabbedPane.class)).getSelectedComponent();
        if (active instanceof SqlEditorPanel ep) {
            var ta = ep.getTextArea();
            try {
                int offset = ta.getLineStartOffset(Math.max(0, line - 1));
                offset = Math.min(offset + Math.max(0, col - 1), ta.getDocument().getLength());
                ta.setCaretPosition(offset);
                ta.requestFocusInWindow();
            } catch (Exception ignored) {}
        }
    }

    private static Component findComponent(Container parent, Class<?> type) {
        for (Component c : parent.getComponents()) {
            if (type.isInstance(c)) return c;
            if (c instanceof Container) {
                Component found = findComponent((Container) c, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void showEncodingPopup(java.awt.event.ActionEvent e) {
        JPopupMenu popup = new JPopupMenu();
        String[] charsets = {"UTF-8", "GBK", "GB2312", "GB18030", "ISO-8859-1", "Shift_JIS"};
        for (String cs : charsets) {
            JMenuItem item = new JMenuItem(cs);
            item.addActionListener(ev -> {
                encodingLabel.setText(cs);
                if (onEncodingChange != null) onEncodingChange.accept(cs);
            });
            popup.add(item);
        }
        popup.show(encodingLabel, 0, encodingLabel.getHeight());
    }

    private boolean locked = false;

    private void toggleLock() {
        locked = !locked;
        lockLabel.setText(locked ? "\uD83D\uDD12" : "\uD83D\uDD13");
        lockLabel.setForeground(locked ? new Color(0xD9534F) : new Color(0x5CB85C));
        if (onLockToggle != null) onLockToggle.accept(locked);
    }

    // ── Theme ──

    public void applyTheme() {
        ThemeManager tm = ThemeManager.getInstance();
        Color bg = tm.resolve("bg.toolbar");
        Color fg = tm.resolve("fg.muted");
        Color border = tm.resolve("border.light");

        setBackground(bg);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, border));

        for (Component c : new Component[]{connLabel, statusLabel, msgLabel, posLabel, encodingLabel, typeLabel, syncLabel}) {
            c.setForeground(fg);
        }
        lockLabel.setForeground(locked ? new Color(0xD9534F) : new Color(0x5CB85C));
        for (Component c : ((JPanel) getComponent(2)).getComponents()) {
            if (c instanceof MemoryBar) { ((MemoryBar) c).applyTheme(); break; }
        }
    }

    // ── Memory Indicator ──

    private class MemoryBar extends JPanel {
        private final Timer refreshTimer;
        private final Timer gcFlashTimer;
        private long used;
        private long max;
        private boolean gcFlash;
        private Color barColor = new Color(0x5CB85C);

        MemoryBar() {
            setPreferredSize(new Dimension(140, 20));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(true);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    triggerGc();
                }
            });

            refreshTimer = new Timer(3000, e -> refresh());
            gcFlashTimer = new Timer(500, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    gcFlash = false;
                    gcFlashTimer.stop();
                    repaint();
                }
            });
            gcFlashTimer.setRepeats(false);
            refresh();
        }

        void start() { if (!refreshTimer.isRunning()) refreshTimer.start(); }
        void stop() { refreshTimer.stop(); }

        private void refresh() {
            Runtime rt = Runtime.getRuntime();
            long total = rt.totalMemory();
            long free = rt.freeMemory();
            used = total - free;
            max = rt.maxMemory();
            if (max < 0) max = total;

            double pct = max > 0 ? (double) used / max : 0;
            if (pct < 0.6) barColor = new Color(0x5CB85C);
            else if (pct < 0.85) barColor = new Color(0xF0AD4E);
            else barColor = new Color(0xD9534F);

            repaint();
        }

        private void triggerGc() {
            gcFlash = true;
            repaint();
            new Thread(() -> {
                System.gc();
                System.runFinalization();
                SwingUtilities.invokeLater(() -> { refresh(); gcFlashTimer.start(); });
            }).start();
        }

        void applyTheme() {
            ThemeManager tm = ThemeManager.getInstance();
            setBackground(tm.resolve("bg.toolbar"));
            setForeground(tm.resolve("fg.main"));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (gcFlash) {
                g2.setColor(getForeground());
                g2.setFont(getFont().deriveFont(10f));
                String txt = "GC...";
                FontMetrics fm = g2.getFontMetrics();
                int x = (w - fm.stringWidth(txt)) / 2;
                int y = (h + fm.getAscent()) / 2 - 1;
                g2.drawString(txt, x, y);
                g2.dispose();
                return;
            }

            int pad = 2;
            int rx = pad;
            int ry = pad;
            int rw = w - pad * 2;
            int rh = h - pad * 2;

            // Thin border
            g2.setColor(ThemeManager.getInstance().resolve("border.light"));
            g2.drawRoundRect(rx, ry, rw - 1, rh - 1, 3, 3);

            // Inner bar background
            int barX = rx + 1;
            int barY = ry + 1;
            int barW = rw - 2;
            int barH = rh - 2;
            g2.setColor(ThemeManager.getInstance().resolve("bg.panel"));
            g2.fillRoundRect(barX, barY, barW, barH, 2, 2);

            // Usage fill
            double pct = max > 0 ? Math.min(1.0, (double) used / max) : 0;
            int fillW = Math.max(1, (int) (barW * pct));
            g2.setColor(barColor);
            g2.fillRoundRect(barX, barY, fillW, barH, 2, 2);

            // Text overlay
            g2.setColor(getForeground());
            g2.setFont(getFont().deriveFont(10f));
            String txt = formatMem(used) + " / " + formatMem(max);
            FontMetrics fm = g2.getFontMetrics();
            int x = (w - fm.stringWidth(txt)) / 2;
            int y = (h + fm.getAscent()) / 2 - 1;
            g2.drawString(txt, x, y);

            g2.dispose();
        }

        private String formatMem(long bytes) {
            long mb = bytes / (1024L * 1024L);
            if (mb >= 1024) {
                double gb = mb / 1024.0;
                return new DecimalFormat("#.#").format(gb) + "G";
            }
            return mb + "M";
        }
    }

    public void startMemoryMonitor() { memoryBar.start(); }
    public void stopMemoryMonitor() { memoryBar.stop(); }
}
