package cn.dancingsnow.neoecoae.gui.common;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Shared formatting for the host performance readout. */
public final class HostPerformanceText {
    private static final ThreadLocal<DecimalFormat> MILLIS = ThreadLocal.withInitial(
        () -> new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US)));

    private HostPerformanceText() {
    }

    public static String formatCorner(long averageNanos) {
        long nanos = Math.max(0L, averageNanos);
        long micros = Math.round(nanos / 1_000.0D);
        if (micros < 1_000L) {
            return micros + " us";
        }
        return MILLIS.get().format(nanos / 1_000_000.0D) + " ms";
    }

    public static String formatTooltip(long averageNanos) {
        long nanos = Math.max(0L, averageNanos);
        long micros = Math.round(nanos / 1_000.0D);
        return micros + " us/" + MILLIS.get().format(nanos / 1_000_000.0D) + " ms";
    }
}
