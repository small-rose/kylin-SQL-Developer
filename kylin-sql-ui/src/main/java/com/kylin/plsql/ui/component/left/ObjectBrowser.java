package com.kylin.plsql.ui.component.left;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.pojo.MetadataLoadResult;
import com.kylin.plsql.core.pojo.ObjectType;
import com.kylin.plsql.core.service.MetadataLoadService;
import com.kylin.plsql.core.service.SchemaService;
import com.kylin.plsql.core.service.ServiceFactory;
import com.kylin.plsql.ui.component.common.IconUtil;
import com.kylin.plsql.ui.component.left.exts.ObjectBrowserDelegate;
import com.kylin.plsql.ui.component.left.exts.ObjectBrowserExpandListener;
import com.kylin.plsql.ui.component.left.exts.ObjectBrowserMouseAdapter;
import com.kylin.plsql.ui.component.left.exts.ObjectBrowserTreeCellRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Database object browser tree with schema/table/view/procedure navigation. */
public class ObjectBrowser extends JPanel implements ObjectBrowserDelegate {
    private static final Logger log = LoggerFactory.getLogger(ObjectBrowser.class);

    private static final Toolkit TK = Toolkit.getDefaultToolkit();

    // ── Fields ──

    private final ObjectBrowserCallback callback;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode root;
    private final JButton refreshBtn;
    private final java.util.Map<String, java.util.List<String>> connFullSchemas = new HashMap<>();
    private final java.util.Map<String, java.util.Set<String>> connHiddenSchemas = new HashMap<>();
    private final ThemeManager theme = ThemeManager.getInstance();

    private ConnectionManager cm;
    private String currentType;
    private ServiceFactory serviceFactory;
    private ConfigManager configManager;
    private MetadataLoadService metadataLoadService;

    public void setConfigManager(ConfigManager cm) { this.configManager = cm; }

    public void setServiceFactory(ServiceFactory sf) { this.serviceFactory = sf; }

    // ── Toolbar icons ──

    private static final Icon ICON_NEW  = IconUtil.makeIcon("+", new Color(0x5CB85C));
    private static final Icon ICON_PROP = IconUtil.makeIcon("⚙", new Color(0x337AB7));
    private static final Icon ICON_REFR = IconUtil.makeIcon("↻", new Color(0xF0AD4E));
    private static final Icon ICON_SQL  = IconUtil.makeIcon("▶", new Color(0x5CB85C));

    // ── Constructor ──

    public ObjectBrowser(ObjectBrowserCallback callback) {
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

        tree.setCellRenderer(new ObjectBrowserTreeCellRenderer(
            connFullSchemas, connHiddenSchemas, theme, node -> getNodeLabel(node, 3)));

        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node == null || node.isRoot()) return;
            if (node.getLevel() == 4) {
                copyToClipboard(node.getUserObject().toString());
            } else if (node.getLevel() == 5 && node.getUserObject() instanceof ColumnInfo ci) {
                copyToClipboard(ci.name);
            }
        });

        tree.addTreeWillExpandListener(new ObjectBrowserExpandListener(callback, treeModel, cm, this));

        tree.addMouseListener(new ObjectBrowserMouseAdapter(
            e -> {
                if (e.getClickCount() == 2) { handleDoubleClick(e); return; }
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
            },
            e -> showPopup(e)
        ));

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    // ── Connection node holder ──

    public String getConnName(DefaultMutableTreeNode node) {
        while (node != null) {
            if (node.getUserObject() instanceof ConnHolder) return ((ConnHolder) node.getUserObject()).info.getName();
            node = (DefaultMutableTreeNode) node.getParent();
        }
        return "";
    }

    public String getNodeLabel(DefaultMutableTreeNode node, int depth) {
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
        label.setFont(FontManager.getInstance().resolve("font.left"));
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

    public void loadColumns(DefaultMutableTreeNode tblNode) {
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

        // ── Cache miss: populate via SchemaService, then read back ──
        if (serviceFactory != null) {
            String dbProduct = getConnDbProduct(tblNode);
            SchemaService ss = serviceFactory.getSchemaService(dbProduct);
            if (ss != null) {
                ss.getColumns(connName, schema, tableName); // populates cache with comments
            }
        }
        List<MetadataCache.CachedColumn> afterCache = cache.getColumns(connName, schema, tableName);
        if (afterCache != null) {
            for (var cc : afterCache) {
                String sz = cc.size > 0 ? String.valueOf(cc.size) : "";
                tblNode.add(new DefaultMutableTreeNode(new ColumnInfo(cc.name, cc.type, sz, cc.comment)));
            }
            treeModel.reload(tblNode);
            return;
        }
        log.warn("无法加载 {} 的列信息", tableName);
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
            log.warn("[DEBUG] loadAll: {} expanded={} hasCache={}", info.getName(), expanded,
            MetadataCache.getInstance().hasMetadata(info.getName()));
            DefaultMutableTreeNode connNode = new DefaultMutableTreeNode(new ConnHolder(info, expanded));
            root.add(connNode);
            if (expanded) {
                loadConnection(connNode);
            } else if (MetadataCache.getInstance().hasMetadata(info.getName())) {
                MetadataLoadResult result = getMetadataLoadService().load(info.getName(), info, pct -> {});
                if (result != null) {
                    ConnHolder ch = (ConnHolder) connNode.getUserObject();
                    ch.dbType = result.dbProduct;
                    connFullSchemas.put(info.getName(), new ArrayList<>(result.schemas));
                    connHiddenSchemas.put(info.getName(), new java.util.LinkedHashSet<>(result.hiddenSchemas.getOrDefault(info.getName(), java.util.Collections.emptySet())));
                }
                rebuildConnectionTreeFromCache(connNode);
            } else {
                connNode.add(new DefaultMutableTreeNode("加载中..."));
                log.warn("[DEBUG] loadAll: 添加了 加载中... 到 {}", info.getName());
                loadConnectionAsync(connNode);
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

        String typeLabel = typeLabelForConn(connName, objectType);

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
            protected Void doInBackground() throws Exception {
                publish(0);
                MetadataCache.getInstance().clearConnection(cname);
                MetadataLoadResult result = getMetadataLoadService().load(cname, h.info, pct -> publish(pct));
                publish(100);
                if (result != null) {
                    h.dbType = result.dbProduct;
                    connFullSchemas.put(cname, new ArrayList<>(result.schemas));
                    java.util.Set<String> hidden = result.hiddenSchemas.getOrDefault(cname, java.util.Collections.emptySet());
                    connHiddenSchemas.put(cname, new java.util.LinkedHashSet<>(hidden));
                }
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
                        if (MetadataCache.getInstance().hasMetadata(cname)) {
                            callback.onSyncComplete(cname);
                        }
                    } else {
                        showRefreshError("刷新失败：未获取到元数据");
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    showRefreshError(cause.getMessage());
                } finally {
                    refreshBtn.setEnabled(true);
                }
            }

            private void showRefreshError(String msg) {
                finalConnNode.removeAllChildren();
                finalConnNode.add(new DefaultMutableTreeNode("连接失败: " + msg));
                treeModel.reload(finalConnNode);
                callback.onSyncError(cname, msg);
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
        allCb.setFont(FontManager.getInstance().resolve("font.left.title"));
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
            cb.setFont(FontManager.getInstance().resolve("font.left"));
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

    // ── Load connection schema tree (cache-aware) ──

    private MetadataLoadService getMetadataLoadService() {
        if (metadataLoadService == null) {
            metadataLoadService = new MetadataLoadService(cm, serviceFactory, configManager);
        }
        return metadataLoadService;
    }

    private boolean loadConnectionData(DefaultMutableTreeNode connNode) {
        return loadConnectionData(connNode, pct -> {});
    }

    public boolean loadConnectionData(DefaultMutableTreeNode connNode, IntConsumer progress) {
        ConnHolder h = (ConnHolder) connNode.getUserObject();
        String name = h.info.getName();
        MetadataCache cache = MetadataCache.getInstance();

        MetadataLoadResult result = getMetadataLoadService().load(name, h.info, progress);
        if (result == null) return false;

        h.dbType = result.dbProduct;
        connFullSchemas.put(name, new ArrayList<>(result.schemas));
        java.util.Set<String> hidden = result.hiddenSchemas.getOrDefault(name, java.util.Collections.emptySet());
        connHiddenSchemas.put(name, new java.util.LinkedHashSet<>(hidden));

        if (cache.hasMetadata(name)) {
            ensureTableCommentsLoaded(name, result.dbProduct, result.schemas, h.info);
        }
        return true;
    }

    /** Synchronous load (backward compat): data + tree ops on caller thread. */
    private void loadConnection(DefaultMutableTreeNode connNode) {
        connNode.removeAllChildren();
        if (!loadConnectionData(connNode)) {
            ConnHolder h = (ConnHolder) connNode.getUserObject();
            connNode.add(new DefaultMutableTreeNode("加载失败"));
            treeModel.reload(connNode);
            javax.swing.SwingUtilities.invokeLater(() ->
                callback.onSyncError(h.info.getName(), "连接失败，请检查配置"));
            return;
        }
        rebuildConnectionTreeFromCache(connNode);
    }

    public void rebuildConnectionTreeFromCache(DefaultMutableTreeNode connNode) {
        ConnHolder h = (ConnHolder) connNode.getUserObject();
        String name = h.info.getName();
        log.warn("[DEBUG] rebuildConnectionTreeFromCache: {}", name);
        MetadataCache cache = MetadataCache.getInstance();
        if (!cache.hasMetadata(name)) {
            log.warn("[DEBUG] rebuildConnectionTreeFromCache: 无缓存，显示 加载失败");
            connNode.removeAllChildren();
            connNode.add(new DefaultMutableTreeNode("加载失败"));
            treeModel.reload(connNode);
            return;
        }
        List<String> schemas = connFullSchemas.get(name);
        log.warn("[DEBUG] rebuildConnectionTreeFromCache: schemas={}", schemas);
        if (schemas == null || schemas.isEmpty()) {
            log.warn("[DEBUG] rebuildConnectionTreeFromCache: schemas为空，显示 (无 schema)");
            connNode.removeAllChildren();
            connNode.add(new DefaultMutableTreeNode("(无 schema)"));
            treeModel.reload(connNode);
            return;
        }
        rebuildConnectionNode(connNode);
    }

    /** Async load: JDBC in background, tree ops on EDT. */
    private void loadConnectionAsync(DefaultMutableTreeNode connNode) {
        loadConnectionAsync(connNode, null);
    }

    private void loadConnectionAsync(DefaultMutableTreeNode connNode, Runnable onDone) {
        connNode.removeAllChildren();
        connNode.add(new DefaultMutableTreeNode("加载中..."));
        treeModel.reload(connNode);
        DefaultMutableTreeNode nodeRef = connNode;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                loadConnectionData(nodeRef);
                return null;
            }
            @Override protected void done() {
                rebuildConnectionTreeFromCache(nodeRef);
                if (onDone != null) onDone.run();
            }
        }.execute();
    }

    /** If table comments are missing from cache for any TABLE/VIEW, connect and load them. */
    private void ensureTableCommentsLoaded(String connName, String dbProduct, List<String> schemas, ConnectionInfo connInfo) {
        SchemaService ss = serviceFactory != null ? serviceFactory.getSchemaService(dbProduct) : null;
        if (ss == null) return;
        if (!cm.isConnected(connName)) {
            try { cm.connect(connInfo); } catch (Exception e) { return; }
        }
        ss.preloadTableComments(connName, schemas);
    }

    private void rebuildConnectionNode(DefaultMutableTreeNode connNode) {
        ConnHolder h = (ConnHolder) connNode.getUserObject();
        String name = h.info.getName();
        java.util.List<String> all = connFullSchemas.get(name);
        log.warn("[DEBUG] rebuildConnectionNode: {} schemas={}", name, all);
        if (all == null) { log.warn("[DEBUG] rebuildConnectionNode: all == null, 直接 return"); return; }
        java.util.Set<String> hidden = connHiddenSchemas.getOrDefault(name, java.util.Collections.emptySet());
        String dbProduct = h.dbType;
        if (dbProduct == null) {
            dbProduct = MetadataCache.getInstance().getDbProduct(name);
            if (dbProduct == null) dbProduct = "oracle";
            h.dbType = dbProduct;
        }
        log.warn("[DEBUG] rebuildConnectionNode: dbProduct={}", dbProduct);
        java.util.List<ObjectType> types = getMetadataLoadService().detectTypes(dbProduct);
        MetadataCache cache = MetadataCache.getInstance();
        log.warn("[DEBUG] rebuildConnectionNode: 移除所有子节点...");
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
        log.warn("[DEBUG] rebuildConnectionNode: 调用 treeModel.reload(connNode)");
        treeModel.reload(connNode);
        TreePath connPath = new TreePath(connNode.getPath());
        log.warn("[DEBUG] rebuildConnectionNode: 调用 tree.expandPath");
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

    // ── SchemaService helpers ──

    private SchemaService getSchemaService(String connName) {
        if (serviceFactory == null) return null;
        String dbProduct = MetadataCache.getInstance().getDbProduct(connName);
        return dbProduct != null ? serviceFactory.getSchemaService(dbProduct) : null;
    }

    private String typeCodeForConn(String connName, String label) {
        SchemaService ss = getSchemaService(connName);
        return ss != null ? ss.getTypeCode(label) : label;
    }

    private boolean isExpandableForConn(String connName, String typeCode) {
        SchemaService ss = getSchemaService(connName);
        return ss != null && ss.isExpandable(typeCode);
    }

    private String typeLabelForConn(String connName, String typeCode) {
        if ("PACKAGE_BODY".equals(typeCode)) return "包";
        SchemaService ss = getSchemaService(connName);
        return ss != null ? ss.getTypeLabel(typeCode) : typeCode;
    }

    // ── Expand package ──

    private void expandPackage(String connName, String schema, String packageName) {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;
        DefaultMutableTreeNode pkgNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        pkgNode.removeAllChildren();
        DefaultMutableTreeNode nodeRef = pkgNode;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // 1) ALL_PROCEDURES 标准路径（Oracle / OB 4.x+）
                String sql = "SELECT OBJECT_NAME, PROCEDURE_NAME, OBJECT_TYPE FROM ALL_PROCEDURES WHERE OWNER = ? AND OBJECT_NAME = ? ORDER BY PROCEDURE_NAME";
                try (Connection conn = cm.getConnection(connName); PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, schema); ps.setString(2, packageName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String sub = rs.getString("PROCEDURE_NAME");
                            if (sub != null) nodeRef.add(new DefaultMutableTreeNode(sub + " (过程)"));
                        }
                        if (nodeRef.getChildCount() > 0) return null; // ALL_PROCEDURES 有数据，直接返回
                    }
                } catch (SQLException e) { log.error("展开包失败: {}", e.getMessage()); }

                // 2) ALL_PROCEDURES 无结果，且为 OceanBase → 从 ALL_SOURCE 解析子程序
                try (Connection conn = cm.getConnection(connName)) {
                    String product = conn.getMetaData().getDatabaseProductName().toLowerCase();
                    if (!product.contains("oceanbase")) return null; // 非 OB 不再降级

                    String q = "SELECT text FROM all_source WHERE owner=? AND name=? AND type='PACKAGE' ORDER BY line";
                    try (PreparedStatement ps = conn.prepareStatement(q)) {
                        ps.setString(1, schema);
                        ps.setString(2, packageName);
                        try (ResultSet rs = ps.executeQuery()) {
                            StringBuilder src = new StringBuilder();
                            while (rs.next()) src.append(rs.getString("text"));
                            if (src.length() == 0) return null;

                            Pattern p = Pattern.compile(
                                "^\\s*(FUNCTION|PROCEDURE)\\s+(\\w+)\\b",
                                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
                            Matcher m = p.matcher(src);
                            while (m.find()) {
                                String subName = m.group(2);
                                String kind = m.group(1).equalsIgnoreCase("FUNCTION") ? "函数" : "过程";
                                nodeRef.add(new DefaultMutableTreeNode(subName + " (" + kind + ")"));
                            }
                        }
                    }
                } catch (SQLException e) {
                    log.warn("ALL_SOURCE 降级展开包也失败: {}", e.getMessage());
                }
                return null;
            }
            @Override
            protected void done() {
                treeModel.reload(nodeRef);
                tree.expandPath(path);
            }
        }.execute();
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
            String connName = getConnName(node);
            String typeCode = typeCodeForConn(connName, typeLabel);
            if ("PROCEDURE".equals(typeCode) || "FUNCTION".equals(typeCode) || "PACKAGE".equals(typeCode)) {
                String schema = getNodePath(node, 2);
                String objName = node.getUserObject().toString();
                callback.onOpenSourceObject(connName, schema, typeCode, objName);
            }
        } else if (level == 5) {
            // Check if parent is a PACKAGE
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
            if (parentNode != null && parentNode.getLevel() == 4) {
                String typeLabel = getNodePath(parentNode, 3);
                String connName = getConnName(node);
                String typeCode = typeCodeForConn(connName, typeLabel);
                if ("PACKAGE".equals(typeCode)) {
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
                menu.add(menuItem("连接", "connect", () -> loadConnectionAsync(node, () -> tree.expandPath(path))));
            } else {
                menu.add(menuItem("断开", "connect", () -> {
                    cm.disconnect(cname);
                    MetadataCache.getInstance().clearConnection(cname);
                    node.removeAllChildren();
                    node.add(new DefaultMutableTreeNode("(未连接)"));
                    treeModel.reload(node);
                }));
            }
            menu.addSeparator();
            menu.add(menuItem("属性", "info", () -> callback.onConnectionProperties(cname)));
            menu.add(menuItem("刷新", "refresh", () -> refreshSelected()));
            menu.addSeparator();
            menu.add(menuItem("新建 SQL 编辑器", "new", () -> callback.onNewSqlEditor(cname)));
        } else if (level == 2) {
            // Schema node
            String connName = getConnName(node);
            String schema = node.getUserObject().toString();
            menu.add(menuItem("新建 SQL 编辑器", "new", () -> callback.onNewSqlEditor(connName, schema)));
            menu.add(menuItem("执行SQL脚本", "execute", () -> callback.onExecuteScript(connName, schema)));
            menu.add(menuItem("刷新", "refresh", () -> refreshAll()));
            menu.addSeparator();
            menu.add(menuItem("复制 Schema 名", "copy", () -> copyToClipboard(schema)));
        } else if (level == 4) {
            String connName = getConnName(node);
            String schema = getNodePath(node, 2);
            String typeLabel = getNodePath(node, 3);
            String objName = node.getUserObject().toString();
            String typeCode = typeCodeForConn(connName, typeLabel);
            boolean pkg = isExpandableForConn(connName, typeCode);

            if ("TABLE".equals(typeCode) || "VIEW".equals(typeCode)) {
                menu.add(menuItem("生成 SELECT", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "SELECT")));
                menu.add(menuItem("生成 SELECT（新标签页）", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "SELECT_NEWTAB")));
                menu.add(menuItem("生成 INSERT", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "INSERT")));
                menu.add(menuItem("生成 INSERT（新标签页）", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "INSERT_NEWTAB")));
                menu.add(menuItem("生成 UPDATE", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "UPDATE")));
                menu.add(menuItem("生成 UPDATE（新标签页）", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "UPDATE_NEWTAB")));
                menu.add(menuItem("生成 DELETE", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "DELETE")));
                menu.add(menuItem("生成 DELETE（新标签页）", null, () -> callback.onObjectAction(connName, schema, typeCode, objName, "DELETE_NEWTAB")));
                menu.addSeparator();
                menu.add(menuItem("数据预览 (前100行)", "search", () -> callback.onObjectAction(connName, schema, typeCode, objName, "PREVIEW")));
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

    public String getNodePath(DefaultMutableTreeNode node, int depth) {
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

    // ── Tree expansion state persistence ──

    /** Collect all expanded node paths as portable strings: "connName/schema/[category/]object". */
    public java.util.List<String> saveExpandedPaths() {
        java.util.List<String> paths = new java.util.ArrayList<>();
        collectExpanded(root, new StringBuilder(), paths);
        return paths;
    }

    private void collectExpanded(DefaultMutableTreeNode node, StringBuilder prefix, java.util.List<String> out) {
        String label;
        Object userObj = node.getUserObject();
        if (userObj instanceof ConnHolder) {
            label = ((ConnHolder) userObj).info.getName();
        } else {
            label = userObj != null ? userObj.toString() : "";
        }
        int len = prefix.length();
        if (len > 0) prefix.append('/');
        prefix.append(label);
        TreePath path = new TreePath(node.getPath());
        if (len > 0 && tree.isExpanded(path)) {
            out.add(prefix.toString());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectExpanded((DefaultMutableTreeNode) node.getChildAt(i), prefix, out);
        }
        prefix.setLength(len);
    }

    /** Restore expanded state from a previously saved path list. */
    public void restoreExpandedPaths(java.util.List<String> saved) {
        if (saved == null || saved.isEmpty()) return;
        for (String pathStr : saved) {
            String[] parts = pathStr.split("/", -1);
            DefaultMutableTreeNode node = root;
            boolean found = true;
            for (String part : parts) {
                if (part.isEmpty()) continue;
                DefaultMutableTreeNode child = findChildByLabel(node, part);
                if (child == null) { found = false; break; }
                node = child;
            }
            if (found) {
                tree.expandPath(new TreePath(node.getPath()));
            }
        }
    }

    private static DefaultMutableTreeNode findChildByLabel(DefaultMutableTreeNode parent, String label) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object uo = child.getUserObject();
            String cl = uo instanceof ConnHolder ? ((ConnHolder) uo).info.getName()
                       : uo != null ? uo.toString() : "";
            if (label.equals(cl)) return child;
        }
        return null;
    }

    // ── Hidden schemas persistence ──

    /** Return hidden schemas as connName → list of schema names. */
    public java.util.Map<String, java.util.List<String>> getHiddenSchemas() {
        java.util.Map<String, java.util.List<String>> map = new java.util.LinkedHashMap<>();
        for (var e : connHiddenSchemas.entrySet()) {
            map.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
        }
        return map;
    }

    /** Restore hidden schemas from a previously saved map. */
    public void setHiddenSchemas(java.util.Map<String, java.util.List<String>> saved) {
        if (saved == null) return;
        connHiddenSchemas.clear();
        for (var e : saved.entrySet()) {
            connHiddenSchemas.put(e.getKey(), new java.util.LinkedHashSet<>(e.getValue()));
        }
    }

    /** Return full schema list per connection. */
    public java.util.Map<String, java.util.List<String>> getConnFullSchemas() {
        return connFullSchemas;
    }
}
