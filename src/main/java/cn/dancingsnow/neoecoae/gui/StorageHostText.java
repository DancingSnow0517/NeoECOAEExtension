package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import net.minecraft.network.chat.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

final class StorageHostText {
    static final int PRIMARY = 0xD6D0E0;
    static final int VALUE = 0x8377FF;
    static final int USED = 0x00FC00;
    static final int MUTED = 0xAAA4B2;
    static final int WARNING = 0xFFD65A;
    static final int ORANGE = 0xFF9A3D;
    static final int ERROR = 0xFF6A75;

    private static final int BYTES_IN_K = 1024;
    private static final long BYTES_IN_M = BYTES_IN_K * 1024L;
    private static final long BYTES_IN_G = BYTES_IN_M * 1024L;
    private static final long BYTES_IN_T = BYTES_IN_G * 1024L;
    private static final long BYTES_IN_P = BYTES_IN_T * 1024L;
    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
        ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));
    private static final ThreadLocal<DecimalFormat> COMPACT_DECIMAL =
        ThreadLocal.withInitial(() -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> PERCENT_DECIMAL =
        ThreadLocal.withInitial(() -> new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US)));

    private StorageHostText() {
    }

    record UsedTotal(String usedText, String maxText, Component suffix) {
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

    static UsedTotal typeProgress(long used, long max) {
        return new UsedTotal(compactTaskAmount(used), compactTaskAmount(max), Component.empty());
    }

    static UsedTotal byteProgress(long used, long max) {
        return new UsedTotal(storageBytesWhole(used), storageBytesWhole(max), Component.empty());
    }

    static int usedValueColor(long used, long max) {
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

    static float usageRatio(long used, long max) {
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

    static String percent(double ratio) {
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

    private static String storageBytesWhole(long value) {
        long safe = Math.max(0L, value);
        if (safe < BYTES_IN_K) {
            return Long.toString(safe);
        }

        long unit = BYTES_IN_K;
        String suffix = "K";
        if (safe >= BYTES_IN_P) {
            unit = BYTES_IN_P;
            suffix = "P";
        } else if (safe >= BYTES_IN_T) {
            unit = BYTES_IN_T;
            suffix = "T";
        } else if (safe >= BYTES_IN_G) {
            unit = BYTES_IN_G;
            suffix = "G";
        } else if (safe >= BYTES_IN_M) {
            unit = BYTES_IN_M;
            suffix = "M";
        }
        return Math.max(1L, Math.round((double) safe / (double) unit)) + suffix;
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
