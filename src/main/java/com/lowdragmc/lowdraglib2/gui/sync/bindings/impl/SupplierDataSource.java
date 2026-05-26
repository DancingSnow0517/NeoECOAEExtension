package com.lowdragmc.lowdraglib2.gui.sync.bindings.impl;

import java.util.function.Supplier;

public class SupplierDataSource<T> {
    private final Supplier<T> supplier;

    private SupplierDataSource(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> SupplierDataSource<T> of(Supplier<T> supplier) {
        return new SupplierDataSource<>(supplier);
    }

    public T get() {
        return supplier.get();
    }
}
