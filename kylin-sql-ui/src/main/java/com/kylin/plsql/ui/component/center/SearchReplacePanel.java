package com.kylin.plsql.ui.component.center;

import com.kylin.plsql.core.config.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SearchReplacePanel extends JPanel {

    private static class MatchRange {
        final int start, end;
        MatchRange(int s, int e) { start = s; end = e; }
    }

    private RSyntaxTextArea textArea;
    private final List<MatchRange> matches = new ArrayList<>();
    private final List<Object> highlightTags = new ArrayList<>();
    private Object currentHighlightTag;
    private int currentMatchIndex = -1;
    private DocumentListener docListener;

    private Color matchColor;
    private Color currentMatchColor;
    private Color bgColor;
    private Color hoverBg;

    private final java.util.List<String> searchHistory = new ArrayList<>();
    private int searchHistoryIndex = -1;
    private final java.util.List<String> replaceHistory = new ArrayList<>();
    private int replaceHistoryIndex = -1;
    private static final int MAX_HISTORY = 20;

    private final JTextField searchField = new JTextField(20);
    private final JLabel matchCountLabel = new JLabel(" ");
    private final JButton prevBtn = new JButton("\u2191");
    private final JButton nextBtn = new JButton("\u2193");
    private final JToggleButton matchCaseBtn = new JToggleButton("Aa");
    private final JToggleButton wordsBtn = new JToggleButton("ab");
    private final JToggleButton regexBtn = new JToggleButton(".*");
    private final JButton closeBtn = new JButton("\u00D7");

    private final JTextField replaceField = new JTextField(20);
    private final JButton replaceBtn = new JButton("\u66FF\u6362");
    private final JButton replaceAllBtn = new JButton("\u5168\u90E8\u66FF\u6362");
    private final JPanel replaceBar;
    private final JButton searchHistoryBtn;
    private final JButton replaceHistoryBtn;

    private JPopupMenu searchHistoryPopup;
    private JPopupMenu replaceHistoryPopup;

    public SearchReplacePanel() {
        super(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        setOpaque(false);

        searchHistoryBtn = new JButton("\u25BC");
        searchHistoryBtn.setToolTipText("\u641C\u7D22\u5386\u53F2");
        searchHistoryBtn.addActionListener(e -> showSearchHistoryPopup());

        replaceHistoryBtn = new JButton("\u25BC");
        replaceHistoryBtn.setToolTipText("\u66FF\u6362\u5386\u53F2");
        replaceHistoryBtn.addActionListener(e -> showReplaceHistoryPopup());

        closeBtn.setToolTipText("\u5173\u95ED\u641C\u7D22 (Esc)");
        closeBtn.addActionListener(e -> hideSearch());

        searchField.setPreferredSize(new Dimension(180, 24));
        searchField.addActionListener(e -> {
            addToSearchHistory(searchField.getText());
            findNext();
        });
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP && (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) == 0) {
                    if (!searchHistory.isEmpty()) {
                        if (searchHistoryIndex < 0) searchHistoryIndex = searchHistory.size();
                        searchHistoryIndex = Math.max(0, searchHistoryIndex - 1);
                        searchField.setText(searchHistory.get(searchHistoryIndex));
                        searchField.selectAll();
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (searchHistoryIndex >= 0 && searchHistoryIndex < searchHistory.size() - 1) {
                        searchHistoryIndex++;
                        searchField.setText(searchHistory.get(searchHistoryIndex));
                        searchField.selectAll();
                    } else {
                        showSearchHistoryPopup();
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                    findPrev();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hideSearch();
                    e.consume();
                }
            }
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_ENTER && e.getKeyCode() != KeyEvent.VK_UP
                        && e.getKeyCode() != KeyEvent.VK_DOWN && e.getKeyCode() != KeyEvent.VK_ESCAPE) {
                    scheduleSearch();
                }
            }
        });
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { searchHistoryIndex = -1; }
            @Override public void removeUpdate(DocumentEvent e) { searchHistoryIndex = -1; }
            @Override public void changedUpdate(DocumentEvent e) { searchHistoryIndex = -1; }
        });

        initButtonStyles();

        // ── Find row ──
        JPanel findRow = new JPanel(new BorderLayout(4, 0));
        findRow.setOpaque(false);
        JPanel findCenter = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        findCenter.setOpaque(false);
        JLabel searchIcon = new JLabel("\uD83D\uDD0D");
        searchIcon.setPreferredSize(new Dimension(24, 24));
        searchIcon.setHorizontalAlignment(SwingConstants.CENTER);
        searchIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));
        searchIcon.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) { showSearchHistoryPopup(); }
        });
        findCenter.add(searchIcon);
        findCenter.add(Box.createHorizontalStrut(4));
        findCenter.add(searchField);
        findCenter.add(Box.createHorizontalStrut(6));
        matchCountLabel.setPreferredSize(new Dimension(50, 24));
        matchCountLabel.setFont(matchCountLabel.getFont().deriveFont(10f));
        findCenter.add(matchCountLabel);
        findCenter.add(Box.createHorizontalStrut(4));
        findCenter.add(prevBtn);
        findCenter.add(nextBtn);
        findCenter.add(matchCaseBtn);
        findCenter.add(wordsBtn);
        findCenter.add(regexBtn);
        findRow.add(findCenter, BorderLayout.CENTER);
        findRow.add(closeBtn, BorderLayout.EAST);
        add(findRow, BorderLayout.NORTH);

        // ── Replace row ──
        replaceBar = new JPanel(new BorderLayout(4, 0));
        replaceBar.setOpaque(false);
        JPanel replaceCenter = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        replaceCenter.setOpaque(false);
        JLabel repIcon = new JLabel("\uD83D\uDD0D");
        repIcon.setPreferredSize(new Dimension(24, 24));
        repIcon.setHorizontalAlignment(SwingConstants.CENTER);
        repIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));
        repIcon.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) { showReplaceHistoryPopup(); }
        });
        replaceCenter.add(repIcon);
        replaceCenter.add(Box.createHorizontalStrut(4));
        replaceField.setPreferredSize(new Dimension(180, 24));
        replaceField.addActionListener(e -> replace());
        replaceField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    showReplaceHistoryPopup();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hideSearch();
                    e.consume();
                }
            }
        });
        replaceField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { replaceHistoryIndex = -1; }
            @Override public void removeUpdate(DocumentEvent e) { replaceHistoryIndex = -1; }
            @Override public void changedUpdate(DocumentEvent e) { replaceHistoryIndex = -1; }
        });
        replaceCenter.add(replaceField);
        replaceCenter.add(Box.createHorizontalStrut(6));
        replaceBtn.setToolTipText("\u66FF\u6362\u5F53\u524D\u5339\u914D");
        replaceBtn.addActionListener(e -> replace());
        replaceBtn.setEnabled(false);
        replaceCenter.add(replaceBtn);
        replaceCenter.add(Box.createHorizontalStrut(2));
        replaceAllBtn.setToolTipText("\u5168\u90E8\u66FF\u6362");
        replaceAllBtn.addActionListener(e -> replaceAll());
        replaceAllBtn.setEnabled(false);
        replaceCenter.add(replaceAllBtn);
        // Same width for replace buttons
        Dimension rd = replaceAllBtn.getPreferredSize();
        rd = new Dimension(Math.max(rd.width, 60), rd.height);
        replaceBtn.setPreferredSize(rd);
        replaceAllBtn.setPreferredSize(rd);
        replaceBar.add(replaceCenter, BorderLayout.CENTER);
        replaceBar.setVisible(false);
        add(replaceBar, BorderLayout.SOUTH);

        prevBtn.addActionListener(e -> findPrev());
        nextBtn.addActionListener(e -> findNext());
        matchCaseBtn.addActionListener(e -> scheduleSearch());
        wordsBtn.addActionListener(e -> scheduleSearch());
        regexBtn.addActionListener(e -> scheduleSearch());
        java.awt.event.ItemListener togBorder = e -> {
            JToggleButton b = (JToggleButton) e.getSource();
            Color accent = ThemeManager.getInstance().resolve("accent.green");
            if (b.isSelected()) {
                b.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
            } else {
                b.setBackground(new Color(0, 0, 0, 0));
            }
        };
        matchCaseBtn.addItemListener(togBorder);
        wordsBtn.addItemListener(togBorder);
        regexBtn.addItemListener(togBorder);

        initSearchHistoryPopup();
        initReplaceHistoryPopup();

        ThemeManager.getInstance().addListener(() -> {
            computeThemeColors();
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color c = bgColor;
        if (c == null) c = UIManager.getColor("Panel.background");
        g.setColor(c);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    // ── History popup ──

    private void initSearchHistoryPopup() {
        searchHistoryPopup = new JPopupMenu();
        searchHistoryPopup.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { rebuildSearchHistoryMenu(); }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(PopupMenuEvent e) {}
        });
    }

    private void initReplaceHistoryPopup() {
        replaceHistoryPopup = new JPopupMenu();
        replaceHistoryPopup.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { rebuildReplaceHistoryMenu(); }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(PopupMenuEvent e) {}
        });
    }

    private static void styleBtn(AbstractButton b) {
        b.setFocusable(false);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
    }

    private static void styleTogBtn(JToggleButton b) {
        styleBtn(b);
    }

    private void initButtonStyles() {
        styleBtn(closeBtn);
        styleBtn(prevBtn);
        styleBtn(nextBtn);
        styleTogBtn(matchCaseBtn);
        styleTogBtn(wordsBtn);
        styleTogBtn(regexBtn);
        styleBtn(searchHistoryBtn);
        styleBtn(replaceHistoryBtn);
        styleBtn(replaceBtn);
        styleBtn(replaceAllBtn);
        for (AbstractButton b : new AbstractButton[]{prevBtn, nextBtn, matchCaseBtn, wordsBtn, regexBtn,
                closeBtn}) {
            addHover(b);
        }
    }

    private void addHover(AbstractButton b) {
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (hoverBg != null) {
                    b.setBackground(hoverBg);
                    b.repaint();
                }
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(new Color(0, 0, 0, 0));
                b.repaint();
            }
        });
    }

    private void rebuildSearchHistoryMenu() {
        searchHistoryPopup.removeAll();
        if (searchHistory.isEmpty()) {
            searchHistoryPopup.add(new JMenuItem("(无历史记录)")).setEnabled(false);
            return;
        }
        for (String h : searchHistory) {
            JMenuItem item = new JMenuItem(h);
            item.addActionListener(e -> {
                searchField.setText(h);
                searchField.selectAll();
                performSearch();
            });
            searchHistoryPopup.add(item);
        }
    }

    private void rebuildReplaceHistoryMenu() {
        replaceHistoryPopup.removeAll();
        if (replaceHistory.isEmpty()) {
            replaceHistoryPopup.add(new JMenuItem("(无历史记录)")).setEnabled(false);
            return;
        }
        for (String h : replaceHistory) {
            JMenuItem item = new JMenuItem(h);
            item.addActionListener(e -> {
                replaceField.setText(h);
            });
            replaceHistoryPopup.add(item);
        }
    }

    private void showSearchHistoryPopup() {
        rebuildSearchHistoryMenu();
        searchHistoryPopup.show(searchField, 0, searchField.getHeight());
    }

    private void showReplaceHistoryPopup() {
        rebuildReplaceHistoryMenu();
        replaceHistoryPopup.show(replaceField, 0, replaceField.getHeight());
    }

    // ── Public API ──

    public void showSearch(RSyntaxTextArea target) {
        this.textArea = target;
        String sel = target.getSelectedText();
        if (sel != null && !sel.contains("\n")) {
            searchField.setText(sel);
        }
        searchField.selectAll();
        matches.clear();
        highlightTags.clear();
        currentMatchIndex = -1;
        currentHighlightTag = null;
        replaceBar.setVisible(false);
        computeThemeColors();
        setVisible(true);
        searchField.requestFocusInWindow();
        registerDocListener();
        scheduleSearch();
    }

    public void showReplace(RSyntaxTextArea target) {
        showSearch(target);
        replaceField.setText(searchField.getText());
        replaceField.selectAll();
        replaceBar.setVisible(true);
        replaceField.requestFocusInWindow();
    }

    public void hideSearch() {
        setVisible(false);
        clearHighlights();
        matches.clear();
        currentMatchIndex = -1;
        if (docListener != null && textArea != null) {
            textArea.getDocument().removeDocumentListener(docListener);
            docListener = null;
        }
        if (textArea != null) {
            textArea.requestFocusInWindow();
            textArea = null;
        }
    }

    public void findNext() {
        if (matches.isEmpty() || textArea == null) return;
        currentMatchIndex = (currentMatchIndex + 1) % matches.size();
        navigateToCurrentMatch();
    }

    public void findPrev() {
        if (matches.isEmpty() || textArea == null) return;
        currentMatchIndex = (currentMatchIndex - 1 + matches.size()) % matches.size();
        navigateToCurrentMatch();
    }

    public void replace() {
        if (textArea == null || currentMatchIndex < 0 || currentMatchIndex >= matches.size()) return;
        String replacement = replaceField.getText();
        if (replacement == null) replacement = "";
        MatchRange m = matches.get(currentMatchIndex);
        Document doc = textArea.getDocument();
        textArea.beginAtomicEdit();
        try {
            doc.remove(m.start, m.end - m.start);
            doc.insertString(m.start, replacement, null);
        } catch (BadLocationException ignored) {
        } finally {
            textArea.endAtomicEdit();
        }
        addToReplaceHistory(replacement);
    }

    public void replaceAll() {
        if (textArea == null || matches.isEmpty()) return;
        String replacement = replaceField.getText();
        if (replacement == null) replacement = "";
        Document doc = textArea.getDocument();
        textArea.beginAtomicEdit();
        try {
            for (int i = matches.size() - 1; i >= 0; i--) {
                MatchRange m = matches.get(i);
                doc.remove(m.start, m.end - m.start);
                doc.insertString(m.start, replacement, null);
            }
        } catch (BadLocationException ignored) {
        } finally {
            textArea.endAtomicEdit();
        }
        addToReplaceHistory(replacement);
    }

    private void scheduleSearch() {
        SwingUtilities.invokeLater(() -> {
            if (!isVisible() || textArea == null) return;
            performSearch();
        });
    }

    private void performSearch() {
        if (textArea == null) return;
        String query = searchField.getText();
        clearHighlights();
        matches.clear();
        currentMatchIndex = -1;

        if (query == null || query.isEmpty()) {
            matchCountLabel.setText(" ");
            replaceBtn.setEnabled(false);
            replaceAllBtn.setEnabled(false);
            return;
        }

        String text = textArea.getText();
        boolean caseSensitive = matchCaseBtn.isSelected();
        boolean wholeWords = wordsBtn.isSelected();
        boolean regex = regexBtn.isSelected();

        try {
            if (regex) {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                Pattern p = Pattern.compile(query, flags);
                var m = p.matcher(text);
                while (m.find()) {
                    matches.add(new MatchRange(m.start(), m.end()));
                }
            } else {
                String searchText = caseSensitive ? text : text.toLowerCase();
                String searchQuery = caseSensitive ? query : query.toLowerCase();
                int idx = 0;
                while (true) {
                    idx = searchText.indexOf(searchQuery, idx);
                    if (idx < 0) break;
                    if (wholeWords && !isWholeWord(text, idx, idx + searchQuery.length())) {
                        idx++;
                        continue;
                    }
                    matches.add(new MatchRange(idx, idx + searchQuery.length()));
                    idx++;
                }
            }
        } catch (PatternSyntaxException e) {
            matchCountLabel.setText("\u6B63\u5219\u9519\u8BEF");
            replaceBtn.setEnabled(false);
            replaceAllBtn.setEnabled(false);
            return;
        }

        if (matches.isEmpty()) {
            matchCountLabel.setText("\u65E0\u5339\u914D");
            replaceBtn.setEnabled(false);
            replaceAllBtn.setEnabled(false);
            return;
        }

        applyHighlights();

        int caretPos = textArea.getCaretPosition();
        int bestIdx = 0;
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).start >= caretPos) {
                bestIdx = i;
                break;
            }
        }
        currentMatchIndex = bestIdx;
        updateMatchCount();
        navigateToCurrentMatch();
        replaceBtn.setEnabled(true);
        replaceAllBtn.setEnabled(true);
    }

    private void addToSearchHistory(String query) {
        if (query == null || query.isEmpty()) return;
        searchHistory.remove(query);
        searchHistory.add(0, query);
        if (searchHistory.size() > MAX_HISTORY) {
            searchHistory.remove(searchHistory.size() - 1);
        }
        searchHistoryIndex = -1;
    }

    void addToReplaceHistory(String text) {
        if (text == null || text.isEmpty()) return;
        replaceHistory.remove(text);
        replaceHistory.add(0, text);
        if (replaceHistory.size() > MAX_HISTORY) {
            replaceHistory.remove(replaceHistory.size() - 1);
        }
        replaceHistoryIndex = -1;
    }

    private static boolean isWholeWord(String text, int start, int end) {
        if (start > 0) {
            char prev = text.charAt(start - 1);
            if (Character.isLetterOrDigit(prev) || prev == '_') return false;
        }
        if (end < text.length()) {
            char next = text.charAt(end);
            if (Character.isLetterOrDigit(next) || next == '_') return false;
        }
        return true;
    }

    private void applyHighlights() {
        if (textArea == null) return;
        Highlighter h = textArea.getHighlighter();
        try {
            for (MatchRange m : matches) {
                Object tag = h.addHighlight(m.start, m.end,
                    new DefaultHighlighter.DefaultHighlightPainter(matchColor));
                highlightTags.add(tag);
            }
        } catch (BadLocationException ignored) {}
    }

    private void clearHighlights() {
        if (textArea == null) return;
        Highlighter h = textArea.getHighlighter();
        for (Object tag : highlightTags) {
            h.removeHighlight(tag);
        }
        highlightTags.clear();
        if (currentHighlightTag != null) {
            h.removeHighlight(currentHighlightTag);
            currentHighlightTag = null;
        }
    }

    private void navigateToCurrentMatch() {
        if (textArea == null || currentMatchIndex < 0 || currentMatchIndex >= matches.size()) return;
        MatchRange m = matches.get(currentMatchIndex);

        Highlighter h = textArea.getHighlighter();
        if (currentHighlightTag != null) {
            h.removeHighlight(currentHighlightTag);
            currentHighlightTag = null;
        }
        try {
            currentHighlightTag = h.addHighlight(m.start, m.end,
                new DefaultHighlighter.DefaultHighlightPainter(currentMatchColor));
        } catch (BadLocationException ignored) {}

        textArea.setCaretPosition(m.start);
        textArea.moveCaretPosition(m.end);
        try {
            Rectangle r = textArea.modelToView(m.start);
            if (r != null) {
                r.grow(10, 10);
                textArea.scrollRectToVisible(r);
            }
        } catch (BadLocationException ignored) {}

        updateMatchCount();
    }

    private void updateMatchCount() {
        if (matches.isEmpty()) {
            matchCountLabel.setText("0");
        } else {
            matchCountLabel.setText((currentMatchIndex + 1) + " / " + matches.size());
        }
    }

    private void computeThemeColors() {
        boolean dark = false;
        if (textArea != null) {
            Color bg = textArea.getBackground();
            dark = bg.getRed() + bg.getGreen() + bg.getBlue() < 382;
        }
        Color fg;
        if (dark) {
            matchColor = new Color(255, 200, 0, 60);
            currentMatchColor = new Color(255, 200, 0, 120);
            fg = new Color(0xBBBBBB);
        } else {
            matchColor = new Color(255, 200, 0, 40);
            currentMatchColor = new Color(255, 200, 0, 100);
            fg = new Color(0x333333);
        }
        bgColor = ThemeManager.getInstance().resolve("bg.toolbar");
        hoverBg = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60);
        matchCountLabel.setForeground(fg);
        matchCountLabel.setFont(matchCountLabel.getFont().deriveFont(10f));
        searchField.setForeground(fg);
        searchField.setCaretColor(fg);
        replaceField.setForeground(fg);
        replaceField.setCaretColor(fg);
        Color fieldBg = dark ? new Color(0x2B2B2B) : Color.WHITE;
        searchField.setBackground(fieldBg);
        replaceField.setBackground(fieldBg);
        for (JComponent b : new JComponent[]{prevBtn, nextBtn, matchCaseBtn, wordsBtn, regexBtn,
                closeBtn}) {
            b.setForeground(fg);
            b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        }
        Color borderColor = ThemeManager.getInstance().resolve("border.default");
        Color grayFg = ThemeManager.getInstance().resolve("fg.muted");
        for (JComponent b : new JComponent[]{replaceBtn, replaceAllBtn}) {
            boolean en = b.isEnabled();
            b.setForeground(en ? fg : grayFg);
            b.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, en ? borderColor : grayFg));
        }
        Color accentColor = ThemeManager.getInstance().resolve("accent.green");
        for (JToggleButton b : new JToggleButton[]{matchCaseBtn, wordsBtn, regexBtn}) {
            if (b.isSelected()) {
                b.setBackground(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 40));
            } else {
                b.setBackground(new Color(0, 0, 0, 0));
            }
        }
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor));
    }

    private void registerDocListener() {
        if (textArea == null) return;
        if (docListener != null) {
            textArea.getDocument().removeDocumentListener(docListener);
        }
        docListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleSearch(); }
        };
        textArea.getDocument().addDocumentListener(docListener);
    }
}
