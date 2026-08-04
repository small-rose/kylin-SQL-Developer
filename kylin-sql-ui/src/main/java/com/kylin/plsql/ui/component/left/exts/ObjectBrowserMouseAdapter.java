package com.kylin.plsql.ui.component.left.exts;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** 树节点鼠标事件适配器，处理单击、双击（加载列/打开对象）、右键弹出菜单。 */


public class ObjectBrowserMouseAdapter extends MouseAdapter {
    private final Consumer<MouseEvent> clickHandler;
    private final Consumer<MouseEvent> popupHandler;

    public ObjectBrowserMouseAdapter(
            Consumer<MouseEvent> clickHandler,
            Consumer<MouseEvent> popupHandler) {
        this.clickHandler = clickHandler;
        this.popupHandler = popupHandler;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        clickHandler.accept(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) popupHandler.accept(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) popupHandler.accept(e);
    }
}
