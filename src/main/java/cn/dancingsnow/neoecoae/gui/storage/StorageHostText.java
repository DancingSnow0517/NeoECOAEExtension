package cn.dancingsnow.neoecoae.gui.storage;

import appeng.util.ReadableNumberConverter;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

public final class StorageHostText {
    public static final int PRIMARY = 0xD6D0E0;
    public static final int VALUE = 0x8377FF;
    public static final int USED = 0x00FC00;
    public static final int MUTED = 0xAAA4B2;
    public static final int WARNING = 0xFFD65A;
    static final int ORANGE = 0xFF9A3D;
    static final int ERROR = 0xFF6A75;

    private static final int BYTES_IN_K = 1024;
    private static final long BYTES_IN_M = BYTES_IN_K * 1024L;
    private static final long BYTES_IN_G = BYTES_IN_M * 1024L;
    private static final long BYTES_IN_T = BYTES_IN_G * 1024L;
    private static final long BYTES_IN_P = BYTES_IN_T * 1024L;
    private static final BigInteger BIG_BYTES_IN_K = BigInteger.valueOf(BYTES_IN_K);
    private static final int TOOLTIP_BYTE_DIGITS = 4;
    private static final String[] EXPANDED_BYTE_UNITS = {"", "K", "M", "G", "T", "P", "E", "Z", "Y"};
    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
        ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));
    private static final ThreadLocal<DecimalFormat> COMPACT_DECIMAL =
        ThreadLocal.withInitial(() -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> PERCENT_DECIMAL =
        ThreadLocal.withInitial(() -> new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US)));

    private StorageHostText() {
    }

    public record UsedTotal(String usedText, String maxText, Component suffix) {
    }

    static UsedTotal energyUsage(long used, long max, int maxWidth) {
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
        return new UsedTotal(ae2Amount(used), ae2Amount(max), Component.empty());
    }

    public static UsedTotal byteProgress(long used, long max) {
        return new UsedTotal(ae2Amount(used), ae2Amount(max), Component.empty());
    }

    public static String expandedStorageBytes(long value) {
        return expandedStorageBytes(BigInteger.valueOf(Math.max(0L, value)));
    }

    public static String expandedStorageBytes(BigInteger value) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        return NUMBER_FORMAT.get().format(safe);
    }

    public static String compactStorageBytes(BigInteger value) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        BigInteger unit = BigInteger.ONE;
        int unitIndex = 0;
        BigInteger limit = BigInteger.TEN.pow(TOOLTIP_BYTE_DIGITS);
        while (unitIndex < EXPANDED_BYTE_UNITS.length - 1 && safe.divide(unit).compareTo(limit) >= 0) {
            unit = unit.multiply(BIG_BYTES_IN_K);
            unitIndex++;
        }
        return new BigDecimal(safe)
            .divide(new BigDecimal(unit), 0, RoundingMode.HALF_UP)
            .toPlainString() + EXPANDED_BYTE_UNITS[unitIndex];
    }

    public static String hugeStackAmount(BigInteger value) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        BigInteger unit = BigInteger.ONE;
        int unitIndex = 0;
        while (unitIndex < EXPANDED_BYTE_UNITS.length - 1
            && safe.compareTo(unit.multiply(BIG_BYTES_IN_K)) >= 0) {
            unit = unit.multiply(BIG_BYTES_IN_K);
            unitIndex++;
        }
        if (unitIndex == 0) {
            return NUMBER_FORMAT.get().format(safe);
        }
        return new BigDecimal(safe)
            .divide(new BigDecimal(unit), 2, RoundingMode.DOWN)
            .toPlainString() + EXPANDED_BYTE_UNITS[unitIndex];
    }

    public static String fitHugeAmount(BigInteger value, int maxWidth) {
        return fitHugeAmount(value, maxWidth, StorageHostText::estimatedTextWidth);
    }

    public static String fitHugeAmount(BigInteger value, int maxWidth, ToIntFunction<String> width) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        String exact = expandedStorageBytes(safe);
        if (width.applyAsInt(exact) <= maxWidth) {
            return exact;
        }

        int naturalUnitIndex = 0;
        BigInteger naturalUnit = BigInteger.ONE;
        while (naturalUnitIndex < EXPANDED_BYTE_UNITS.length - 1
            && safe.compareTo(naturalUnit.multiply(BIG_BYTES_IN_K)) >= 0) {
            naturalUnit = naturalUnit.multiply(BIG_BYTES_IN_K);
            naturalUnitIndex++;
        }
        for (int decimals = 2; decimals >= 0; decimals--) {
            BigInteger unit = BIG_BYTES_IN_K;
            for (int unitIndex = 1; unitIndex <= naturalUnitIndex; unitIndex++) {
                String candidate = new BigDecimal(safe)
                    .divide(new BigDecimal(unit), decimals, RoundingMode.HALF_UP)
                    .toPlainString() + EXPANDED_BYTE_UNITS[unitIndex];
                if (width.applyAsInt(candidate) <= maxWidth) {
                    return candidate;
                }
                unit = unit.multiply(BIG_BYTES_IN_K);
            }
        }

        int exponent = Math.max(0, safe.toString().length() - 1);
        BigDecimal divisor = BigDecimal.TEN.pow(exponent);
        for (int decimals = 3; decimals >= 0; decimals--) {
            String candidate = new BigDecimal(safe)
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
            lines.add(Component.literal(exact.substring(start, end)).withColor(color));
            start = end;
        }
        return List.copyOf(lines);
    }

    static UsedTotal fullTypeProgress(long used, long max) {
        return new UsedTotal(number(Math.max(0L, used)), number(Math.max(0L, max)), Component.empty());
    }

    static UsedTotal fullByteProgressValues(long used, long max) {
        return new UsedTotal(
            number(Math.max(0L, used)),
            number(Math.max(0L, max)),
            Component.translatable("gui.neoecoae.host.metric.bytes")
        );
    }

    public static Component fullByteProgress(long used, long max) {
        return Component.literal(number(Math.max(0L, used)))
            .append(" / ")
            .append(number(Math.max(0L, max)))
            .append(" ")
            .append(Component.translatable("gui.neoecoae.host.metric.bytes"));
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

    static String percent(long used, long max) {
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

    static int gaugeColor(float ratio) {
        return 0xBF000000 | gaugeTextColor(ratio);
    }

    static int gaugeTextColor(float ratio) {
        return usedValueColor(Math.round(Math.max(0.0F, Math.min(1.0F, ratio)) * 1000.0F), 1000L);
    }

    static int storageTypeAccentColor(ECOCellType cellType, int index) {
        if (cellType.desc().getStyle().getColor() != null) {
            return cellType.desc().getStyle().getColor().getValue();
        }
        int[] palette = {0xE06C75, 0x61AFEF, 0x98C379, 0xD19A66, 0xC678DD};
        return palette[Math.floorMod(index, palette.length)];
    }

    private static String number(long value) {
        return NUMBER_FORMAT.get().format(value);
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

    static String ae2Amount(long value) {
        return ReadableNumberConverter.format(Math.max(0L, value), 4);
    }

    public static String ae2Amount(BigInteger value) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        return ae2Amount(safe.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
    }

    private static String compactTaskAmount(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        if (safe < 1_000_000L) {
            return compactDecimal(safe, 1_000L, "K");
        }
        if (safe < 1_000_000_000L) {
            return compactDecimal(safe, 1_000_000L, "M");
        }
        if (safe < 1_000_000_000_000L) {
            return compactDecimal(safe, 1_000_000_000L, "G");
        }
        return compactDecimal(safe, 1_000_000_000_000L, "T");
    }

    private static String compactDecimal(long value, long unit, String suffix) {
        double scaled = (double) Math.max(0L, value) / (double) unit;
        if (scaled >= 100.0D || Math.abs(scaled - Math.rint(scaled)) < 0.05D) {
            return String.format(Locale.US, "%.0f%s", scaled, suffix);
        }
        return String.format(Locale.US, "%.1f%s", scaled, suffix);
    }
}
