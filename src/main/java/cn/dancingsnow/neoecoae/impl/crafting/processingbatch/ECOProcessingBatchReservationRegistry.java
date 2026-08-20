package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Target-scoped physical capacity registry for processing-provider batch proposals. */
public final class ECOProcessingBatchReservationRegistry {
    private final Object lock = new Object();
    private final LongSupplier clock;
    private final long leaseTtl;
    private final Map<Object, List<Lease>> leasesByTarget = new HashMap<>();

    public ECOProcessingBatchReservationRegistry(LongSupplier clock, long leaseTtl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (leaseTtl <= 0L) {
            throw new IllegalArgumentException("leaseTtl must be positive");
        }
        this.leaseTtl = leaseTtl;
    }

    ECOProcessingBatchReservation tryReserve(
            Object targetIdentity,
            long physicalCapacityCrafts,
            long requestedCrafts) {
        if (targetIdentity == null || physicalCapacityCrafts <= 0L || requestedCrafts <= 0L) {
            return null;
        }
        synchronized (lock) {
            cleanupLocked();
            List<Lease> current = leasesByTarget.computeIfAbsent(
                    targetIdentity, ignored -> new ArrayList<>());
            long used = 0L;
            for (Lease lease : current) {
                used = saturatingAdd(used, lease.reservedCrafts);
            }
            long remaining = physicalCapacityCrafts == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : physicalCapacityCrafts - used;
            if (remaining <= 0L) {
                return null;
            }
            long reserved = Math.min(requestedCrafts, remaining);
            Lease lease = new Lease(
                    targetIdentity,
                    reserved,
                    safeAdd(clock.getAsLong(), leaseTtl)
            );
            current.add(lease);
            return new ECOProcessingBatchReservation(this, lease);
        }
    }

    public int activeReservations(Object targetIdentity) {
        synchronized (lock) {
            cleanupLocked();
            List<Lease> current = leasesByTarget.get(targetIdentity);
            return current == null ? 0 : current.size();
        }
    }

    boolean isActive(ECOProcessingBatchReservation reservation) {
        synchronized (lock) {
            cleanupLocked();
            return reservation != null && reservation.lease.active;
        }
    }

    void transferOwnership(ECOProcessingBatchReservation reservation, BooleanSupplier pending) {
        synchronized (lock) {
            cleanupLocked();
            Lease lease = reservation.lease;
            if (!lease.active) {
                return;
            }
            lease.ownershipTransferred = true;
            lease.pending = pending;
            if (!isPending(lease)) {
                removeLocked(lease);
            }
        }
    }

    void release(ECOProcessingBatchReservation reservation) {
        if (reservation == null) {
            return;
        }
        synchronized (lock) {
            removeLocked(reservation.lease);
        }
    }

    private void cleanupLocked() {
        long now = clock.getAsLong();
        for (Iterator<List<Lease>> groups = leasesByTarget.values().iterator(); groups.hasNext();) {
            List<Lease> leases = groups.next();
            for (Iterator<Lease> entries = leases.iterator(); entries.hasNext();) {
                Lease lease = entries.next();
                if (!lease.active || (!isPending(lease) && now >= lease.expiresAt)) {
                    lease.active = false;
                    entries.remove();
                }
            }
            if (leases.isEmpty()) {
                groups.remove();
            }
        }
    }

    private boolean isPending(Lease lease) {
        if (!lease.ownershipTransferred || lease.pending == null) {
            return false;
        }
        try {
            return lease.pending.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void removeLocked(Lease lease) {
        if (!lease.active) {
            return;
        }
        lease.active = false;
        List<Lease> group = leasesByTarget.get(lease.targetIdentity);
        if (group != null) {
            group.remove(lease);
            if (group.isEmpty()) {
                leasesByTarget.remove(lease.targetIdentity);
            }
        }
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    static final class Lease {
        final Object targetIdentity;
        final long reservedCrafts;
        final long expiresAt;
        boolean ownershipTransferred;
        BooleanSupplier pending;
        boolean active = true;

        private Lease(Object targetIdentity, long reservedCrafts, long expiresAt) {
            this.targetIdentity = targetIdentity;
            this.reservedCrafts = reservedCrafts;
            this.expiresAt = expiresAt;
        }
    }
}
