package com.lowdragmc.lowdraglib2.syncdata.storage;

public class FieldManagedStorage implements IManagedStorage {
    private final Object owner;

    public FieldManagedStorage(Object owner) {
        this.owner = owner;
    }

    public Object owner() {
        return owner;
    }
}
