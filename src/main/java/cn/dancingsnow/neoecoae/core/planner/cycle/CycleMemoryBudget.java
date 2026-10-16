package cn.dancingsnow.neoecoae.core.planner.cycle;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import java.math.BigInteger;

/** Conservative retained-size estimates, not a cap on recipe count or integer precision. */
public class CycleMemoryBudget {
    private final long maximum;
    private long retained;

    public CycleMemoryBudget(long maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Memory allowance must be positive");
        this.maximum = maximum;
    }

    public void retain(long bytes) {
        check(retained, bytes);
        retained += bytes;
    }

    public void release(long bytes) { retained -= bytes; }

    public void check(long temporary) { check(retained, temporary); }

    private void check(long existing, long additional) {
        if (additional < 0 || additional > maximum - existing) {
            throw exhausted("Estimated cycle data exceeds " + maximum + " bytes; result remains unknown");
        }
    }

    // Include object/array headers and eight-byte references even on compressed-oops JVMs.
    public static long integerBytes(BigInteger value) {
        return 64L + 4L * ((value.bitLength() + 31L) / 32L);
    }

    public static long markingBytes(PlannerAmount[] marking) {
        long bytes = 192L + 16L * marking.length; // Node, queue/set entries and two inventory arrays.
        for (PlannerAmount amount : marking) bytes += 32L + integerBytes(amount.toBigInteger());
        return bytes;
    }

    protected Exhausted exhausted(String message) { return new Exhausted(message); }

    public static class Exhausted extends RuntimeException {
        protected Exhausted(String message) { super(message, null, false, false); }
    }
}
