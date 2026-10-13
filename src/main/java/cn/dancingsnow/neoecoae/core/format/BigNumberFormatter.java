package cn.dancingsnow.neoecoae.core.format;

import cn.dancingsnow.neoecoae.core.math.BigIntegers;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.function.ToIntFunction;

/** Shared display format for exact amounts that can exceed AE2's extended units. */
public final class BigNumberFormatter {
    private static final BigInteger TWO_Q = BigInteger.TEN.pow(60);
    private static final BigDecimal TWO_Q_DECIMAL = new BigDecimal(TWO_Q);
    private static final int SIGNIFICANT_DIGITS = 3;
    // Decimal prefixes continued beyond Q by appending another Q every ten groups.
    private static final String[] PREFIXES = {"", "K", "M", "G", "T", "P", "E", "Z", "Y", "R"};
    // Largest unit used when shortening to fit; anything larger falls through to the exponent form.
    private static final int MAX_FIT_GROUP = 10;

    private BigNumberFormatter() {
    }

    /** Formats an amount after converting it from raw units to the key's display unit. */
    public static String format(BigInteger amount, int amountPerUnit, boolean full) {
        return format(amount, amountPerUnit, full, false);
    }

    /** Formats an exact amount using SI decimal prefixes and at most three significant digits. */
    public static String formatSI(BigInteger amount, int amountPerUnit) {
        return format(amount, amountPerUnit, false, false);
    }

    /** As {@link #formatSI(BigInteger, int)}, but {@code lowercaseKilo} spells the thousands prefix as SI "k". */
    public static String formatSI(BigInteger amount, int amountPerUnit, boolean lowercaseKilo) {
        return format(amount, amountPerUnit, false, lowercaseKilo);
    }

    /** Formats an exact amount for a tooltip without shortening the integer digits. */
    public static String formatTooltip(BigInteger amount, int amountPerUnit) {
        BigDecimal display = display(amount, amountPerUnit).setScale(0, RoundingMode.HALF_UP).stripTrailingZeros();
        return DisplayNumbers.grouped(display.toPlainString());
    }

    /**
     * Formats a non-negative amount in base-1000 units with a fixed number of fraction digits, rounded with
     * {@code rounding}. Use this where the displayed precision differs from {@link #formatSI(BigInteger, int)}.
     */
    public static String compactFixed(BigInteger amount, int decimals, RoundingMode rounding, boolean lowercaseKilo) {
        BigDecimal value = new BigDecimal(BigIntegers.nonNegative(amount));
        return atGroup(value, group(value), decimals, rounding, lowercaseKilo);
    }

    /**
     * Shortens an amount until {@code width} reports it fits in {@code maxWidth}: the exact digits first, then
     * base-1000 units from the smallest up to Q with progressively fewer decimals, then an exponent form.
     */
    public static String fit(BigInteger amount, int maxWidth, ToIntFunction<String> width) {
        BigInteger safe = BigIntegers.nonNegative(amount);
        String exact = format(safe, 1, true);
        if (width.applyAsInt(exact) <= maxWidth) {
            return exact;
        }

        BigDecimal value = new BigDecimal(safe);
        int natural = Math.min(group(value), MAX_FIT_GROUP);
        for (int decimals = 2; decimals >= 0; decimals--) {
            for (int group = 1; group <= natural; group++) {
                String candidate = atGroup(value, group, decimals, RoundingMode.HALF_UP, false);
                if (width.applyAsInt(candidate) <= maxWidth) {
                    return candidate;
                }
            }
        }

        int exponent = Math.max(0, safe.toString().length() - 1);
        BigDecimal divisor = BigDecimal.TEN.pow(exponent);
        for (int decimals = 3; decimals >= 0; decimals--) {
            String candidate = value.divide(divisor, decimals, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "e" + exponent;
            if (width.applyAsInt(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return "e" + exponent;
    }

    private static String format(BigInteger amount, int amountPerUnit, boolean full, boolean lowercaseKilo) {
        BigDecimal display = display(amount, amountPerUnit).stripTrailingZeros();
        if (display.compareTo(TWO_Q_DECIMAL) > 0) {
            return scientific(display);
        }
        if (full) {
            return DisplayNumbers.grouped(display.toPlainString());
        }
        return compact(display, lowercaseKilo);
    }

    private static BigDecimal display(BigInteger amount, int amountPerUnit) {
        return new BigDecimal(BigIntegers.nonNegative(amount))
            .divide(BigDecimal.valueOf(Math.max(1, amountPerUnit)), 6, RoundingMode.DOWN);
    }

    private static String compact(BigDecimal value, boolean lowercaseKilo) {
        if (value.compareTo(BigDecimal.ONE) < 0) {
            return value.toPlainString();
        }

        int group = group(value);
        BigDecimal scaled = value.movePointLeft(group * 3);
        int integerDigits = Math.max(1, scaled.precision() - scaled.scale());
        int decimals = Math.max(0, SIGNIFICANT_DIGITS - integerDigits);
        return withSuffix(scaled, decimals, RoundingMode.DOWN, group, lowercaseKilo);
    }

    private static String atGroup(BigDecimal value, int group, int decimals, RoundingMode rounding,
                                  boolean lowercaseKilo) {
        return withSuffix(value.movePointLeft(group * 3), decimals, rounding, group, lowercaseKilo);
    }

    private static String withSuffix(BigDecimal scaled, int decimals, RoundingMode rounding, int group,
                                     boolean lowercaseKilo) {
        String mantissa = scaled.setScale(decimals, rounding).stripTrailingZeros().toPlainString();
        return DisplayNumbers.grouped(mantissa) + suffix(group, lowercaseKilo);
    }

    /** Index of the base-1000 group holding the value, i.e. floor(log10(value) / 3). */
    private static int group(BigDecimal value) {
        int exponent = value.precision() - value.scale() - 1;
        return Math.max(0, exponent / 3);
    }

    /** Returns the suffix for a base-1000 group: Q, KQ, ... QQ, KQQ, ... */
    private static String suffix(int group, boolean lowercaseKilo) {
        int prefix = group % PREFIXES.length;
        // Index 1 is the thousands prefix, the only one whose SI spelling differs from the mod's K.
        String name = lowercaseKilo && prefix == 1 ? "k" : PREFIXES[prefix];
        return name + "Q".repeat(group / PREFIXES.length);
    }

    private static String scientific(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        int exponent = normalized.precision() - normalized.scale() - 1;
        String mantissa = normalized.movePointLeft(exponent)
            .setScale(SIGNIFICANT_DIGITS - 1, RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString();
        return mantissa + "×10^" + exponent;
    }
}
