package com.lowdragmc.lowdraglib2.gui.ui.style;

public class Property<T> {
    private final String name;
    private final T defaultValue;

    public Property(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String name() {
        return name;
    }

    public T defaultValue() {
        return defaultValue;
    }
}
