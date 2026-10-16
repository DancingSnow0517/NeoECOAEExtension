package cn.dancingsnow.neoecoae.core.ui;

import cn.dancingsnow.neoecoae.core.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.core.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.core.math.BigIntegers;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/** Content and thresholds shared by host panels; rendering and translated Components remain platform code. */
public final class HostTextContent {
    public enum UsageLevel { NORMAL, WARNING, HIGH, FULL }
    private HostTextContent() {}
    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
        ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));
    private static final ThreadLocal<DecimalFormat> PERCENT_DECIMAL =
        ThreadLocal.withInitial(() -> new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US)));


    public static String expandedNumber(long value) {
        return NUMBER_FORMAT.get().format(Math.max(0L, value));
    }

    public static String fullBytes(long value) {
        return expandedNumber(value);
    }

    public static String fullBytes(BigInteger value) {
        return DisplayNumbers.grouped(BigIntegers.nonNegative(value).toString());
    }

    public static String expandedStorageBytes(BigInteger value) {
        return BigNumberFormatter.format(BigIntegers.nonNegative(value), 1, true);
    }

    public static float usageRatio(long used, long max) {
        if (max <= 0) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, (double) used / (double) max));
    }

    public static String percent(long used, long max) {
        if (max <= 0) {
            return "N/A";
        }
        return percent(usageRatio(used, max));
    }

    public static String percent(double ratio) {
        if (!Double.isFinite(ratio)) {
            return "N/A";
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, ratio));
        return PERCENT_DECIMAL.get().format(clamped * 100.0D) + "%";
    }

    public static UsageLevel usageLevel(long used, long max) {
        if (used <= 0 || max <= 0) return UsageLevel.NORMAL;
        double ratio = (double) used / (double) max;
        if (ratio >= 1.0D) return UsageLevel.FULL;
        if (ratio >= 0.9D) return UsageLevel.HIGH;
        if (ratio >= 0.75D) return UsageLevel.WARNING;
        return UsageLevel.NORMAL;
    }

    public static int storageTypeAccent(int index) {
        int[] palette = {0xE06C75, 0x61AFEF, 0x98C379, 0xD19A66, 0xC678DD};
        return palette[Math.floorMod(index, palette.length)];
    }
}
