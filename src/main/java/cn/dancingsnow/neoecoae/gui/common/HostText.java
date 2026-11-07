package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class HostText {
    public static final int PRIMARY = 0xD6D0E0;
    public static final int VALUE = 0x8377FF;
    public static final int USED = 0x00FC00;
    public static final int MUTED = 0xAAA4B2;
    public static final int WARNING = 0xFFD65A;
    public static final int ORANGE = 0xFF9A3D;
    public static final int ERROR = 0xFF6A75;
    // Header title of every host window; starts right of the tier logo (x 23..36) and centres on it.
    public static final float TITLE_SCALE = 1.0F;
    public static final int TITLE_X = 44;
    public static final int TITLE_Y = 10;

    private static final int BYTES_IN_K = 1024;
    private static final long BYTES_IN_M = BYTES_IN_K * 1024L;
    private static final long BYTES_IN_G = BYTES_IN_M * 1024L;
    private static final long BYTES_IN_T = BYTES_IN_G * 1024L;
    private static final long BYTES_IN_P = BYTES_IN_T * 1024L;
    private static final PlannerAmount BYTE_UNIT_FACTOR = PlannerAmount.of(BYTES_IN_K);
    private static final int TOOLTIP_BYTE_DIGITS = 4;
    private static final PlannerAmount TOOLTIP_BYTE_LIMIT = PlannerAmount.of(10L).pow(TOOLTIP_BYTE_DIGITS);
    // Continue the binary-prefix display beyond yotta for effectively unbounded storage amounts.
    private static final String[] EXPANDED_BYTE_UNITS = {
        "", "K", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q"
    };

    private HostText() {
    }

    public record UsedTotal(String usedText, String maxText, Component suffix) {
    }

    public static UsedTotal energyUsage(long used, long max, int maxWidth) {
        String prefix = "Energy Storage: ";
        String usedText = number(used);
        String maxText = number(max);
        Component suffix = Component.literal("AE");
        if (usedTotalWidth(prefix, usedText, maxText, "AE") > maxWidth) {
            usedText = compactTaskAmount(used);
            maxText = compactTaskAmount(max);
        }
        return new UsedTotal(usedText, maxText, suffix);
    }

    public static UsedTotal typeProgress(long used, long max) {
        return new UsedTotal(ae2Amount(used), max < 0L ? "\u221E" : ae2Amount(max), Component.empty());
    }

    public static UsedTotal byteProgress(long used, long max) {
        return typeProgress(used, max);
    }

    public static String expandedNumber(long value) {
        return DisplayNumbers.grouped(Math.max(0L, value));
    }

    /** Exact byte counts for tooltips; unlike the panel labels this never uses a unit suffix. */
    public static String fullBytes(long value) {
        return expandedNumber(value);
    }

    /** Exact byte counts for tooltips, including values beyond the long range. */
    public static String fullBytes(BigInteger value) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        return DisplayNumbers.grouped(safe.toString());
    }

    public static String expandedStorageBytes(long value) {
        return expandedNumber(value);
    }

    public static String expandedStorageBytes(BigInteger value) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        return BigNumberFormatter.format(safe, 1, true);
    }

    public static String compactStorageBytes(BigInteger value) {
        PlannerAmount safe = value == null || value.signum() < 0 ? PlannerAmount.ZERO : PlannerAmount.of(value);
        PlannerAmount unit = PlannerAmount.ONE;
        int unitIndex = 0;
        while (unitIndex < EXPANDED_BYTE_UNITS.length - 1 && safe.divide(unit).compareTo(TOOLTIP_BYTE_LIMIT) >= 0) {
            unit = unit.multiply(BYTE_UNIT_FACTOR);
            unitIndex++;
        }
        return DisplayNumbers.grouped(new BigDecimal(safe.toBigInteger())
            .divide(new BigDecimal(unit.toBigInteger()), 0, RoundingMode.HALF_UP)
            .toPlainString()) + EXPANDED_BYTE_UNITS[unitIndex];
    }

    /** Matches the 1.20.1 System Load tooltip: retain up to ten integer digits plus two decimals. */
    public static String preciseHugeAmount(BigInteger value) {
        PlannerAmount safe = value == null || value.signum() < 0 ? PlannerAmount.ZERO : PlannerAmount.of(value);
        if (safe.signum() == 0) {
            return "0";
        }
        int unitIndex = 0;
        PlannerAmount unit = PlannerAmount.ONE;
        while (unitIndex < EXPANDED_BYTE_UNITS.length - 1
            && safe.compareTo(unit.multiply(BYTE_UNIT_FACTOR)) >= 0) {
            unit = unit.multiply(BYTE_UNIT_FACTOR);
            unitIndex++;
        }
        while (unitIndex > 0) {
            PlannerAmount smallerUnit = unit.divide(BYTE_UNIT_FACTOR);
            if (safe.divide(smallerUnit).toString().length() > 10) {
                break;
            }
            unit = smallerUnit;
            unitIndex--;
        }
        if (unitIndex == 0) {
            return DisplayNumbers.grouped(safe.toString());
        }
        BigDecimal scaled = new BigDecimal(safe.toBigInteger())
            .divide(new BigDecimal(unit.toBigInteger()), 2, RoundingMode.DOWN);
        return DisplayNumbers.grouped(scaled.toPlainString()) + EXPANDED_BYTE_UNITS[unitIndex];
    }

    public static String hugeStackAmount(BigInteger value) {
        PlannerAmount safe = value == null || value.signum() < 0 ? PlannerAmount.ZERO : PlannerAmount.of(value);
        PlannerAmount unit = PlannerAmount.ONE;
        int unitIndex = 0;
        while (unitIndex < EXPANDED_BYTE_UNITS.length - 1
            && safe.compareTo(unit.multiply(BYTE_UNIT_FACTOR)) >= 0) {
            unit = unit.multiply(BYTE_UNIT_FACTOR);
            unitIndex++;
        }
        if (unitIndex == 0) {
            return DisplayNumbers.grouped(safe.toString());
        }
        return DisplayNumbers.grouped(new BigDecimal(safe.toBigInteger())
            .divide(new BigDecimal(unit.toBigInteger()), 2, RoundingMode.DOWN)
            .toPlainString()) + EXPANDED_BYTE_UNITS[unitIndex];
    }

    public static String fitHugeAmount(BigInteger value, int maxWidth) {
        return fitHugeAmount(value, maxWidth, HostText::estimatedTextWidth);
    }

    public static String fitHugeAmount(BigInteger value, int maxWidth, ToIntFunction<String> width) {
        PlannerAmount safe = value == null || value.signum() < 0 ? PlannerAmount.ZERO : PlannerAmount.of(value);
        String exact = expandedStorageBytes(safe.toBigInteger());
        if (width.applyAsInt(exact) <= maxWidth) {
            return exact;
        }

        int naturalUnitIndex = 0;
        PlannerAmount naturalUnit = PlannerAmount.ONE;
        while (naturalUnitIndex < EXPANDED_BYTE_UNITS.length - 1
            && safe.compareTo(naturalUnit.multiply(BYTE_UNIT_FACTOR)) >= 0) {
            naturalUnit = naturalUnit.multiply(BYTE_UNIT_FACTOR);
            naturalUnitIndex++;
        }
        for (int decimals = 2; decimals >= 0; decimals--) {
            PlannerAmount unit = BYTE_UNIT_FACTOR;
            for (int unitIndex = 1; unitIndex <= naturalUnitIndex; unitIndex++) {
                String candidate = DisplayNumbers.grouped(new BigDecimal(safe.toBigInteger())
                    .divide(new BigDecimal(unit.toBigInteger()), decimals, RoundingMode.HALF_UP)
                    .toPlainString()) + EXPANDED_BYTE_UNITS[unitIndex];
                if (width.applyAsInt(candidate) <= maxWidth) {
                    return candidate;
                }
                unit = unit.multiply(BYTE_UNIT_FACTOR);
            }
        }

        int exponent = Math.max(0, safe.toString().length() - 1);
        BigDecimal divisor = new BigDecimal(PlannerAmount.of(10L).pow(exponent).toBigInteger());
        for (int decimals = 3; decimals >= 0; decimals--) {
            String candidate = new BigDecimal(safe.toBigInteger())
                .divide(divisor, decimals, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "e" + exponent;
            if (width.applyAsInt(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return "e" + exponent;
    }

    public static List<Component> exactAmountTooltip(BigInteger value, int color) {
        String exact = expandedStorageBytes(value);
        List<Component> lines = new ArrayList<>();
        int start = 0;
        while (start < exact.length()) {
            int end = Math.min(exact.length(), start + 32);
            lines.add(Component.literal(exact.substring(start, end)).withStyle(neColor -> neColor.withColor(color)));
            start = end;
        }
        return lines;
    }

    public static UsedTotal fullTypeProgress(long used, long max) {
        return new UsedTotal(
            number(Math.max(0L, used)),
            max < 0L ? "\u221E" : number(max),
            Component.empty()
        );
    }

    public static UsedTotal fullByteProgressValues(long used, long max) {
        return new UsedTotal(
            number(Math.max(0L, used)),
            number(Math.max(0L, max)),
            Component.translatable("gui.neoecoae.host.metric.bytes")
        );
    }

    public static Component fullByteProgress(long used, long max) {
        return usedOfTotal(usedPart(number(Math.max(0L, used)), used, max), " / ",
            totalPart(number(Math.max(0L, max)) + " ").append(Component.translatable("gui.neoecoae.host.metric.bytes")));
    }

    /** Used figure coloured by how full the resource is. */
    public static MutableComponent usedPart(String text, long used, long max) {
        return Component.literal(text).withStyle(neColor -> neColor.withColor(usedValueColor(used, max)));
    }

    /** Capacity figure in value colour. */
    public static MutableComponent totalPart(String text) {
        return Component.literal(text).withStyle(neColor -> neColor.withColor(VALUE));
    }

    /** "used / total" with a muted separator; the parts keep their own colours. */
    public static MutableComponent usedOfTotal(Component used, String separator, Component total) {
        return used.copy().append(Component.literal(separator).withStyle(neColor -> neColor.withColor(MUTED))).append(total);
    }

    public static int usedValueColor(long used, long max) {
        if (used <= 0 || max <= 0) {
            return USED;
        }
        double pct = (double) used / (double) max;
        if (pct >= 1.0D) {
            return ERROR;
        }
        if (pct >= 0.9D) {
            return ORANGE;
        }
        if (pct >= 0.75D) {
            return WARNING;
        }
        return USED;
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
        return DisplayNumbers.decimal(clamped * 100.0D, 1) + "%";
    }

    public static int gaugeColor(float ratio) {
        return 0xBF000000 | gaugeTextColor(ratio);
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

    private static String number(long value) {
        return DisplayNumbers.grouped(value);
    }

    private static int usedTotalWidth(String prefix, String usedText, String maxText, String suffix) {
        int width = estimatedTextWidth(prefix + usedText + " / " + maxText);
        return suffix.isEmpty() ? width : width + estimatedTextWidth(" " + suffix);
    }

    private static int estimatedTextWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                width += 4;
            } else if (c < 128) {
                width += 6;
            } else {
                width += 8;
            }
        }
        return width;
    }

    public static String ae2Amount(long value) {
        return DisplayNumbers.compact(Math.max(0L, value));
    }

    public static String ae2Amount(BigInteger value) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        return DisplayNumbers.compact(safe);
    }

    private static String compactTaskAmount(long value) {
        return DisplayNumbers.compact(Math.max(0L, value));
    }
}
