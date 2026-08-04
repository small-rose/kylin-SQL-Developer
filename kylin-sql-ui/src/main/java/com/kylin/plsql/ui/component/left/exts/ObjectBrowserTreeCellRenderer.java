package com.kylin.plsql.ui.component.left.exts;

import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.ui.component.common.IconUtil;

/** 树节点渲染器，根据节点类型（连接/Schema/表/视图/包等）显示不同图标和字体。 */

import com.kylin.plsql.ui.component.left.ColumnInfo;
import com.kylin.plsql.ui.component.left.ConnHolder;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ObjectBrowserTreeCellRenderer extends DefaultTreeCellRenderer implements TreeCellRenderer {
    private final Map<String, List<String>> connFullSchemas;
    private final Map<String, Set<String>> connHiddenSchemas;
    private final ThemeManager theme;
    private final Function<DefaultMutableTreeNode, String> nodeLabelFn;

    private static final Icon ICON_DB     = IconUtil.makeIcon("DB", new Color(0xFF2183DA, true));
    private static final Icon ICON_SCHEMA = IconUtil.makeIcon("S",  new Color(0x5CB85C));
    private static final Icon ICON_TABLE  = IconUtil.makeIcon("T",  new Color(0x337AB7));
    private static final Icon ICON_VIEW   = IconUtil.makeIcon("V",  new Color(0x1685A9));
    private static final Icon ICON_INDEX  = IconUtil.makeIcon("I",  new Color(0xF0AD4E));
    private static final Icon ICON_SEQ    = IconUtil.makeIcon("N",  new Color(0x8E44AD));
    private static final Icon ICON_FUNC   = IconUtil.makeIcon("F",  new Color(0xff4777));
    private static final Icon ICON_PROC   = IconUtil.makeIcon("P",  new Color(0xD9534F));
    private static final Icon ICON_PKG    = IconUtil.makeIcon("K",  new Color(0xA0522D));
    private static final Icon ICON_COLUMN = IconUtil.makeIcon("C",  new Color(0x059775));
    private static final Icon ICON_SYNONYM = IconUtil.makeIcon("Y", new Color(0xCCA4E3));

    public ObjectBrowserTreeCellRenderer(
            Map<String, List<String>> connFullSchemas,
            Map<String, Set<String>> connHiddenSchemas,
            ThemeManager theme,
            Function<DefaultMutableTreeNode, String> nodeLabelFn) {
        this.connFullSchemas = connFullSchemas;
        this.connHiddenSchemas = connHiddenSchemas;
        this.theme = theme;
        this.nodeLabelFn = nodeLabelFn;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree t, Object value,
            boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        int level = node.getLevel();

        if (level == 1 && node.getUserObject() instanceof ConnHolder ch) {
            String cn = ch.info.getName();
            List<String> all = connFullSchemas.get(cn);
            int total = all != null ? all.size() : 0;
            Set<String> hidden = connHiddenSchemas.getOrDefault(cn, java.util.Collections.emptySet());
            int shown = total - hidden.size();
            Color bg; Color fg;
            if (ch.info.isColorEnabled() && ch.info.getColorTag() != null) {
                try {
                    Color cc = Color.decode(ch.info.getColorTag());
                    bg = cc;
                    fg = isDark(cc) ? Color.WHITE : Color.BLACK;
                } catch (Exception ignored) {
                    bg = sel ? getBackgroundSelectionColor() : t.getBackground();
                    fg = sel ? getTextSelectionColor() : getTextNonSelectionColor();
                }
            } else {
                bg = sel ? getBackgroundSelectionColor() : t.getBackground();
                fg = sel ? getTextSelectionColor() : getTextNonSelectionColor();
            }
            JPanel p = new JPanel(new BorderLayout(10, 0));
            p.setOpaque(true); p.setBackground(bg);
            JLabel nl = new JLabel(cn);
            nl.setFont(FontManager.getInstance().resolve("font.left")); nl.setForeground(fg);
            if (sel) nl.setFont(nl.getFont().deriveFont(java.awt.Font.BOLD));
            p.add(nl, BorderLayout.CENTER);
            JLabel bl = new JLabel(shown + "  of  " + total);
            bl.setFont(FontManager.getInstance().resolve("font.left"));
            bl.setForeground(sel ? fg : theme.resolve("fg.muted"));
            bl.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 4));
            p.add(bl, BorderLayout.EAST);
            JPanel wrap = new JPanel(new BorderLayout(6, 0));
            wrap.setOpaque(false);
            Icon icon = ICON_DB;
            ImageIcon dbIcon = IconUtil.menuIcon(ch.info.getDbType());
            if (dbIcon != null) icon = dbIcon;
            JLabel il = new JLabel(icon);
            wrap.add(il, BorderLayout.WEST);
            wrap.add(p, BorderLayout.CENTER);
            return wrap;
        }

        JLabel label = (JLabel) super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, hasFocus);
        Icon ic = null;
        if (level == 2) ic = ICON_SCHEMA;
        else if (level == 3) ic = iconForTypeLabel(node.getUserObject().toString());
        else if (level == 4) { String tl = nodeLabelFn.apply(node); ic = iconForTypeLabel(tl); }
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

    private static String colorHex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    private static boolean isDark(Color c) {
        return (c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114) / 1000 < 128;
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
}
