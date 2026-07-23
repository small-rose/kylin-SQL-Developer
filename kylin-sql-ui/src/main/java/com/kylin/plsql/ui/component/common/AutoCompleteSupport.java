package com.kylin.plsql.ui.component.common;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Vector;

public class AutoCompleteSupport {
    private static final String KEY = "autocomplete.data";

    public static void install(JComboBox<String> combo) {
        if (!(combo.getEditor().getEditorComponent() instanceof JTextField tf)) return;
        sync(combo);

        boolean[] fromSelection = {false};

        combo.addActionListener(e -> {
            fromSelection[0] = true;
        });

        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                if (!e.isTemporary()) {
                    combo.hidePopup();
                }
            }
        });

        tf.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
            private void filter() {
                if (fromSelection[0]) {
                    fromSelection[0] = false;
                    return;
                }
                if (!tf.hasFocus()) return;
                SwingUtilities.invokeLater(() -> {
                    @SuppressWarnings("unchecked")
                    Vector<String> data = (Vector<String>) combo.getClientProperty(KEY);
                    if (data == null) return;
                    String input = tf.getText();
                    combo.removeAllItems();
                    if (input.isEmpty()) {
                        data.forEach(combo::addItem);
                    } else {
                        String lower = input.toLowerCase();
                        for (String s : data) {
                            if (s.toLowerCase().contains(lower)) combo.addItem(s);
                        }
                    }
                    if (!input.isEmpty() && combo.getItemCount() > 0 && tf.hasFocus()) {
                        combo.showPopup();
                    }
                });
            }
        });
    }

    public static void sync(JComboBox<String> combo) {
        Vector<String> data = new Vector<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            data.add(combo.getItemAt(i));
        }
        combo.putClientProperty(KEY, data);
    }
}
