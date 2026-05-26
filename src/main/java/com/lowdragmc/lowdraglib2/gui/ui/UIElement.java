package com.lowdragmc.lowdraglib2.gui.ui;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UIElement {
    private final List<UIElement> children = new ArrayList<>();
    private final Layout layout = new Layout();

    public UIElement addChild(UIElement child) {
        children.add(child);
        return this;
    }

    public UIElement addChildren(UIElement... children) {
        for (UIElement child : children) {
            addChild(child);
        }
        return this;
    }

    public UIElement addClass(String name) {
        return this;
    }

    public UIElement layout(Consumer<Layout> consumer) {
        consumer.accept(layout);
        return this;
    }

    public Layout getLayout() {
        return layout;
    }

    public UIElement style(Consumer<Style> consumer) {
        consumer.accept(new Style());
        return this;
    }

    public UIElement textStyle(Consumer<com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.TextStyle> consumer) {
        consumer.accept(new com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.TextStyle());
        return this;
    }

    public UIElement addEventListener(Object event, Consumer<UIEvents.HoverTooltipEvent> consumer) {
        return this;
    }

    public static class Layout {
        public Layout paddingAll(int value) { return this; }
        public Layout paddingBottom(int value) { return this; }
        public Layout gapAll(int value) { return this; }
        public Layout flexDirection(FlexDirection value) { return this; }
        public Layout alignItems(AlignItems value) { return this; }
        public Layout alignSelf(AlignItems value) { return this; }
        public Layout justifyItems(AlignItems value) { return this; }
        public Layout justifyContent(AlignContent value) { return this; }
        public Layout display(TaffyDisplay value) { return this; }
        public Layout positionType(TaffyPosition value) { return this; }
        public Layout marginTop(int value) { return this; }
        public Layout marginBottom(int value) { return this; }
        public Layout marginLeft(int value) { return this; }
        public Layout marginRight(int value) { return this; }
        public Layout left(int value) { return this; }
        public Layout right(int value) { return this; }
        public Layout top(int value) { return this; }
        public Layout width(int value) { return this; }
        public Layout widthPercent(int value) { return this; }
        public Layout height(int value) { return this; }
        public Layout heightPercent(int value) { return this; }
        public Layout size(int width, int height) { return this; }
        public Layout gridTemplateColumns(String value) { return this; }
        public Layout gridTemplateRows(String value) { return this; }
        public Layout gridRow(String value) { return this; }
        public Layout gridColumn(String value) { return this; }
    }

    public static class Style {
        public Style background(IGuiTexture texture) { return this; }
    }
}
