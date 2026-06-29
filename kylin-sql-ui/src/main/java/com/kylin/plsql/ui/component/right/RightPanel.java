package com.kylin.plsql.ui.component.right;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ConfigManager.SavedFileRecord;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.ui.component.center.SourceViewerPanel;
import com.kylin.plsql.ui.component.center.SqlEditorPanel;
import com.kylin.plsql.ui.component.common.VerticalTabButton;

import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class RightPanel extends JPanel {

    public interface Callback {
        void onFileSelected(String filePath);
    }

    private enum TabLabel { FILES, THUMBNAIL }

    private final ThemeManager theme = ThemeManager.getInstance();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final JPanel tabStrip = new JPanel();
    private final FilesContent filesContent;
    private final ThumbnailContent thumbnailContent;
    private final transient Callback callback;
    private final transient Runnable onToggle;
    private final transient ConfigManager configManager;
    private TabLabel activeTab = TabLabel.FILES;
    private boolean expanded = true;

    public RightPanel(ConfigManager configManager, Runnable onToggle, Callback callback) {
        this.configManager = configManager;
        this.onToggle = onToggle;
        this.callback = callback;

        setLayout(new BorderLayout(0, 0));

        tabStrip.setLayout(new BoxLayout(tabStrip, BoxLayout.Y_AXIS));
        tabStrip.setPreferredSize(new Dimension(28, 0));
        tabStrip.setOpaque(true);
        tabStrip.setBackground(theme.resolve("bg.main"));

        filesContent = new FilesContent();
        thumbnailContent = new ThumbnailContent();

        contentPanel.add(filesContent, "FILES");
        contentPanel.add(thumbnailContent, "THUMBNAIL");

        add(contentPanel, BorderLayout.CENTER);
        add(tabStrip, BorderLayout.EAST);

        addTab("SQL", TabLabel.FILES);
        addTab("THUMBNAIL", TabLabel.THUMBNAIL);

        selectTab(TabLabel.FILES);
        tabStrip.add(Box.createVerticalGlue());
        applyTheme();
    }

    public boolean locateFileInFiles(String filePath) {
        filesContent.refresh();
        return filesContent.locateFile(filePath);
    }

    public void selectFilesTab() {
        boolean wasCollapsed = !expanded;
        activeTab = TabLabel.FILES;
        cardLayout.show(contentPanel, TabLabel.FILES.name());
        if (wasCollapsed || !contentPanel.isVisible()) {
            expanded = true;
            contentPanel.setVisible(true);
            filesContent.refresh();
        }
        updateTabActive();
        revalidate();
        if (wasCollapsed && onToggle != null) onToggle.run();
    }

    public void applyTheme() {
        setBackground(theme.resolve("bg.main"));
        tabStrip.setBackground(theme.resolve("bg.main"));
        tabStrip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, theme.resolve("border.default")));
        contentPanel.setBackground(theme.resolve("bg.main"));
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 0, theme.resolve("border.default")));
        filesContent.applyTheme();
        thumbnailContent.applyTheme();
    }

    private void addTab(String label, TabLabel tab) {
        VerticalTabButton btn = new VerticalTabButton(label);
        btn.addActionListener(e -> selectTab(tab));
        tabStrip.add(btn);
    }

    private void selectTab(TabLabel tab) {
        if (expanded && tab == activeTab) {
            expanded = false;
            contentPanel.setVisible(false);
            updateTabActive();
            revalidate();
            if (onToggle != null) onToggle.run();
        } else {
            activeTab = tab;
            cardLayout.show(contentPanel, tab.name());
            boolean wasCollapsed = !expanded;
            if (wasCollapsed) {
                expanded = true;
                contentPanel.setVisible(true);
            }
            updateTabActive();
            if (expanded && tab == TabLabel.FILES) filesContent.refresh();
            if (expanded && tab == TabLabel.THUMBNAIL) thumbnailContent.repaint();
            revalidate();
            if (wasCollapsed && onToggle != null) onToggle.run();
        }
    }

    private void updateTabActive() {
        for (Component c : tabStrip.getComponents()) {
            if (c instanceof VerticalTabButton vtb) {
                vtb.setActive(expanded && tabStrip.getComponentZOrder(c) == getTabIndex(activeTab));
            }
        }
    }

    private int getTabIndex(TabLabel tab) {
        int idx = 0;
        for (Component c : tabStrip.getComponents()) {
            if (c instanceof VerticalTabButton) {
                if (tab.ordinal() == idx) return tabStrip.getComponentZOrder(c);
                idx++;
            }
        }
        return 0;
    }

    public boolean isExpanded() { return expanded; }

    public void onFileOpenedOrSaved(String filePath) {
        SavedFileRecord r = new SavedFileRecord();
        r.filePath = filePath;
        r.fileName = new java.io.File(filePath).getName();
        r.lastOpened = System.currentTimeMillis();
        configManager.saveFileRecord(r);
        filesContent.refresh();
    }

    public void removeFileRecord(String filePath) {
        configManager.removeFileRecord(filePath);
        filesContent.refresh();
    }

    public void setActiveEditor(SqlEditorPanel editor) {
        thumbnailContent.setEditor(editor);
    }

    public void setActiveSourceViewer(SourceViewerPanel viewer) {
        thumbnailContent.setText(viewer.getTextArea().getText());
        thumbnailContent.setOnNavigate(line -> {
            try {
                int off = viewer.getTextArea().getLineStartOffset(line);
                viewer.getTextArea().setCaretPosition(off);
            } catch (Exception ignored) {}
        });
    }

    public void caretUpdated(int line, int totalLines) {
        thumbnailContent.setCaretLine(line, totalLines);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(expanded ? 140 : 28, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(28, 0);
    }

    // ── Files Content ──

    class FilesContent extends JPanel {
        private final DefaultListModel<Object> listModel;
        private final JList<Object> fileList;
        private static final int ICON_SIZE = 16;

        FilesContent() {
            setLayout(new BorderLayout());
            setBackground(theme.resolve("bg.main"));

            listModel = new DefaultListModel<>();
            fileList = new JList<>(listModel);
            fileList.setFixedCellHeight(26);
            fileList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            fileList.setBackground(theme.resolve("bg.main"));
            fileList.setForeground(theme.resolve("fg.main"));
            fileList.setCellRenderer(new FileListRenderer());

            fileList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int idx = fileList.locationToIndex(e.getPoint());
                    if (idx < 0) return;
                    Object val = listModel.getElementAt(idx);
                    if (val instanceof String) return; // header
                    if (val instanceof SavedFileRecord rec && e.getClickCount() == 2) {
                        callback.onFileSelected(rec.filePath);
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) { handlePopup(e); }
                @Override
                public void mouseReleased(MouseEvent e) { handlePopup(e); }

                private void handlePopup(MouseEvent e) {
                    if (!e.isPopupTrigger()) return;
                    int idx = fileList.locationToIndex(e.getPoint());
                    if (idx < 0) return;
                    Object val = listModel.getElementAt(idx);
                    if (val instanceof SavedFileRecord rec) {
                        showFilePopup(e.getX(), e.getY(), rec);
                    }
                }
            });

            JScrollPane scroll = new JScrollPane(fileList);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            add(scroll, BorderLayout.CENTER);
        }

        private void showFilePopup(int x, int y, SavedFileRecord rec) {
            JPopupMenu popup = new JPopupMenu();
            popup.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

            JMenuItem openItem = new JMenuItem("\u5728\u6807\u7B7E\u9875\u4E2D\u6253\u5F00");
            openItem.addActionListener(e -> callback.onFileSelected(rec.filePath));
            popup.add(openItem);

            JMenuItem openLocItem = new JMenuItem("\u6253\u5F00\u6587\u4EF6\u6240\u5728\u4F4D\u7F6E");
            openLocItem.addActionListener(e -> openFileLocation(rec.filePath));
            popup.add(openLocItem);

            popup.addSeparator();

            JMenuItem removeItem = new JMenuItem("\u5220\u9664\u6587\u4EF6");
            removeItem.addActionListener(e -> {
                configManager.removeFileRecord(rec.filePath);
                refresh();
            });
            popup.add(removeItem);

            JMenuItem deleteItem = new JMenuItem("\u6C38\u4E45\u5220\u9664\u6587\u4EF6");
            deleteItem.addActionListener(e -> permanentlyDelete(rec));
            popup.add(deleteItem);

            popup.show(fileList, x, y);
        }

        private void openFileLocation(String filePath) {
            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    JOptionPane.showMessageDialog(RightPanel.this,
                        "\u6587\u4EF6\u4E0D\u5B58\u5728: " + filePath);
                    return;
                }
                Desktop.getDesktop().open(file.getParentFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(RightPanel.this,
                    "\u65E0\u6CD5\u6253\u5F00\u6587\u4EF6\u4F4D\u7F6E: " + ex.getMessage());
            }
        }

        private void permanentlyDelete(SavedFileRecord rec) {
            int result = JOptionPane.showConfirmDialog(RightPanel.this,
                "\u786E\u5B9A\u8981\u6C38\u4E45\u5220\u9664\u6587\u4EF6 " + rec.fileName
                    + " \u5417\uFF1F\n\u6B64\u64CD\u4F5C\u4E0D\u53EF\u6062\u590D\u3002",
                "\u6C38\u4E45\u5220\u9664\u6587\u4EF6",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) return;

            try {
                File file = new File(rec.filePath);
                if (file.exists() && !file.delete()) {
                    JOptionPane.showMessageDialog(RightPanel.this,
                        "\u5220\u9664\u6587\u4EF6\u5931\u8D25: " + rec.filePath);
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(RightPanel.this,
                    "\u5220\u9664\u6587\u4EF6\u5931\u8D25: " + ex.getMessage());
                return;
            }
            configManager.removeFileRecord(rec.filePath);
            refresh();
        }

        void applyTheme() {
            setBackground(theme.resolve("bg.main"));
            fileList.setBackground(theme.resolve("bg.main"));
            fileList.setForeground(theme.resolve("fg.main"));
        }

        boolean locateFile(String filePath) {
            for (int i = 0; i < listModel.size(); i++) {
                Object val = listModel.getElementAt(i);
                if (val instanceof SavedFileRecord rec && rec.filePath.equals(filePath)) {
                    fileList.setSelectedIndex(i);
                    fileList.ensureIndexIsVisible(i);
                    fileList.requestFocusInWindow();
                    return true;
                }
            }
            return false;
        }

        void refresh() {
            listModel.clear();
            listModel.addElement("SQL");
            List<SavedFileRecord> records = configManager.loadFileRecords();
            for (SavedFileRecord r : records) {
                listModel.addElement(r);
            }
        }

        private class FileListRenderer extends JPanel implements ListCellRenderer<Object> {
            private final JLabel label = new JLabel();

            FileListRenderer() {
                setLayout(new BorderLayout());
                add(label, BorderLayout.CENTER);
            }

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                           int index, boolean isSel, boolean hasFocus) {
                Color bg = isSel ? theme.resolve("selection.listBg") : theme.resolve("bg.main");
                Color fg = isSel ? theme.resolve("selection.listFg") : theme.resolve("fg.main");
                setBackground(bg);
                label.setOpaque(false);
                label.setForeground(fg);
                label.setFont(list.getFont());

                if (value instanceof String header) {
                    label.setIcon(makeFolderIcon());
                    label.setText(header);
                    label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                    label.setFont(list.getFont().deriveFont(Font.BOLD));
                } else if (value instanceof SavedFileRecord rec) {
                    label.setIcon(makeFileIcon(rec.fileName));
                    label.setBorder(BorderFactory.createEmptyBorder(2, 20, 2, 6));
                    Color muted = theme.resolve("fg.muted");
                    String pathHex = String.format("#%02x%02x%02x", muted.getRed(), muted.getGreen(), muted.getBlue());
                    String escFile = rec.fileName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                    FontMetrics fm = label.getFontMetrics(list.getFont());
                    int maxW = list.getWidth() - 52;
                    String displayPath = rec.filePath;
                    if (maxW > 0 && fm.stringWidth(rec.fileName + " (" + rec.filePath + ")") > maxW) {
                        int pathMax = maxW - fm.stringWidth(rec.fileName + " ()");
                        if (pathMax > fm.stringWidth("...")) {
                            String p = rec.filePath;
                            while (fm.stringWidth("..." + p) > pathMax && p.length() > 3)
                                p = p.substring(4);
                            displayPath = "..." + p;
                        } else {
                            displayPath = "";
                        }
                    }
                    String html = "<html>"
                        + (isSel ? "<b>" + escFile + "</b>" : escFile)
                        + " <span style='color:" + pathHex + "'>(" + displayPath + ")</span></html>";
                    label.setText(html);
                    label.setToolTipText(rec.filePath);
                }
                return this;
            }
        }

        private Icon makeFolderIcon() {
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

        private Icon makeFileIcon(String fileName) {
            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
            Color bg;
            String letter;
            switch (ext) {
                case "sql" -> { bg = new Color(0x337AB7); letter = "S"; }
                case "csv" -> { bg = new Color(0x27AE60); letter = "C"; }
                case "txt" -> { bg = new Color(0x7F8C8D); letter = "T"; }
                case "xml" -> { bg = new Color(0xE67E22); letter = "X"; }
                case "json" -> { bg = new Color(0x8E44AD); letter = "J"; }
                case "log" -> { bg = new Color(0x95A5A6); letter = "L"; }
                default   -> { bg = new Color(0x95A5A6); letter = "F"; }
            }
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
    }

    // ── Thumbnail Content ──

    class ThumbnailContent extends JPanel {
        private java.util.function.Consumer<String> onTextChange;
        private java.util.function.Consumer<Integer> onNavigate;
        private String text = "";
        private int caretLine = 1;
        private int totalLines = 1;

        ThumbnailContent() {
            setLayout(null);
            setBackground(theme.resolve("bg.main"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onNavigate != null) {
                        int lineH = Math.max(1, Math.min(3, (getHeight() - 4) / Math.max(totalLines, 1)));
                        int line = (e.getY() - 2) / lineH;
                        onNavigate.accept(Math.max(0, Math.min(line, totalLines - 1)));
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (onNavigate != null) {
                        int lineH = Math.max(1, Math.min(3, (getHeight() - 4) / Math.max(totalLines, 1)));
                        int line = (e.getY() - 2) / lineH;
                        onNavigate.accept(Math.max(0, Math.min(line, totalLines - 1)));
                    }
                }
            });
        }

        void applyTheme() {
            setBackground(theme.resolve("bg.main"));
            repaint();
        }

        void setEditor(SqlEditorPanel editor) {
            this.text = editor.getText();
            this.totalLines = Math.max(editor.getTextArea().getLineCount(), 1);
            this.onNavigate = editor::navigateToLine;
            editor.getTextArea().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { text = editor.getText(); totalLines = Math.max(editor.getTextArea().getLineCount(), 1); repaint(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { text = editor.getText(); totalLines = Math.max(editor.getTextArea().getLineCount(), 1); repaint(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { text = editor.getText(); totalLines = Math.max(editor.getTextArea().getLineCount(), 1); repaint(); }
            });
            repaint();
        }

        void setText(String t) {
            this.text = t != null ? t : "";
            this.totalLines = text.isEmpty() ? 1 : text.split("\n", -1).length;
            repaint();
        }
        void setOnNavigate(java.util.function.Consumer<Integer> fn) { this.onNavigate = fn; }

        void setCaretLine(int line, int total) {
            this.caretLine = line;
            this.totalLines = Math.max(total, 1);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0 || totalLines < 1) return;

            int lineH = Math.max(1, Math.min(3, (h - 4) / totalLines));
            int startY = 2;

            // Draw code as miniature lines (minimap)
            if (text != null && !text.isEmpty()) {
                String[] lines = text.split("\n", -1);
                if (lines.length > 0) {
                    int maxLen = 0;
                    for (String line : lines) maxLen = Math.max(maxLen, line.length());
                    if (maxLen > 0) {
                        int contentW = w - 6;
                        for (int i = 0; i < lines.length && i < totalLines; i++) {
                            int y = startY + i * lineH + lineH / 2;
                            int lineLen = lines[i].length();
                            if (lineLen > 0) {
                                int segW = Math.max(2, lineLen * contentW / maxLen);
                                g.setColor(theme.resolve("fg.secondary"));
                                g.drawLine(2, y, 2 + segW, y);
                            }
                        }
                    }
                }
            }

            // Draw caret line
            g.setColor(theme.resolve("accent.green"));
            int caretY = startY + (caretLine - 1) * lineH;
            g.fillRect(2, caretY, w - 4, lineH);
        }
    }
}
