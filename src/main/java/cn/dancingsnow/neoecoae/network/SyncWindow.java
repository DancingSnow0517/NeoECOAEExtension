package cn.dancingsnow.neoecoae.network;

/** Tick-based coalescing; only discrete state transitions may bypass the ordinary window. */
public final class SyncWindow {
    private final int interval;
    private long last = Long.MIN_VALUE;

    public SyncWindow(int interval) {
        this.interval = Math.max(1, interval);
    }

    public boolean allow(long tick, boolean transition) {
        if (!transition && last != Long.MIN_VALUE && tick - last < interval) return false;
        last = tick;
        return true;
    }

    public void reset() {
        last = Long.MIN_VALUE;
    }
}
