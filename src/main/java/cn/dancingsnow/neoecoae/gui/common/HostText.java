package cn.dancingsnow.neoecoae.gui.common;

import appeng.util.ReadableNumberConverter;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.core.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.core.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.core.math.BigIntegers;
import cn.dancingsnow.neoecoae.gui.theme.Palette;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public final class HostText {
    // Header title of every host window; starts right of the tier logo (x 23..36) and centres on it.
    public static final float TITLE_SCALE = 1.0F;
    public static final int TITLE_X = 44;
    public static final int TITLE_Y = 10;

    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
        ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));
    private static final ThreadLocal<DecimalFormat> PERCENT_DECIMAL =
        ThreadLocal.withInitial(() -> new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US)));

    private HostText() {
    }

    public record UsedTotal(String usedText, String maxText, Component suffix) {
    }

    public static UsedTotal typeProgress(long used, long max) {
        return new UsedTotal(ae2Amount(used), max < 0L ? "\u221E" : ae2Amount(max), Component.empty());
    }

    public static String expandedNumber(long value) {
        return NUMBER_FORMAT.get().format(Math.max(0L, value));
    }

    /** Exact byte counts for tooltips; unlike the panel labels this never uses a unit suffix. */
    public static String fullBytes(long value) {
        return expandedNumber(value);
    }

    /** Exact byte counts for tooltips, including values beyond the long range. */
    public static String fullBytes(BigInteger value) {
        return DisplayNumbers.grouped(BigIntegers.nonNegative(value).toString());
    }

    public static String expandedStorageBytes(BigInteger value) {
        return BigNumberFormatter.format(BigIntegers.nonNegative(value), 1, true);
    }

    public static Component fullByteProgress(long used, long max) {
        return usedOfTotal(usedPart(expandedNumber(used), used, max), " / ",
            totalPart(expandedNumber(max) + " ").append(Component.translatable("gui.neoecoae.host.metric.bytes")));
    }

    /** Used figure coloured by how full the resource is. */
    public static MutableComponent usedPart(String text, long used, long max) {
        return Component.literal(text).withColor(usedValueColor(used, max));
    }

    /** Capacity figure in value colour. */
    public static MutableComponent totalPart(String text) {
        return Component.literal(text).withColor(Palette.HOST_VALUE);
    }

    /** "used / total" with a muted separator; the parts keep their own colours. */
    public static MutableComponent usedOfTotal(Component used, String separator, Component total) {
        return used.copy().append(Component.literal(separator).withColor(Palette.HOST_MUTED)).append(total);
    }

    public static int usedValueColor(long used, long max) {
        if (used <= 0 || max <= 0) {
            return Palette.HOST_USED;
        }
        double pct = (double) used / (double) max;
        if (pct >= 1.0D) {
            return Palette.HOST_ERROR;
        }
        if (pct >= 0.9D) {
            return Palette.HOST_ORANGE;
        }
        if (pct >= 0.75D) {
            return Palette.HOST_WARNING;
        }
        return Palette.HOST_USED;
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

    public static int gaugeTextColor(float ratio) {
        return usedValueColor(Math.round(Math.max(0.0F, Math.min(1.0F, ratio)) * 1000.0F), 1000L);
    }

    public static int storageTypeAccentColor(ECOCellType cellType, int index) {
        if (cellType.desc().getStyle().getColor() != null) {
            return cellType.desc().getStyle().getColor().getValue();
        }
        int[] palette = {0xE06C75, 0x61AFEF, 0x98C379, 0xD19A66, 0xC678DD};
        return palette[Math.floorMod(index, palette.length)];
    }

    public static String ae2Amount(long value) {
        return DisplayNumbers.grouped(ReadableNumberConverter.format(Math.max(0L, value), 4));
    }

    public static String ae2Amount(BigInteger value) {
        BigInteger safe = BigIntegers.nonNegative(value);
        if (safe.bitLength() < Long.SIZE) {
            return ae2Amount(safe.longValue());
        }
        return BigNumberFormatter.format(safe, 1, false);
    }
}
