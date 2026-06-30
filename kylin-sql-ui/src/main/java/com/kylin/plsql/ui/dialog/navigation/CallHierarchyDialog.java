package com.kylin.plsql.ui.dialog.navigation;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.parser.PlSqlCallHierarchy;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

/** PL/SQL call hierarchy tree dialog showing procedure/function call relationships */
public class CallHierarchyDialog extends JDialog {

    @FunctionalInterface
    public interface NavigateCallback {
        void navigate(String name, int line);
    }

    public CallHierarchyDialog(Window owner, PlSqlCallHierarchy.CallNode root,
                                NavigateCallback callback) {
        super(owner, "\u8C03\u7528\u5C42\u6B21", ModalityType.MODELESS);
        setSize(500, 400);
        setLocationRelativeTo(owner);

        var treeNode = buildTreeNode(root);
        var treeModel = new DefaultTreeModel(treeNode);
        var tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(22);
        tree.addTreeSelectionListener(e -> {
            var node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof PlSqlCallHierarchy.CallNode cn) {
                if (cn.line > 0 && callback != null) {
                    callback.navigate(cn.name, cn.line);
                }
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);

        var closeBtn = new JButton("\u5173\u95ED");
        closeBtn.addActionListener(e -> dispose());
        var panel = new JPanel();
        panel.add(closeBtn);
        add(panel, BorderLayout.SOUTH);

        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        applyTheme();
    }

    private void applyTheme() {
        ThemeManager tm = ThemeManager.getInstance();
        getContentPane().setBackground(tm.resolve("bg.main"));
    }

    private DefaultMutableTreeNode buildTreeNode(PlSqlCallHierarchy.CallNode node) {
        var treeNode = new DefaultMutableTreeNode(node);
        for (var child : node.callees) {
            treeNode.add(buildTreeNode(child));
        }
        return treeNode;
    }
}
