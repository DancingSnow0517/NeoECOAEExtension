package com.lowdragmc.lowdraglib2.gui.ui.elements;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;

import java.util.function.Consumer;

public class ProgressBar extends UIElement {
    public ProgressBar progressBarStyle(Consumer<ProgressBarStyle> consumer) {
        consumer.accept(new ProgressBarStyle());
        return this;
    }

    public ProgressBar bind(Object binding) {
        return this;
    }

    public ProgressBar setMaxValue(int value) { return this; }
    public float getValue() { return 0; }
    public ProgressBar barContainer(Consumer<UIElement> consumer) {
        consumer.accept(new UIElement());
        return this;
    }

    public ProgressBar label(Consumer<TextElement> consumer) {
        consumer.accept(new TextElement());
        return this;
    }

    public static class ProgressBarStyle {
        public ProgressBarStyle fillDirection(FillDirection direction) { return this; }
        public ProgressBarStyle interpolate(boolean value) { return this; }
        public ProgressBarStyle emptyTexture(IGuiTexture texture) { return this; }
        public ProgressBarStyle fullTexture(IGuiTexture texture) { return this; }
    }
}
