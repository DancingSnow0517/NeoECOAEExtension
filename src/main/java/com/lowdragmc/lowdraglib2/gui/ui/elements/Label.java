package com.lowdragmc.lowdraglib2.gui.ui.elements;

import net.minecraft.network.chat.Component;

public class Label extends TextElement {
    public Label bindDataSource(Object source) {
        return this;
    }

    @Override
    public Label setText(String text) {
        return this;
    }

    @Override
    public Label setText(Component text) {
        return this;
    }
}
