package cn.dancingsnow.neoecoae.api.me;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathDiagnostics;

/**
 * Collects per-tick CPU timing for {@link ECOCraftingCPULogic} and emits the tick profile.
 *
 * <p>All recording methods are no-ops when timing is disabled, so callers do not need to guard
 * them. Callers should still use {@link #isTimingEnabled()} to avoid paying for
 * {@code System.nanoTime()} when timing is off.
 */
public final class ECOCraftingDiagnostics {
    private final ECOCraftingCPU cpu;

    @Nullable
    private CpuTickTiming active;

    ECOCraftingDiagnostics(ECOCraftingCPU cpu) {
        this.cpu = cpu;
    }

    /** Returns whether timing is being collected for the current tick. */
    public boolean isTimingEnabled() {
        return active != null;
    }

    /**
     * Begins timing collection for a CPU tick. Does nothing unless FastPath debugging is enabled.
     *
     * @param cpuTick   the current AE2 tick, used to attribute FastPath apply time
     * @param taskCount number of tasks in the executing job, or 0 when idle
     */
    public void startTickTiming(long cpuTick, int taskCount) {
        if (!NEConfig.debugEcoFastPath) {
            active = null;
            return;
        }
        CpuTickTiming timing = new CpuTickTiming(
            cpuTick,
            System.nanoTime(),
            ECOFastPathDiagnostics.currentTickApplyMicros(cpuTick)
        );
        timing.taskCount = taskCount;
        active = timing;
    }

    /** Emits the tick profile and clears timing state. Safe to call when timing is disabled. */
    public void endTickTiming() {
        CpuTickTiming timing = active;
        active = null;
        if (timing != null) {
            timing.log(cpu.getOwner() == null ? BlockPos.ZERO : cpu.getOwner().getBlockPos());
        }
    }

    // ==================== Phase recording ====================

    public void recordSetup(long micros) {
        if (active != null) {
            active.setupMicros = saturatingAdd(active.setupMicros, micros);
        }
    }

    public void recordOutputRetry(long micros) {
        if (active != null) {
            active.ejectionMicros = saturatingAdd(active.ejectionMicros, micros);
        }
    }

    public void recordScheduler(long micros) {
        if (active != null) {
            active.schedulerMicros = saturatingAdd(active.schedulerMicros, micros);
        }
    }

    public void recordTaskIteration(long micros) {
        if (active != null) {
            active.taskIterationMicros = saturatingAdd(active.taskIterationMicros, micros);
        }
    }

    public void recordTaskState(long micros) {
        if (active != null) {
            active.taskStateMicros = saturatingAdd(active.taskStateMicros, micros);
        }
    }

    public void recordDependency(long micros) {
        if (active != null) {
            active.dependencyMicros = saturatingAdd(active.dependencyMicros, micros);
        }
    }

    public void recordPatternPreparation(long micros) {
        if (active != null) {
            active.patternPreparationMicros = saturatingAdd(active.patternPreparationMicros, micros);
        }
    }

    public void recordPendingInputSnapshot(long micros) {
        if (active != null) {
            active.pendingInputSnapshotMicros =
                saturatingAdd(active.pendingInputSnapshotMicros, micros);
        }
    }

    public void recordInputDiagnostic(long micros) {
        if (active != null) {
            active.inputDiagnosticMicros = saturatingAdd(active.inputDiagnosticMicros, micros);
            active.inputDiagnosticCount++;
        }
    }

    public void recordAccounting(long micros) {
        if (active != null) {
            active.accountingMicros = saturatingAdd(active.accountingMicros, micros);
        }
    }

    public void recordStatusChange(long micros) {
        if (active != null) {
            active.statusChangeMicros = saturatingAdd(active.statusChangeMicros, micros);
        }
    }

    public void recordFastPathCoordination(long micros) {
        if (active != null) {
            active.fastPathCoordinationMicros =
                saturatingAdd(active.fastPathCoordinationMicros, micros);
        }
    }

    // ==================== Fallback recording ====================

    /** Records time spent on any fallback path. Pair with the per-path recorder below. */
    public void recordFallback(long micros) {
        if (active != null) {
            active.fallbackMicros = saturatingAdd(active.fallbackMicros, micros);
        }
    }

    public void recordFallbackFastPath(long micros) {
        if (active != null) {
            active.fallbackFastPathMicros = saturatingAdd(active.fallbackFastPathMicros, micros);
        }
    }

    public void recordAe2ltFallback(long micros) {
        if (active != null) {
            active.fallbackAe2ltMicros = saturatingAdd(active.fallbackAe2ltMicros, micros);
        }
    }

    public void recordMegacellsFallback(long micros) {
        if (active != null) {
            active.fallbackMegacellsMicros = saturatingAdd(active.fallbackMegacellsMicros, micros);
        }
    }

    public void recordProviderFallback(long micros) {
        if (active != null) {
            active.fallbackProviderMicros = saturatingAdd(active.fallbackProviderMicros, micros);
        }
    }

    public void incrementFastPathFallback() {
        if (active != null) {
            active.fastPathFallbackCount++;
        }
    }

    public void incrementAe2ltFallback() {
        if (active != null) {
            active.ae2ltFallbackCount++;
        }
    }

    public void incrementMegacellsFallback() {
        if (active != null) {
            active.megacellsFallbackCount++;
        }
    }

    public void incrementProviderAttempt() {
        if (active != null) {
            active.providerAttemptCount++;
        }
    }

    // ==================== Input diagnostic aggregation ====================

    /** Folds an input-diagnostic context's counters into the tick profile. */
    public void recordInputDiagnosticContext(
        Set<AEKey> uniqueKeys,
        long cacheHits,
        long cacheMisses,
        long networkMicros,
        long dependencyWaitCount,
        long hardFailureCount
    ) {
        if (active == null) {
            return;
        }
        active.inputDiagnosticKeys.addAll(uniqueKeys);
        active.inputDiagnosticUniqueKeys = active.inputDiagnosticKeys.size();
        active.inputDiagnosticCacheHits = saturatingAdd(active.inputDiagnosticCacheHits, cacheHits);
        active.inputDiagnosticCacheMisses =
            saturatingAdd(active.inputDiagnosticCacheMisses, cacheMisses);
        active.inputDiagnosticNetworkMicros =
            saturatingAdd(active.inputDiagnosticNetworkMicros, networkMicros);
        active.dependencyWaitDiagnosticCount =
            saturatingAdd(active.dependencyWaitDiagnosticCount, dependencyWaitCount);
        active.hardFailureDiagnosticCount =
            saturatingAdd(active.hardFailureDiagnosticCount, hardFailureCount);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return Math.max(0L, left);
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    /** Mutable per-tick timing accumulator. */
    private static final class CpuTickTiming {
        private final long cpuTick;
        private final long tickStartedNanos;
        private final long fastPathApplyBaselineMicros;

        private long fastPathApplyMicros;
        private long fastPathCoordinationMicros;
        private long ejectionMicros;
        private long schedulerMicros;
        private long dependencyMicros;
        private long fallbackMicros;
        private long fallbackFastPathMicros;
        private long fallbackAe2ltMicros;
        private long fallbackMegacellsMicros;
        private long fallbackProviderMicros;
        private long fastPathFallbackCount;
        private long ae2ltFallbackCount;
        private long megacellsFallbackCount;
        private long providerAttemptCount;
        private long taskIterationMicros;
        private long taskStateMicros;
        private long patternPreparationMicros;
        private long pendingInputSnapshotMicros;
        private long inputDiagnosticMicros;
        private long inputDiagnosticUniqueKeys;
        private long inputDiagnosticCacheHits;
        private long inputDiagnosticCacheMisses;
        private long inputDiagnosticNetworkMicros;
        private long dependencyWaitDiagnosticCount;
        private long hardFailureDiagnosticCount;
        private final Set<AEKey> inputDiagnosticKeys = new HashSet<>();
        private long accountingMicros;
        private long statusChangeMicros;
        private long setupMicros;
        private long inputDiagnosticCount;
        private int taskCount;

        private CpuTickTiming(long cpuTick, long tickStartedNanos, long fastPathApplyBaselineMicros) {
            this.cpuTick = cpuTick;
            this.tickStartedNanos = tickStartedNanos;
            this.fastPathApplyBaselineMicros = fastPathApplyBaselineMicros;
        }

        private void log(BlockPos position) {
            long totalCpuTickMicros = Math.max(0L, (System.nanoTime() - tickStartedNanos) / 1_000L);
            fastPathApplyMicros = Math.max(
                0L,
                ECOFastPathDiagnostics.currentTickApplyMicros(cpuTick) - fastPathApplyBaselineMicros
            );
            long accounted = saturatingAdd(
                saturatingAdd(
                    saturatingAdd(
                        saturatingAdd(fastPathApplyMicros, fastPathCoordinationMicros),
                        ejectionMicros
                    ),
                    saturatingAdd(schedulerMicros, dependencyMicros)
                ),
                saturatingAdd(
                    saturatingAdd(
                        saturatingAdd(fallbackMicros, taskIterationMicros),
                        saturatingAdd(taskStateMicros, patternPreparationMicros)
                    ),
                    saturatingAdd(
                        saturatingAdd(pendingInputSnapshotMicros, inputDiagnosticMicros),
                        saturatingAdd(
                            saturatingAdd(accountingMicros, statusChangeMicros),
                            setupMicros
                        )
                    )
                )
            );
            long trueOtherMicros = Math.max(0L, totalCpuTickMicros - accounted);
            ECOFastPathDiagnostics.logCpuTickProfile(
                position,
                cpuTick,
                totalCpuTickMicros,
                fastPathApplyMicros,
                fastPathCoordinationMicros,
                ejectionMicros,
                schedulerMicros,
                dependencyMicros,
                fallbackMicros,
                fallbackFastPathMicros,
                fallbackAe2ltMicros,
                fallbackMegacellsMicros,
                fallbackProviderMicros,
                fastPathFallbackCount,
                ae2ltFallbackCount,
                megacellsFallbackCount,
                providerAttemptCount,
                taskIterationMicros,
                taskStateMicros,
                patternPreparationMicros,
                pendingInputSnapshotMicros,
                inputDiagnosticMicros,
                accountingMicros,
                statusChangeMicros,
                setupMicros,
                trueOtherMicros,
                inputDiagnosticCount,
                inputDiagnosticUniqueKeys,
                inputDiagnosticCacheHits,
                inputDiagnosticCacheMisses,
                inputDiagnosticNetworkMicros,
                dependencyWaitDiagnosticCount,
                hardFailureDiagnosticCount,
                taskCount
            );
        }
    }
}
