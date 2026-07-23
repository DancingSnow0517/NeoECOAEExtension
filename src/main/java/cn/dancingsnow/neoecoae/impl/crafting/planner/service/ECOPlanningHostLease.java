package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reserves one ECO computation-host thread for a planning job. */
public final class ECOPlanningHostLease implements AutoCloseable {
    private static final Map<NEComputationCluster, Integer> ACTIVE_JOBS = new WeakHashMap<>();
    private static final long MAX_SEARCH_STATES = 500_000;

    private final NEComputationCluster host;
    private final ECOSolveBudget budget;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ECOPlanningHostLease(NEComputationCluster host) {
        this.host = host;
        long storageStates = host.getAvailableStorage() / 64L;
        long maxStates = Math.max(1, Math.min(MAX_SEARCH_STATES, storageStates));
        int maxDepth = (int) Math.max(1, Math.min(1_024, host.getAvailableStorage() / 4_096L));
        int alternatives = Math.max(1, Math.min(8, 1 + host.getCPUAccelerators() / 16));
        this.budget = new ECOSolveBudget(maxStates, maxDepth, alternatives);
    }

    public static Optional<ECOPlanningHostLease> tryAcquire(Collection<NEComputationCluster> candidates) {
        var ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingInt(NEComputationCluster::getCPUAccelerators)
            .reversed()
            .thenComparing(Comparator.comparingInt(NEComputationCluster::getMaxThreads).reversed())
            .thenComparing(Comparator.comparingLong(NEComputationCluster::getAvailableStorage).reversed()));
        synchronized (ACTIVE_JOBS) {
            for (NEComputationCluster candidate : ordered) {
                if (candidate == null
                    || !candidate.isActive()
                    || candidate.getMaxThreads() <= 0
                    || candidate.getAvailableStorage() <= 0) {
                    continue;
                }
                int active = ACTIVE_JOBS.getOrDefault(candidate, 0);
                if (active >= candidate.getMaxThreads()) {
                    continue;
                }
                ACTIVE_JOBS.put(candidate, active + 1);
                return Optional.of(new ECOPlanningHostLease(candidate));
            }
        }
        return Optional.empty();
    }

    public ECOSolveBudget budget() {
        return budget;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (ACTIVE_JOBS) {
            int active = ACTIVE_JOBS.getOrDefault(host, 0);
            if (active <= 1) {
                ACTIVE_JOBS.remove(host);
            } else {
                ACTIVE_JOBS.put(host, active - 1);
            }
        }
    }
}
