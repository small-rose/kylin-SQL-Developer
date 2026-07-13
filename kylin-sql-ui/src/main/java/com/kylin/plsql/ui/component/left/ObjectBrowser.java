package com.kylin.plsql.ui.component.left;

import com.kylin.plsql.ui.component.common.IconUtil;
import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.DbMetadataConfig;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.sql.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/** Database object browser tree with schema/table/view/procedure navigation. */
public class ObjectBrowser extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ObjectBrowser.class);

    private static final Toolkit TK = Toolkit.getDefaultToolkit();

    // ── Callback interface ──

    public interface Callback {
        void onObjectAction(String connName, String schema, String objectType, String objectName, String action);
        void onNewSqlEditor(String connName);
        void onOpenConnections();
        void onConnectionProperties(String connName);
        void onOpenSourceObject(String connName, String schema, String objectType, String objectName);
        void onSyncProgress(String connName, int percent);
        void onSyncComplete(String connName);
    }

    // ── Fields ──

    private final Callback callback;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode root;
    private final JButton refreshBtn;
    private final java.util.Map<String, java.util.List<String>> connFullSchemas = new HashMap<>();
    private final java.util.Map<String, java.util.Set<String>> connHiddenSchemas = new HashMap<>();
    private final ThemeManager theme = ThemeManager.getInstance();

    private ConnectionManager cm;
    private String currentType; // last detected db type for column queries

    private ConfigManager configManager;

    public void setConfigManager(ConfigManager cm) { this.configManager = cm; }

    // ── Object type config ──

    private static class ObjectType {
        final String label, typeCode;
        final String querySql;       // null for FIXED_LIST
        final List<String> fixedValues; // null for SQL
        final boolean expandable;
        ObjectType(String label, String typeCode, String querySql, boolean expandable) {
            this.label = label; this.typeCode = typeCode;
            this.querySql = querySql; this.fixedValues = null;
            this.expandable = expandable;
        }
        ObjectType(String label, String typeCode, List<String> fixedValues, boolean expandable) {
            this.label = label; this.typeCode = typeCode;
            this.querySql = null; this.fixedValues = fixedValues;
            this.expandable = expandable;
        }
    }

    private static final List<ObjectType> ORACLE_TYPES = List.of(
        new ObjectType("模式", "SCHEMA",    java.util.Collections.emptyList(), false),
        new ObjectType("表", "TABLE",     "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name", true),
        new ObjectType("视图", "VIEW",    "SELECT view_name FROM all_views WHERE owner = ? ORDER BY view_name", false),
        new ObjectType("索引", "INDEX",   "SELECT index_name FROM all_indexes WHERE owner = ? ORDER BY index_name", false),
        new ObjectType("序列", "SEQUENCE","SELECT sequence_name FROM all_sequences WHERE sequence_owner = ? ORDER BY sequence_name", false),
        new ObjectType("同义词", "SYNONYM","SELECT synonym_name FROM all_synonyms WHERE owner = ? ORDER BY synonym_name", false),
        new ObjectType("函数", "FUNCTION", "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'FUNCTION' ORDER BY object_name", false),
        new ObjectType("过程", "PROCEDURE","SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'PROCEDURE' ORDER BY object_name", false),
        new ObjectType("包", "PACKAGE",   "SELECT DISTINCT object_name FROM all_procedures WHERE owner = ? AND object_type IN ('PACKAGE','PACKAGE BODY') ORDER BY object_name", true)
    );

    private static final List<ObjectType> PG_TYPES = List.of(
        new ObjectType("模式", "SCHEMA",    java.util.Collections.emptyList(), false),
        new ObjectType("表", "TABLE",     "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = ? ORDER BY tablename", true),
        new ObjectType("视图", "VIEW",    "SELECT viewname FROM pg_catalog.pg_views WHERE schemaname = ? ORDER BY viewname", false),
        new ObjectType("索引", "INDEX",   "SELECT indexname FROM pg_catalog.pg_indexes WHERE schemaname = ? ORDER BY indexname", false),
        new ObjectType("序列", "SEQUENCE","SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ? ORDER BY sequence_name", false),
        new ObjectType("函数", "FUNCTION", "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'FUNCTION' ORDER BY routine_name", false),
        new ObjectType("过程", "PROCEDURE","SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'PROCEDURE' ORDER BY routine_name", false)
    );

    private static final List<ObjectType> MYSQL_TYPES = List.of(
        new ObjectType("模式", "SCHEMA",    java.util.Collections.emptyList(), false),
        new ObjectType("表", "TABLE",     "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name", true),
        new ObjectType("视图", "VIEW",    "SELECT table_name FROM information_schema.views WHERE table_schema = ? ORDER BY table_name", false),
        new ObjectType("函数", "FUNCTION", "SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'FUNCTION' ORDER BY routine_name", false),
        new ObjectType("过程", "PROCEDURE","SELECT routine_name FROM information_schema.routines WHERE routine_schema = ? AND routine_type = 'PROCEDURE' ORDER BY routine_name", false)
    );

    private static final List<ObjectType> OB_ORACLE_TYPES = List.of(
        new ObjectType("模式", "SCHEMA",    java.util.Collections.emptyList(), false),
        new ObjectType("表", "TABLE",       "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name", true),
        new ObjectType("视图", "VIEW",      "SELECT view_name FROM all_views WHERE owner = ? ORDER BY view_name", false),
        new ObjectType("索引", "INDEX",     "SELECT index_name FROM all_indexes WHERE owner = ? ORDER BY index_name", false),
        new ObjectType("序列", "SEQUENCE",  "SELECT sequence_name FROM all_sequences WHERE sequence_owner = ? ORDER BY sequence_name", false),
        new ObjectType("同义词", "SYNONYM", "SELECT synonym_name FROM all_synonyms WHERE owner = ? ORDER BY synonym_name", false),
        new ObjectType("函数", "FUNCTION",  "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'FUNCTION' ORDER BY object_name", false),
        new ObjectType("过程", "PROCEDURE", "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = 'PROCEDURE' ORDER BY object_name", false),
        new ObjectType("包", "PACKAGE",     "SELECT DISTINCT object_name FROM all_procedures WHERE owner = ? AND object_type IN ('PACKAGE','PACKAGE BODY') ORDER BY object_name", true)
    );

    private static final Map<String, String> LABEL_TO_CODE = new LinkedHashMap<>();
    private static final Map<String, String> CODE_TO_LABEL = new LinkedHashMap<>();
    static {
        for (var t : ORACLE_TYPES) { LABEL_TO_CODE.put(t.label, t.typeCode); CODE_TO_LABEL.put(t.typeCode, t.label); }
        for (var t : PG_TYPES) { LABEL_TO_CODE.put(t.label, t.typeCode); CODE_TO_LABEL.put(t.typeCode, t.label); }
        for (var t : MYSQL_TYPES) { LABEL_TO_CODE.put(t.label, t.typeCode); CODE_TO_LABEL.put(t.typeCode, t.label); }
        for (var t : OB_ORACLE_TYPES) { LABEL_TO_CODE.put(t.label, t.typeCode); CODE_TO_LABEL.put(t.typeCode, t.label); }
    }
    // PACKAGE_BODY also maps to "包"
    static { CODE_TO_LABEL.put("PACKAGE_BODY", "包"); }

    // ── Column info holder for table child nodes ──

    private static class ColumnInfo {
        final String name;
        final String dataType;
        final String sizeStr;
        final String comment;
        ColumnInfo(String name, String dataType, String sizeStr, String comment) {
            this.name = name; this.dataType = dataType; this.sizeStr = sizeStr; this.comment = comment;
        }
        @Override public String toString() {
            return name;
        }
        String toDisplayHtml(String nameColor, String grayColor) {
            String suffix = dataType;
            if (sizeStr != null) suffix += "(" + sizeStr + ")";
            return "<html><span style='color:" + nameColor + "'>" + esc(name) + "</span>"
                + " <span style='color:" + grayColor + "'>" + esc(suffix) + "</span></html>";
        }
        private static String esc(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
    // ── Icons ──

    private static final Icon ICON_DB     = makeIcon("DB", new Color(0x4A90D9));
    private static final Icon ICON_SCHEMA = makeIcon("S",  new Color(0x5CB85C));
    private static final Icon ICON_TABLE  = makeIcon("T",  new Color(0x337AB7));
    private static final Icon ICON_VIEW   = makeIcon("V",  new Color(0x5BC0DE));
    private static final Icon ICON_INDEX  = makeIcon("I",  new Color(0xF0AD4E));
    private static final Icon ICON_SEQ    = makeIcon("N",  new Color(0x8E44AD));
    private static final Icon ICON_FUNC   = makeIcon("F",  new Color(0xD9534F));
    private static final Icon ICON_PROC   = makeIcon("P",  new Color(0xD9534F));
    private static final Icon ICON_PKG    = makeIcon("K",  new Color(0xA0522D));
    private static final Icon ICON_COLUMN = makeIcon("C",  new Color(0x059775));

    private static Icon makeIcon(String text, Color bg) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(1, 1, 14, 14, 3, 3);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        int x = (16 - fm.stringWidth(text)) / 2;
        int y = (16 + fm.getAscent()) / 2 - 1;
        g.drawString(text, x, y);
        g.dispose();
        return new ImageIcon(img);
    }

    private static final Icon ICON_SYNONYM = makeIcon("Y", new Color(0x7B8D8E));

    // ── Toolbar icons (same approach as tree icons) ──

    private static final Icon ICON_NEW  = makeIcon("+", new Color(0x5CB85C));
    private static final Icon ICON_PROP = makeIcon("⚙", new Color(0x337AB7));
    private static final Icon ICON_REFR = makeIcon("↻", new Color(0xF0AD4E));
    private static final Icon ICON_SQL  = makeIcon("▶", new Color(0x5CB85C));

    private static ImageIcon makeIcon18(String text, Color bg) {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(1, 1, 16, 16, 4, 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Dialog", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int x = (18 - fm.stringWidth(text)) / 2;
        int y = (18 + fm.getAscent()) / 2 - 1;
        g.drawString(text, x, y);
        g.dispose();
        return new ImageIcon(img);
    }

    private static String colorHex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    private static Icon iconForTypeLabel(String label) {
        return switch (label) {
            case "表" -> ICON_TABLE;
            case "视图" -> ICON_VIEW;
            case "索引" -> ICON_INDEX;
            case "序列" -> ICON_SEQ;
            case "同义词" -> ICON_SYNONYM;
            case "函数" -> ICON_FUNC;
            case "过程" -> ICON_PROC;
            case "包" -> ICON_PKG;
            default -> ICON_DB;
        };
    }

    // ── Constructor ──

    public ObjectBrowser(Callback callback) {
        this.callback = callback;
        setBorder(null);
        setLayout(new BorderLayout());

        // Toolbar (icon-only, DataGrip style)
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        JButton newBtn = makeToolBtn(ICON_NEW, "新建连接", e -> callback.onOpenConnections());
        tb.add(newBtn);

        JButton propBtn = makeToolBtn(ICON_PROP, "连接属性", e -> showProperties());
        tb.add(propBtn);

        refreshBtn = makeToolBtn(ICON_REFR, "刷新当前连接", e -> refreshSelected());
        tb.add(refreshBtn);

        tb.addSeparator();

        JButton sqlBtn = makeToolBtn(ICON_SQL, "新建 SQL 编辑器", e -> callback.onNewSqlEditor(getSelectedConnName()));
        tb.add(sqlBtn);

        add(tb, BorderLayout.NORTH);

        // Tree
        root = new DefaultMutableTreeNode("数据库");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(22);

        // Custom cell renderer with icons + connection badge + connection badge
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value,
                    boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                int level = node.getLevel();
                if (level == 1 && node.getUserObject() instanceof ConnHolder ch) {
                    String cn = ch.info.getName();
                    java.util.List<String> all = connFullSchemas.get(cn);
                    int total = all != null ? all.size() : 0;
                    java.util.Set<String> hidden = connHiddenSchemas.getOrDefault(cn, java.util.Collections.emptySet());
                    int shown = total - hidden.size();
                    Color bg = sel ? getBackgroundSelectionColor() : t.getBackground();
                    Color fg = sel ? getTextSelectionColor() : getTextNonSelectionColor();
                    JPanel p = new JPanel(new BorderLayout(10, 0));
                    p.setOpaque(true); p.setBackground(bg);
                    JLabel nl = new JLabel(cn);
                    nl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); nl.setForeground(fg);
                    p.add(nl, BorderLayout.CENTER);
                    JLabel bl = new JLabel(shown + "  of  " + total);
                    bl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                    bl.setForeground(sel ? fg : theme.resolve("fg.muted")); bl.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 4));
                    p.add(bl, BorderLayout.EAST);
                    JPanel wrap = new JPanel(new BorderLayout(6, 0));
                    wrap.setOpaque(false);
                    JLabel il = new JLabel(ICON_DB); wrap.add(il, BorderLayout.WEST); wrap.add(p, BorderLayout.CENTER);
                    return wrap;
                }
                JLabel label = (JLabel) super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, hasFocus);
                Icon ic = null;
                if (level == 2) ic = ICON_SCHEMA;
                else if (level == 3) ic = iconForTypeLabel(node.getUserObject().toString());
                else if (level == 4) { String tl = getNodeLabel(node, 3); ic = iconForTypeLabel(tl); }
                else if (level == 5) {
                    ic = ICON_COLUMN;
                    Object uo = node.getUserObject();
                    if (uo instanceof ColumnInfo ci) {
                        String nc = colorHex(sel ? getTextSelectionColor() : getTextNonSelectionColor());
                        String gc = sel ? nc : "888888";
                        label.setText(ci.toDisplayHtml(nc, gc));
                        if (ci.comment != null && !ci.comment.isEmpty()) {
                            label.setToolTipText(ci.comment);
                        }
                    }
                }
                if (ic != null) label.setIcon(ic);
                return label;
            }
        });

        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node == null || node.isRoot()) return;
            if (node.getLevel() == 4) {
                copyToClipboard(node.getUserObject().toString());
            } else if (node.getLevel() == 5 && node.getUserObject() instanceof ColumnInfo ci) {
                copyToClipboard(ci.name);
            }
        });

        // Lazy load on expand
        tree.addTreeWillExpandListener(new javax.swing.event.TreeWillExpandListener() {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
                // Level 1: load schemas lazily
                if (node.getUserObject() instanceof ConnHolder && node.getChildCount() == 1) {
                    DefaultMutableTreeNode first = (DefaultMutableTreeNode) node.getChildAt(0);
                    if ("加载中...".equals(first.getUserObject())) {
                        SwingWorker<Void, Void> worker = new SwingWorker<>() {
                            @Override
                            protected Void doInBackground() {
                                loadConnection(node);
                                return null;
                            }
                        };
                        worker.execute();
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    }
                }
                // Level 4: expandable object node - lazy load columns
                if (node.getLevel() == 4 && node.getChildCount() == 1) {
                    DefaultMutableTreeNode first = (DefaultMutableTreeNode) node.getChildAt(0);
                    if ("".equals(first.getUserObject())) {
                        String typeLabel = getNodeLabel(node, 3);
                        if ("表".equals(typeLabel)) {
                        SwingWorker<Void, Void> worker = new SwingWorker<>() {
                            @Override
                            protected Void doInBackground() {
                                loadColumns(node);
                                return null;
                            }
                        };
                        worker.execute();
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    }
                }
            }
            } // treeWillExpand
            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent e) {}
        });

        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) { handleDoubleClick(e); return; }
                // Single click on connection badge
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    DefaultMutableTreeNode n = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (n.getLevel() == 1 && n.getUserObject() instanceof ConnHolder) {
                        java.awt.Rectangle r = tree.getPathBounds(path);
                        if (r != null && e.getX() > r.x + r.width - 60) {
                            showSchemaPopup(n, e.getX(), e.getY());
                        }
                    }
                }
            }
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    // ── Connection node holder ──

    private static class ConnHolder {
        final ConnectionInfo info;
        final boolean expanded;
        String dbType;
        ConnHolder(ConnectionInfo info, boolean expanded) { this.info = info; this.expanded = expanded; }
        @Override public String toString() { return info.getName(); }
    }

    private String getConnName(DefaultMutableTreeNode node) {
        while (node != null) {
            if (node.getUserObject() instanceof ConnHolder) return ((ConnHolder) node.getUserObject()).info.getName();
            node = (DefaultMutableTreeNode) node.getParent();
        }
        return "";
    }

    private String getNodeLabel(DefaultMutableTreeNode node, int depth) {
        Object[] objs = node.getUserObjectPath();
        return depth < objs.length && objs[depth] != null ? objs[depth].toString() : "";
    }

    public JTree getTree() { return tree; }

    public void applyTheme() {
        Color bg = theme.resolve("bg.main");
        Color fg = theme.resolve("list.fg");
        setBackground(bg);
        tree.setBackground(bg);
        tree.setForeground(fg);
        for (Component c : getComponents()) {
            if (c instanceof JScrollPane sp) {
                sp.getViewport().setBackground(bg);
                sp.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, theme.resolve("border.default")));
            }
            if (c instanceof JToolBar tb) {
                tb.setBackground(bg);
                tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, theme.resolve("border.default")));
            }
        }
        tree.repaint();
    }

    private static JButton makeToolBtn(Icon icon, String tip, java.awt.event.ActionListener action) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tip);
        btn.setFocusable(false);
        btn.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        btn.setContentAreaFilled(false);
        btn.addActionListener(action);
        return btn;
    }

    private void copyToClipboard(String text) {
        TK.getSystemClipboard().setContents(new StringSelection(text), null);
        showToast("已复制: " + text);
    }

    private void showToast(String msg) {
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        if (ancestor == null) return;
        JWindow toast = new JWindow(ancestor);
        JLabel label = new JLabel(msg);
        label.setOpaque(true);
        Color bg = ThemeManager.getInstance().resolve("bg.panel");
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

    // ── Load columns for a table node (cache-aware) ──

    private void loadColumns(DefaultMutableTreeNode tblNode) {
        String connName = getConnName(tblNode);
        String schema = getNodePath(tblNode, 2);
        String tableName = tblNode.getUserObject().toString();
        MetadataCache cache = MetadataCache.getInstance();

        tblNode.removeAllChildren();

        // ── Try cache first ──
        List<MetadataCache.CachedColumn> cached = cache.getColumns(connName, schema, tableName);
        if (cached != null) {
            for (var cc : cached) {
                String sz = cc.size > 0 ? String.valueOf(cc.size) : "";
                tblNode.add(new DefaultMutableTreeNode(new ColumnInfo(cc.name, cc.type, sz, cc.comment)));
            }
            treeModel.reload(tblNode);
            return;
        }

        // ── Cache miss: query DB ──
        String dbProduct = getConnDbProduct(tblNode);
        String sql;
        if (dbProduct != null && (dbProduct.contains("mysql") || dbProduct.contains("mariadb"))) {
            sql = "SELECT column_name, data_type, character_maximum_length, column_comment FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
        } else if (dbProduct != null && (dbProduct.contains("postgresql") || dbProduct.contains("edb"))) {
            sql = "SELECT column_name, data_type, character_maximum_length, column_comment FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
        } else {
            sql = "SELECT c.column_name, c.data_type, c.data_length, cc.comments FROM all_tab_columns c LEFT JOIN all_col_comments cc ON cc.owner=c.owner AND cc.table_name=c.table_name AND cc.column_name=c.column_name WHERE c.owner = ? AND c.table_name = ? ORDER BY c.column_id";
        }

        try (Connection conn = cm.getConnection(connName);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            List<MetadataCache.CachedColumn> cols = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString(1);
                    String dataType = rs.getString(2);
                    String sizeStr = rs.getString(3);
                    String comment = rs.getString(4);
                    if (colName != null) {
                        String dt = dataType != null ? dataType : "";
                        String sz = sizeStr != null ? sizeStr : "";
                        tblNode.add(new DefaultMutableTreeNode(new ColumnInfo(colName, dt, sz, comment)));
                        MetadataCache.CachedColumn cc = new MetadataCache.CachedColumn();
                        cc.name = colName;
                        cc.type = dt;
                        try { cc.size = Integer.parseInt(sz); } catch (NumberFormatException ignored) {}
                        cc.comment = comment;
                        cols.add(cc);
                    }
                }
            }
            cache.putColumns(connName, schema, tableName, cols);
        } catch (SQLException e) {
            log.warn("加载列失败 {}: {}", tableName, e.getMessage());
        }
        treeModel.reload(tblNode);
    }

    private String getConnDbProduct(DefaultMutableTreeNode node) {
        while (node != null) {
            if (node.getUserObject() instanceof ConnHolder) return ((ConnHolder) node.getUserObject()).dbType;
            node = (DefaultMutableTreeNode) node.getParent();
        }
        return null;
    }

    // ── Public API ──

    public void loadAll(ConnectionManager cm, List<ConnectionInfo> connections) {
        this.cm = cm;
        root.removeAllChildren();
        for (ConnectionInfo info : connections) {
            boolean expanded = cm.isConnected(info.getName());
            DefaultMutableTreeNode connNode = new DefaultMutableTreeNode(new ConnHolder(info, expanded));
            connNode.add(new DefaultMutableTreeNode("加载中..."));
            root.add(connNode);
            if (expanded) {
                loadConnection(connNode);
            }
        }
        treeModel.reload();
    }

    public void locateObject(String connName, String schema, String objectType, String objectName) {
        DefaultMutableTreeNode connNode = null;
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) root.getChildAt(i);
            if (n.getUserObject() instanceof ConnHolder ch && ch.info.getName().equals(connName)) {
                connNode = n;
                break;
            }
        }
        if (connNode == null) return;

        // If connection not yet loaded, trigger load
        if (connNode.getChildCount() == 1) {
            Object first = connNode.getFirstChild();
            if (first instanceof DefaultMutableTreeNode && "加载中...".equals(((DefaultMutableTreeNode) first).getUserObject())) {
                loadConnection(connNode);
            }
        }

        String typeLabel = CODE_TO_LABEL.getOrDefault(objectType, objectType);

        // Depth-first search for schema → typeLabel → objectName
        DefaultMutableTreeNode target = findDescendant(connNode, schema, typeLabel, objectName);
        if (target != null) {
            TreePath path = new TreePath(target.getPath());
            tree.expandPath(path);
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    private static DefaultMutableTreeNode findDescendant(DefaultMutableTreeNode node, String... labels) {
        java.util.List<DefaultMutableTreeNode> candidates = java.util.Collections.singletonList(node);
        for (String label : labels) {
            java.util.List<DefaultMutableTreeNode> next = new java.util.ArrayList<>();
            for (DefaultMutableTreeNode c : candidates) {
                for (int i = 0; i < c.getChildCount(); i++) {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) c.getChildAt(i);
                    if (label.equals(child.getUserObject().toString())) {
                        next.add(child);
                    }
                }
            }
            if (next.isEmpty()) return null;
            candidates = next;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public void refreshAll() {
        refreshBtn.setEnabled(false);
        List<ConnectionInfo> conns = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) root.getChildAt(i);
            if (n.getUserObject() instanceof ConnHolder) {
                conns.add(((ConnHolder) n.getUserObject()).info);
            }
        }
        loadAll(cm, conns);
        refreshBtn.setEnabled(true);
    }

    private void refreshSelected() {
        TreePath path = tree.getSelectionPath();
        if (path == null) { refreshAll(); return; }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        DefaultMutableTreeNode connNode = null;
        if (node.getUserObject() instanceof ConnHolder) {
            connNode = node;
        } else {
            DefaultMutableTreeNode p = node;
            while (p != null) {
                if (p.getUserObject() instanceof ConnHolder) { connNode = p; break; }
                p = (DefaultMutableTreeNode) p.getParent();
            }
        }
        if (connNode == null) { refreshAll(); return; }

        ConnHolder h = (ConnHolder) connNode.getUserObject();
        String cname = h.info.getName();
        final DefaultMutableTreeNode finalConnNode = connNode;

        finalConnNode.removeAllChildren();
        finalConnNode.add(new DefaultMutableTreeNode("刷新中..."));
        treeModel.reload(finalConnNode);
        refreshBtn.setEnabled(false);
        callback.onSyncProgress(cname, 0);

        new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() {
                publish(0);
                MetadataCache.getInstance().clearConnection(cname);
                if (!cm.isConnected(cname)) {
                    try {
                        cm.connect(h.info);
                    } catch (Exception e) {
                        log.warn("自动连接 '{}' 失败: {}", cname, e.getMessage());
                        return null;
                    }
                }
                try (Connection conn = cm.getConnection(cname)) {
                    String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
                    h.dbType = dbProduct;
                    List<ObjectType> types = detectTypes(dbProduct);
                    publish(5);

                    java.util.Set<String> schemas = collectSchemas(conn, dbProduct.contains("oracle") || dbProduct.contains("oceanbase"));
                    MetadataCache mc = MetadataCache.getInstance();
                    mc.putSchemas(cname, dbProduct, schemas);
                    java.util.List<String> schemaList = new ArrayList<>(schemas);
                    connFullSchemas.put(cname, schemaList);
                    initHiddenSchemas(h, schemaList);
                    publish(10);

                    if (!schemas.isEmpty()) {
                        int queryTypeCount = 0;
                        for (ObjectType ot : types) {
                            if (!"SCHEMA".equals(ot.typeCode)) queryTypeCount++;
                        }
                        int totalOps = schemas.size() * queryTypeCount;
                        int doneOps = 0;
                        for (String schema : schemas) {
                            for (ObjectType ot : types) {
                                if ("SCHEMA".equals(ot.typeCode)) continue;
                                List<String> objects = queryObjects(conn, ot, schema);
                                mc.putObjects(cname, schema, ot.typeCode, objects);
                                doneOps++;
                                int pct = Math.min(95, 10 + doneOps * 85 / totalOps);
                                publish(pct);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("刷新连接 '{}' 失败", cname, e);
                }
                publish(100);
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int pct = chunks.get(chunks.size() - 1);
                callback.onSyncProgress(cname, pct);
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (MetadataCache.getInstance().hasMetadata(cname)) {
                        loadConnection(finalConnNode);
                    } else {
                        finalConnNode.removeAllChildren();
                        finalConnNode.add(new DefaultMutableTreeNode("刷新失败"));
                        treeModel.reload(finalConnNode);
                    }
                } catch (Exception e) {
                    finalConnNode.removeAllChildren();
                    finalConnNode.add(new DefaultMutableTreeNode("刷新失败: " + e.getMessage()));
                    treeModel.reload(finalConnNode);
                } finally {
                    refreshBtn.setEnabled(true);
                    callback.onSyncComplete(cname);
                }
            }
        }.execute();
    }

    private void showSchemaPopup(DefaultMutableTreeNode connNode, int x, int y) {
        ConnHolder h = (ConnHolder) connNode.getUserObject();
        String cn = h.info.getName();
        java.util.List<String> all = connFullSchemas.get(cn);
        if (all == null || all.isEmpty()) return;

        java.util.Set<String> hidden = connHiddenSchemas.computeIfAbsent(cn, k -> new java.util.HashSet<>());

        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(new Color(0x555555)));
        popup.setLayout(new BorderLayout());

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        // ALL_SCHEMA checkbox
        JCheckBox allCb = new JCheckBox("ALL SCHEMA");
        allCb.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        allCb.setSelected(hidden.isEmpty());
        allCb.addActionListener(ev -> {
            if (allCb.isSelected()) hidden.clear();
            else hidden.addAll(all);
            // Sync individual checkboxes
            for (int i = 0; i < listPanel.getComponentCount(); i++) {
                Component comp = listPanel.getComponent(i);
                if (comp instanceof JCheckBox cb && cb != allCb) {
                    cb.setSelected(!hidden.contains(cb.getText()));
                }
            }
        });
        listPanel.add(allCb);
        listPanel.add(new JSeparator());

        // All individual schema checkboxes
        java.util.List<JCheckBox> schemaCbs = new java.util.ArrayList<>();
        String defaultSchema = h.info.getSchema();
        boolean hasDefault = defaultSchema != null && !defaultSchema.isEmpty() && all.contains(defaultSchema);

        for (String s : all) {
            JCheckBox cb = new JCheckBox(s);
            cb.setFont(new Font(Font.SANS_SERIF, hasDefault && s.equals(defaultSchema) ? Font.BOLD : Font.PLAIN, 11));
            cb.setSelected(!hidden.contains(s));
            cb.addActionListener(ev -> {
                if (cb.isSelected()) hidden.remove(s);
                else hidden.add(s);
                // Sync ALL_SCHEMA checkbox
                boolean allSelected = true;
                for (JCheckBox scb : schemaCbs) {
                    if (!scb.isSelected()) { allSelected = false; break; }
                }
                allCb.setSelected(allSelected);
            });
            listPanel.add(cb);
            schemaCbs.add(cb);
        }
        // Add default schema at top if applicable (already added via loop, just bolded)

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(200, Math.min(350, listPanel.getPreferredSize().height + 10)));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(scroll, BorderLayout.CENTER);
        popup.add(wrap, BorderLayout.CENTER);

        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                rebuildConnectionNode(connNode);
            }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                rebuildConnectionNode(connNode);
            }
        });

        popup.show(tree, x, y);
    }

    private void initHiddenSchemas(ConnHolder h, java.util.List<String> all) {
        String cn = h.info.getName();
        String defaultSchema = h.info.getSchema();
        java.util.Set<String> hidden = new java.util.HashSet<>();
        if (defaultSchema != null && !defaultSchema.isEmpty()) {
            String matched = null;
            for (String s : all) {
                if (s.equalsIgnoreCase(defaultSchema)) { matched = s; break; }
            }
            if (matched != null) {
                hidden.addAll(all);
                hidden.remove(matched);
            }
        }
        connHiddenSchemas.put(cn, hidden);
    }

    // ── Load connection schema tree (cache-aware) ──

    private void loadConnection(DefaultMutableTreeNode connNode) {
        ConnHolder h = (ConnHolder) connNode.getUserObject();
        String name = h.info.getName();
        connNode.removeAllChildren();

        MetadataCache cache = MetadataCache.getInstance();

        // ── Try cache first ──
        if (cache.hasMetadata(name)) {
            String dbProduct = cache.getDbProduct(name);
            h.dbType = dbProduct;
            List<String> schemas = cache.getSchemas(name);
            connFullSchemas.put(name, new ArrayList<>(schemas));
            initHiddenSchemas(h, schemas);
            if (schemas.isEmpty()) {
                connNode.add(new DefaultMutableTreeNode("(无 schema)"));
                treeModel.reload(connNode);
            } else {
                rebuildConnectionNode(connNode);
                ensureTableCommentsLoaded(name, dbProduct, schemas, h.info);
            }
            return;
        }

        // ── Cache miss: query DB ──
        if (!cm.isConnected(name)) {
            try {
                cm.connect(h.info);
            } catch (Exception e) {
                log.warn("自动连接 '{}' 失败: {}", name, e.getMessage());
                connNode.add(new DefaultMutableTreeNode("连接失败: " + e.getMessage()));
                treeModel.reload(connNode);
                return;
            }
        }

        try (Connection conn = cm.getConnection(name)) {
            String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
            h.dbType = dbProduct;
            boolean isOracleLike = dbProduct.contains("oracle") || dbProduct.contains("oceanbase");
            List<ObjectType> types = detectTypes(dbProduct);
            java.util.Set<String> schemas = collectSchemas(conn, isOracleLike);

            // Save schemas to cache
            cache.putSchemas(name, dbProduct, schemas);
            java.util.List<String> schemaList = new ArrayList<>(schemas);
            connFullSchemas.put(name, schemaList);
            initHiddenSchemas(h, schemaList);

            if (!schemas.isEmpty()) {
                for (String schema : schemas) {
                    for (ObjectType ot : types) {
                        if ("SCHEMA".equals(ot.typeCode)) continue;
                        List<String> objects = queryObjects(conn, ot, schema);
                        cache.putObjects(name, schema, ot.typeCode, objects);
                    }
                    loadTableComments(conn, name, dbProduct, schema);
                }
                rebuildConnectionNode(connNode);
            } else {
                connNode.add(new DefaultMutableTreeNode("(无 schema)"));
                treeModel.reload(connNode);
            }
        } catch (SQLException e) {
            log.error("加载连接 '{}' 失败", name, e);
            connNode.add(new DefaultMutableTreeNode("加载失败: " + e.getMessage()));
            treeModel.reload(connNode);
        }
    }

    /** Bulk-load table/view comments into cache for a single schema. */
    private void loadTableComments(Connection conn, String connName, String dbProduct, String schema) {
        MetadataCache cache = MetadataCache.getInstance();
        boolean isOracleLike = dbProduct.contains("oracle") || dbProduct.contains("oceanbase");
        String sql;
        if (isOracleLike) {
            sql = "SELECT table_name, comments FROM all_tab_comments WHERE owner = ?";
        } else if (dbProduct.contains("mysql") || dbProduct.contains("mariadb")) {
            sql = "SELECT table_name, table_comment FROM information_schema.tables WHERE table_schema = ?";
        } else {
            sql = "SELECT tablename, obj_description((schemaname||'.'||tablename)::regclass, 'pg_class') FROM pg_catalog.pg_tables WHERE schemaname = ?" +
                  " UNION SELECT viewname, obj_description((schemaname||'.'||viewname)::regclass, 'pg_class') FROM pg_catalog.pg_views WHERE schemaname = ?";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            if (!isOracleLike && !dbProduct.contains("mysql") && !dbProduct.contains("mariadb")) {
                ps.setString(2, schema);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String comment = rs.getString(2);
                    if (name != null && comment != null && !comment.isEmpty()) {
                        cache.putTableComment(connName, schema, name, comment);
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("loadTableComments {} {} failed: {}", connName, schema, e.getMessage());
        }
    }

    /** If table comments are missing from cache for any TABLE/VIEW, connect and load them. */
    private void ensureTableCommentsLoaded(String connName, String dbProduct, List<String> schemas, ConnectionInfo connInfo) {
        MetadataCache cache = MetadataCache.getInstance();
        boolean needsLoad = false;
        outer:
        for (String schema : schemas) {
            var byType = cache.getObjectNamesByType(connName, schema);
            if (byType == null) continue;
            List<String> tables = byType.get("TABLE");
            if (tables != null && !tables.isEmpty()
                && cache.getTableComment(connName, schema, tables.get(0)) == null) {
                needsLoad = true;
                break outer;
            }
        }
        if (!needsLoad) return;
        try {
            if (!cm.isConnected(connName)) cm.connect(connInfo);
            try (Connection conn = cm.getConnection(connName)) {
                for (String schema : schemas) {
                    loadTableComments(conn, connName, dbProduct, schema);
                }
            }
        } catch (Exception e) {
            log.debug("ensureTableCommentsLoaded failed: {}", e.getMessage());
        }
    }

    private void rebuildConnectionNode(DefaultMutableTreeNode connNode) {
        ConnHolder h = (ConnHolder) connNode.getUserObject();
        String name = h.info.getName();
        java.util.List<String> all = connFullSchemas.get(name);
        if (all == null) return;
        java.util.Set<String> hidden = connHiddenSchemas.getOrDefault(name, java.util.Collections.emptySet());
        String dbProduct = h.dbType;
        if (dbProduct == null) {
            MetadataCache m = MetadataCache.getInstance();
            if (m.hasMetadata(name)) dbProduct = m.getDbProduct(name);
        }
        java.util.List<ObjectType> types = detectTypes(dbProduct);
        MetadataCache cache = MetadataCache.getInstance();
        connNode.removeAllChildren();
        for (String schema : all) {
            if (hidden.contains(schema)) continue;
            DefaultMutableTreeNode schemaNode = new DefaultMutableTreeNode(schema);
            connNode.add(schemaNode);
            for (ObjectType ot : types) {
                if ("SCHEMA".equals(ot.typeCode)) continue;
                java.util.List<String> objs = cache.getObjects(name, schema, ot.typeCode);
                if (objs != null && !objs.isEmpty()) {
                    DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(ot.label);
                    schemaNode.add(catNode);
                    for (String obj : objs) {
                        DefaultMutableTreeNode objNode = new DefaultMutableTreeNode(obj);
                        if (ot.expandable) objNode.add(new DefaultMutableTreeNode(""));
                        catNode.add(objNode);
                    }
                }
            }
        }
        treeModel.reload(connNode);
        TreePath connPath = new TreePath(connNode.getPath());
        tree.expandPath(connPath);
        // Auto-expand default schema if configured
        String defSchema = h.info.getSchema();
        if (defSchema != null && !defSchema.isEmpty()) {
            for (int i = 0; i < connNode.getChildCount(); i++) {
                DefaultMutableTreeNode sn = (DefaultMutableTreeNode) connNode.getChildAt(i);
                if (defSchema.equals(sn.getUserObject().toString())) {
                    tree.expandPath(connPath.pathByAddingChild(sn));
                    break;
                }
            }
        }
    }

    // ── Schema collection ──

    private java.util.Set<String> collectSchemas(Connection conn, boolean isOracleLike) {
        java.util.Set<String> schemas = new java.util.LinkedHashSet<>();
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                String s = rs.getString("TABLE_SCHEM");
                if (s == null) continue;
                String l = s.toLowerCase();
                if (l.startsWith("information_schema") || l.startsWith("pg_")
                    || "pg_catalog".equals(l) || "pg_toast".equals(l)
                    || "sys".equals(l) || "system".equals(l)
                    || "oceanbase".equals(l) || "mysql".equals(l)) continue;
                schemas.add(s);
            }
        } catch (SQLException e) { log.debug("getSchemas failed: {}", e.getMessage()); }

        if (schemas.isEmpty()) {
            try {
                String sql = isOracleLike
                    ? "SELECT DISTINCT owner FROM all_objects WHERE owner NOT IN ('SYS','SYSTEM','PUBLIC','OCEANBASE','MYSQL') ORDER BY owner"
                    : "SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_schema NOT IN ('information_schema','pg_catalog','pg_toast') ORDER BY table_schema";
                try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) { String s = rs.getString(1); if (s != null) schemas.add(s); }
                }
            } catch (SQLException ex) { log.debug("schema fallback failed: {}", ex.getMessage()); }
        }
        return schemas;
    }

    // ── Object queries ──

    private List<String> queryObjects(Connection conn, ObjectType type, String schema) {
        // FIXED_LIST type: return predefined values directly
        if (type.fixedValues != null) {
            return new ArrayList<>(type.fixedValues);
        }
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(type.querySql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) names.add(rs.getString(1)); }
        } catch (SQLException e) { log.debug("query {} {} failed: {}", type.label, schema, e.getMessage()); }
        java.util.Collections.sort(names);
        return names;
    }

    private List<ObjectType> detectTypes(String dbProduct) {
        // Check metadata config first
        if (configManager != null) {
            String key = dbProductToKey(dbProduct);
            for (DbMetadataConfig cfg : configManager.loadMetadataConfigs()) {
                if (cfg.getDbTypeKey().equals(key) && cfg.isEnabled()) {
                    return cfg.getTypes().stream()
                        .map(td -> {
                            if ("FIXED_LIST".equals(td.getQueryType())) {
                                return new ObjectType(td.getLabel(), td.getTypeCode(),
                                    new ArrayList<>(td.getFixedValues() != null ? td.getFixedValues() : List.of()),
                                    td.isExpandable());
                            }
                            return new ObjectType(td.getLabel(), td.getTypeCode(),
                                td.getQuerySql(), td.isExpandable());
                        })
                        .collect(Collectors.toList());
                }
            }
        }
        // Fallback to hardcoded
        if (dbProduct.contains("mysql") || dbProduct.contains("mariadb")) return MYSQL_TYPES;
        if (dbProduct.contains("postgresql") || dbProduct.contains("edb")) return PG_TYPES;
        if (dbProduct.contains("oceanbase")) return OB_ORACLE_TYPES;
        return ORACLE_TYPES;
    }

    private static String dbProductToKey(String dbProduct) {
        if (dbProduct.contains("mysql") || dbProduct.contains("mariadb")) return "mysql";
        if (dbProduct.contains("postgresql") || dbProduct.contains("edb")) return "postgresql";
        if (dbProduct.contains("oceanbase")) return "oceanbase";
        return "oracle";
    }

    // ── Expand package ──

    private void expandPackage(String connName, String schema, String packageName) {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;
        DefaultMutableTreeNode pkgNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        pkgNode.removeAllChildren();
        String sql = "SELECT OBJECT_NAME, PROCEDURE_NAME, OBJECT_TYPE FROM ALL_PROCEDURES WHERE OWNER = ? AND OBJECT_NAME = ? ORDER BY PROCEDURE_NAME";
        try (Connection conn = cm.getConnection(connName); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, packageName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { String p = rs.getString("PROCEDURE_NAME"); if (p != null) pkgNode.add(new DefaultMutableTreeNode(p + " (过程)")); }
            }
        } catch (SQLException e) { log.error("展开包失败: {}", e.getMessage()); }
        treeModel.reload(pkgNode);
        tree.expandPath(path);
    }

    // ── Double-click handler ──

    private void handleDoubleClick(java.awt.event.MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node == null || node.isRoot()) return;

        int level = node.getLevel();
        if (level == 4) {
            String typeLabel = getNodePath(node, 3);
            String typeCode = LABEL_TO_CODE.getOrDefault(typeLabel, typeLabel);
            if ("PROCEDURE".equals(typeCode) || "FUNCTION".equals(typeCode) || "PACKAGE".equals(typeCode)) {
                String connName = getConnName(node);
                String schema = getNodePath(node, 2);
                String objName = node.getUserObject().toString();
                callback.onOpenSourceObject(connName, schema, typeCode, objName);
            }
        } else if (level == 5) {
            // Check if parent is a PACKAGE
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
            if (parentNode != null && parentNode.getLevel() == 4) {
                String typeLabel = getNodePath(parentNode, 3);
                String typeCode = LABEL_TO_CODE.getOrDefault(typeLabel, typeLabel);
                if ("PACKAGE".equals(typeCode)) {
                    String connName = getConnName(node);
                    String schema = getNodePath(node, 2);
                    String pkgName = parentNode.getUserObject().toString();
                    callback.onOpenSourceObject(connName, schema, "PACKAGE", pkgName);
                }
            }
        }
    }

    // ── Context menu ──

    private void showPopup(java.awt.event.MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;
        tree.setSelectionPath(path);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node == null || node.isRoot()) return;

        int level = node.getLevel();
        JPopupMenu menu = new JPopupMenu();

        if (level == 1) {
            // Connection node
            ConnHolder h = (ConnHolder) node.getUserObject();
            String cname = h.info.getName();
            boolean connected = cm.isConnected(cname);

            if (!connected) {
                menu.add(menuItem("连接", "connect", () -> { loadConnection(node); tree.expandPath(path); }));
            } else {
                menu.add(menuItem("断开", "connect", () -> { cm.disconnect(cname); loadAll(cm, getConnInfos()); }));
            }
            menu.addSeparator();
            menu.add(menuItem("属性", "info", () -> callback.onConnectionProperties(cname)));
            menu.add(menuItem("刷新", "refresh", () -> {
                MetadataCache.getInstance().clearConnection(cname);
                loadConnection(node);
            }));
            menu.addSeparator();
            menu.add(menuItem("新建 SQL 编辑器", "new", () -> callback.onNewSqlEditor(cname)));
        } else if (level == 4) {
            String connName = getConnName(node);
            String schema = getNodePath(node, 2);
            String typeLabel = getNodePath(node, 3);
            String objName = node.getUserObject().toString();
            String typeCode = LABEL_TO_CODE.getOrDefault(typeLabel, typeLabel);
            boolean pkg = false;
            for (var t : ORACLE_TYPES) { if (t.label.equals(typeLabel) && t.expandable) { pkg = true; break; } }

            if ("TABLE".equals(typeCode) || "VIEW".equals(typeCode)) {
                menu.add(menuItem("生成 SELECT", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "SELECT")));
                menu.add(menuItem("生成 INSERT", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "INSERT")));
                menu.add(menuItem("生成 UPDATE", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "UPDATE")));
                menu.add(menuItem("生成 DELETE", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "DELETE")));
                menu.addSeparator();
                menu.add(menuItem("数据预览 (前100行)", "search", () -> callback.onObjectAction(connName, schema, typeCode, objName, "PREVIEW")));
                menu.addSeparator();
            }
            menu.add(menuItem("查看 DDL", "database-search", () -> callback.onObjectAction(connName, schema, typeCode, objName, "DDL")));
            menu.addSeparator();
            menu.add(menuItem("复制表名", "copy", () -> copyToClipboard(objName)));
            if (pkg) {
                menu.addSeparator();
                menu.add(menuItem("展开包 (过程/函数)", "skip-forward", () -> expandPackage(connName, schema, objName)));
            }
        } else if (level == 5 && node.getUserObject() instanceof ColumnInfo colInfo) {
            menu.add(menuItem("复制列名", "copy", () -> copyToClipboard(colInfo.name)));
        }

        if (menu.getComponentCount() > 0) menu.show(tree, e.getX(), e.getY());
    }

    // ── Helpers ──

    private static JMenuItem menuItem(String text, String icon, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (icon != null) item.setIcon(IconUtil.menuIcon(icon));
        item.addActionListener(ev -> action.run());
        return item;
    }

    private static JMenuItem menuItem(String text, Runnable action) {
        return menuItem(text, null, action);
    }

    private String getNodePath(DefaultMutableTreeNode node, int depth) {
        Object[] objs = node.getUserObjectPath();
        if (depth < objs.length && objs[depth] != null) return objs[depth].toString();
        return "";
    }

    private List<ConnectionInfo> getConnInfos() {
        List<ConnectionInfo> list = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) root.getChildAt(i);
            if (n.getUserObject() instanceof ConnHolder) list.add(((ConnHolder) n.getUserObject()).info);
        }
        return list;
    }

    private String getSelectedConnName() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node == null) return null;
        String cn = getConnName(node);
        return cn.isEmpty() ? null : cn;
    }

    private void showProperties() {
        TreePath path = tree.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            while (node != null) {
                if (node.getUserObject() instanceof ConnHolder) {
                    callback.onConnectionProperties(((ConnHolder) node.getUserObject()).info.getName());
                    return;
                }
                node = (DefaultMutableTreeNode) node.getParent();
            }
        }
        callback.onOpenConnections();
    }
}
