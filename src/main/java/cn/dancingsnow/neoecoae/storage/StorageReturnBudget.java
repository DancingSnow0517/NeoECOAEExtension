package cn.dancingsnow.neoecoae.storage;

import java.util.function.LongSupplier;

/** Shared by CPUs returning to one grid, so cancelling many orders does not multiply their per-CPU budget. */
public final class StorageReturnBudget {
    private static final int OPERATIONS = 2048;
    private static final long NANOS = 2_000_000L;
    private final LongSupplier clock;
    private long tick = Long.MIN_VALUE;
    private long started;
    private int used;

    public interface Source {
        StorageReturnBudget neoecoae$returnBudget();
    }

    public StorageReturnBudget() { this(System::nanoTime); }
    StorageReturnBudget(LongSupplier clock) { this.clock = clock; }

    public boolean available(long currentTick) {
        if (currentTick != tick) { tick = currentTick; used = 0; started = clock.getAsLong(); }
        return used < OPERATIONS && clock.getAsLong() - started < NANOS;
    }

    public boolean acquire(long currentTick) {
        if (!available(currentTick)) return false;
        used++;
        return true;
    }
}
