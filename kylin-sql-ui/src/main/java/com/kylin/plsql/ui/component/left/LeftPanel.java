package com.kylin.plsql.ui.component.left;

import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.ui.component.common.VerticalTabButton;

import javax.swing.*;
import java.awt.*;

/** Collapsible left panel with DATABASE tab for object browser. */
public class LeftPanel extends JPanel {
    private final ThemeManager theme = ThemeManager.getInstance();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final JPanel tabStrip;
    private final ObjectBrowser browser;
    private final LocalFileBrowser fileBrowser;
    private boolean expanded = true;
    private int activeIdx = -1;
    private final Runnable onToggle;

    private enum Tab { DATABASE, FILES }

    public LeftPanel(ObjectBrowser browser, LocalFileBrowser fileBrowser, Runnable onToggle) {
        this.onToggle = onToggle;
        this.browser = browser;
        this.fileBrowser = fileBrowser;
        setLayout(new BorderLayout(0, 0));

        tabStrip = new JPanel();
        tabStrip.setLayout(new BoxLayout(tabStrip, BoxLayout.Y_AXIS));
        tabStrip.setPreferredSize(new Dimension(28, 0));
        tabStrip.setOpaque(true);
        tabStrip.setBackground(theme.resolve("bg.main"));

        contentPanel.add(browser, Tab.DATABASE.name());
        contentPanel.add(fileBrowser, Tab.FILES.name());

        add(tabStrip, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        addTab("Database", Tab.DATABASE);
        addTab("Files", Tab.FILES);
        tabStrip.add(Box.createVerticalGlue());

        selectTab(Tab.DATABASE);
        applyTheme();
    }

    public LocalFileBrowser getFileBrowser() { return fileBrowser; }

    private void addTab(String label, Tab tab) {
        VerticalTabButton btn = new VerticalTabButton(label);
        btn.addActionListener(e -> selectTab(tab));
        tabStrip.add(btn);
    }

    public void selectTab(Tab tab) {
        if (expanded && tab.ordinal() == activeIdx) {
            expanded = false;
            contentPanel.setVisible(false);
            updateTabActive();
            revalidate();
            if (onToggle != null) onToggle.run();
            return;
        }
        activeIdx = tab.ordinal();
        cardLayout.show(contentPanel, tab.name());
        boolean wasCollapsed = !expanded;
        if (wasCollapsed) {
            expanded = true;
            contentPanel.setVisible(true);
        }
        updateTabActive();
        revalidate();
        if (wasCollapsed && onToggle != null) onToggle.run();
    }

    public void selectFilesTab() {
        selectTab(Tab.FILES);
    }

    public void selectDatabaseTab() {
        selectTab(Tab.DATABASE);
    }

    public void ensureDatabaseTab() {
        if (!expanded || activeIdx != Tab.DATABASE.ordinal()) {
            selectTab(Tab.DATABASE);
        }
    }

    public boolean isDatabaseTabActive() { return expanded && activeIdx == Tab.DATABASE.ordinal(); }

    private void updateTabActive() {
        int idx = 0;
        for (Component c : tabStrip.getComponents()) {
            if (c instanceof VerticalTabButton vtb) {
                vtb.setActive(expanded && idx == activeIdx);
                idx++;
            }
        }
    }

    public void applyTheme() {
        setBackground(theme.resolve("bg.main"));
        tabStrip.setBackground(theme.resolve("bg.main"));
        tabStrip.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, theme.resolve("border.default")));
        contentPanel.setBackground(theme.resolve("bg.main"));
        contentPanel.setBorder(null);
        browser.applyTheme();
        fileBrowser.applyTheme();
    }

    public boolean isExpanded() { return expanded; }

    @Override
    public Dimension getMinimumSize() { return new Dimension(28, 0); }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(expanded ? 250 : 28, super.getPreferredSize().height);
    }
}
