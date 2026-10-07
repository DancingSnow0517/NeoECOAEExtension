package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.math.BigInteger;

/** Conservative retained-size estimates, not a cap on recipe count or integer precision. */
final class CycleMemoryBudget {
    private final long maximum;
    private long retained;

    CycleMemoryBudget() {
        this(Math.max(1L, NEConfig.ecoPlanningMaxMemoryMiB) * 1024L * 1024L);
    }

    CycleMemoryBudget(long maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Memory allowance must be positive");
        this.maximum = maximum;
    }

    void retain(long bytes) {
        check(retained, bytes);
        retained += bytes;
    }

    void release(long bytes) { retained -= bytes; }

    void check(long temporary) { check(retained, temporary); }

    private void check(long existing, long additional) {
        if (additional < 0 || additional > maximum - existing) {
            throw new Exhausted("Estimated cycle data exceeds " + maximum + " bytes; result remains unknown");
        }
    }

    // Include object/array headers and eight-byte references even on compressed-oops JVMs.
    static long integerBytes(BigInteger value) {
        return 64L + 4L * ((value.bitLength() + 31L) / 32L);
    }

    static long markingBytes(PlannerAmount[] marking) {
        long bytes = 192L + 16L * marking.length; // Node, queue/set entries and two inventory arrays.
        for (PlannerAmount amount : marking) bytes += 32L + integerBytes(amount.toBigInteger());
        return bytes;
    }

    static final class Exhausted extends RuntimeException {
        Exhausted(String message) { super(message, null, false, false); }
    }
}
