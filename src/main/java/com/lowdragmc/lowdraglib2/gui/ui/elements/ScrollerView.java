package com.lowdragmc.lowdraglib2.gui.ui.elements;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.function.Consumer;

public class ScrollerView extends UIElement {
    private final UIElement viewContainer = new UIElement();

    public ScrollerView viewContainer(Consumer<UIElement> consumer) {
        consumer.accept(viewContainer);
        return this;
    }

    public ScrollerView addScrollViewChild(UIElement child) {
        viewContainer.addChild(child);
        return this;
    }

    public ScrollerView addScrollViewChildren(UIElement... children) {
        for (UIElement child : children) {
            viewContainer.addChild(child);
        }
        return this;
    }
}
