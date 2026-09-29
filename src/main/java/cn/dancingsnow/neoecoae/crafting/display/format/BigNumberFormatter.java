package cn.dancingsnow.neoecoae.crafting.display.format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Formats exact terminal quantities without narrowing to long or double. */
public final class BigNumberFormatter {
    private static final int SIGNIFICANT_DIGITS = 3;
    private static final String[] UNITS = {"", "K", "M", "G", "T", "P", "E", "Z", "Y", "B", "N", "D"};

    private BigNumberFormatter() {}

    public static String formatSI(BigInteger amount, int amountPerUnit) {
        BigDecimal value = units(amount, amountPerUnit);
        if (value.signum() == 0) return "0";
        int exponent = value.precision() - value.scale() - 1;
        int group = Math.max(0, exponent / 3);
        BigDecimal scaled = value.movePointLeft(group * 3);
        int integerDigits = Math.max(1, scaled.precision() - scaled.scale());
        int decimals = Math.max(0, SIGNIFICANT_DIGITS - integerDigits);
        String mantissa = scaled.setScale(decimals, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
        // Keep the established AE2 suffixes for quantities beyond the vanilla range.
        String suffix = group < UNITS.length ? UNITS[group] : "×10^" + (group * 3);
        return mantissa + suffix;
    }

    public static String formatTooltip(BigInteger amount, int amountPerUnit) {
        // Keep the fractional part produced by the key's unit size. `units` is intentionally
        // limited to twelve decimal places, which is enough for fluid and custom resource keys
        // while keeping the value deterministic and free of binary floating point rounding.
        String value = units(amount, amountPerUnit).toPlainString();
        int dot = value.indexOf('.');
        int end = dot < 0 ? value.length() : dot;
        StringBuilder out = new StringBuilder(value.length() + value.length() / 3);
        int start = value.startsWith("-") ? 1 : 0;
        out.append(value, 0, start);
        for (int i = start; i < end; i++) {
            if (i > start && (end - i) % 3 == 0) out.append(',');
            out.append(value.charAt(i));
        }
        if (dot >= 0) out.append(value, dot, value.length());
        return out.toString();
    }

    public static String format(BigInteger amount, int amountPerUnit, boolean full) {
        return full ? formatTooltip(amount, amountPerUnit) : formatSI(amount, amountPerUnit);
    }

    private static BigDecimal units(BigInteger amount, int amountPerUnit) {
        BigInteger safe = amount == null || amount.signum() < 0 ? BigInteger.ZERO : amount;
        return new BigDecimal(safe).divide(BigDecimal.valueOf(Math.max(1, amountPerUnit)), 12, RoundingMode.DOWN)
                .stripTrailingZeros();
    }
}
