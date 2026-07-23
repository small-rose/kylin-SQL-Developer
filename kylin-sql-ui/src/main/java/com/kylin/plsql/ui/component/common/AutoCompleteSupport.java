package com.kylin.plsql.ui.component.common;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Vector;

public class AutoCompleteSupport {
    public static void install(JComboBox<String> combo) {
        if (!(combo.getEditor().getEditorComponent() instanceof JTextField tf)) return;
        Vector<String> original = new Vector<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            original.add(combo.getItemAt(i));
        }
        tf.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
            private void filter() {
                SwingUtilities.invokeLater(() -> {
                    String input = tf.getText();
                    combo.removeAllItems();
                    if (input.isEmpty()) {
                        original.forEach(combo::addItem);
                    } else {
                        String lower = input.toLowerCase();
                        for (String s : original) {
                            if (s.toLowerCase().contains(lower)) combo.addItem(s);
                        }
                    }
                    if (combo.getItemCount() > 0) combo.showPopup();
                });
            }
        });
    }
}
