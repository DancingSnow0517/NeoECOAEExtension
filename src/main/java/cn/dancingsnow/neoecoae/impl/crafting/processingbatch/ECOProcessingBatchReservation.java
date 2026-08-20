package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Internal lease returned by the processing batch reservation registry. */
final class ECOProcessingBatchReservation implements AutoCloseable {
    private final ECOProcessingBatchReservationRegistry registry;
    final ECOProcessingBatchReservationRegistry.Lease lease;
    private final AtomicBoolean closed = new AtomicBoolean();

    ECOProcessingBatchReservation(
            ECOProcessingBatchReservationRegistry registry,
            ECOProcessingBatchReservationRegistry.Lease lease) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lease = Objects.requireNonNull(lease, "lease");
    }

    long reservedCrafts() {
        return lease.reservedCrafts;
    }

    boolean active() {
        return !closed.get() && registry.isActive(this);
    }

    void transferOwnership(BooleanSupplierHolder pending) {
        registry.transferOwnership(this, pending == null ? null : pending.supplier);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            registry.release(this);
        }
    }

    record BooleanSupplierHolder(java.util.function.BooleanSupplier supplier) {
    }
}
