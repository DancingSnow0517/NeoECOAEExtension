package cn.dancingsnow.neoecoae.api.me;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongPredicate;

/** Pure, server-thread-only N/N2/N4 probe state machine. Provider ownership is handled by its caller. */
public final class ECOBatchProbeScheduler {
    public static final int MAX_BATCH_PROBES_PER_TASK_PER_TICK = 3;
    public static final int MAX_BATCH_PROBES_PER_CPU_PER_TICK = 64;

    private ECOBatchProbeScheduler() {
    }

    public static boolean canStartCpuProbeWindow(int probesAlreadyUsed, int requiredProbes) {
        if (probesAlreadyUsed < 0 || requiredProbes <= 0) return false;
        return probesAlreadyUsed <= MAX_BATCH_PROBES_PER_CPU_PER_TICK - requiredProbes;
    }

    public static ProbeResult probe(
        BatchCapacityProbeState state,
        long legalUpperBound,
        int availableProbeBudget,
        LongPredicate simulation
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(simulation, "simulation");
        int allowed = Math.min(MAX_BATCH_PROBES_PER_TASK_PER_TICK, Math.max(0, availableProbeBudget));
        long upper = state.startingUpperBound(legalUpperBound);
        if (allowed == 0 || upper <= 0L) {
            return new ProbeResult(upper, new long[0], 0L);
        }

        long[] candidates = candidates(upper);
        int attempts = Math.min(allowed, candidates.length);
        long[] attempted = new long[attempts];
        for (int i = 0; i < attempts; i++) {
            long candidate = candidates[i];
            attempted[i] = candidate;
            if (simulation.test(candidate)) {
                state.recordSuccess(candidate);
                return new ProbeResult(upper, attempted, candidate);
            }
            state.recordFailedCandidate(candidate);
        }
        if (attempts == candidates.length) {
            state.continueBelow(candidates[candidates.length - 1]);
        }
        return new ProbeResult(upper, attempted, 0L);
    }

    static long[] candidates(long upper) {
        if (upper <= 0L) return new long[0];
        long[] raw = { upper, upper / 2L, upper / 4L };
        long[] compact = new long[3];
        int size = 0;
        for (long candidate : raw) {
            if (candidate <= 0L) continue;
            if (size > 0 && compact[size - 1] == candidate) continue;
            compact[size++] = candidate;
        }
        return Arrays.copyOf(compact, size);
    }

    public record ProbeResult(long upperBound, long[] candidates, long selected) {
        public ProbeResult {
            candidates = candidates.clone();
        }

        @Override public long[] candidates() {
            return candidates.clone();
        }

        public int probeCount() {
            return candidates.length;
        }
    }
}
