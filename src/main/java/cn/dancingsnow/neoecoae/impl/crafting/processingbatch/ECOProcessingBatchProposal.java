package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import appeng.api.stacks.KeyCounter;

/** One-shot processing batch proposal with a second target-capacity validation at commit time. */
public final class ECOProcessingBatchProposal implements AutoCloseable {
    private final ECOProcessingBatchTarget target;
    private final Object targetIdentity;
    private final KeyCounter[] preparedPrototype;
    private final long requestedCrafts;
    private final long physicalCapacityCrafts;
    private final ECOProcessingBatchReservation reservation;
    private boolean attempted;
    private boolean ownershipTransferred;

    private ECOProcessingBatchProposal(
            ECOProcessingBatchTarget target,
            KeyCounter[] preparedPrototype,
            long requestedCrafts,
            long physicalCapacityCrafts,
            ECOProcessingBatchReservation reservation) {
        this.target = target;
        this.targetIdentity = target.identity();
        this.preparedPrototype = preparedPrototype;
        this.requestedCrafts = requestedCrafts;
        this.physicalCapacityCrafts = physicalCapacityCrafts;
        this.reservation = reservation;
    }

    public static ECOProcessingBatchProposal reserve(
            ECOProcessingBatchTarget target,
            KeyCounter[] prototype,
            long requestedCrafts,
            ECOProcessingBatchReservationRegistry registry) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(registry, "registry");
        if (requestedCrafts <= 0L) {
            return null;
        }
        ECOProcessingBatchCapacity capacity = ECOProcessingBatchCapacity.capturePhysical(
                target, prototype);
        if (capacity == null || capacity.maxCrafts() <= 0L) {
            return null;
        }
        ECOProcessingBatchReservation reservation = registry.tryReserve(
                target.identity(), capacity.maxCrafts(), requestedCrafts);
        if (reservation == null) {
            return null;
        }
        return new ECOProcessingBatchProposal(
                target, prototype, requestedCrafts, capacity.maxCrafts(), reservation);
    }

    public long requestedCrafts() {
        return requestedCrafts;
    }

    public long physicalCapacityCrafts() {
        return physicalCapacityCrafts;
    }

    public long reservedCrafts() {
        return reservation.reservedCrafts();
    }

    public boolean hasTransferredInputOwnership() {
        return ownershipTransferred;
    }

    public boolean validate(ECOProcessingBatchTarget currentTarget, KeyCounter[] prototype) {
        if (currentTarget == null || prototype != preparedPrototype
                || !Objects.equals(currentTarget.identity(), targetIdentity)
                || !reservation.active()) {
            return false;
        }
        ECOProcessingBatchCapacity current = ECOProcessingBatchCapacity.capturePhysical(
                currentTarget, preparedPrototype);
        return current != null && current.maxCrafts() == physicalCapacityCrafts;
    }

    public boolean commit(
            ECOProcessingBatchTarget currentTarget,
            KeyCounter[] prototype,
            BooleanSupplier providerCommit) {
        return commit(currentTarget, prototype, providerCommit, () -> false);
    }

    public boolean commit(
            ECOProcessingBatchTarget currentTarget,
            KeyCounter[] prototype,
            BooleanSupplier providerCommit,
            BooleanSupplier pendingInput) {
        Objects.requireNonNull(providerCommit, "providerCommit");
        if (attempted) {
            throw new IllegalStateException("Processing batch proposal has already been attempted");
        }
        if (!validate(currentTarget, prototype)) {
            attempted = true;
            close();
            return false;
        }
        attempted = true;

        final boolean accepted;
        try {
            accepted = providerCommit.getAsBoolean();
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
        if (!accepted) {
            close();
            return false;
        }

        ownershipTransferred = true;
        reservation.transferOwnership(
                new ECOProcessingBatchReservation.BooleanSupplierHolder(pendingInput));
        if (!isPending(pendingInput)) {
            reservation.close();
        }
        return true;
    }

    @Override
    public void close() {
        reservation.close();
    }

    private static boolean isPending(BooleanSupplier pending) {
        if (pending == null) {
            return false;
        }
        try {
            return pending.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
