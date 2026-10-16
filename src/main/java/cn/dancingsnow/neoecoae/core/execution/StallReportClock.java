package cn.dancingsnow.neoecoae.core.execution;

/** Transient report timing only; it never mutates job progress or recovery state. */
public final class StallReportClock {
    private final long initialDelay;
    private final long interval;
    private long lastProgress;
    private long lastReport;
    private boolean reported;

    public StallReportClock(long initialDelay, long interval) {
        if (initialDelay < 0 || interval < 0) throw new IllegalArgumentException("Negative report interval");
        this.initialDelay = initialDelay;
        this.interval = interval;
    }

    public void progress(long tick) { lastProgress = tick; lastReport = tick; reported = false; }
    public boolean timeReversed(long tick) { return tick < lastProgress; }
    public long stalledTicks(long tick) { return Math.max(0L, tick - lastProgress); }

    public boolean shouldReport(long tick) {
        if (stalledTicks(tick) < initialDelay || (reported && tick - lastReport < interval)) return false;
        lastReport = tick;
        reported = true;
        return true;
    }
}
