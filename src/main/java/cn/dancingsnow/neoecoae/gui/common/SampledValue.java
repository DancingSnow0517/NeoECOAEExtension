package cn.dancingsnow.neoecoae.gui.common;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Menu-owned sampling, independent of render FPS and with an immediate initial value. */
public final class SampledValue<T> implements Supplier<T> {
    private final LongSupplier clock;
    private final Supplier<T> source;
    private final int interval;
    private boolean initialized;
    private long sampledAt;
    private T value;

    public SampledValue(LongSupplier clock, Supplier<T> source, int interval) {
        if (interval < 1) throw new IllegalArgumentException("Invalid sampling interval");
        this.clock = clock;
        this.source = source;
        this.interval = interval;
    }

    @Override public T get() {
        long now = clock.getAsLong();
        if (!initialized || now < sampledAt || now - sampledAt >= interval) {
            value = source.get();
            sampledAt = now;
            initialized = true;
        }
        return value;
    }
}
