package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.Objects;

/** Immutable point-in-time offer used by the fast-path reservation registry. */
public final class ECOFastPathOfferProposal {
    private final RouteIdentity route;
    private final MachineIdentity machine;
    private final long publicationRevision;
    private final int providerCapacity;
    private final int machineCapacity;
    private final long routeEpoch;
    private final long workerEpoch;
    private final long machineEpoch;
    private final long validFromTick;
    private final long expiresAtTick;
    private final long maxLogicalBatchSize;
    private final int workerPhysicalCapacity;
    private final int routeCapacity;
    private final int requiredPhysicalThreadSlots;
    private final int laneCapacity;
    private final boolean executable;

    public ECOFastPathOfferProposal(
            RouteIdentity route,
            MachineIdentity machine,
            long publicationRevision,
            int providerCapacity,
            int machineCapacity,
            long routeEpoch,
            long workerEpoch,
            long machineEpoch,
            long validFromTick,
            long expiresAtTick,
            long maxLogicalBatchSize,
            int workerPhysicalCapacity,
            int routeCapacity,
            int requiredPhysicalThreadSlots,
            int laneCapacity,
            boolean executable) {
        this.route = Objects.requireNonNull(route, "route");
        this.machine = Objects.requireNonNull(machine, "machine");
        this.publicationRevision = publicationRevision;
        this.providerCapacity = providerCapacity;
        this.machineCapacity = machineCapacity;
        this.routeEpoch = routeEpoch;
        this.workerEpoch = workerEpoch;
        this.machineEpoch = machineEpoch;
        this.validFromTick = validFromTick;
        this.expiresAtTick = expiresAtTick;
        this.maxLogicalBatchSize = maxLogicalBatchSize;
        this.workerPhysicalCapacity = workerPhysicalCapacity;
        this.routeCapacity = routeCapacity;
        this.requiredPhysicalThreadSlots = requiredPhysicalThreadSlots;
        this.laneCapacity = laneCapacity;
        this.executable = executable;
    }

    public RouteIdentity route() {
        return route;
    }

    public MachineIdentity machine() {
        return machine;
    }

    public long publicationRevision() {
        return publicationRevision;
    }

    public int providerCapacity() {
        return providerCapacity;
    }

    public int machineCapacity() {
        return machineCapacity;
    }

    public long routeEpoch() {
        return routeEpoch;
    }

    public long workerEpoch() {
        return workerEpoch;
    }

    public long machineEpoch() {
        return machineEpoch;
    }

    public long validFromTick() {
        return validFromTick;
    }

    public long expiresAtTick() {
        return expiresAtTick;
    }

    public long maxLogicalBatchSize() {
        return maxLogicalBatchSize;
    }

    public int workerPhysicalCapacity() {
        return workerPhysicalCapacity;
    }

    public int routeCapacity() {
        return routeCapacity;
    }

    public int requiredPhysicalThreadSlots() {
        return requiredPhysicalThreadSlots;
    }

    public int laneCapacity() {
        return laneCapacity;
    }

    public boolean executable() {
        return executable;
    }

    /** The two legacy capacity fields describe the same reservation domain in older callers. */
    int effectiveWorkerCapacity() {
        return Math.max(workerPhysicalCapacity, machineCapacity);
    }

    int effectiveRouteCapacity() {
        return Math.max(routeCapacity, providerCapacity);
    }

    public record EndpointIdentity(String dimension, long id) {
        public EndpointIdentity {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    public record RouteIdentity(EndpointIdentity provider, EndpointIdentity worker) {
        public RouteIdentity {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(worker, "worker");
        }
    }

    public record MachineIdentity(EndpointIdentity controller, int lane) {
        public MachineIdentity {
            Objects.requireNonNull(controller, "controller");
        }
    }
}
