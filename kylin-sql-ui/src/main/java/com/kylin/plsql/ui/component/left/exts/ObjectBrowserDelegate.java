package com.kylin.plsql.ui.component.left.exts;

import javax.swing.tree.DefaultMutableTreeNode;

/** ObjectBrowser 委派接口，供外部监听器（如展开监听器）回调树操作方法。 */

import java.util.function.IntConsumer;

public interface ObjectBrowserDelegate {
    boolean loadConnectionData(DefaultMutableTreeNode node, IntConsumer progress);
    void rebuildConnectionTreeFromCache(DefaultMutableTreeNode node);
    void loadColumns(DefaultMutableTreeNode node);
    String getConnName(DefaultMutableTreeNode node);
    String getNodeLabel(DefaultMutableTreeNode node, int depth);
    String getNodePath(DefaultMutableTreeNode node, int depth);
}
