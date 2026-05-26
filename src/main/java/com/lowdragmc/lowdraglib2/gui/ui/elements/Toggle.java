package com.lowdragmc.lowdraglib2.gui.ui.elements;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.function.Consumer;

public class Toggle extends UIElement {
    public final Button toggleButton = new Button();

    public Toggle noText() {
        return this;
    }

    public Toggle toggleStyle(Consumer<ToggleStyle> consumer) {
        consumer.accept(new ToggleStyle());
        return this;
    }

    public Toggle toggleButton(Consumer<Button> consumer) {
        consumer.accept(new Button());
        return this;
    }

    public Toggle bindDataSource(Object source) {
        return this;
    }

    public Toggle setOnToggleChanged(Consumer<Boolean> consumer) {
        return this;
    }

    public static class ToggleStyle {
        public ToggleStyle markTexture(IGuiTexture texture) { return this; }
        public ToggleStyle unmarkTexture(IGuiTexture texture) { return this; }
    }
}
