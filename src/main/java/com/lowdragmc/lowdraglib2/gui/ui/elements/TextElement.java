package com.lowdragmc.lowdraglib2.gui.ui.elements;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class TextElement extends UIElement {
    public TextElement setText(Component text) {
        return this;
    }

    public TextElement setText(String text) {
        return this;
    }

    public TextElement setText(String text, boolean translate) {
        return this;
    }

    @Override
    public TextElement textStyle(Consumer<TextStyle> consumer) {
        consumer.accept(new TextStyle());
        return this;
    }

    public static class TextStyle {
        public TextStyle textWrap(TextWrap value) { return this; }
        public TextStyle adaptiveHeight(boolean value) { return this; }
        public TextStyle adaptiveWidth(boolean value) { return this; }
        public TextStyle textShadow(boolean value) { return this; }
        public TextStyle textColor(int value) { return this; }
    }
}
