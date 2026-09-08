package cn.dancingsnow.neoecoae.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Formats CPU storage without relying on AE2's four-entry byte unit table. */
public final class NEByteFormatter {
    private static final long KIB = 1L << 10;
    private static final long MIB = 1L << 20;
    private static final long GIB = 1L << 30;
    private static final long TIB = 1L << 40;
    private static final long PIB = 1L << 50;
    private static final long EIB = 1L << 60;

    private static final long[] UNIT_SIZES = {1L, KIB, MIB, GIB, TIB, PIB, EIB};
    private static final String[] UNIT_NAMES = {"B", "KB", "MB", "GB", "TB", "PB", "EB"};
    private static final String[] DECIMAL_UNIT_NAMES = {"", "k", "M", "G", "T", "P", "E"};

    private NEByteFormatter() {
    }

    /**
     * Formats a non-negative byte amount using the largest available binary unit.
     * The value is rounded like AE2's amount formatter and saturates at EB.
     */
    public static String format(long bytes) {
        long safeBytes = Math.max(0L, bytes);
        int unitIndex = 0;
        while (unitIndex + 1 < UNIT_SIZES.length && safeBytes >= UNIT_SIZES[unitIndex + 1]) {
            unitIndex++;
        }

        BigDecimal scaled = BigDecimal.valueOf(safeBytes)
                .divide(BigDecimal.valueOf(UNIT_SIZES[unitIndex]), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return scaled.toPlainString() + UNIT_NAMES[unitIndex];
    }

    /**
     * Keeps CPUSelectionList's original display for ordinary AE2 CPUs. Larger CPUs use the
     * extended formatter so their capacity is not passed to AE2's limited byte formatter.
     */
    public static String formatCpuStorage(long storage) {
        long safeStorage = Math.max(0L, storage);
        if (safeStorage >= GIB) {
            return format(safeStorage);
        }
        if (safeStorage >= MIB) {
            return (safeStorage / MIB) + "M";
        }
        return (safeStorage / KIB) + "k";
    }

    /**
     * Formats the co-processor count using AE2's decimal unit conventions without exposing a
     * potentially very large raw integer in the confirmation screen.
     */
    public static String formatCpuCoProcessors(long coProcessors) {
        long safeCoProcessors = Math.max(0L, coProcessors);
        if (safeCoProcessors < 1_000L) {
            return NumberFormat.getIntegerInstance(Locale.ROOT).format(safeCoProcessors);
        }

        int unitIndex = 0;
        long unitSize = 1L;
        while (unitIndex + 1 < DECIMAL_UNIT_NAMES.length && safeCoProcessors / unitSize >= 1_000L) {
            unitIndex++;
            unitSize *= 1_000L;
        }

        BigDecimal scaled = BigDecimal.valueOf(safeCoProcessors)
                .divide(BigDecimal.valueOf(unitSize), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return scaled.toPlainString() + DECIMAL_UNIT_NAMES[unitIndex];
    }
}
