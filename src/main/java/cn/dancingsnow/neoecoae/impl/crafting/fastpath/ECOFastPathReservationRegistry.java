package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-dispatcher ownership registry for fast-path proposals.
 *
 * <p>Epochs make an offer point-in-time data. A release returns capacity, while ownership
 * transfer advances the route and endpoint epochs so sibling offers cannot be reused.</p>
 */
public final class ECOFastPathReservationRegistry {
    private final Object lock = new Object();
    private final Map<ECOFastPathOfferProposal.RouteIdentity, Long> routeEpochs = new HashMap<>();
    private final Map<ECOFastPathOfferProposal.EndpointIdentity, Long> endpointEpochs = new HashMap<>();
    private final Map<ECOFastPathOfferProposal.RouteIdentity, Integer> routeUsage = new HashMap<>();
    private final Map<ECOFastPathOfferProposal.EndpointIdentity, Integer> workerUsage = new HashMap<>();
    private final Map<ECOFastPathOfferProposal.MachineIdentity, Integer> machineUsage = new HashMap<>();
    private final Map<ECOFastPathReservation, Usage> active = new HashMap<>();

    public Epochs captureEpochs(
            ECOFastPathOfferProposal.RouteIdentity route,
            ECOFastPathOfferProposal.EndpointIdentity worker,
            ECOFastPathOfferProposal.EndpointIdentity controller) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(controller, "controller");
        synchronized (lock) {
            return new Epochs(
                    routeEpochs.getOrDefault(route, 1L),
                    endpointEpochs.getOrDefault(worker, 1L),
                    endpointEpochs.getOrDefault(controller, 1L)
            );
        }
    }

    public void capacityChanged(
            ECOFastPathOfferProposal.EndpointIdentity worker,
            ECOFastPathOfferProposal.EndpointIdentity controller) {
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(controller, "controller");
        synchronized (lock) {
            endpointEpochs.put(worker, next(endpointEpochs.getOrDefault(worker, 1L)));
            endpointEpochs.put(controller, next(endpointEpochs.getOrDefault(controller, 1L)));
        }
    }

    public ECOFastPathReservation tryAcquire(
            ECOFastPathOfferProposal offer,
            long requestedLogicalBatchSize,
            long tick) {
        if (offer == null || requestedLogicalBatchSize <= 0L || !offer.executable()
                || offer.requiredPhysicalThreadSlots() <= 0
                || offer.maxLogicalBatchSize() <= 0L
                || requestedLogicalBatchSize > offer.maxLogicalBatchSize()
                || requestedLogicalBatchSize > offer.laneCapacity()
                || tick < offer.validFromTick() || tick >= offer.expiresAtTick()) {
            return null;
        }

        synchronized (lock) {
            if (!epochsMatch(offer)) {
                return null;
            }
            int routeLimit = offer.effectiveRouteCapacity();
            int workerLimit = offer.effectiveWorkerCapacity();
            int requiredSlots = offer.requiredPhysicalThreadSlots();
            if (routeLimit <= 0 || workerLimit <= 0) {
                return null;
            }
            int currentRoute = routeUsage.getOrDefault(offer.route(), 0);
            int currentWorker = workerUsage.getOrDefault(offer.route().worker(), 0);
            int currentMachine = machineUsage.getOrDefault(offer.machine(), 0);
            if (currentRoute >= routeLimit
                    || currentWorker > workerLimit - requiredSlots
                    || currentMachine > 0) {
                return null;
            }

            ECOFastPathReservation reservation = new ECOFastPathReservation(
                    this, offer, requestedLogicalBatchSize, tick);
            routeUsage.put(offer.route(), currentRoute + 1);
            workerUsage.put(offer.route().worker(), currentWorker + requiredSlots);
            machineUsage.put(offer.machine(), currentMachine + 1);
            active.put(reservation, new Usage(requiredSlots));
            return reservation;
        }
    }

    boolean isCurrent(ECOFastPathReservation reservation) {
        synchronized (lock) {
            if (!active.containsKey(reservation)) {
                return false;
            }
            if (!epochsMatch(reservation.proposal())) {
                active.remove(reservation);
                decrement(reservation.proposal());
                reservation.markReleasedByRegistry();
                return false;
            }
            return true;
        }
    }

    boolean transferOwnership(ECOFastPathReservation reservation) {
        synchronized (lock) {
            if (!active.containsKey(reservation) || !epochsMatch(reservation.proposal())) {
                return false;
            }
            active.remove(reservation);
            decrement(reservation.proposal());
            ECOFastPathOfferProposal offer = reservation.proposal();
            routeEpochs.put(offer.route(), next(routeEpochs.getOrDefault(offer.route(), 1L)));
            endpointEpochs.put(
                    offer.route().worker(),
                    next(endpointEpochs.getOrDefault(offer.route().worker(), 1L))
            );
            endpointEpochs.put(
                    offer.machine().controller(),
                    next(endpointEpochs.getOrDefault(offer.machine().controller(), 1L))
            );
            return true;
        }
    }

    void release(ECOFastPathReservation reservation) {
        synchronized (lock) {
            if (active.remove(reservation) != null) {
                decrement(reservation.proposal());
            }
        }
    }

    private boolean epochsMatch(ECOFastPathOfferProposal offer) {
        return routeEpochs.getOrDefault(offer.route(), 1L) == offer.routeEpoch()
                && endpointEpochs.getOrDefault(offer.route().worker(), 1L) == offer.workerEpoch()
                && endpointEpochs.getOrDefault(offer.machine().controller(), 1L) == offer.machineEpoch();
    }

    private void decrement(ECOFastPathOfferProposal offer) {
        decrement(routeUsage, offer.route(), 1);
        decrement(workerUsage, offer.route().worker(), offer.requiredPhysicalThreadSlots());
        decrement(machineUsage, offer.machine(), 1);
    }

    private static <K> void decrement(Map<K, Integer> values, K key, int amount) {
        int remaining = values.getOrDefault(key, 0) - amount;
        if (remaining <= 0) {
            values.remove(key);
        } else {
            values.put(key, remaining);
        }
    }

    private static long next(long value) {
        return value == Long.MAX_VALUE ? 1L : value + 1L;
    }

    private record Usage(int physicalSlots) {
    }

    public record Epochs(long routeEpoch, long workerEpoch, long machineEpoch) {
    }
}
