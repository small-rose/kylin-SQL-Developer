package com.kylin.plsql.ui.component.bottom;

import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.SqlExecutor;
import com.kylin.plsql.core.parser.SqlTableExtractor;
import com.kylin.plsql.core.service.DataChangeService;
import com.kylin.plsql.ui.component.common.IconUtil;
import com.kylin.plsql.ui.dialog.tools.ExportDialog;
import com.kylin.plsql.ui.dialog.tools.ImportDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Paginated result set display with row numbers, column resize, cell expansion. */
public class ResultPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ResultPanel.class);
    private final ThemeManager theme = ThemeManager.getInstance();
    private Color hoverBg;

    private final JTabbedPane resultTabs;
    private final JTextArea messageArea;
    private final List<ResultTabData> tabDataList = new ArrayList<>();
    private int resultCounter = 0;
    private boolean batchExecuting;
    private com.kylin.plsql.core.db.ConnectionManager connectionManager;
    private com.kylin.plsql.core.service.ServiceFactory serviceFactory;
    private java.util.function.BiConsumer<String, String> refreshExecutor;

    private static class ResultTabData {
        String label;
        String sql;
        String connName;
        List<String> columns;
        List<SqlExecutor.ColumnMeta> columnMeta;
        List<List<Object>> allRows;
        List<List<Object>> originalRows;
        int pageSize = 100;
        int currentPage = 0;
        boolean pinned;
        JTable table;
        JTable rowHeader;
        PaginatedTableModel model;
        JLabel pageInfoLabel;
        JTextArea columnInfoLabel;
        JPanel columnInfoBar;
        CancelToken cancelToken;
        Set<Integer> newRowIndices = new HashSet<>();
        Set<Integer> deletedRowIndices = new HashSet<>();
        Set<Point> expandedCells = new HashSet<>();
        transient boolean syncingSelection;

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
        Runnable onDataChange;

        void setData(ResultTabData d, List<String> cols) {
            this.data = d;
            this.columns = cols;
            fireTableStructureChanged();
        }

        @Override public void fireTableDataChanged() {
            super.fireTableDataChanged();
            if (onDataChange != null) onDataChange.run();
        }

        @Override public void fireTableStructureChanged() {
            super.fireTableStructureChanged();
            if (onDataChange != null) onDataChange.run();
        }

        @Override public int getRowCount() { return data != null ? data.getDisplayRowCount() : 0; }
        @Override public int getColumnCount() { return (columns != null ? columns.size() : 0); }
        @Override public Object getValueAt(int row, int col) {
            if (data == null || data.allRows == null) return null;
            int absRow = data.getOffset() + row;
            if (absRow >= data.allRows.size()) return null;
            List<Object> rowData = data.allRows.get(absRow);
            return col < rowData.size() ? rowData.get(col) : null;
        }
        @Override public String getColumnName(int col) {
            if (columns == null || col >= columns.size()) return "";
            return columns.get(col);
        }
        @Override public boolean isCellEditable(int r, int c) { return true; }
        @Override public void setValueAt(Object val, int row, int col) {
            if (data == null || data.allRows == null) return;
            int absRow = data.getOffset() + row;
            if (absRow >= data.allRows.size()) return;
            List<Object> rowData = data.allRows.get(absRow);
            if (col < rowData.size()) rowData.set(col, val);
            fireTableCellUpdated(row, col);
        }
    }

    public ResultPanel() {
        setLayout(new BorderLayout());

        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setFont(FontManager.getInstance().resolve("font.bottom.message"));
        applyMessageColors();
        JScrollPane msgScroll = new JScrollPane(messageArea);
        msgScroll.setBorder(null);
        msgScroll.getViewport().setBackground(theme.resolve("bg.output"));

        resultTabs = new JTabbedPane();
        resultTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        applyTabTheme();
        resultTabs.addTab("消息", msgScroll);
        installResultTabContextMenu();
        add(resultTabs, BorderLayout.CENTER);
    }

    public void applyTheme() {
        setBackground(theme.resolve("bg.main"));
        Color fg = theme.resolve("fg.main");
        hoverBg = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60);
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
        // Rebuild result tab components to pick up new theme colors
        for (int i = 0; i < tabDataList.size(); i++) {
            updateResultTabComponent(i + 1);
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
        resultTabs.updateUI();
    }

    private void applyTableTheme(JTable table) {
        table.setFont(FontManager.getInstance().resolve("font.bottom.result"));
        table.setRowHeight(22);
        table.setBackground(theme.resolve("bg.output"));
        table.setForeground(theme.resolve("fg.main"));
        table.setGridColor(theme.resolve("border.default"));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(true);
        table.setSelectionBackground(theme.resolve("selection.bg"));
        table.setSelectionForeground(theme.resolve("selection.fg"));
        JTableHeader h = table.getTableHeader();
        h.setBackground(theme.resolve("bg.toolbar"));
        h.setForeground(theme.resolve("fg.secondary"));
        h.setFont(FontManager.getInstance().resolve("font.bottom.result.header"));
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private ResultTabData findTabData(JTable table) {
        for (ResultTabData d : tabDataList) {
            if (d.table == table) return d;
        }
        return null;
    }

    private class ExpandedCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setFont(table.getFont());
            ResultTabData d = findTabData(table);
            if (d != null && d.expandedCells.contains(new Point(row, column))) {
                String text = value != null ? escapeHtml(value.toString()) : "";
                int w = Math.max(60, table.getColumnModel().getColumn(column).getWidth() - 8);
                label.setText("<html><body style='width:" + w + "px; padding:2px 0;'>" + text + "</body></html>");
                label.setVerticalAlignment(SwingConstants.TOP);
                return label;
            }
            label.setText(value != null ? value.toString() : "");
            label.setVerticalAlignment(SwingConstants.CENTER);
            if (!isSelected) {
                boolean isDeleted = d != null && d.deletedRowIndices.contains(d.getOffset() + row);
                if (isDeleted) {
                    label.setBackground(new Color(0xE8E8E8));
                    label.setForeground(new Color(0xBBBBBB));
                    label.setOpaque(true);
                } else {
                    String kw = (String) table.getClientProperty("searchKeyword");
                    if (kw != null && !kw.isEmpty() && value != null
                            && value.toString().toLowerCase().contains(kw.toLowerCase())) {
                        label.setBackground(new Color(0xFFFF99));
                        label.setOpaque(true);
                    } else if (kw != null && !kw.isEmpty()) {
                        label.setBackground(table.getBackground());
                        label.setOpaque(table.isOpaque());
                    }
                }
            }
            return label;
        }
    }

    private static class JTextAreaCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JTextArea textArea;
        private final JScrollPane scrollPane;

        JTextAreaCellEditor() {
            textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            scrollPane = new JScrollPane(textArea);
            scrollPane.setBorder(null);
        }

        @Override public boolean isCellEditable(EventObject e) {
            if (e instanceof MouseEvent) return ((MouseEvent) e).getClickCount() >= 2;
            return true;
        }

        @Override public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            textArea.setText(value != null ? value.toString() : "");
            SwingUtilities.invokeLater(() -> {
                textArea.requestFocusInWindow();
                textArea.selectAll();
            });
            return scrollPane;
        }

        @Override public Object getCellEditorValue() { return textArea.getText(); }
    }

    public void showToast(String msg) {
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        if (ancestor == null) return;
        JWindow toast = new JWindow(ancestor);
        JLabel label = new JLabel(msg);
        label.setOpaque(true);
        Color bg = theme.resolve("bg.panel");
        boolean dark = bg.getRed() + bg.getGreen() + bg.getBlue() < 382;
        label.setBackground(dark ? new Color(0xE0E0E0) : new Color(0x444444));
        label.setForeground(dark ? new Color(0x222222) : Color.WHITE);
        label.setFont(UIManager.getFont("Label.font"));
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

        JButton prevBtn = makeTbBtn("arrow-left", "上一页", "上一页");
        prevBtn.addActionListener(e -> { if (d.currentPage > 0) { d.currentPage--; d.expandedCells.clear(); d.model.fireTableDataChanged(); updatePageInfo(d); }});

        JComboBox<String> psc = new JComboBox<>(new String[]{"10", "100", "250", "500", "1000", "全部", "自定义"});
        psc.setSelectedItem("100");
        psc.setFont(FontManager.getInstance().resolve("font.bottom"));
        psc.setPreferredSize(new Dimension(90, 22));
        psc.setMaximumSize(new Dimension(90, 22));
        psc.addActionListener(e -> {
            if (psc.getSelectedItem() == null) return;
            String v = (String) psc.getSelectedItem();
            if ("自定义".equals(v)) {
                String input = JOptionPane.showInputDialog(this, "每页条数大小为：", "自定义分页大小", JOptionPane.PLAIN_MESSAGE);
                if (input != null) {
                    try {
                        int n = Integer.parseInt(input.trim());
                        if (n <= 0) throw new NumberFormatException();
                        d.pageSize = n;
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "请输入正整数");
                    }
                }
                psc.setSelectedItem(String.valueOf(d.pageSize));
                // 不 return，继续到公共刷新代码（值不在 combo 列表中时 setSelectedItem 无效果）
            }
            if ("全部".equals(v)) {
                int totalRows = d.model.getRowCount();
                if (totalRows > 100000) {
                    int r = JOptionPane.showConfirmDialog(this,
                        "当前数据量 " + totalRows + " 行，超过 10 万行。\n加载全部数据可能导致内存溢出或界面卡顿。\n是否继续？",
                        "数据量较大", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (r != JOptionPane.YES_OPTION) {
                        psc.setSelectedItem(String.valueOf(d.pageSize));
                        return;
                    }
                }
                d.pageSize = Integer.MAX_VALUE;
            } else if (!"自定义".equals(v)) {
                d.pageSize = Integer.parseInt(v);
            }
            d.currentPage = 0;
            d.expandedCells.clear();
            d.model.fireTableDataChanged();
            updatePageInfo(d);
        });

        JButton nextBtn = makeTbBtn("arrow-right", "下一页", "下一页");
        nextBtn.addActionListener(e -> { if (d.currentPage < d.getTotalPages() - 1) { d.currentPage++; d.expandedCells.clear(); d.model.fireTableDataChanged(); updatePageInfo(d); }});

        JButton firstBtn = makeTbBtn("arrow-left-to-line", "首页", "首页");
        firstBtn.addActionListener(e -> { d.currentPage = 0; d.expandedCells.clear(); d.model.fireTableDataChanged(); updatePageInfo(d); });

        JButton lastBtn = makeTbBtn("arrow-right-to-line", "末页", "末页");
        lastBtn.addActionListener(e -> { d.currentPage = d.getTotalPages() - 1; d.expandedCells.clear(); d.model.fireTableDataChanged(); updatePageInfo(d); });

        JButton exportBtn = makeTbBtn("arrow-down-to-line", "导出", "导出当前结果集");
        exportBtn.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            new ExportDialog(owner, d.model, d.sql).setVisible(true);
        });

        JButton importBtn = makeTbBtn("arrow-up-to-line", "导入", "导入数据");
        importBtn.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            new ImportDialog(owner, d.connName, serviceFactory, connectionManager).setVisible(true);
        });

        JButton addRowBtn = makeTbBtn("plus", "\u6DFB\u52A0\u884C", "\u6DFB\u52A0\u884C");
        addRowBtn.addActionListener(e -> {
            int nCols = d.columns != null ? d.columns.size() : 0;
            if (nCols == 0) return;
            java.util.List<Object> newRow = new java.util.ArrayList<>(java.util.Collections.nCopies(nCols, null));
            d.allRows.add(newRow);
            d.newRowIndices.add(d.allRows.size() - 1);
            d.model.fireTableDataChanged();
        });

        JButton deleteRowBtn = makeTbBtn("minus", "\u5220\u9664\u884C", "\u5220\u9664\u884C");
        deleteRowBtn.addActionListener(e -> {
            int[] rows = d.table.getSelectedRows();
            if (rows.length == 0) return;
            for (int vRow : rows) {
                int absRow = d.getOffset() + vRow;
                if (absRow >= 0 && absRow < d.allRows.size()) {
                    d.deletedRowIndices.add(absRow);
                }
            }
            d.model.fireTableDataChanged();
        });

        JButton refreshBtn = makeTbBtn("refresh", "刷新", "刷新结果集");
        refreshBtn.addActionListener(e -> { d.currentPage = 0; d.expandedCells.clear(); d.model.fireTableDataChanged(); updatePageInfo(d); });

        JButton stopBtn = makeTbBtn("stop", "\u505C\u6B62", "\u505C\u6B62\u67E5\u8BE2");
        stopBtn.setEnabled(d.cancelToken != null);
        stopBtn.addActionListener(e -> {
            if (d.cancelToken != null) d.cancelToken.cancel();
        });

        JTextField searchField = new JTextField(10);
        searchField.putClientProperty("JTextField.placeholderText", "\u641C\u7D22");
        searchField.setFont(FontManager.getInstance().resolve("font.bottom"));
        searchField.setMaximumSize(new Dimension(120, 22));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { doSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { doSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { doSearch(); }
            private void doSearch() {
                String kw = searchField.getText().trim();
                d.table.putClientProperty("searchKeyword", kw.isEmpty() ? null : kw);
                d.table.repaint();
            }
        });

        JButton submitBtn = makeTbBtn("arrow-big-up", "\u63D0\u4EA4\u4FEE\u6539", "\u63D0\u4EA4\u4FEE\u6539");
        submitBtn.addActionListener(e -> {
            if (d.connName == null || d.originalRows == null) return;
            String rawTableName = SqlTableExtractor.guessTableName(d.sql);
            if (rawTableName == null) {
                if (d.columnInfoLabel != null) d.columnInfoLabel.setText("\u26A0 \u65E0\u6CD5\u8BC6\u522B\u8868\u540D");
                return;
            }

            // 如果表名包含点号，从中提取 schema + table
            final String schema;
            final String tableName;
            int dotIdx = rawTableName.indexOf('.');
            if (dotIdx > 0) {
                schema = rawTableName.substring(0, dotIdx);
                tableName = rawTableName.substring(dotIdx + 1);
            } else {
                schema = getSchemaForConn(d.connName);
                tableName = rawTableName;
            }
            String dbType = getDbTypeForConn(d.connName);
            DataChangeService svc = serviceFactory.getDataChangeService(dbType);
            DataChangeService.DiffResult diff = svc.diff(d.columns, d.originalRows,
                    d.allRows, d.newRowIndices, d.deletedRowIndices);
            if (diff.isEmpty()) { showToast("\u65E0\u53D8\u66F4"); return; }

            int result = JOptionPane.showConfirmDialog(this,
                    "\u5C06\u63D0\u4EA4 " + diff.totalOps() + " \u6761\u53D8\u66F4\u5230\u6570\u636E\u5E93\uFF0C\u786E\u8BA4\uFF1F",
                    "\u63D0\u4EA4\u4FEE\u6539", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) return;

            javax.swing.SwingWorker<Integer, Void> worker = new javax.swing.SwingWorker<Integer, Void>() {
                @Override protected Integer doInBackground() throws Exception {
                    try (java.sql.Connection conn = connectionManager.getConnection(d.connName)) {
                        return svc.execute(conn, schema, tableName, d.columns, diff);
                    }
                }
                @Override protected void done() {
                    try {
                        int n = get();
                        showToast("\u63D0\u4EA4\u5B8C\u6210: " + n + " \u884C");
                        if (refreshExecutor != null) refreshExecutor.accept(d.connName, d.sql);
                    } catch (Exception ex) {
                        if (d.columnInfoLabel != null) {
                            d.columnInfoLabel.setText("\u2716 \u63D0\u4EA4\u5931\u8D25: " + ex.getMessage());
                            d.columnInfoLabel.setForeground(new Color(0xC62828));
                        }
                    }
                }
            };
            worker.execute();
        });

        JButton commitBtn = makeTbBtn("commit", "\u4E8B\u52A1\u63D0\u4EA4", "\u4E8B\u52A1\u63D0\u4EA4");
        commitBtn.setEnabled(false);
        commitBtn.addActionListener(e -> {
            if (d.connName != null) {
                try {
                    connectionManager.commit(d.connName);
                    if (d.columnInfoLabel != null) {
                        d.columnInfoLabel.setText("\u2714 \u4E8B\u52A1\u5DF2\u63D0\u4EA4");
                        d.columnInfoLabel.setForeground(theme.resolve("fg.muted"));
                    }
                } catch (Exception ex) {
                    if (d.columnInfoLabel != null) {
                        d.columnInfoLabel.setText("\u2716 \u4E8B\u52A1\u63D0\u4EA4\u5931\u8D25: " + ex.getMessage());
                        d.columnInfoLabel.setForeground(new Color(0xC62828));
                    }
                }
            }
        });

        JButton rollbackBtn = makeTbBtn("rollback", "\u4E8B\u52A1\u56DE\u9000", "\u4E8B\u52A1\u56DE\u9000");
        rollbackBtn.setEnabled(false);
        rollbackBtn.addActionListener(e -> {
            if (d.connName != null) {
                try {
                    connectionManager.rollback(d.connName);
                    if (d.columnInfoLabel != null) {
                        d.columnInfoLabel.setText("\u2714 \u4E8B\u52A1\u5DF2\u56DE\u9000");
                        d.columnInfoLabel.setForeground(theme.resolve("fg.muted"));
                    }
                } catch (Exception ex) {
                    if (d.columnInfoLabel != null) {
                        d.columnInfoLabel.setText("\u2716 \u4E8B\u52A1\u56DE\u9000\u5931\u8D25: " + ex.getMessage());
                        d.columnInfoLabel.setForeground(new Color(0xC62828));
                    }
                }
            }
        });

        JButton txBtn = new JButton("\u81EA\u52A8\u4E8B\u52A1");
        ImageIcon chevronIcon = IconUtil.loadButtonIcon("chevron-down", null);
        if (chevronIcon != null) txBtn.setIcon(chevronIcon);
        txBtn.setFont(FontManager.getInstance().resolve("font.bottom"));
        txBtn.setFocusable(false);
        txBtn.setContentAreaFilled(false);
        txBtn.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        txBtn.setForeground(theme.resolve("fg.main"));
        txBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        txBtn.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            boolean isManual = !connectionManager.isAutoCommit(d.connName);
            JRadioButtonMenuItem autoItem = new JRadioButtonMenuItem("\u81EA\u52A8\u4E8B\u52A1", !isManual);
            JRadioButtonMenuItem manualItem = new JRadioButtonMenuItem("\u4EBA\u5DE5\u4E8B\u52A1", isManual);
            ButtonGroup group = new ButtonGroup();
            group.add(autoItem); group.add(manualItem);
            autoItem.addActionListener(ev -> {
                connectionManager.setAutoCommit(d.connName, true);
                txBtn.setText("\u81EA\u52A8\u4E8B\u52A1 \u25BE");
                commitBtn.setEnabled(false);
                rollbackBtn.setEnabled(false);
            });
            manualItem.addActionListener(ev -> {
                connectionManager.setAutoCommit(d.connName, false);
                txBtn.setText("\u4EBA\u5DE5\u4E8B\u52A1 \u25BE");
                commitBtn.setEnabled(true);
                rollbackBtn.setEnabled(true);
            });
            menu.add(autoItem);
            menu.add(manualItem);
            menu.addSeparator();
            JMenu isolation = new JMenu("\u4E8B\u52A1\u4F20\u64AD\u7EA7\u522B");
            JCheckBoxMenuItem rcItem = new JCheckBoxMenuItem("\u8BFB\u5DF2\u63D0\u4EA4", true);
            JCheckBoxMenuItem rrItem = new JCheckBoxMenuItem("\u53EF\u91CD\u590D\u8BFB");
            JCheckBoxMenuItem serItem = new JCheckBoxMenuItem("\u53EF\u4E32\u884C\u5316");
            rcItem.addActionListener(ev -> connectionManager.setTransactionIsolation(d.connName, java.sql.Connection.TRANSACTION_READ_COMMITTED));
            rrItem.addActionListener(ev -> connectionManager.setTransactionIsolation(d.connName, java.sql.Connection.TRANSACTION_REPEATABLE_READ));
            serItem.addActionListener(ev -> connectionManager.setTransactionIsolation(d.connName, java.sql.Connection.TRANSACTION_SERIALIZABLE));
            isolation.add(rcItem);
            isolation.add(rrItem);
            isolation.add(serItem);
            menu.add(isolation);
            menu.show(txBtn, 0, txBtn.getHeight());
        });

        JButton pinBtn = makeTbBtn("pin", "固定", "固定标签不被替换");
        pinBtn.addActionListener(e -> {
            d.pinned = !d.pinned;
            pinBtn.setForeground(d.pinned ? theme.resolve("accent.green") : theme.resolve("fg.main"));
            int idx = resultTabs.indexOfComponent(panel);
            if (idx > 0) updateResultTabComponent(idx);
        });

        JLabel infoLbl = new JLabel();
        infoLbl.setFont(FontManager.getInstance().resolve("font.bottom"));
        infoLbl.setForeground(theme.resolve("fg.muted"));
        infoLbl.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        d.pageInfoLabel = infoLbl;

        tb.add(firstBtn);
        tb.add(prevBtn);
        tb.add(psc);
        tb.add(nextBtn);
        tb.add(lastBtn);
        tb.add(makeSeparator());
        tb.add(addRowBtn);
        tb.add(deleteRowBtn);
        tb.add(refreshBtn);
        tb.add(searchField);
        tb.add(stopBtn);
        tb.add(makeSeparator());
        tb.add(txBtn);
        tb.add(submitBtn);
        tb.add(commitBtn);
        tb.add(rollbackBtn);
        tb.add(makeSeparator());
        tb.add(pinBtn);
        tb.add(exportBtn);
        tb.add(importBtn);
        tb.add(Box.createHorizontalGlue());
        tb.add(infoLbl);

        JScrollPane scroll = new JScrollPane(d.table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(theme.resolve("bg.output"));

        d.rowHeader = new JTable(new AbstractTableModel() {
            @Override public int getRowCount() { return d.getDisplayRowCount(); }
            @Override public int getColumnCount() { return 1; }
            @Override public Object getValueAt(int r, int c) {
                int absRow = d.getOffset() + r;
                String prefix = d.newRowIndices.contains(absRow) ? "+" : d.deletedRowIndices.contains(absRow) ? "-" : "";
                return prefix + (absRow + 1);
            }
            @Override public String getColumnName(int c) { return "#"; }
        });
        d.rowHeader.setFont(d.table.getFont());
        d.rowHeader.setRowHeight(d.table.getRowHeight());
        d.rowHeader.setBackground(theme.resolve("bg.output"));
        d.rowHeader.setForeground(theme.resolve("fg.muted"));
        d.rowHeader.setGridColor(theme.resolve("border.default"));
        d.rowHeader.setShowVerticalLines(false);
        d.rowHeader.setShowHorizontalLines(true);
        d.rowHeader.setPreferredScrollableViewportSize(new Dimension(48, 0));
        d.rowHeader.setFocusable(false);
        d.rowHeader.setRowSelectionAllowed(true);
        d.rowHeader.setCellSelectionEnabled(false);
        d.rowHeader.setColumnSelectionAllowed(false);
        d.rowHeader.getTableHeader().setReorderingAllowed(false);
        d.rowHeader.getTableHeader().setResizingAllowed(false);
        d.rowHeader.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        d.rowHeader.setSelectionBackground(theme.resolve("selection.bg"));
        d.rowHeader.setSelectionForeground(theme.resolve("selection.fg"));
        d.rowHeader.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1,theme.resolve("border.default")));
        TableCellRenderer rhRenderer = new TableCellRenderer() {
            private final JLabel label = new JLabel();
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                label.setFont(FontManager.getInstance().resolve("font.bottom.result"));
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setText(value != null ? value.toString() : "");
                boolean rowSel = table.isRowSelected(row);
                if (rowSel) {
                    label.setOpaque(true);
                    label.setBackground(table.getSelectionBackground());
                    label.setForeground(table.getSelectionForeground());
                } else {
                    label.setOpaque(false);
                    label.setForeground(theme.resolve("fg.muted"));
                }
                return label;
            }
        };
        d.rowHeader.setDefaultRenderer(Object.class, rhRenderer);
        d.rowHeader.getSelectionModel().addListSelectionListener((ListSelectionListener) e -> {
            if (d.syncingSelection) return;
            d.syncingSelection = true;
            try {
                int minM = d.rowHeader.getSelectionModel().getMinSelectionIndex();
                int maxM = d.rowHeader.getSelectionModel().getMaxSelectionIndex();
                log.info("行头选中事件: min={}, max={}", minM, maxM);
                if (minM >= 0) {
                    int vMin = d.table.convertRowIndexToView(minM);
                    int vMax = d.table.convertRowIndexToView(maxM);
                    if (vMin >= 0) {
                        int rMin = Math.min(vMin, vMax);
                        int rMax = Math.max(vMin, vMax);
                        d.table.getSelectionModel().setSelectionInterval(rMin, rMax);
                        d.table.getColumnModel().getSelectionModel()
                            .setSelectionInterval(0, d.table.getColumnCount() - 1);
                    }
                }
            } finally { d.syncingSelection = false; }
        });
        d.rowHeader.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int row = d.rowHeader.rowAtPoint(e.getPoint());
                if (row < 0) return;
                int anchor = d.rowHeader.getSelectionModel().getAnchorSelectionIndex();
                if (anchor < 0) anchor = row;
                d.rowHeader.getSelectionModel().setSelectionInterval(Math.min(anchor, row), Math.max(anchor, row));
            }
        });
        d.rowHeader.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (d.syncingSelection) return;
                int row = d.rowHeader.rowAtPoint(e.getPoint());
                if (row < 0) return;
                d.syncingSelection = true;
                try {
                    int vMin = d.table.convertRowIndexToView(row);
                    if (vMin >= 0) {
                        d.table.getSelectionModel().setSelectionInterval(vMin, vMin);
                        d.table.getColumnModel().getSelectionModel()
                            .setSelectionInterval(0, d.table.getColumnCount() - 1);
                    }
                } finally { d.syncingSelection = false; }
            }
        });
        d.table.getSelectionModel().addListSelectionListener((ListSelectionListener) e -> {
            if (d.syncingSelection) return;
            d.syncingSelection = true;
            try {
                int vMin = d.table.getSelectionModel().getMinSelectionIndex();
                int vMax = d.table.getSelectionModel().getMaxSelectionIndex();
                if (vMin >= 0) {
                    int mMin = d.table.convertRowIndexToModel(vMin);
                    int mMax = d.table.convertRowIndexToModel(vMax);
                    d.rowHeader.getSelectionModel().setSelectionInterval(Math.min(mMin, mMax), Math.max(mMin, mMax));
                }
            } finally { d.syncingSelection = false; }
        });
        scroll.setRowHeaderView(d.rowHeader);
        scroll.setViewportBorder(BorderFactory.createMatteBorder(1, 1, 0, 0, theme.resolve("border.default")));
        JPanel cornerPanel = new JPanel(new BorderLayout());
        cornerPanel.setBackground(d.table.getTableHeader().getBackground());
        cornerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, theme.resolve("border.default")));
        cornerPanel.setPreferredSize(new Dimension(48, d.table.getTableHeader().getPreferredSize().height > 0
                ? d.table.getTableHeader().getPreferredSize().height : 24));
        cornerPanel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { d.table.selectAll(); }
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showCornerMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showCornerMenu(e); }
            private void showCornerMenu(MouseEvent e) {
                JPopupMenu menu = new JPopupMenu();
                menu.add(new JMenuItem("\u590D\u5236")).addActionListener(ev -> copyAllRows(d));
                JMenu copyAs = new JMenu("\u9AD8\u7EA7\u590D\u5236");
                for (String fmt : new String[]{"INSERT", "CSV", "JSON", "Markdown"}) {
                    JMenuItem item = new JMenuItem(fmt);
                    item.addActionListener(ev -> copyAllAs(d, fmt));
                    copyAs.add(item);
                }
                menu.add(copyAs);
                menu.addSeparator();
                menu.add(new JMenuItem("\u5168\u9009")).addActionListener(ev -> selectAll(d));
                menu.add(new JMenuItem("\u53D6\u6D88\u5168\u9009")).addActionListener(ev -> d.table.clearSelection());
                menu.show(cornerPanel, e.getX(), e.getY());
            }
        });
        scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, cornerPanel);
        d.model.onDataChange = () -> {
            if (d.rowHeader != null) {
                AbstractTableModel rhm = (AbstractTableModel) d.rowHeader.getModel();
                rhm.fireTableDataChanged();
            }
        };

        JPanel infoBar = buildColumnInfoBar(d);
        installRowHeaderMenu(d);
        installTableHeaderMenu(d);
        panel.add(tb, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(infoBar, BorderLayout.SOUTH);
        return panel;
    }

    private Component makeSeparator() {
        JPanel sep = new JPanel();
        sep.setPreferredSize(new Dimension(1, 16));
        sep.setMaximumSize(new Dimension(1, 16));
        sep.setBackground(new Color(0xBBBBBB));
        sep.setOpaque(true);
        return sep;
    }

    private JPanel buildColumnInfoBar(ResultTabData d) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setBackground(theme.resolve("bg.output"));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.resolve("border.light")));
        JTextArea label = new JTextArea("\u70B9\u51FB\u5217\u5934\u67E5\u770B\u5217\u4FE1\u606F");
        label.setFont(FontManager.getInstance().resolve("font.bottom"));
        label.setForeground(theme.resolve("fg.muted"));
        label.setEditable(false);
        label.setBorder(null);
        label.setBackground(theme.resolve("bg.output"));
        label.setRows(1);
        bar.add(label, BorderLayout.CENTER);
        d.columnInfoLabel = label;
        d.columnInfoBar = bar;
        return bar;
    }

    private void updateColumnInfo(ResultTabData d, int colIndex) {
        if (d.columnInfoLabel == null) return;
        if (colIndex < 0) {
            d.columnInfoLabel.setText("\u70B9\u51FB\u5217\u5934\u67E5\u770B\u5217\u4FE1\u606F");
            return;
        }
        StringBuilder sb = new StringBuilder();
        String colName = d.columns != null && colIndex < d.columns.size() ? d.columns.get(colIndex) : "#" + colIndex;
        sb.append("\u5217\u540D: ").append(colName);
        if (d.columnMeta != null && colIndex < d.columnMeta.size()) {
            SqlExecutor.ColumnMeta cm = d.columnMeta.get(colIndex);
            sb.append("  |  \u7C7B\u578B: ").append(cm.type);
            if (cm.size > 0) sb.append("(").append(cm.size).append(")");
            sb.append("  |  \u975E\u7A7A: ").append(cm.nullable ? "\u5426" : "\u662F");
        } else {
            Class<?> clazz = d.table.getColumnClass(colIndex);
            sb.append("  |  Java\u7C7B\u578B: ").append(clazz != null ? clazz.getSimpleName() : "?");
        }
        d.columnInfoLabel.setText(sb.toString());
    }

    private void copySelectedRows(ResultTabData d) {
        int[] rows = d.table.getSelectedRows();
        if (rows.length == 0) return;
        int[] cols = d.table.getSelectedColumns();
        if (cols.length == 0) {
            cols = new int[d.table.getColumnCount()];
            for (int i = 0; i < cols.length; i++) cols[i] = i;
        }
        StringBuilder sb = new StringBuilder();
        for (int r : rows) {
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) sb.append("\t");
                Object v = d.table.getValueAt(r, cols[i]);
                sb.append(v != null ? v.toString() : "");
            }
            sb.append("\n");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(sb.toString().stripTrailing()), null);
    }

    private void copySelectedAs(ResultTabData d, String format) {
        List<Integer> colList = new java.util.ArrayList<>();
        for (int i = 0; i < d.table.getColumnCount(); i++) colList.add(i);
        javax.swing.table.TableModel model = d.table.getModel();
        String result = com.kylin.plsql.core.export.ExportEngine.export(model, colList, format, new com.kylin.plsql.core.export.ExportOptions());
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(result), null);
    }

    private void copyAllRows(ResultTabData d) {
        int nCols = d.table.getColumnCount();
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < d.getDisplayRowCount(); r++) {
            for (int c = 0; c < nCols; c++) {
                if (c > 0) sb.append("\t");
                Object v = d.table.getValueAt(r, c);
                sb.append(v != null ? v.toString() : "");
            }
            sb.append("\n");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(sb.toString().stripTrailing()), null);
    }

    private void copyAllAs(ResultTabData d, String format) {
        copySelectedAs(d, format);
    }

    private void selectAll(ResultTabData d) {
        d.table.selectAll();
    }

    private void goToRow(ResultTabData d) {
        String input = JOptionPane.showInputDialog(this, "输入行号（1-" + d.allRows.size() + "）");
        if (input == null) return;
        try {
            int row = Integer.parseInt(input.trim()) - 1 + d.getOffset();
            if (row >= 0 && row < d.allRows.size()) {
                d.table.scrollRectToVisible(d.table.getCellRect(row, 0, true));
                d.table.setRowSelectionInterval(row, row);
            }
        } catch (NumberFormatException ignored) {}
    }

    private void goToFirstRow(ResultTabData d) {
        if (d.allRows == null || d.allRows.isEmpty()) return;
        d.table.scrollRectToVisible(d.table.getCellRect(0, 0, true));
        d.table.setRowSelectionInterval(0, 0);
    }

    private void goToLastRow(ResultTabData d) {
        if (d.allRows == null || d.allRows.isEmpty()) return;
        int last = d.getDisplayRowCount() - 1;
        d.table.scrollRectToVisible(d.table.getCellRect(last, 0, true));
        d.table.setRowSelectionInterval(last, last);
    }

    private void exportToFile(ResultTabData d) {
        if (d.model == null) return;
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new com.kylin.plsql.ui.dialog.tools.ExportDialog(owner, d.model, d.sql).setVisible(true);
    }

    private void refreshResult(ResultTabData d) {
        d.currentPage = 0;
        d.expandedCells.clear();
        d.model.fireTableDataChanged();
        updatePageInfo(d);
    }

    private void installRowHeaderMenu(ResultTabData d) {
        d.rowHeader.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }
            private void showMenu(MouseEvent e) {
                JPopupMenu menu = new JPopupMenu();
                menu.add(new JMenuItem("\u590D\u5236\u884C")).addActionListener(ev -> copySelectedRows(d));
                JMenu copyAs = new JMenu("\u9AD8\u7EA7\u590D\u5236");
                for (String fmt : new String[]{"INSERT", "CSV", "JSON", "Markdown"}) {
                    JMenuItem item = new JMenuItem(fmt);
                    item.addActionListener(ev -> copySelectedAs(d, fmt));
                    copyAs.add(item);
                }
                menu.add(copyAs);
                menu.addSeparator();
                menu.add(new JMenuItem("\u5168\u9009")).addActionListener(ev -> selectAll(d));
                menu.add(new JMenuItem("\u5168\u9009\u5E76\u590D\u5236")).addActionListener(ev -> { selectAll(d); copySelectedRows(d); });
                menu.addSeparator();
                menu.add(new JMenuItem("\u8DF3\u8F6C\u5230\u884C...")).addActionListener(ev -> goToRow(d));
                menu.add(new JMenuItem("\u9996\u884C")).addActionListener(ev -> goToFirstRow(d));
                menu.add(new JMenuItem("\u672B\u884C")).addActionListener(ev -> goToLastRow(d));
                menu.addSeparator();
                menu.add(new JMenuItem("\u5BFC\u51FA\u5230\u6587\u4EF6...")).addActionListener(ev -> exportToFile(d));
                menu.add(new JMenuItem("\u5237\u65B0")).addActionListener(ev -> refreshResult(d));
                menu.show(d.rowHeader, e.getX(), e.getY());
            }
        });
    }

    private void sortColumn(ResultTabData d, int colIndex, javax.swing.SortOrder order) {
        if (d.table.getRowSorter() == null) return;
        int modelCol = d.table.convertColumnIndexToModel(colIndex);
        d.table.getRowSorter().setSortKeys(java.util.List.of(
            new javax.swing.RowSorter.SortKey(modelCol, order)));
    }

    private void clearSort(ResultTabData d) {
        if (d.table.getRowSorter() != null) d.table.getRowSorter().setSortKeys(null);
    }

    private void hideColumn(ResultTabData d, int colIndex) {
        if (colIndex < 0 || colIndex >= d.table.getColumnCount()) return;
        TableColumn col = d.table.getColumnModel().getColumn(colIndex);
        col.setMinWidth(0);
        col.setMaxWidth(0);
        col.setPreferredWidth(0);
    }

    private void showAllColumns(ResultTabData d) {
        for (int i = 0; i < d.table.getColumnCount(); i++) {
            TableColumn col = d.table.getColumnModel().getColumn(i);
            col.setMinWidth(15);
            col.setMaxWidth(Integer.MAX_VALUE);
            col.setPreferredWidth(100);
        }
        autoResizeColWidths(d);
    }

    private void freezeColumn(ResultTabData d, int colIndex) {
        d.table.getColumnModel().moveColumn(colIndex, 0);
    }

    private void copyColumnName(ResultTabData d, int colIndex) {
        if (colIndex < 0 || colIndex >= d.columns.size()) return;
        String name = d.columns.get(colIndex);
        int dot = name.lastIndexOf('.');
        if (dot >= 0) name = name.substring(dot + 1);
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(name), null);
        showToast("\u5DF2\u590D\u5236: " + name);
    }

    private void installTableHeaderMenu(ResultTabData d) {
        d.table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }
            @Override public void mouseClicked(MouseEvent e) {
                int col = d.table.columnAtPoint(e.getPoint());
                if (col >= 0) {
                    if (e.getClickCount() == 2) {
                        copyColumnName(d, col);
                    } else {
                        updateColumnInfo(d, col);
                    }
                } else {
                    updateColumnInfo(d, -1);
                }
            }
            private void showMenu(MouseEvent e) {
                int col = d.table.columnAtPoint(e.getPoint());
                if (col < 0) return;
                JPopupMenu menu = new JPopupMenu();
                menu.add(new JMenuItem("\u590D\u5236\u5217\u540D")).addActionListener(ev -> copyColumnName(d, col));
                menu.addSeparator();
                JMenu sortMenu = new JMenu("\u6392\u5E8F");
                sortMenu.add(new JMenuItem("\u5347\u5E8F")).addActionListener(ev -> sortColumn(d, col, javax.swing.SortOrder.ASCENDING));
                sortMenu.add(new JMenuItem("\u964D\u5E8F")).addActionListener(ev -> sortColumn(d, col, javax.swing.SortOrder.DESCENDING));
                sortMenu.add(new JMenuItem("\u6E05\u9664\u6392\u5E8F")).addActionListener(ev -> clearSort(d));
                menu.add(sortMenu);
                menu.addSeparator();
                menu.add(new JMenuItem("\u9690\u85CF\u8BE5\u5217")).addActionListener(ev -> hideColumn(d, col));
                menu.add(new JMenuItem("\u663E\u793A\u6240\u6709\u5217")).addActionListener(ev -> showAllColumns(d));
                menu.add(new JMenuItem("\u5DE6\u51BB\u7ED3\u8BE5\u5217")).addActionListener(ev -> freezeColumn(d, col));
                menu.add(new JMenuItem("\u53D6\u6D88\u51BB\u7ED3")).addActionListener(ev -> d.table.getColumnModel().moveColumn(col, col));
                menu.add(new JMenuItem("\u8C03\u6574\u5217\u5BBD")).addActionListener(ev -> autoResizeColWidths(d));
                menu.show(d.table.getTableHeader(), e.getX(), e.getY());
            }
        });
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
            d.pageInfoLabel.setText(from + " - " + to + " / 共 " + total + " 行");
        }
        if (d.table != null) d.table.repaint();
    }

    private JButton makeTbBtn(String iconName, String fallback, String tip) {
        JButton btn = new JButton();
        btn.setToolTipText(tip);
        ImageIcon svgIcon = IconUtil.loadButtonIcon(iconName, null);
        if (svgIcon != null) {
            btn.setIcon(svgIcon);
        } else {
            java.net.URL url = getClass().getResource("/icons/" + iconName + ".png");
            if (url != null) {
                btn.setIcon(new ImageIcon(url));
            } else {
                btn.setText(fallback);
                btn.setFont(FontManager.getInstance().resolve("font.bottom"));
            }
        }
        btn.setFocusable(false);
        btn.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        btn.setContentAreaFilled(false);
        btn.setForeground(theme.resolve("fg.main"));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setContentAreaFilled(true);
                btn.setOpaque(true);
                btn.setBackground(hoverBg);
                btn.repaint();
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setContentAreaFilled(false);
                btn.setOpaque(false);
                btn.repaint();
            }
        });
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

        JMenuItem closeItem = new JMenuItem("关闭");
        closeItem.setIcon(IconUtil.menuIcon("x"));
        closeItem.setEnabled(!pinned);
        closeItem.addActionListener(e -> closeResultTab(tabIndex));
        menu.add(closeItem);

        JMenuItem closeOthersItem = new JMenuItem("关闭其他");
        closeOthersItem.setIcon(IconUtil.menuIcon("x"));
        closeOthersItem.addActionListener(e -> closeOtherResultTabs(tabIndex));
        menu.add(closeOthersItem);

        JMenuItem closeAllItem = new JMenuItem("关闭全部");
        closeAllItem.setIcon(IconUtil.menuIcon("x"));
        closeAllItem.addActionListener(e -> closeAllResultTabs());
        menu.add(closeAllItem);

        JMenuItem closeLeftItem = new JMenuItem("关闭左侧标签");
        closeLeftItem.setIcon(IconUtil.menuIcon("x"));
        closeLeftItem.addActionListener(e -> closeLeftResultTabs(tabIndex));
        menu.add(closeLeftItem);

        JMenuItem closeRightItem = new JMenuItem("关闭右侧标签");
        closeRightItem.setIcon(IconUtil.menuIcon("x"));
        closeRightItem.addActionListener(e -> closeRightResultTabs(tabIndex));
        menu.add(closeRightItem);

        menu.addSeparator();

        JMenuItem pinItem = new JMenuItem(pinned ? "取消固定" : "固定标签");
        pinItem.setIcon(IconUtil.menuIcon(pinned ? "pin-off" : "pin"));
        pinItem.addActionListener(e -> toggleResultPin(tabIndex));
        menu.add(pinItem);

        menu.addSeparator();

        JMenuItem refreshItem = new JMenuItem("刷新结果");
        refreshItem.setIcon(IconUtil.menuIcon("refresh"));
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
        JButton btn = new JButton("×") {
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
        btn.setFont(FontManager.getInstance().resolve("font.bottom"));
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

    // === CancelToken ===

    public static class CancelToken {
        private final AtomicReference<Statement> ref = new AtomicReference<>();
        private volatile Runnable onCancel;
        public void register(Statement stmt) { ref.set(stmt); }
        public void setOnCancel(Runnable r) { this.onCancel = r; }
        public void cancel() {
            Statement s = ref.getAndSet(null);
            if (s != null) try { s.cancel(); } catch (Exception ignored) {}
            Runnable r = onCancel;
            if (r != null) r.run();
        }
        public boolean isActive() { return ref.get() != null; }
        public void clear() { ref.set(null); }
    }

    public synchronized void showResultLoading(String sql, String connName, CancelToken cancelToken) {
        ResultTabData d = new ResultTabData();
        d.label = "\u67E5\u8BE2\u4E2D";
        d.sql = sql;
        d.connName = connName;
        d.columns = new ArrayList<>();
        d.allRows = new ArrayList<>();
        d.cancelToken = cancelToken;
        JPanel loadingPanel = new JPanel(new BorderLayout());
        loadingPanel.setBackground(theme.resolve("bg.output"));
        JLabel loadingLabel = new JLabel("\u67E5\u8BE2\u6267\u884C\u4E2D...", SwingConstants.CENTER);
        loadingLabel.setFont(FontManager.getInstance().resolve("font.bottom.result"));
        loadingLabel.setForeground(theme.resolve("fg.muted"));
        loadingPanel.add(loadingLabel, BorderLayout.CENTER);
        resultTabs.addTab(d.label, loadingPanel);
        resultTabs.setSelectedIndex(resultTabs.getTabCount() - 1);
        tabDataList.add(d);
    }

    // === Public API ===

    public synchronized void showResult(String sql, SqlExecutor.SqlResult result) {
        showResult(sql, result, "");
    }

    public synchronized void showResult(String sql, SqlExecutor.SqlResult result, String connName) {
        // remove loading tab for the same SQL
        for (int i = tabDataList.size() - 1; i >= 0; i--) {
            ResultTabData ld = tabDataList.get(i);
            if ((ld.columns == null || ld.columns.isEmpty()) && sql != null && sql.equals(ld.sql)) {
                tabDataList.remove(i);
                resultTabs.removeTabAt(i + 1);
            }
        }
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
                    reuse.originalRows = new ArrayList<>();
                    for (List<Object> row : result.rows) reuse.originalRows.add(new ArrayList<>(row));
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
            d.columns = result.columns;
            d.columnMeta = result.columnMeta;
            d.allRows = result.rows;
            d.originalRows = new ArrayList<>();
            for (List<Object> row : result.rows) d.originalRows.add(new ArrayList<>(row));
            d.pageSize = 100;
            d.currentPage = 0;
            d.pinned = false;
            d.table = new JTable();
            d.table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            d.table.setFillsViewportHeight(true);
            d.table.getTableHeader().setReorderingAllowed(false);
            d.table.setCellSelectionEnabled(true);
            d.table.setRowSelectionAllowed(true);
            d.table.setAutoCreateRowSorter(false);
            d.table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            d.table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = d.table.rowAtPoint(e.getPoint());
                    int col = d.table.columnAtPoint(e.getPoint());
                    if (row < 0 || col < 0) return;
                    if (e.getClickCount() == 2 && d.table.isCellEditable(row, col)) {
                        if (d.table.editCellAt(row, col, e)) {
                            Component editor = d.table.getEditorComponent();
                            if (editor != null) editor.requestFocusInWindow();
                        }
                    }
                }
                @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showCellMenu(e); }
                @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showCellMenu(e); }
                private void showCellMenu(MouseEvent e) {
                    int row = d.table.rowAtPoint(e.getPoint());
                    int col = d.table.columnAtPoint(e.getPoint());
                    if (row < 0 || col < 0) return;
                    Object val = d.table.getValueAt(row, col);
                    JPopupMenu menu = new JPopupMenu();
                    menu.add(new JMenuItem("\u590D\u5236")).addActionListener(ev -> {
                        if (val != null) {
                            Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(val.toString()), null);
                        }
                    });
                    menu.add(new JMenuItem("\u5C55\u5F00\u5355\u5143\u683C")).addActionListener(ev -> {
                        Point cell = new Point(row, col);
                        if (val != null) {
                            d.expandedCells.clear();
                            d.expandedCells.add(cell);
                            String text = val.toString();
                            int lines = 1 + (int) text.chars().filter(c -> c == '\n').count();
                            int estH = Math.max(60, Math.min(lines * 18 + 8, 300));
                            int h = Math.max(44, estH);
                            d.table.setRowHeight(row, h);
                            if (d.rowHeader != null) d.rowHeader.setRowHeight(row, h);
                            d.table.repaint();
                        }
                    });
                    menu.show(d.table, e.getX(), e.getY());
                }
            });
            d.model = new PaginatedTableModel();
            d.model.setData(d, result.columns);
            d.table.setModel(d.model);
            applyTableTheme(d.table);
            d.table.setDefaultRenderer(Object.class, new ExpandedCellRenderer());
            d.table.setDefaultEditor(Object.class, new JTextAreaCellEditor());

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
        if (name.length() > 20) name = name.substring(0, 20) + "…";
        return name;
    }

    private static double textWidth(Font font, String text) {
        BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        double w = font.getStringBounds(text, g.getFontRenderContext()).getWidth();
        g.dispose();
        return w;
    }

    private void autoResizeColWidths(ResultTabData d) {
        if (d.table == null || d.columns == null) return;
        Font cellFont = d.table.getFont();
        Font hdrFont = d.table.getTableHeader().getFont();
        for (int i = 0; i < d.model.getColumnCount(); i++) {
            int max = 80;
            String header = d.model.getColumnName(i);
            max = Math.max(max, (int) textWidth(hdrFont, header) + 30);
            for (int r = 0; r < Math.min(d.allRows.size(), 100); r++) {
                Object val = d.allRows.get(r).get(i);
                if (val != null) {
                    max = Math.max(max, Math.min((int) textWidth(cellFont, val.toString()) + 24, 800));
                }
            }
            TableColumn col = d.table.getColumnModel().getColumn(i);
            col.setPreferredWidth(max);
            col.setWidth(max);
        }
        d.table.doLayout();
        d.table.getTableHeader().resizeAndRepaint();
    }

    public void showError(String message) {
        messageArea.setText("错误:\n" + message);
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

    /** 切换到消息标签页。 */
    public void selectMessageTab() {
        resultTabs.setSelectedIndex(0);
    }

    public void clearAll() {
        for (int i = resultTabs.getTabCount() - 1; i > 0; i--) {
            int di = i - 1;
            if (di < tabDataList.size() && tabDataList.get(di).pinned) continue;
            if (di < tabDataList.size()) tabDataList.remove(di);
            resultTabs.removeTabAt(i);
        }
        resultCounter = 0;
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

    public void setConnectionManager(com.kylin.plsql.core.db.ConnectionManager cm) {
        this.connectionManager = cm;
    }

    public void setServiceFactory(com.kylin.plsql.core.service.ServiceFactory sf) {
        this.serviceFactory = sf;
    }

    public String getCurrentConnName() {
        int sel = resultTabs.getSelectedIndex();
        if (sel <= 0) return null;
        int dataIdx = sel - 1;
        if (dataIdx < 0 || dataIdx >= tabDataList.size()) return null;
        return tabDataList.get(dataIdx).connName;
    }

    public TableModel getCurrentTableModel() {
        int sel = resultTabs.getSelectedIndex();
        if (sel <= 0) return null;
        int dataIdx = sel - 1;
        if (dataIdx < 0 || dataIdx >= tabDataList.size()) return null;
        return tabDataList.get(dataIdx).model;
    }

    private String getDbTypeForConn(String connName) {
        var connections = com.kylin.plsql.core.config.ConfigManager.getInstance().loadConnections();
        for (var ci : connections) {
            if (ci.getName().equals(connName)) return ci.getDbType();
        }
        return "oracle";
    }

    private String getSchemaForConn(String connName) {
        var connections = com.kylin.plsql.core.config.ConfigManager.getInstance().loadConnections();
        for (var ci : connections) {
            if (ci.getName().equals(connName)) {
                String s = ci.getSchema();
                return s != null && !s.isEmpty() ? s : null;
            }
        }
        return null;
    }
}
