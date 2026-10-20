package cn.dancingsnow.neoecoae.gui.common;

import java.util.function.LongSupplier;
import net.minecraft.Util;

/** Reveals a host gauge up to its live value during its first half-second of rendering. */
public final class HostProgressAnimation {
    private static final long DURATION_MS = 500L;
    private final LongSupplier clock;
    private long startMs;
    private boolean started;

    public HostProgressAnimation() {
        this(Util::getMillis);
    }

    HostProgressAnimation(LongSupplier clock) {
        this.clock = clock;
    }

    public float value(double target) {
        if (!Double.isFinite(target) || target <= 0) return 0;
        long now = clock.getAsLong();
        if (!started) {
            startMs = now;
            started = true;
        }
        double progress = Math.clamp((now - startMs) / (double) DURATION_MS, 0, 1);
        double eased = progress * progress * (3 - 2 * progress);
        return (float) (Math.min(1, target) * eased);
    }

    public void reset() {
        started = false;
    }
}
