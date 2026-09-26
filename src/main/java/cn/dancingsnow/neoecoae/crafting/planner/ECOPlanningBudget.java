package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.function.LongSupplier;
import cn.dancingsnow.neoecoae.config.NEConfig;

/** One calculation's shared allowance: recipe discovery, alternate routes, seeds and cycle search. */
public final class ECOPlanningBudget implements ECOCancellation {
    private final ECOCancellation cancellation;
    private final long maxWork;
    private final long maxNanos;
    private final LongSupplier clock;
    private final long started;
    private long work;

    public ECOPlanningBudget(ECOCancellation cancellation) {
        this(cancellation, Math.max(1L, NEConfig.ecoPlanningMaxWork),
            Math.max(1L, NEConfig.ecoPlanningMaxMillis) * 1_000_000L, System::nanoTime);
    }

    public ECOPlanningBudget(ECOCancellation cancellation, long maxWork, long maxNanos, LongSupplier clock) {
        if (maxWork < 1 || maxNanos < 1) throw new IllegalArgumentException("Planning budgets must be positive");
        this.cancellation = cancellation;
        this.maxWork = maxWork;
        this.maxNanos = maxNanos;
        this.clock = clock;
        this.started = clock.getAsLong();
    }

    @Override
    public synchronized void checkpoint() throws InterruptedException {
        cancellation.checkpoint();
        if (++work > maxWork || clock.getAsLong() - started >= maxNanos) {
            throw new Exhausted("Shared planning allowance exhausted after " + work + " checkpoints; result remains unknown");
        }
    }

    /** Distinct from cancellation, internal errors and a proof of missing materials. */
    public static final class Exhausted extends RuntimeException {
        private Exhausted(String message) { super(message, null, false, false); }
    }
}
