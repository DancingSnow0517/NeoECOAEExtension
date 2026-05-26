package com.lowdragmc.lowdraglib2.gui.ui.style;

public class PropertyRegistry {
    public static <T> Property<T> create(String name, T defaultValue) {
        return new Property<>(name, defaultValue);
    }
}
