package com.kylin.plsql.ui.component;

import com.kylin.plsql.core.parser.PlSqlNavigator;

import javax.swing.*;
import javax.swing.tree.*;
import java.util.List;

public class OutlinePanel extends JPanel {
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode root;
    private List<PlSqlNavigator.OutlineEntry> entries;

    @FunctionalInterface
    public interface OutlineItemCallback {
        void onSelect(int line);
    }

    public OutlinePanel(OutlineItemCallback callback) {
        setLayout(new java.awt.BorderLayout());

        root = new DefaultMutableTreeNode("包/过程/函数");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(22);

        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null && !node.isRoot()) {
                Object obj = node.getUserObject();
                if (obj instanceof PlSqlNavigator.OutlineEntry entry) {
                    callback.onSelect(entry.line);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        add(scroll, java.awt.BorderLayout.CENTER);
    }

    public void updateOutline(String plsqlSource) {
        root.removeAllChildren();
        if (plsqlSource == null || plsqlSource.isBlank()) {
            treeModel.reload();
            return;
        }

        entries = PlSqlNavigator.parse(plsqlSource);
        for (var entry : entries) {
            root.add(new DefaultMutableTreeNode(entry));
        }
        treeModel.reload();
        expandAll();
    }

    public void highlightForLine(int caretLine) {
        if (entries == null || entries.isEmpty()) return;
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            if (entry.line <= caretLine) {
                TreePath path = getPathForRow(i + 1);
                if (path != null) {
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                }
                return;
            }
        }
    }

    private TreePath getPathForRow(int row) {
        if (row < 0 || row >= tree.getRowCount()) return null;
        TreePath path = tree.getPathForRow(row);
        if (path != null && path.getPathCount() >= 2) {
            return path;
        }
        return null;
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    public void clear() {
        root.removeAllChildren();
        treeModel.reload();
        entries = null;
    }
}
