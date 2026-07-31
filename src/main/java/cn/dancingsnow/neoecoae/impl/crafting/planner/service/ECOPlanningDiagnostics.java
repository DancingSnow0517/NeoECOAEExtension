package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Process-wide counters for explaining why a planning request used AE2's fallback. */
public final class ECOPlanningDiagnostics {
    private static final Map<Outcome, LongAdder> COUNTERS = new EnumMap<>(Outcome.class);

    static {
        for (Outcome outcome : Outcome.values()) {
            COUNTERS.put(outcome, new LongAdder());
        }
    }

    private ECOPlanningDiagnostics() {
    }

    public static void record(Outcome outcome) {
        COUNTERS.get(outcome).increment();
    }

    public static Snapshot snapshot() {
        Map<Outcome, Long> values = new EnumMap<>(Outcome.class);
        COUNTERS.forEach((outcome, counter) -> values.put(outcome, counter.sum()));
        return new Snapshot(Map.copyOf(values));
    }

    public record Snapshot(Map<Outcome, Long> counts) {
    }

    public enum Outcome {
        SNAPSHOT_STARTED,
        UNSUPPORTED_REQUEST,
        SNAPSHOT_REJECTED,
        PATTERN_INCOMPATIBLE,
        SNAPSHOT_LIMIT_EXCEEDED,
        VARIANT_LIMIT_REJECTED,
        FAST_PATH_ESCALATED,
        PRECISE_PATH_ACCEPTED,
        SOLVER_REJECTED,
        ASSEMBLY_REJECTED,
        ECO_ACCEPTED,
        AE2_FALLBACK,
        DIFFERENTIAL_MISMATCH
    }
}
