package com.lowdragmc.lowdraglib2.gui.ui.elements;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.function.Consumer;

public class Button extends UIElement {
    public Button setText(String text) { return this; }
    public Button setText(String text, boolean translate) { return this; }
    public Button noText() { return this; }
    public Button icon(IGuiTexture texture) { return this; }
    public Button addPostIcon(IGuiTexture texture) { return this; }
    public Button setOnClick(Consumer<ClickEvent> consumer) { return this; }
    public Button setOnServerClick(Consumer<ClickEvent> consumer) { return this; }
    public Button buttonStyle(Consumer<ButtonStyle> consumer) {
        consumer.accept(new ButtonStyle());
        return this;
    }

    public static class ClickEvent {
    }

    public static class ButtonStyle {
        public ButtonStyle normal(IGuiTexture texture) { return this; }
        public ButtonStyle hover(IGuiTexture texture) { return this; }
        public ButtonStyle pressed(IGuiTexture texture) { return this; }
        public ButtonStyle disabled(IGuiTexture texture) { return this; }
        public ButtonStyle baseTexture(IGuiTexture texture) { return this; }
        public ButtonStyle hoverTexture(IGuiTexture texture) { return this; }
        public ButtonStyle pressedTexture(IGuiTexture texture) { return this; }
    }
}
