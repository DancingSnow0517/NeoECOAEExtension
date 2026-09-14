package com.atir.molecularmanipulator.api.crafting;

/** Compile-only ABI declaration. Never shipped. */
public interface OmniBatchAdmission extends AutoCloseable {
    long maxCrafts();
    void commit(OmniBatchDelivery delivery);
    default void close() {}
}
