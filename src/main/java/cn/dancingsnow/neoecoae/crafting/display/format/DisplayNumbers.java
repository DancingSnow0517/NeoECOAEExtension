package cn.dancingsnow.neoecoae.crafting.display.format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Shared, locale-independent number display. Never use these strings for resource accounting. */
public final class DisplayNumbers {
    private DisplayNumbers() {}

    /** Complete numeric value with a comma every three integer digits. */
    public static String grouped(Number value) {
        return grouped(decimalValue(value).stripTrailingZeros().toPlainString());
    }

    /** Decimal prefixes (K, M, G, ...) with at most three significant digits. */
    public static String compact(Number value) {
        return compact(value, 6);
    }

    /** Same compact format, with fewer decimal places when a label must fit a narrow column. */
    public static String compact(Number value, int maxDecimals) {
        BigDecimal decimal = decimalValue(value).stripTrailingZeros();
        if (decimal.signum() == 0) return "0";
        BigDecimal magnitude = decimal.abs();
        int exponent = magnitude.precision() - magnitude.scale() - 1;
        // Retain the established extended-unit range, then bound the label with scientific notation.
        if (magnitude.compareTo(BigDecimal.ONE.scaleByPowerOfTen(60)) > 0) {
            return grouped(decimal.movePointLeft(exponent).setScale(Math.min(2, Math.max(0, maxDecimals)), RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString()) + "×10^" + grouped(exponent);
        }
        if (exponent < 0) return grouped(decimal.toPlainString());
        int group = exponent / 3;
        BigDecimal scaled = decimal.movePointLeft(group * 3);
        int integerDigits = Math.max(1, scaled.precision() - scaled.scale());
        int decimals = Math.min(Math.max(0, maxDecimals), Math.max(0, 3 - integerDigits));
        return grouped(scaled.setScale(decimals, RoundingMode.DOWN).stripTrailingZeros().toPlainString())
            + ExtendedDecimalUnits.suffix(group);
    }

    /** Readable tooltip values: three decimal places, spaced prefixes through Y, then scientific notation. */
    public static String compactTooltip(Number value) {
        BigDecimal decimal = decimalValue(value).stripTrailingZeros();
        if (decimal.abs().compareTo(BigDecimal.valueOf(1000)) < 0) {
            BigDecimal rounded = decimal.setScale(3, RoundingMode.HALF_UP);
            if (rounded.abs().compareTo(BigDecimal.valueOf(1000)) < 0)
                return grouped(rounded.signum() == 0 ? decimal : rounded);
        }
        int exponent = decimal.precision() - decimal.scale() - 1;
        int group = exponent / 3;
        BigDecimal scaled = decimal.movePointLeft(group * 3).setScale(3, RoundingMode.HALF_UP);
        if (scaled.abs().compareTo(BigDecimal.valueOf(1000)) >= 0) {
            group++;
            scaled = scaled.movePointLeft(3);
        }
        if (group <= 8) return grouped(scaled) + " " + ExtendedDecimalUnits.suffix(group);

        BigDecimal mantissa = decimal.movePointLeft(exponent).setScale(3, RoundingMode.HALF_UP);
        if (mantissa.abs().compareTo(BigDecimal.TEN) >= 0) {
            exponent++;
            mantissa = mantissa.movePointLeft(1);
        }
        StringBuilder power = new StringBuilder();
        for (char digit : Integer.toString(exponent).toCharArray()) power.append("⁰¹²³⁴⁵⁶⁷⁸⁹".charAt(digit - '0'));
        return grouped(mantissa) + " × 10" + power;
    }

    /** Fixed decimal precision for temperatures, percentages and other values whose unit must not scale. */
    public static String decimal(double value, int decimals) {
        return grouped(decimalValue(value).setScale(Math.max(0, decimals), RoundingMode.HALF_UP).toPlainString());
    }

    public static String signedDecimal(double value, int decimals) {
        String formatted = decimal(value, decimals);
        return formatted.startsWith("-") ? formatted : "+" + formatted;
    }

    private static BigDecimal decimalValue(Number value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof BigInteger integer) return new BigDecimal(integer);
        if (value instanceof Double || value instanceof Float) {
            return Double.isFinite(value.doubleValue()) ? new BigDecimal(value.toString()) : BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value.longValue());
    }

    public static String grouped(String value) {
        int start = value.startsWith("-") || value.startsWith("+") ? 1 : 0;
        int end = start;
        while (end < value.length() && (value.charAt(end) >= '0' && value.charAt(end) <= '9'
                || value.charAt(end) == ',')) end++;
        String digits = value.substring(start, end).replace(",", "");
        StringBuilder result = new StringBuilder(value.substring(0, start));
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) result.append(',');
            result.append(digits.charAt(i));
        }
        return result.append(value.substring(end)).toString();
    }

    /** Converts a compact amount such as 500PQ to a complete byte label such as 500 PQB. */
    public static String bytes(String compactAmount) {
        int end = 0;
        while (end < compactAmount.length()) {
            char c = compactAmount.charAt(end);
            if (!(c >= '0' && c <= '9' || c == ',' || c == '.' || c == '-' || c == '+')) break;
            end++;
        }
        return grouped(compactAmount.substring(0, end)) + " "
            + compactAmount.substring(end).trim().toUpperCase(java.util.Locale.ROOT) + "B";
    }
}
