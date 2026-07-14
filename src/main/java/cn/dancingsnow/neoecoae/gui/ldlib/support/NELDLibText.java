package cn.dancingsnow.neoecoae.gui.ldlib.support;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public final class NELDLibText {
    private static final int MAX_EXACT_HUGE_DIGITS = 120;
    private static final int COMPACT_HUGE_SIGNIFICANT_DIGITS = 3;
    private static final long BYTES_IN_K = 1024L;
    private static final long BYTES_IN_M = BYTES_IN_K * 1024L;
    private static final long BYTES_IN_G = BYTES_IN_M * 1024L;
    private static final long BYTES_IN_T = BYTES_IN_G * 1024L;
    private static final long BYTES_IN_P = BYTES_IN_T * 1024L;
    private static final BigInteger BIG_1024 = BigInteger.valueOf(1024L);
    private static final String[] HUGE_SUFFIXES = {"", "K", "M", "G", "T", "P", "E", "Z", "Y"};

    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
            ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));
    private static final ThreadLocal<DecimalFormat> COMPACT_DECIMAL =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> PRECISE_HUGE_DECIMAL =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> PERCENT_DECIMAL =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> PRECISE_PERCENT_DECIMAL =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));

    private NELDLibText() {}

    public static String number(long value) {
        return NUMBER_FORMAT.get().format(value);
    }

    public static String number(int value) {
        return number((long) value);
    }

    public static String percent(double value) {
        if (!Double.isFinite(value)) {
            return "N/A";
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return PERCENT_DECIMAL.get().format(clamped * 100.0D) + "%";
    }

    public static String percentOrNA(long used, long max) {
        return max <= 0 ? "N/A" : percent((double) Math.max(0L, used) / (double) max);
    }

    public static String precisePercentOrNA(long used, long max) {
        if (max <= 0L) {
            return "N/A";
        }
        double ratio = Math.max(0.0D, Math.min(1.0D, (double) Math.max(0L, used) / (double) max));
        return PRECISE_PERCENT_DECIMAL.get().format(ratio * 100.0D) + "%";
    }

    public static String usedTotal(long used, long max) {
        return number(Math.max(0L, used)) + " / " + number(Math.max(0L, max));
    }

    public static String storageBytes(long value) {
        long safe = Math.max(0L, value);
        if (safe < BYTES_IN_G) {
            return number(safe);
        }

        long unit = BYTES_IN_G;
        String suffix = "G";
        if (safe >= BYTES_IN_P) {
            unit = BYTES_IN_P;
            suffix = "P";
        } else if (safe >= BYTES_IN_T) {
            unit = BYTES_IN_T;
            suffix = "T";
        }
        return COMPACT_DECIMAL.get().format((double) safe / (double) unit) + suffix;
    }

    public static String storageBytesCompact(long value) {
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
        return COMPACT_DECIMAL.get().format((double) safe / (double) unit) + suffix;
    }

    public static String hugeAmount(String decimalAmount) {
        String compact = compactHugeDecimal(decimalAmount);
        if (compact != null) {
            return compact;
        }
        BigInteger value;
        try {
            value = new BigInteger(decimalAmount);
        } catch (RuntimeException ignored) {
            return decimalAmount;
        }
        if (value.signum() <= 0) {
            return "0";
        }

        int unitIndex = 0;
        BigInteger unit = BigInteger.ONE;
        while (unitIndex < HUGE_SUFFIXES.length - 1 && value.compareTo(unit.multiply(BIG_1024)) >= 0) {
            unit = unit.multiply(BIG_1024);
            unitIndex++;
        }
        if (unitIndex == 0) {
            return NUMBER_FORMAT.get().format(value);
        }

        BigDecimal scaled = new BigDecimal(value).divide(new BigDecimal(unit), 2, RoundingMode.DOWN);
        return scaled.toPlainString() + HUGE_SUFFIXES[unitIndex];
    }

    public static String preciseHugeAmount(String decimalAmount) {
        String normalized = normalizeUnsignedDecimal(decimalAmount);
        if (normalized == null) {
            return decimalAmount == null ? "" : decimalAmount;
        }
        BigInteger value = new BigInteger(normalized);
        if (value.signum() <= 0) {
            return "0";
        }

        int unitIndex = 0;
        BigInteger unit = BigInteger.ONE;
        while (unitIndex < HUGE_SUFFIXES.length - 1 && value.compareTo(unit.multiply(BIG_1024)) >= 0) {
            unit = unit.multiply(BIG_1024);
            unitIndex++;
        }
        while (unitIndex > 0) {
            BigInteger smallerUnit = unit.divide(BIG_1024);
            int smallerIntegerDigits = value.divide(smallerUnit).toString().length();
            if (smallerIntegerDigits > 10) {
                break;
            }
            unit = smallerUnit;
            unitIndex--;
        }
        if (unitIndex == 0) {
            return NUMBER_FORMAT.get().format(value);
        }

        BigDecimal scaled = new BigDecimal(value).divide(new BigDecimal(unit), 2, RoundingMode.DOWN);
        return PRECISE_HUGE_DECIMAL.get().format(scaled) + HUGE_SUFFIXES[unitIndex];
    }

    public static String compactHugeAmountForSync(String decimalAmount) {
        String compact = compactHugeDecimal(decimalAmount);
        if (compact != null) {
            return compact;
        }
        String normalized = normalizeUnsignedDecimal(decimalAmount);
        if (normalized != null) {
            return normalized;
        }
        return bounded(decimalAmount, 128);
    }

    public static String bounded(String text, int maxLength) {
        if (text == null || maxLength <= 0) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String compactHugeDecimal(String decimalAmount) {
        String normalized = normalizeUnsignedDecimal(decimalAmount);
        if (normalized == null || normalized.length() <= MAX_EXACT_HUGE_DIGITS) {
            return null;
        }
        int digits = normalized.length();
        String significant = normalized.substring(0, Math.min(COMPACT_HUGE_SIGNIFICANT_DIGITS, digits));
        StringBuilder builder = new StringBuilder();
        builder.append(significant.charAt(0));
        if (significant.length() > 1) {
            builder.append('.').append(significant.substring(1));
        }
        builder.append('e').append(digits - 1);
        return builder.toString();
    }

    private static String normalizeUnsignedDecimal(String decimalAmount) {
        if (decimalAmount == null || decimalAmount.isBlank()) {
            return "0";
        }
        String value = decimalAmount.trim();
        int firstNonZero = -1;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
            if (c != '0' && firstNonZero < 0) {
                firstNonZero = i;
            }
        }
        return firstNonZero < 0 ? "0" : value.substring(firstNonZero);
    }

    public static String compactDecimal(long value, long unit, String suffix) {
        double scaled = (double) Math.max(0L, value) / (double) unit;
        if (scaled >= 100.0D || Math.abs(scaled - Math.rint(scaled)) < 0.05D) {
            return String.format(Locale.US, "%.0f%s", scaled, suffix);
        }
        return String.format(Locale.US, "%.1f%s", scaled, suffix);
    }

    public static String compactTaskAmount(long value) {
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

    public static String compactMetric(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        long unit = 1_000L;
        String suffix = "K";
        if (safe >= 1_000_000_000_000L) {
            unit = 1_000_000_000_000L;
            suffix = "T";
        } else if (safe >= 1_000_000_000L) {
            unit = 1_000_000_000L;
            suffix = "G";
        } else if (safe >= 1_000_000L) {
            unit = 1_000_000L;
            suffix = "M";
        }
        return COMPACT_DECIMAL.get().format((double) safe / (double) unit) + suffix;
    }

    public static String compactCount(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        if (safe < 1_000_000L) {
            return (safe / 1_000L) + "K";
        }
        if (safe < 1_000_000_000L) {
            return (safe / 1_000_000L) + "M";
        }
        if (safe < 1_000_000_000_000L) {
            return (safe / 1_000_000_000L) + "B";
        }
        return compactDecimal(safe, 1_000_000_000_000L, "T");
    }
}
