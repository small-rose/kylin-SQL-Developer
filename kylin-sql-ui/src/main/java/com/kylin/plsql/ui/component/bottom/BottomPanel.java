package com.kylin.plsql.ui.component.bottom;

import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.SqlExecutor;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public class BottomPanel extends JPanel {
    private enum Tab { TODO, SERVICES }

    public interface TabDataProvider {
        java.util.List<TabInfo> getOpenTabs();
    }

    public static class TabInfo {
        public String connName;
        public String tabTitle;
        public String filePath; // null for database objects
        public boolean open = true;
    }

    private final ThemeManager theme = ThemeManager.getInstance();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JTextArea todoArea = new JTextArea();
    private final ResultPanel resultPanel = new ResultPanel();
    private final JTree connTree;
    private final DefaultTreeModel connTreeModel;
    private final DefaultMutableTreeNode connRoot;

    private Tab activeTab = Tab.SERVICES;
    private boolean expanded = true;
    private TabDataProvider dataProvider;
    private Runnable onReopenClosedTab;
    private Runnable onRefresh;
    private Consumer<String> onNewQuery;
    private Consumer<String> onCloseAllTabs;
    private Consumer<TabInfo> onSaveTab;
    private Consumer<TabInfo> onCloseTab;
    private Consumer<TabInfo> onOpenInNewTab;
    private Consumer<TabInfo> onDeleteRecord;
    private Consumer<TabInfo> onOpenClosedTab;
    private final java.util.Map<String, String> connectionDialects = new java.util.HashMap<>();

    private final JButton todoBtn;
    private final JButton servicesBtn;

    public BottomPanel() {
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.resolve("border.light")));
        setLayout(new BorderLayout());

        tabBar.setPreferredSize(new Dimension(0, 28));
        tabBar.setBackground(theme.resolve("bg.main"));
        tabBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.resolve("border.light")));

        todoBtn = makeTabBtn("TODO");
        servicesBtn = makeTabBtn("Services");
        tabBar.add(todoBtn);
        tabBar.add(servicesBtn);
        tabBar.add(Box.createHorizontalGlue());

        todoBtn.addActionListener(e -> toggleTab(Tab.TODO));
        servicesBtn.addActionListener(e -> toggleTab(Tab.SERVICES));

        todoArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        todoArea.setBackground(theme.resolve("bg.main"));
        todoArea.setForeground(theme.resolve("fg.secondary"));
        todoArea.setCaretColor(theme.resolve("fg.secondary"));
        JScrollPane todoScroll = new JScrollPane(todoArea);
        todoScroll.setBorder(null);
        todoScrollViewport = todoScroll.getViewport();
        todoScrollViewport.setBackground(theme.resolve("bg.main"));
        contentPanel.add(todoScroll, "TODO");

        connRoot = new DefaultMutableTreeNode("数据库连接 (0)");
        connTreeModel = new DefaultTreeModel(connRoot);
        connTree = new JTree(connTreeModel);
        connTree.setRootVisible(true);
        connTree.setShowsRootHandles(true);
        connTree.setRowHeight(22);
        connTree.setBackground(theme.resolve("bg.main"));
        connTree.setForeground(theme.resolve("fg.secondary"));
        connTree.setCellRenderer(new ConnTreeRenderer());
        connTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { handleTreePopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handleTreePopup(e); }
            private void handleTreePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                TreePath path = connTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object uo = node.getUserObject();
                JPopupMenu menu = new JPopupMenu();
                if (node.isRoot()) {
                    addItem(menu, "\u5237\u65B0", () -> { if (onRefresh != null) onRefresh.run(); });
                    menu.addSeparator();
                    addItem(menu, "\u5C55\u5F00\u5168\u90E8", () -> expandAllNodes());
                    addItem(menu, "\u6298\u53E0\u5168\u90E8", () -> collapseAllNodes());
                } else if (uo instanceof String connName) {
                    addItem(menu, "\u65B0\u5EFA SQL \u67E5\u8BE2", () -> { if (onNewQuery != null) onNewQuery.accept(connName); });
                    addItem(menu, "\u5173\u95ED\u6240\u6709\u6807\u7B7E", () -> { if (onCloseAllTabs != null) onCloseAllTabs.accept(connName); });
                } else if (uo instanceof TabInfo ti) {
                    if (ti.open) {
                        addItem(menu, "\u4FDD\u5B58", () -> { if (onSaveTab != null) onSaveTab.accept(ti); });
                        addItem(menu, "\u5173\u95ED", () -> { if (onCloseTab != null) onCloseTab.accept(ti); });
                    } else {
                        addItem(menu, "\u6253\u5F00", () -> { if (onOpenClosedTab != null) onOpenClosedTab.accept(ti); });
                    }
                    menu.addSeparator();
                    addItem(menu, "\u6253\u5F00\u5230\u65B0\u7684\u6807\u7B7E\u9875", () -> { if (onOpenInNewTab != null) onOpenInNewTab.accept(ti); });
                    addItem(menu, "\u5220\u9664\u8BB0\u5F55", () -> { if (onDeleteRecord != null) onDeleteRecord.accept(ti); });
                    menu.addSeparator();
                    addItem(menu, "\u91CD\u65B0\u6253\u5F00\u5DF2\u5173\u95ED\u6807\u7B7E\u9875", () -> { if (onReopenClosedTab != null) onReopenClosedTab.run(); });
                }
                menu.show(connTree, e.getX(), e.getY());
            }
        });
        JScrollPane connScroll = new JScrollPane(connTree);
        connScroll.setBorder(null);
        connScrollViewport = connScroll.getViewport();
        connScrollViewport.setBackground(theme.resolve("bg.main"));

        JSplitPane servicesSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, connScroll, resultPanel);
        servicesSplit.setBorder(null);
        servicesSplit.setDividerLocation(220);
        servicesSplit.setResizeWeight(0);
        contentPanel.add(servicesSplit, "SERVICES");

        contentPanel.setVisible(true);
        add(contentPanel, BorderLayout.CENTER);
        add(tabBar, BorderLayout.SOUTH);

        selectTab(Tab.SERVICES);
        cardLayout.show(contentPanel, "SERVICES");
    }

    public void applyTheme() {
        setBackground(theme.resolve("bg.main"));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.resolve("border.light")));
        tabBar.setBackground(theme.resolve("bg.main"));
        tabBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.resolve("border.light")));
        todoArea.setBackground(theme.resolve("bg.main"));
        todoArea.setForeground(theme.resolve("fg.secondary"));
        todoArea.setCaretColor(theme.resolve("fg.secondary"));
        todoScrollViewport.setBackground(theme.resolve("bg.main"));
        connTree.setBackground(theme.resolve("bg.main"));
        connTree.setForeground(theme.resolve("fg.secondary"));
        connScrollViewport.setBackground(theme.resolve("bg.main"));
        resultPanel.applyTheme();
        selectTab(activeTab);
    }

    private JViewport todoScrollViewport;
    private JViewport connScrollViewport;

    public void setDataProvider(TabDataProvider provider) {
        this.dataProvider = provider;
    }

    public void setOnReopenClosedTab(Runnable r) {
        this.onReopenClosedTab = r;
    }

    public void setOnRefresh(Runnable r) { this.onRefresh = r; }
    public void setOnNewQuery(Consumer<String> c) { this.onNewQuery = c; }
    public void setOnCloseAllTabs(Consumer<String> c) { this.onCloseAllTabs = c; }
    public void setOnSaveTab(Consumer<TabInfo> c) { this.onSaveTab = c; }
    public void setOnCloseTab(Consumer<TabInfo> c) { this.onCloseTab = c; }
    public void setOnOpenInNewTab(Consumer<TabInfo> c) { this.onOpenInNewTab = c; }
    public void setOnDeleteRecord(Consumer<TabInfo> c) { this.onDeleteRecord = c; }
    public void setOnOpenClosedTab(Consumer<TabInfo> c) { this.onOpenClosedTab = c; }

    public void refreshConnTree() {
        java.util.Set<String> expanded = new java.util.HashSet<>();
        for (int i = 0; i < connTree.getRowCount(); i++) {
            if (connTree.isExpanded(i)) {
                TreePath tp = connTree.getPathForRow(i);
                if (tp != null && tp.getPathCount() == 2) {
                    Object uo = ((DefaultMutableTreeNode) tp.getLastPathComponent()).getUserObject();
                    if (uo instanceof String s) expanded.add(s);
                }
            }
        }
        connRoot.removeAllChildren();
        if (dataProvider != null) {
            java.util.List<TabInfo> tabs = dataProvider.getOpenTabs();
            java.util.Map<String, java.util.List<TabInfo>> grouped = new java.util.LinkedHashMap<>();
            for (TabInfo ti : tabs) {
                String cn = ti.connName != null && !ti.connName.isEmpty() ? ti.connName : "(未绑定)";
                grouped.computeIfAbsent(cn, k -> new java.util.ArrayList<>()).add(ti);
            }
            int openCount = (int) tabs.stream().filter(t -> t.open).count();
            connRoot.setUserObject("数据库连接 (" + openCount + "/" + tabs.size() + ")");
            java.util.List<String> connNames = new java.util.ArrayList<>(grouped.keySet());
            java.util.Collections.sort(connNames);
            for (String cn : connNames) {
                DefaultMutableTreeNode connNode = new DefaultMutableTreeNode(cn);
                connRoot.add(connNode);
                java.util.List<TabInfo> tabList = grouped.get(cn);
                tabList.sort((a, b) -> {
                    if (a.tabTitle == null) return -1;
                    if (b.tabTitle == null) return 1;
                    return a.tabTitle.compareToIgnoreCase(b.tabTitle);
                });
                for (TabInfo ti : tabList) {
                    connNode.add(new DefaultMutableTreeNode(ti));
                }
            }
        }
        connTreeModel.reload();
        for (int i = 0; i < connTree.getRowCount(); i++) {
            TreePath tp = connTree.getPathForRow(i);
            if (tp != null && tp.getPathCount() == 2) {
                Object uo = ((DefaultMutableTreeNode) tp.getLastPathComponent()).getUserObject();
                if (uo instanceof String s && expanded.contains(s)) connTree.expandPath(tp);
            }
        }
    }

    public void expandAllNodes() {
        for (int i = 0; i < connTree.getRowCount(); i++) connTree.expandRow(i);
    }

    public void collapseAllNodes() {
        for (int i = connTree.getRowCount() - 1; i >= 0; i--) connTree.collapseRow(i);
    }

    private static final int ICON_SIZE = 16;

    private static Icon makeFolderIcon() {
        BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xF5D36E));
        g.fillRoundRect(2, 5, 12, 9, 2, 2);
        g.setColor(new Color(0xE6A817));
        g.fillRect(3, 5, 6, 2);
        g.setColor(new Color(0xC49A0D));
        g.drawRoundRect(2, 5, 12, 9, 2, 2);
        g.dispose();
        return new ImageIcon(img);
    }

    private static Icon makeBadgeIcon(Color bg, String letter) {
        BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(1, 1, 14, 14, 3, 3);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        int x = (ICON_SIZE - fm.stringWidth(letter)) / 2;
        int y = (ICON_SIZE + fm.getAscent()) / 2 - 1;
        g.drawString(letter, x, y);
        g.dispose();
        return new ImageIcon(img);
    }

    private static class ConnTreeRenderer implements TreeCellRenderer {
        private final JLabel label = new JLabel();
        private final Icon folderIcon = makeFolderIcon();
        private final Icon connIcon = makeBadgeIcon(new Color(0xE67E22), "C");
        private final Icon fileIcon = makeBadgeIcon(new Color(0x337AB7), "F");
        private final Icon dbIcon = makeBadgeIcon(new Color(0xE74C3C), "S");
        private final Icon closedIcon = makeBadgeIcon(new Color(0x888888), "S");

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                       boolean expanded, boolean leaf, int row, boolean foc) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object uo = node.getUserObject();
            label.setOpaque(true);
            Color bg = sel ? ThemeManager.getInstance().resolve("selection.listBg") : ThemeManager.getInstance().resolve("bg.main");
            Color fg = sel ? ThemeManager.getInstance().resolve("selection.listFg") : ThemeManager.getInstance().resolve("fg.main");
            label.setBackground(bg);
            label.setForeground(fg);
            label.setFont(tree.getFont());
            label.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
            if (node.isRoot()) {
                label.setText(uo.toString());
                label.setIcon(folderIcon);
            } else if (uo instanceof String) {
                label.setText((String) uo);
                label.setIcon(connIcon);
            } else if (uo instanceof TabInfo ti) {
                label.setText(ti.tabTitle);
                if (ti.open) {
                    label.setIcon(ti.filePath != null ? fileIcon : dbIcon);
                } else {
                    label.setForeground(ThemeManager.getInstance().resolve("fg.muted"));
                    label.setIcon(closedIcon);
                }
            }
            return label;
        }
    }

    private void toggleTab(Tab tab) {
        if (expanded && tab == activeTab) {
            expanded = false;
            contentPanel.setVisible(false);
        } else {
            activeTab = tab;
            selectTab(tab);
            cardLayout.show(contentPanel, tab.name());
            if (!expanded) {
                expanded = true;
                contentPanel.setVisible(true);
            }
        }
        selectTab(activeTab);
        revalidate();
        Container p = getParent();
        while (p != null) { p.revalidate(); p = p.getParent(); }
        repaint();
    }

    private void selectTab(Tab tab) {
        updateBtnStyle(todoBtn, tab == Tab.TODO);
        updateBtnStyle(servicesBtn, tab == Tab.SERVICES);
    }

    private void updateBtnStyle(JButton btn, boolean selected) {
        btn.setBorder(selected
            ? BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, theme.resolve("accent.tab")),
                BorderFactory.createEmptyBorder(4, 10, 4, 10))
            : BorderFactory.createEmptyBorder(4, 20, 4, 20));
        btn.setForeground(selected ? theme.resolve("fg.tab.active") : theme.resolve("fg.tab.inactive"));
    }

    private static JButton makeTabBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setFocusable(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public boolean isExpanded() { return expanded; }
    public boolean isTabActive(String name) { return name != null && activeTab.name().equals(name); }

    public void showResult(String sql, SqlExecutor.SqlResult result) {
        ensureServicesVisible();
        resultPanel.showResult(sql, result);
    }

    public void showResult(String sql, SqlExecutor.SqlResult result, String connName) {
        ensureServicesVisible();
        resultPanel.showResult(sql, result, connName);
    }

    public void setRefreshExecutor(java.util.function.BiConsumer<String, String> executor) {
        resultPanel.setRefreshExecutor(executor);
    }

    public void showError(String message) {
        ensureServicesVisible();
        resultPanel.showError(message);
    }

    public void appendMessage(String message) {
        ensureServicesVisible();
        resultPanel.appendMessage(message);
    }

    public void clear() { resultPanel.clear(); }
    public void clearAll() { resultPanel.clearAll(); }
    public void showToast(String msg) { resultPanel.showToast(msg); }

    public ResultPanel getResultPanel() { return resultPanel; }

    public void setBatchExecuting(boolean b) { resultPanel.setBatchExecuting(b); }

    private void ensureServicesVisible() {
        if (!expanded || activeTab != Tab.SERVICES) {
            activeTab = Tab.SERVICES;
            cardLayout.show(contentPanel, "SERVICES");
            if (!expanded) { expanded = true; contentPanel.setVisible(true); }
            selectTab(Tab.SERVICES);
            revalidate();
            Container p = getParent();
            while (p != null) { p.revalidate(); p = p.getParent(); }
            repaint();
        }
    }

    // ── Dialect per connection ──
    public java.util.Map<String, String> getConnectionDialects() { return connectionDialects; }
    public void setConnectionDialects(java.util.Map<String, String> map) { connectionDialects.clear(); connectionDialects.putAll(map); }
    public String getConnectionDialect(String connName) { return connectionDialects.get(connName); }
    public void setConnectionDialect(String connName, String dialect) { connectionDialects.put(connName, dialect); }

    private static void addItem(JPopupMenu menu, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    @Override
    public Dimension getPreferredSize() { return new Dimension(0, expanded ? 278 : 28); }
    @Override
    public Dimension getMinimumSize() { return new Dimension(0, 28); }
    @Override
    public Dimension getMaximumSize() { return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE); }
}
