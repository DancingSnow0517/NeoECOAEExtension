package cn.dancingsnow.neoecoae.core.format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Formats CPU storage and co-processor counts for the ECO crafting screens. */
public final class NEByteFormatter {
    private static final long KIB = 1L << 10;
    private static final long MIB = 1L << 20;
    private static final long GIB = 1L << 30;
    private static final long TIB = 1L << 40;
    private static final long PIB = 1L << 50;
    private static final long EIB = 1L << 60;

    private static final long[] UNIT_SIZES = {1L, KIB, MIB, GIB, TIB, PIB, EIB};
    private static final String[] UNIT_NAMES = {"B", "KB", "MB", "GB", "TB", "PB", "EB"};

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
        return DisplayNumbers.grouped(scaled.toPlainString()) + " " + UNIT_NAMES[unitIndex];
    }

    /**
     * Formats the co-processor count using AE2's decimal unit conventions without exposing a
     * potentially very large raw integer in the confirmation screen.
     */
    public static String formatCpuCoProcessors(long coProcessors) {
        return BigNumberFormatter.compactFixed(BigInteger.valueOf(coProcessors), 3, RoundingMode.HALF_UP, true);
    }
}
