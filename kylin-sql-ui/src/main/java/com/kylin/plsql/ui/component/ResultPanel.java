package com.kylin.plsql.ui.component;

import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.SqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ResultPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ResultPanel.class);
    private final ThemeManager theme = ThemeManager.getInstance();

    private final JTabbedPane resultTabs;
    private final JTextArea messageArea;
    private final List<ResultTabData> tabDataList = new ArrayList<>();
    private int resultCounter = 0;
    private boolean batchExecuting;
    private java.util.function.BiConsumer<String, String> refreshExecutor;

    private static class ResultTabData {
        String label;
        String sql;
        String connName;
        List<String> columns;
        List<List<Object>> allRows;
        int pageSize = 100;
        int currentPage = 0;
        boolean pinned;
        JTable table;
        PaginatedTableModel model;
        JLabel pageInfoLabel;

        int getTotalPages() {
            if (allRows == null || allRows.isEmpty() || pageSize <= 0) return 1;
            return (int) Math.ceil((double) allRows.size() / pageSize);
        }
        int getOffset() { return currentPage * pageSize; }
        int getDisplayRowCount() {
            if (allRows == null || allRows.isEmpty()) return 0;
            return Math.min(pageSize, allRows.size() - getOffset());
        }
    }

    private static class PaginatedTableModel extends AbstractTableModel {
        private ResultTabData data;
        private List<String> columns;

        void setData(ResultTabData d, List<String> cols) {
            this.data = d;
            this.columns = cols;
            fireTableStructureChanged();
        }

        @Override public int getRowCount() { return data != null ? data.getDisplayRowCount() : 0; }
        @Override public int getColumnCount() { return (columns != null ? columns.size() : 0) + 1; }
        @Override public Object getValueAt(int row, int col) {
            if (data == null || data.allRows == null) return null;
            int absRow = data.getOffset() + row;
            if (absRow >= data.allRows.size()) return null;
            if (col == 0) return absRow + 1;
            int dc = col - 1;
            List<Object> rowData = data.allRows.get(absRow);
            return dc < rowData.size() ? rowData.get(dc) : null;
        }
        @Override public String getColumnName(int col) {
            if (col == 0) return "#";
            if (columns == null || col - 1 >= columns.size()) return "";
            return columns.get(col - 1);
        }
        @Override public boolean isCellEditable(int r, int c) { return false; }
    }

    public ResultPanel() {
        setLayout(new BorderLayout());

        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        applyMessageColors();
        JScrollPane msgScroll = new JScrollPane(messageArea);
        msgScroll.setBorder(null);
        msgScroll.getViewport().setBackground(theme.resolve("bg.output"));

        resultTabs = new JTabbedPane();
        resultTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        applyTabTheme();
        resultTabs.addTab("\u6D88\u606F", msgScroll);
        installResultTabContextMenu();
        add(resultTabs, BorderLayout.CENTER);
    }

    public void applyTheme() {
        setBackground(theme.resolve("bg.main"));
        applyTabTheme();
        applyMessageColors();
        for (Component c : getComponents()) {
            if (c instanceof JScrollPane sp) {
                sp.getViewport().setBackground(theme.resolve("bg.output"));
            }
        }
        // Apply to all result tab content panels recursively
        for (Component c : resultTabs.getComponents()) {
            if (c instanceof JScrollPane sp) {
                sp.getViewport().setBackground(theme.resolve("bg.output"));
            } else {
                applyComponentTreeTheme(c);
            }
        }
        // Directly update all existing result tables by iterating tabDataList
        for (ResultTabData d : tabDataList) {
            if (d.table != null) applyTableTheme(d.table);
        }
    }

    private void applyComponentTreeTheme(Component c) {
        if (c == null) return;
        if (c instanceof JPanel p) p.setBackground(theme.resolve("bg.output"));
        if (c instanceof JViewport vp) vp.setBackground(theme.resolve("bg.output"));
        if (c instanceof JScrollPane sp) sp.getViewport().setBackground(theme.resolve("bg.output"));
        if (c instanceof JToolBar tb) tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.resolve("border.light")));
        if (c instanceof JTable table) {
            table.setBackground(theme.resolve("bg.output"));
            table.setForeground(theme.resolve("fg.main"));
            table.setGridColor(theme.resolve("border.default"));
            table.setSelectionBackground(theme.resolve("selection.bg"));
            table.setSelectionForeground(theme.resolve("selection.fg"));
            JTableHeader h = table.getTableHeader();
            h.setBackground(theme.resolve("bg.toolbar"));
            h.setForeground(theme.resolve("fg.secondary"));
        }
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                applyComponentTreeTheme(child);
            }
        }
    }

    private void applyMessageColors() {
        messageArea.setBackground(theme.resolve("bg.output"));
        messageArea.setForeground(theme.resolve("fg.secondary"));
        messageArea.setCaretColor(theme.resolve("fg.secondary"));
    }

    private void applyTabTheme() {
        resultTabs.setBackground(theme.resolve("bg.main"));
        resultTabs.setForeground(theme.resolve("fg.secondary"));
    }

    private void applyTableTheme(JTable table) {
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.setBackground(theme.resolve("bg.output"));
        table.setForeground(theme.resolve("fg.main"));
        table.setGridColor(theme.resolve("border.default"));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(theme.resolve("selection.bg"));
        table.setSelectionForeground(theme.resolve("selection.fg"));
        JTableHeader h = table.getTableHeader();
        h.setBackground(theme.resolve("bg.toolbar"));
        h.setForeground(theme.resolve("fg.secondary"));
        h.setFont(new Font("Segoe UI", Font.BOLD, 11));
    }

    private void showToast(String msg) {
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        if (ancestor == null) return;
        JWindow toast = new JWindow(ancestor);
        JLabel label = new JLabel(msg);
        label.setOpaque(true);
        Color bg = theme.resolve("bg.panel");
        boolean dark = bg.getRed() + bg.getGreen() + bg.getBlue() < 382;
        label.setBackground(dark ? new Color(0xE0E0E0) : new Color(0x444444));
        label.setForeground(dark ? new Color(0x222222) : Color.WHITE);
        label.setFont(new Font("Dialog", Font.PLAIN, 12));
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(dark ? new Color(0xAAAAAA) : new Color(0x666666)),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        toast.add(label);
        toast.pack();
        Point p = ancestor.getLocation();
        toast.setLocation(p.x + ancestor.getWidth() - toast.getWidth() - 24,
                          p.y + ancestor.getHeight() - toast.getHeight() - 40);
        toast.setVisible(true);
        new Timer(1500, e -> { toast.dispose(); }).start();
    }

    // ── per-result-tab toolbar builder ──

    private JPanel buildResultContent(ResultTabData d) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(theme.resolve("bg.output"));

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.resolve("border.light")));

        JButton prevBtn = makeTbBtn("\u25C0", "\u4E0A\u4E00\u9875");
        prevBtn.addActionListener(e -> { if (d.currentPage > 0) { d.currentPage--; d.model.fireTableDataChanged(); updatePageInfo(d); }});

        JComboBox<String> psc = new JComboBox<>(new String[]{"25", "50", "100", "500", "\u5168\u90E8"});
        psc.setSelectedItem("100");
        psc.setFont(new Font("Dialog", Font.PLAIN, 12));
        psc.setPreferredSize(new Dimension(55, 22));
        psc.setMaximumSize(new Dimension(55, 22));
        psc.addActionListener(e -> {
            if (psc.getSelectedItem() == null) return;
            String v = (String) psc.getSelectedItem();
            d.pageSize = "\u5168\u90E8".equals(v) ? Integer.MAX_VALUE : Integer.parseInt(v);
            d.currentPage = 0;
            d.model.fireTableDataChanged();
            updatePageInfo(d);
        });

        JButton nextBtn = makeTbBtn("\u25B6", "\u4E0B\u4E00\u9875");
        nextBtn.addActionListener(e -> { if (d.currentPage < d.getTotalPages() - 1) { d.currentPage++; d.model.fireTableDataChanged(); updatePageInfo(d); }});

        JButton refreshBtn = makeTbBtn("\u21BB", "\u5237\u65B0\u7ED3\u679C\u96C6");
        refreshBtn.addActionListener(e -> { d.currentPage = 0; d.model.fireTableDataChanged(); updatePageInfo(d); });

        JButton stopBtn = makeTbBtn("\u25A0", "\u505C\u6B62\u67E5\u8BE2");
        stopBtn.addActionListener(e -> appendMessage("\u67E5\u8BE2\u5DF2\u53D6\u6D88"));

        JButton pinBtn = makeTbBtn("\uD83D\uDCCC", "\u56FA\u5B9A\u6807\u7B7E\u4E0D\u88AB\u66FF\u6362");
        pinBtn.addActionListener(e -> {
            d.pinned = !d.pinned;
            pinBtn.setForeground(d.pinned ? theme.resolve("accent.green") : theme.resolve("fg.main"));
            int idx = resultTabs.indexOfComponent(panel);
            if (idx > 0) updateResultTabComponent(idx);
        });

        JLabel infoLbl = new JLabel();
        infoLbl.setFont(new Font("Dialog", Font.PLAIN, 11));
        infoLbl.setForeground(theme.resolve("fg.muted"));
        infoLbl.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        d.pageInfoLabel = infoLbl;

        tb.add(prevBtn);
        tb.add(psc);
        tb.add(nextBtn);
        tb.addSeparator(new Dimension(10, 0));
        tb.add(refreshBtn);
        tb.add(stopBtn);
        tb.add(pinBtn);
        tb.add(Box.createHorizontalGlue());
        tb.add(infoLbl);

        JScrollPane scroll = new JScrollPane(d.table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(theme.resolve("bg.output"));

        panel.add(tb, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void updatePageInfo(ResultTabData d) {
        if (d == null || d.allRows == null || d.allRows.isEmpty()) {
            if (d != null && d.pageInfoLabel != null) d.pageInfoLabel.setText("");
            return;
        }
        int total = d.allRows.size();
        int from = d.getOffset() + 1;
        int to = Math.min(d.getOffset() + d.pageSize, total);
        if (d.pageInfoLabel != null) {
            d.pageInfoLabel.setText(from + " - " + to + " / \u5171 " + total + " \u884C");
        }
        if (d.table != null) d.table.repaint();
    }

    private JButton makeTbBtn(String text, String tip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tip);
        btn.setFont(new Font("Dialog", Font.PLAIN, 11));
        btn.setFocusable(false);
        btn.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        btn.setContentAreaFilled(false);
        btn.setForeground(theme.resolve("fg.main"));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void installTabClose(int tabIndex, String closeLabel) {
        updateResultTabComponent(tabIndex);
    }

    private void updateResultTabComponent(int tabIndex) {
        if (tabIndex <= 0 || tabIndex > tabDataList.size()) return;
        int dataIdx = tabIndex - 1;
        ResultTabData d = tabDataList.get(dataIdx);
        JPanel tabComp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabComp.setOpaque(false);
        boolean isTableResult = d.label != null && !d.label.startsWith("result");
        JLabel iconLbl = new JLabel(makeResultIcon(isTableResult ? "T" : "R", new Color(0x337AB7)));
        tabComp.add(iconLbl);
        tabComp.add(Box.createHorizontalStrut(2));
        JLabel lbl = new JLabel(d.label);
        lbl.setForeground(theme.resolve("fg.secondary"));
        tabComp.add(lbl);
        tabComp.add(Box.createHorizontalStrut(2));
        tabComp.add(Box.createHorizontalGlue());
        if (d.pinned) {
            tabComp.add(new JLabel(new PinIcon(theme.resolve("accent.green"))));
        } else {
            JButton closeBtn = makeResultCloseBtn();
            closeBtn.addActionListener(e -> closeResultTab(tabIndex));
            tabComp.add(closeBtn);
        }
        tabComp.add(Box.createHorizontalStrut(2));
        resultTabs.setTabComponentAt(tabIndex, tabComp);
    }

    // ── Result tab context menu ──

    private void installResultTabContextMenu() {
        resultTabs.addMouseListener(new java.awt.event.MouseAdapter() {
            private void showMenu(java.awt.event.MouseEvent e) {
                int idx = resultTabs.indexAtLocation(e.getX(), e.getY());
                if (idx <= 0) return;
                resultTabs.setSelectedIndex(idx);
                showResultTabMenu(e.getX(), e.getY(), idx);
            }
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showMenu(e);
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showMenu(e);
            }
        });
    }

    private void showResultTabMenu(int x, int y, int tabIndex) {
        int dataIdx = tabIndex - 1;
        if (dataIdx < 0 || dataIdx >= tabDataList.size()) return;
        ResultTabData d = tabDataList.get(dataIdx);
        JPopupMenu menu = new JPopupMenu();
        boolean pinned = d.pinned;

        JMenuItem closeItem = new JMenuItem("\u5173\u95ED");
        closeItem.setEnabled(!pinned);
        closeItem.addActionListener(e -> closeResultTab(tabIndex));
        menu.add(closeItem);

        JMenuItem closeOthersItem = new JMenuItem("\u5173\u95ED\u5176\u4ED6");
        closeOthersItem.addActionListener(e -> closeOtherResultTabs(tabIndex));
        menu.add(closeOthersItem);

        JMenuItem closeAllItem = new JMenuItem("\u5173\u95ED\u5168\u90E8");
        closeAllItem.addActionListener(e -> closeAllResultTabs());
        menu.add(closeAllItem);

        JMenuItem closeLeftItem = new JMenuItem("\u5173\u95ED\u5DE6\u4FA7\u6807\u7B7E");
        closeLeftItem.addActionListener(e -> closeLeftResultTabs(tabIndex));
        menu.add(closeLeftItem);

        JMenuItem closeRightItem = new JMenuItem("\u5173\u95ED\u53F3\u4FA7\u6807\u7B7E");
        closeRightItem.addActionListener(e -> closeRightResultTabs(tabIndex));
        menu.add(closeRightItem);

        menu.addSeparator();

        JMenuItem pinItem = new JMenuItem(pinned ? "\u53D6\u6D88\u56FA\u5B9A" : "\u56FA\u5B9A\u6807\u7B7E");
        pinItem.addActionListener(e -> toggleResultPin(tabIndex));
        menu.add(pinItem);

        menu.addSeparator();

        JMenuItem refreshItem = new JMenuItem("\u5237\u65B0\u7ED3\u679C");
        refreshItem.addActionListener(e -> {
            if (refreshExecutor != null && d.sql != null && !d.sql.isBlank()) {
                refreshExecutor.accept(d.connName, d.sql);
            }
        });
        menu.add(refreshItem);

        menu.show(resultTabs, x, y);
    }

    private void toggleResultPin(int tabIndex) {
        int dataIdx = tabIndex - 1;
        if (dataIdx < 0 || dataIdx >= tabDataList.size()) return;
        tabDataList.get(dataIdx).pinned = !tabDataList.get(dataIdx).pinned;
        updateResultTabComponent(tabIndex);
    }

    private void closeOtherResultTabs(int keepIndex) {
        for (int i = resultTabs.getTabCount() - 1; i > 0; i--) {
            if (i == keepIndex) continue;
            int di = i - 1;
            if (di < tabDataList.size() && tabDataList.get(di).pinned) continue;
            tabDataList.remove(di);
            resultTabs.removeTabAt(i);
            if (i < keepIndex) keepIndex--;
        }
    }

    private void closeAllResultTabs() {
        for (int i = resultTabs.getTabCount() - 1; i > 0; i--) {
            int di = i - 1;
            if (di < tabDataList.size() && tabDataList.get(di).pinned) continue;
            tabDataList.remove(di);
            resultTabs.removeTabAt(i);
        }
    }

    private void closeLeftResultTabs(int anchor) {
        for (int i = anchor - 1; i > 0; i--) {
            int di = i - 1;
            if (di < tabDataList.size() && tabDataList.get(di).pinned) continue;
            tabDataList.remove(di);
            resultTabs.removeTabAt(i);
        }
    }

    private void closeRightResultTabs(int anchor) {
        for (int i = resultTabs.getTabCount() - 1; i > anchor; i--) {
            int di = i - 1;
            if (di < tabDataList.size() && tabDataList.get(di).pinned) continue;
            tabDataList.remove(di);
            resultTabs.removeTabAt(i);
        }
    }

    private static Icon makeResultIcon(String text, Color bg) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(1, 1, 14, 14, 3, 3);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        int x = (16 - fm.stringWidth(text)) / 2;
        int y = (16 + fm.getAscent()) / 2 - 1;
        g.drawString(text, x, y);
        g.dispose();
        return new ImageIcon(img);
    }

    private static JButton makeResultCloseBtn() {
        JButton btn = new JButton("\u00D7") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(0xE81123));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.setColor(Color.WHITE);
                } else {
                    g2.setColor(getForeground());
                }
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 1;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Dialog", Font.PLAIN, 12));
        btn.setPreferredSize(new Dimension(18, 18));
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setContentAreaFilled(false);
        btn.setFocusable(false);
        btn.setRolloverEnabled(true);
        btn.setForeground(new Color(0x888888));
        return btn;
    }

    private static class PinIcon implements Icon {
        private final Color color;
        PinIcon(Color c) { this.color = c; }
        @Override public int getIconWidth() { return 10; }
        @Override public int getIconHeight() { return 10; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillOval(x + 1, y + 1, 8, 8);
        }
    }

    private void closeResultTab(int tabIndex) {
        if (tabIndex <= 0 || tabIndex >= resultTabs.getTabCount()) return;
        int dataIdx = tabIndex - 1;
        if (dataIdx < tabDataList.size() && tabDataList.get(dataIdx).pinned) return;
        if (dataIdx < tabDataList.size()) tabDataList.remove(dataIdx);
        resultTabs.removeTabAt(tabIndex);
    }

    // === Public API ===

    public synchronized void showResult(String sql, SqlExecutor.SqlResult result) {
        showResult(sql, result, "");
    }

    public synchronized void showResult(String sql, SqlExecutor.SqlResult result, String connName) {
        if (result.isSuccess() && result.isQuery && result.columns != null && !result.columns.isEmpty()) {
            // reuse same-sql unpinned tab if exists (skip during batch multi-statement execution)
            if (!batchExecuting) {
                ResultTabData reuse = null;
                for (ResultTabData d : tabDataList) {
                    if (!d.pinned && sql != null && sql.equals(d.sql)) { reuse = d; break; }
                }
                if (reuse != null) {
                    reuse.columns = result.columns;
                    reuse.allRows = result.rows;
                    reuse.currentPage = 0;
                    reuse.model.setData(reuse, result.columns);
                    resultTabs.setSelectedIndex(tabDataList.indexOf(reuse) + 1);
                    autoResizeColWidths(reuse);
                    updatePageInfo(reuse);
                    return;
                }
            }
            // guess label from SQL
            String label = guessLabel(sql);
            if (label == null) label = "result" + (++resultCounter);
            ResultTabData d = new ResultTabData();
            d.label = label;
            d.sql = sql;
            d.connName = connName;
            d.allRows = result.rows;
            d.pageSize = 100;
            d.currentPage = 0;
            d.pinned = false;
            d.table = new JTable();
            d.table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            d.table.setFillsViewportHeight(true);
            d.table.getTableHeader().setReorderingAllowed(false);
            d.table.getTableHeader().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int col = d.table.columnAtPoint(e.getPoint());
                        if (col > 0 && col - 1 < d.columns.size()) {
                            String name = d.columns.get(col - 1);
                            int dot = name.lastIndexOf('.');
                            if (dot >= 0) name = name.substring(dot + 1);
                            Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(name), null);
                            showToast("\u5DF2\u590D\u5236: " + name);
                        }
                    }
                }
            });
            d.table.setAutoCreateRowSorter(true);
            d.table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int row = d.table.rowAtPoint(e.getPoint());
                        int col = d.table.columnAtPoint(e.getPoint());
                        if (row >= 0 && col >= 0) {
                            Object val = d.table.getValueAt(row, col);
                            if (val != null) {
                                Toolkit.getDefaultToolkit().getSystemClipboard()
                                    .setContents(new StringSelection(val.toString()), null);
                                showToast("\u5DF2\u590D\u5236: " + val.toString());
                            }
                        }
                    }
                }
            });
            d.model = new PaginatedTableModel();
            d.model.setData(d, result.columns);
            d.table.setModel(d.model);
            applyTableTheme(d.table);

            JPanel tabContent = buildResultContent(d);
            resultTabs.addTab(label, tabContent);
            int idx = resultTabs.getTabCount() - 1;
            resultTabs.setSelectedIndex(idx);
            tabDataList.add(d);
            installTabClose(idx, label);
            autoResizeColWidths(d);
            updatePageInfo(d);
        } else if (result.isSuccess()) {
            showMessage(result.getSummary());
        } else {
            showError(result.error);
        }
    }

    // ── smart tab label ──

    private static String guessLabel(String sql) {
        if (sql == null || sql.isBlank()) return null;
        int fromIdx = -1;
        for (int i = 0; i <= sql.length() - 4; i++) {
            if (sql.substring(i, i + 4).equalsIgnoreCase("FROM")) { fromIdx = i; break; }
        }
        if (fromIdx < 0) return null;
        String after = sql.substring(fromIdx + 4).trim();
        if (after.isEmpty()) return null;
        String upper = after.toUpperCase();
        if (upper.contains("(") || upper.contains("JOIN") || after.contains(",")) return null;
        String[] parts = after.split("\\s+");
        if (parts.length == 0) return null;
        String name = parts[0];
        int dot = name.indexOf('.');
        if (dot >= 0) name = name.substring(dot + 1);
        name = name.replace("\"", "").replace("`", "").replace("'", "");
        if (name.isEmpty()) return null;
        if (name.length() > 20) name = name.substring(0, 20) + "\u2026";
        return name;
    }

    private void autoResizeColWidths(ResultTabData d) {
        if (d.table == null || d.columns == null) return;
        d.table.getColumnModel().getColumn(0).setPreferredWidth(45);
        d.table.getColumnModel().getColumn(0).setMaxWidth(55);
        FontMetrics fm = d.table.getFontMetrics(d.table.getFont());
        for (int i = 1; i < d.model.getColumnCount(); i++) {
            int max = 80;
            String header = d.model.getColumnName(i);
            max = Math.max(max, fm.stringWidth(header) + 20);
            for (int r = 0; r < Math.min(d.allRows.size(), 100); r++) {
                Object val = d.allRows.get(r).get(i - 1);
                if (val != null) {
                    max = Math.max(max, Math.min(fm.stringWidth(val.toString()) + 20, 400));
                }
            }
            d.table.getColumnModel().getColumn(i).setPreferredWidth(max);
        }
    }

    public void showError(String message) {
        messageArea.setText("\u9519\u8BEF:\n" + message);
        resultTabs.setSelectedIndex(0);
        messageArea.setCaretPosition(0);
    }

    private void showMessage(String message) {
        messageArea.setText(message);
        resultTabs.setSelectedIndex(0);
        messageArea.setCaretPosition(0);
    }

    public void appendMessage(String message) {
        String existing = messageArea.getText();
        messageArea.setText(existing.isEmpty() ? message : existing + "\n" + message);
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }

    public void clear() {
        for (int i = resultTabs.getTabCount() - 1; i > 0; i--) {
            int di = i - 1;
            if (di < tabDataList.size() && tabDataList.get(di).pinned) continue;
            if (di < tabDataList.size()) tabDataList.remove(di);
            resultTabs.removeTabAt(i);
        }
        resultCounter = 0;
        messageArea.setText("");
    }

    public void setBatchExecuting(boolean b) {
        batchExecuting = b;
    }

    public void setRefreshExecutor(java.util.function.BiConsumer<String, String> executor) {
        this.refreshExecutor = executor;
    }

    public TableModel getCurrentTableModel() {
        int sel = resultTabs.getSelectedIndex();
        if (sel <= 0) return null;
        int dataIdx = sel - 1;
        if (dataIdx < 0 || dataIdx >= tabDataList.size()) return null;
        return tabDataList.get(dataIdx).model;
    }
}
