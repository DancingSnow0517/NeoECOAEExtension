package com.lowdragmc.lowdraglib2.gui.sync.bindings.impl;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DataBindingBuilder<T> {
    public static DataBindingBuilder<Boolean> bool(BooleanSupplier getter, Consumer<Boolean> setter) {
        return new DataBindingBuilder<>();
    }

    public static DataBindingBuilder<Float> floatValS2C(Supplier<Float> getter) {
        return new DataBindingBuilder<>();
    }

    public Object build() {
        return this;
    }
}
