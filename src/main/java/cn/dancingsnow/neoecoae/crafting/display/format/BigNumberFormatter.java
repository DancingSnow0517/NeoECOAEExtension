package cn.dancingsnow.neoecoae.crafting.display.format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Shared display format for exact amounts that can exceed AE2's extended units. */
public final class BigNumberFormatter {
    private static final BigInteger TWO_Q = BigInteger.TEN.pow(60);
    private static final BigDecimal TWO_Q_DECIMAL = new BigDecimal(TWO_Q);
    private static final int SIGNIFICANT_DIGITS = 3;

    private BigNumberFormatter() {
    }

    /** Formats an amount after converting it from raw units to the key's display unit. */
    public static String format(BigInteger amount, int amountPerUnit, boolean full) {
        BigInteger safe = amount == null || amount.signum() < 0 ? BigInteger.ZERO : amount;
        BigDecimal display = new BigDecimal(safe)
            .divide(BigDecimal.valueOf(Math.max(1, amountPerUnit)), 6, RoundingMode.DOWN)
            .stripTrailingZeros();
        if (display.compareTo(TWO_Q_DECIMAL) > 0) {
            return scientific(display);
        }
        if (full) {
            return DisplayNumbers.grouped(display.toPlainString());
        }
        return compact(display);
    }

    /** Formats an exact amount using SI decimal prefixes and at most three significant digits. */
    public static String formatSI(BigInteger amount, int amountPerUnit) {
        return format(amount, amountPerUnit, false);
    }

    /** Formats an exact amount for a tooltip without shortening the integer digits. */
    public static String formatTooltip(BigInteger amount, int amountPerUnit) {
        BigInteger safe = amount == null || amount.signum() < 0 ? BigInteger.ZERO : amount;
        BigDecimal display = new BigDecimal(safe)
            .divide(BigDecimal.valueOf(Math.max(1, amountPerUnit)), 6, RoundingMode.DOWN)
            .setScale(0, RoundingMode.HALF_UP)
            .stripTrailingZeros();
        return DisplayNumbers.grouped(display.toPlainString());
    }

    private static String compact(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }
        if (value.compareTo(BigDecimal.ONE) < 0) {
            return value.toPlainString();
        }

        int exponent = value.precision() - value.scale() - 1;
        int group = Math.max(0, exponent / 3);
        BigDecimal scaled = value.movePointLeft(group * 3);
        int integerDigits = Math.max(1, scaled.precision() - scaled.scale());
        int decimals = Math.max(0, SIGNIFICANT_DIGITS - integerDigits);
        String mantissa = scaled.setScale(decimals, RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString();
        return DisplayNumbers.grouped(mantissa) + ExtendedDecimalUnits.suffix(group);
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
