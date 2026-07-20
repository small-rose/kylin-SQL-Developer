package com.kylin.plsql.ui.component.left;

import com.kylin.plsql.core.config.ThemeManager;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Local file system tree browser for SQL files. */
public class LocalFileBrowser extends JPanel {

    public interface Callback {
        void onFileOpen(String filePath);
    }

    private final ThemeManager theme = ThemeManager.getInstance();
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode root;
    private final Callback callback;
    private final Set<String> openedFiles = new HashSet<>();
    private final Map<String, Icon> iconCache = new HashMap<>();

    private Runnable onFolderAdded;
    private Runnable onLocateFile;

    private static final Color LIGHT_GRAY = new Color(0x95A5A6);
    private static final Color GREEN = new Color(0x3D8B3D);

    // Folder icon matching RightPanel's style (16x16)
    private static final int IS = 16;
    private static final Icon ICON_FOLDER = makeFolderIcon();
    // Toolbar icons (18x18)
    private static final Icon ICON_FOLDER_OPEN = makeToolbarFolderIcon();
    private static final Icon ICON_EXPAND = makeExpandCollapseIcon(false);
    private static final Icon ICON_COLLAPSE = makeExpandCollapseIcon(true);

    private static ImageIcon makeFolderIcon() {
        BufferedImage img = new BufferedImage(IS, IS, BufferedImage.TYPE_INT_ARGB);
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

    // Toolbar folder: same style but 18x18 for toolbar
    private static ImageIcon makeToolbarFolderIcon() {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xF5D36E));
        g.fillRoundRect(3, 6, 12, 10, 2, 2);
        g.setColor(new Color(0xE6A817));
        g.fillRect(4, 6, 6, 2);
        g.setColor(new Color(0xC49A0D));
        g.drawRoundRect(3, 6, 12, 10, 2, 2);
        g.dispose();
        return new ImageIcon(img);
    }

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

    private static ImageIcon makeExpandCollapseIcon(boolean collapse) {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xF0AD4E));
        int dir = collapse ? -1 : 1;
        for (int i = 0; i < 2; i++) {
            int baseY = 5 + i * 6;
            int[] xp = {5, 9, 13};
            int[] yp = {baseY + dir * 2, baseY, baseY + dir * 2};
            g.fillPolygon(xp, yp, 3);
        }
        g.dispose();
        return new ImageIcon(img);
    }

    private static ImageIcon makeIcon16(String letter, Color bg) {
        BufferedImage img = new BufferedImage(IS, IS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(1, 1, 14, 14, 3, 3);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        int x = (IS - fm.stringWidth(letter)) / 2;
        int y = (IS + fm.getAscent()) / 2 - 1;
        g.drawString(letter, x, y);
        g.dispose();
        return new ImageIcon(img);
    }

    private Icon iconForFile(String name) {
        boolean opened = openedFiles.contains(name);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        String letter = ext.isEmpty() ? "F" : ext.substring(0, 1).toUpperCase();
        if (opened) return makeIcon16(letter, GREEN);
        return iconCache.computeIfAbsent(letter, k -> makeIcon16(k, LIGHT_GRAY));
    }

    public LocalFileBrowser(Callback callback) {
        this.callback = callback;
        setBorder(null);
        setLayout(new BorderLayout());

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBackground(theme.resolve("bg.main"));

        JButton openBtn = makeToolBtn(ICON_FOLDER_OPEN, "\u6253\u5F00\u6587\u4EF6\u5939", e -> openFolder());
        tb.add(openBtn);

        tb.add(Box.createHorizontalGlue());
        tb.addSeparator();

        JButton expandBtn = makeToolBtn(ICON_EXPAND, "\u5C55\u5F00\u5168\u90E8", e -> expandAll());
        tb.add(expandBtn);

        JButton collapseBtn = makeToolBtn(ICON_COLLAPSE, "\u6298\u53E0\u5168\u90E8", e -> collapseAll());
        tb.add(collapseBtn);

        add(tb, BorderLayout.NORTH);

        root = new DefaultMutableTreeNode("\u672C\u5730\u6587\u4EF6");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(22);

        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value,
                    boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                JLabel c = (JLabel) super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object uo = node.getUserObject();
                if (uo instanceof DirEntry de) {
                    c.setIcon(ICON_FOLDER);
                    c.setText(de.isRoot ? de.name + " (" + de.path + ")" : de.name);
                    c.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                } else if (uo instanceof File file) {
                    c.setIcon(iconForFile(file.getName()));
                    c.setText(file.getName());
                    c.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                }
                return c;
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    Object uo = node.getUserObject();
                    if (uo instanceof File file && file.isFile()) {
                        doOpenFile(file);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        applyTheme();
    }

    private static final Toolkit TK = Toolkit.getDefaultToolkit();

    private void doOpenFile(File file) {
        String path = file.getAbsolutePath();
        openedFiles.add(file.getName());
        tree.repaint();
        callback.onFileOpen(path);
    }

    /** Find a tree node by absolute file path (recursive). */
    public DefaultMutableTreeNode findNodeByPath(String filePath) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode found = findNodeRecursive((DefaultMutableTreeNode) root.getChildAt(i), filePath);
            if (found != null) return found;
        }
        return null;
    }

    private DefaultMutableTreeNode findNodeRecursive(DefaultMutableTreeNode node, String filePath) {
        Object uo = node.getUserObject();
        if (uo instanceof File f && f.getAbsolutePath().equals(filePath)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = findNodeRecursive((DefaultMutableTreeNode) node.getChildAt(i), filePath);
            if (found != null) return found;
        }
        return null;
    }

    /** Select a tree node and scroll to visible. */
    public void selectAndScrollToNode(DefaultMutableTreeNode node) {
        TreePath tp = new TreePath(node.getPath());
        tree.setSelectionPath(tp);
        tree.scrollPathToVisible(tp);
        tree.requestFocusInWindow();
    }

    private void showHint(String msg) {
        JWindow hint = new JWindow();
        JLabel label = new JLabel(msg, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(new Color(0x333333));
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        label.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        hint.add(label);
        hint.pack();
        Point loc = getLocationOnScreen();
        hint.setLocation(loc.x + getWidth() / 2 - hint.getWidth() / 2, loc.y + 30);
        hint.setAlwaysOnTop(true);
        hint.setVisible(true);
        new javax.swing.Timer(2000, ev -> hint.dispose()).start();
    }

    public void markOpened(String fileName) {
        openedFiles.add(fileName);
        tree.repaint();
    }

    public TreePath getSelectionPath() { return tree.getSelectionPath(); }

    /** Return list of root folder paths for persistence. */
    public java.util.List<String> getOpenFolderPaths() {
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            Object uo = ((DefaultMutableTreeNode) root.getChildAt(i)).getUserObject();
            if (uo instanceof DirEntry de && de.isRoot) paths.add(de.path);
        }
        return paths;
    }

    /** Add root folders without triggering onFolderAdded callback. */
    public void addRootFoldersSilently(java.util.List<String> paths) {
        for (String p : paths) {
            if (!hasRootFolder(p)) addRootFolder(p);
        }
    }

    public DefaultMutableTreeNode getSelectedNode() {
        TreePath p = tree.getSelectionPath();
        return p != null ? (DefaultMutableTreeNode) p.getLastPathComponent() : null;
    }

    public void setOnFolderAdded(Runnable r) { this.onFolderAdded = r; }

    public void setOnLocateFile(Runnable r) { this.onLocateFile = r; }

    public Runnable getOnLocateFile() { return onLocateFile; }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void collapseAll() {
        for (int i = tree.getRowCount() - 1; i >= 0; i--) tree.collapseRow(i);
    }

    private void showPopup(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        JPopupMenu menu = new JPopupMenu();

        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object uo = node.getUserObject();
            if (uo instanceof DirEntry de) {
                JMenuItem refreshItem = new JMenuItem("\u5237\u65B0");
                refreshItem.addActionListener(ev -> refreshDirNode(de));
                menu.add(refreshItem);

                JMenuItem removeItem = new JMenuItem("\u79FB\u9664");
                removeItem.addActionListener(ev -> {
                    if (de.isRoot) removeRootFolder(de);
                });
                if (de.isRoot) menu.add(removeItem);
            } else if (uo instanceof File file && file.isFile()) {
                JMenuItem openItem = new JMenuItem("\u6253\u5F00");
                openItem.addActionListener(ev -> doOpenFile(file));
                menu.add(openItem);

                JMenuItem revealItem = new JMenuItem("\u6253\u5F00\u6587\u4EF6\u6240\u5728\u4F4D\u7F6E");
                revealItem.addActionListener(ev -> revealInExplorer(file));
                menu.add(revealItem);

                JMenuItem copyPathItem = new JMenuItem("\u590D\u5236\u8DEF\u5F84");
                copyPathItem.addActionListener(ev ->
                    TK.getSystemClipboard().setContents(new StringSelection(file.getAbsolutePath()), null));
                menu.add(copyPathItem);
            }
        } else {
            JMenuItem refreshAllItem = new JMenuItem("\u5237\u65B0\u5168\u90E8");
            refreshAllItem.addActionListener(ev -> refreshAll());
            menu.add(refreshAllItem);

            JMenuItem expandAllItem = new JMenuItem("\u5C55\u5F00\u5168\u90E8");
            expandAllItem.addActionListener(ev -> expandAll());
            menu.add(expandAllItem);

            JMenuItem collapseAllItem = new JMenuItem("\u6298\u53E0\u5168\u90E8");
            collapseAllItem.addActionListener(ev -> collapseAll());
            menu.add(collapseAllItem);

            menu.addSeparator();

            JMenuItem openFolderItem = new JMenuItem("\u6253\u5F00\u65B0\u6587\u4EF6\u5939");
            openFolderItem.addActionListener(ev -> openFolder());
            menu.add(openFolderItem);
        }

        menu.show(tree, e.getX(), e.getY());
    }

    private void revealInExplorer(File file) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", file.getAbsolutePath()});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", "-R", file.getAbsolutePath()});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", file.getParentFile().getAbsolutePath()});
            }
        } catch (Exception ignored) {}
    }

    private void openFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("\u9009\u62E9\u6587\u4EF6\u5939");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (hasRootFolder(path)) {
                showHint("\u8BE5\u6587\u4EF6\u5939\u5DF2\u5B58\u5728");
                return;
            }
            addRootFolder(path);
            if (onFolderAdded != null) onFolderAdded.run();
        }
    }

    private boolean hasRootFolder(String path) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
            if (node.getUserObject() instanceof DirEntry de && de.isRoot && de.path.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void addRootFolder(String path) {
        Path dir = Path.of(path);
        if (!Files.isDirectory(dir)) return;
        String displayName = dir.getFileName().toString();
        DirEntry de = new DirEntry(displayName, dir.toString(), true);
        DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(de);
        loadChildren(folderNode);
        root.add(folderNode);
        treeModel.reload();
        tree.expandPath(new TreePath(folderNode.getPath()));
    }

    private void loadChildren(DefaultMutableTreeNode parentNode) {
        parentNode.removeAllChildren();
        Object uo = parentNode.getUserObject();
        if (!(uo instanceof DirEntry de)) return;
        Path dir = Path.of(de.path);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.sorted((a, b) -> {
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
                if (aDir != bDir) return aDir ? -1 : 1;
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }).forEach(p -> {
                if (Files.isDirectory(p)) {
                    String name = p.getFileName().toString();
                    DirEntry sub = new DirEntry(name, p.toString(), false);
                    DefaultMutableTreeNode subNode = new DefaultMutableTreeNode(sub);
                    loadChildren(subNode);
                    parentNode.add(subNode);
                } else {
                    parentNode.add(new DefaultMutableTreeNode(p.toFile()));
                }
            });
        } catch (Exception ignored) {}
    }

    private void refreshDirNode(DirEntry de) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
            if (node.getUserObject() instanceof DirEntry d && d.path.equals(de.path)) {
                boolean wasExpanded = tree.isExpanded(new TreePath(node.getPath()));
                loadChildren(node);
                treeModel.reload();
                if (wasExpanded) tree.expandPath(new TreePath(node.getPath()));
                return;
            }
        }
    }

    private void refreshAll() {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
            if (node.getUserObject() instanceof DirEntry) {
                boolean wasExpanded = tree.isExpanded(new TreePath(node.getPath()));
                loadChildren(node);
                if (wasExpanded) tree.expandPath(new TreePath(node.getPath()));
            }
        }
        treeModel.reload();
    }

    private void removeRootFolder(DirEntry de) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
            if (node.getUserObject() instanceof DirEntry d && d.path.equals(de.path)) {
                root.remove(i);
                treeModel.reload();
                return;
            }
        }
    }

    private static class DirEntry {
        final String name;
        final String path;
        final boolean isRoot;
        DirEntry(String name, String path, boolean isRoot) {
            this.name = name;
            this.path = path;
            this.isRoot = isRoot;
        }
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

    public void applyTheme() {
        Color bg = theme.resolve("bg.main");
        Color fg = theme.resolve("list.fg");
        setBackground(bg);
        tree.setBackground(bg);
        tree.setForeground(fg);
        for (Component c : getComponents()) {
            if (c instanceof JScrollPane sp) {
                sp.getViewport().setBackground(bg);
                sp.setBorder(null);
            }
            if (c instanceof JToolBar tb) {
                tb.setBackground(bg);
                tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.resolve("border.default")));
            }
        }
        tree.repaint();
    }
}
