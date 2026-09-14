package com.atir.molecularmanipulator.api.crafting;

/** Compile-only ABI declaration. Never shipped. */
public interface OmniBatchDelivery {
    OmniBatchRequest request();
    void accept(Receipt receipt);
    void reject(Rejection rejection);
    enum Ownership { TRANSFERRED_TO_DURABLE_TARGET, PERSISTED_PROVIDER_QUEUE }
    enum Backpressure { MAY_ACCEPT_MORE, RECHECK_NEXT_TICK, SATURATED }
    enum RejectReason { CAPACITY_CHANGED, PATTERN_UNAVAILABLE, UNSUPPORTED_INPUT, INTERNAL_ERROR, OTHER }
    record Receipt(Ownership ownership, Backpressure backpressure) {}
    record Rejection(RejectReason reason) {}
}
