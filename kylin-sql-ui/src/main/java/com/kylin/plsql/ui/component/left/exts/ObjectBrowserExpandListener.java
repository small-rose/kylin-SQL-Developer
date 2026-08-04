package com.kylin.plsql.ui.component.left.exts;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.ui.component.left.ObjectBrowserCallback;

/** 树节点展开监听器，根据节点类型（连接/Schema/对象分类）异步加载子节点并展开。 */


import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ObjectBrowserExpandListener implements javax.swing.event.TreeWillExpandListener {
    private final ObjectBrowserCallback callback;
    private final DefaultTreeModel treeModel;
    private final ConnectionManager cm;
    private final ObjectBrowserDelegate delegate;

    public ObjectBrowserExpandListener(
            ObjectBrowserCallback callback,
            DefaultTreeModel treeModel,
            ConnectionManager cm,
            ObjectBrowserDelegate delegate) {
        this.callback = callback;
        this.treeModel = treeModel;
        this.cm = cm;
        this.delegate = delegate;
    }

    @Override
    public void treeWillExpand(javax.swing.event.TreeExpansionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();

        // Level 1: load schemas lazily
        if (node.getUserObject() instanceof com.kylin.plsql.ui.component.left.ConnHolder && node.getChildCount() == 1) {
            DefaultMutableTreeNode first = (DefaultMutableTreeNode) node.getChildAt(0);
            if ("加载中...".equals(first.getUserObject())) {
                DefaultMutableTreeNode nodeRef = node;
                com.kylin.plsql.ui.component.left.ConnHolder h =
                    (com.kylin.plsql.ui.component.left.ConnHolder) node.getUserObject();
                String cname = h.info.getName();
                callback.onSyncProgress(cname, 0);
                new SwingWorker<Void, Integer>() {
                    @Override protected Void doInBackground() {
                        delegate.loadConnectionData(nodeRef, pct -> publish(pct));
                        return null;
                    }
                    @Override protected void process(List<Integer> chunks) {
                        callback.onSyncProgress(cname, chunks.get(chunks.size() - 1));
                    }
                    @Override protected void done() {
                        try {
                            delegate.rebuildConnectionTreeFromCache(nodeRef);
                            boolean hasMeta = MetadataCache.getInstance().hasMetadata(cname);
                            callback.onSyncComplete(cname);
                            if (!hasMeta) {
                                callback.onSyncError(cname, "连接失败，请检查配置");
                            }
                        } catch (Exception ex) {
                            nodeRef.removeAllChildren();
                            nodeRef.add(new DefaultMutableTreeNode("加载失败"));
                            treeModel.reload(nodeRef);
                            callback.onSyncError(cname, "加载异常");
                        }
                    }
                }.execute();
            }
        }

        // Level 4: table/package expand
        if (node.getLevel() == 4 && node.getChildCount() == 1) {
            DefaultMutableTreeNode first = (DefaultMutableTreeNode) node.getChildAt(0);
            if ("".equals(first.getUserObject())) {
                String typeLabel = delegate.getNodeLabel(node, 3);
                DefaultMutableTreeNode nodeRef = node;
                if ("表".equals(typeLabel)) {
                    new SwingWorker<Void, Void>() {
                        @Override protected Void doInBackground() {
                            delegate.loadColumns(nodeRef);
                            return null;
                        }
                        @Override protected void done() {
                            treeModel.reload(nodeRef);
                        }
                    }.execute();
                } else if ("包".equals(typeLabel)) {
                    String connName = delegate.getConnName(node);
                    String schema = delegate.getNodePath(node, 2);
                    String pkgName = node.getUserObject().toString();
                    new SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() {
                            String sql = "SELECT DISTINCT PROCEDURE_NAME FROM ALL_PROCEDURES WHERE OWNER = ? AND OBJECT_NAME = ? AND PROCEDURE_NAME IS NOT NULL ORDER BY PROCEDURE_NAME";
                            try (java.sql.Connection conn = cm.getConnection(connName);
                                 PreparedStatement ps = conn.prepareStatement(sql)) {
                                ps.setString(1, schema); ps.setString(2, pkgName);
                                nodeRef.removeAllChildren();
                                try (ResultSet rs = ps.executeQuery()) {
                                    while (rs.next()) nodeRef.add(new DefaultMutableTreeNode(rs.getString(1)));
                                }
                            } catch (SQLException ex) {
                                // ignore
                            }
                            return null;
                        }
                        @Override
                        protected void done() {
                            treeModel.reload(nodeRef);
                        }
                    }.execute();
                }
            }
        }
    }

    @Override
    public void treeWillCollapse(javax.swing.event.TreeExpansionEvent e) {}
}
