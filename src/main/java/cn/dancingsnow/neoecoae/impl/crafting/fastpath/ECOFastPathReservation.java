package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** A bounded lease over one fast-path route, worker slot, and machine lane. */
public final class ECOFastPathReservation implements AutoCloseable {
    private final ECOFastPathReservationRegistry registry;
    private final ECOFastPathOfferProposal proposal;
    private final long reservedLogicalBatchSize;
    private final long acceptedAtTick;
    private final AtomicBoolean released = new AtomicBoolean();
    private volatile State state = State.ACTIVE;
    private volatile boolean ownershipTransferred;

    ECOFastPathReservation(
            ECOFastPathReservationRegistry registry,
            ECOFastPathOfferProposal proposal,
            long reservedLogicalBatchSize,
            long acceptedAtTick) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.proposal = Objects.requireNonNull(proposal, "proposal");
        this.reservedLogicalBatchSize = reservedLogicalBatchSize;
        this.acceptedAtTick = acceptedAtTick;
    }

    public long reservedLogicalBatchSize() {
        return reservedLogicalBatchSize;
    }

    public long acceptedAtTick() {
        return acceptedAtTick;
    }

    public State state() {
        return state;
    }

    public boolean ownershipTransferred() {
        return ownershipTransferred;
    }

    public boolean isActiveAt(long tick) {
        if (state != State.ACTIVE) {
            return false;
        }
        if (tick < proposal.validFromTick() || tick >= proposal.expiresAtTick()) {
            expire();
            return false;
        }
        return registry.isCurrent(this);
    }

    public void transferOwnership() {
        if (state != State.ACTIVE || released.get()) {
            return;
        }
        if (!registry.transferOwnership(this)) {
            return;
        }
        ownershipTransferred = true;
        state = State.OWNERSHIP_TRANSFERRED;
    }

    public void expire() {
        if (state != State.ACTIVE) {
            return;
        }
        if (released.compareAndSet(false, true)) {
            registry.release(this);
            state = State.EXPIRED;
        }
    }

    @Override
    public void close() {
        if (state != State.ACTIVE) {
            return;
        }
        if (released.compareAndSet(false, true)) {
            registry.release(this);
            state = State.RELEASED;
        }
    }

    ECOFastPathOfferProposal proposal() {
        return proposal;
    }

    boolean markReleasedByRegistry() {
        return released.compareAndSet(false, true);
    }

    enum InternalState {
        ACTIVE
    }

    public enum State {
        ACTIVE,
        RELEASED,
        OWNERSHIP_TRANSFERRED,
        EXPIRED,
        STALE
    }
}
