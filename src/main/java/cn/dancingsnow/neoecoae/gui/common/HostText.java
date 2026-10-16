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

public final class HostText {
    // Header title of every host window; starts right of the tier logo (x 23..36) and centres on it.
    public static final float TITLE_SCALE = 1.0F;
    public static final int TITLE_X = 44;
    public static final int TITLE_Y = 10;


    private HostText() {
    }

    public record UsedTotal(String usedText, String maxText, Component suffix) {
    }

    public static UsedTotal typeProgress(long used, long max) {
        return new UsedTotal(ae2Amount(used), max < 0L ? "\u221E" : ae2Amount(max), Component.empty());
    }

    public static String expandedNumber(long value) {
        return cn.dancingsnow.neoecoae.core.ui.HostTextContent.expandedNumber(value);
    }

    /** Exact byte counts for tooltips; unlike the panel labels this never uses a unit suffix. */
    public static String fullBytes(long value) {
        return cn.dancingsnow.neoecoae.core.ui.HostTextContent.fullBytes(value);
    }

    /** Exact byte counts for tooltips, including values beyond the long range. */
    public static String fullBytes(BigInteger value) {
        return cn.dancingsnow.neoecoae.core.ui.HostTextContent.fullBytes(value);
    }

    public static String expandedStorageBytes(BigInteger value) {
        return cn.dancingsnow.neoecoae.core.ui.HostTextContent.expandedStorageBytes(value);
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
        return switch (cn.dancingsnow.neoecoae.core.ui.HostTextContent.usageLevel(used, max)) {
            case NORMAL -> Palette.HOST_USED;
            case WARNING -> Palette.HOST_WARNING;
            case HIGH -> Palette.HOST_ORANGE;
            case FULL -> Palette.HOST_ERROR;
        };
    }

    public static float usageRatio(long used, long max) {
        return cn.dancingsnow.neoecoae.core.ui.HostTextContent.usageRatio(used, max);
    }

    public static String percent(long used, long max) {
        return cn.dancingsnow.neoecoae.core.ui.HostTextContent.percent(used, max);
    }

    public static String percent(double ratio) {
        return cn.dancingsnow.neoecoae.core.ui.HostTextContent.percent(ratio);
    }

    public static int gaugeTextColor(float ratio) {
        return usedValueColor(Math.round(Math.max(0.0F, Math.min(1.0F, ratio)) * 1000.0F), 1000L);
    }

    public static int storageTypeAccentColor(ECOCellType cellType, int index) {
        if (cellType.desc().getStyle().getColor() != null) {
            return cellType.desc().getStyle().getColor().getValue();
        }
        return cn.dancingsnow.neoecoae.core.ui.HostTextContent.storageTypeAccent(index);
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
