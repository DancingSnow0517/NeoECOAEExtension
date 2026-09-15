package cn.dancingsnow.neoecoae.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class ExactAmountFormatter {
    private ExactAmountFormatter() {}

    public static String full(BigInteger amount, int amountPerUnit) {
        return new BigDecimal(amount)
                .divide(BigDecimal.valueOf(Math.max(1, amountPerUnit)), 12, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString();
    }

    /** Scientific notation remains bounded even for numbers beyond the named SI suffixes. */
    public static String compact(BigInteger amount, int amountPerUnit) {
        BigDecimal units = new BigDecimal(amount)
                .divide(BigDecimal.valueOf(Math.max(1, amountPerUnit)), 12, RoundingMode.DOWN)
                .stripTrailingZeros();
        if (units.compareTo(BigDecimal.valueOf(10000)) < 0) return units.toPlainString();
        int exponent = units.precision() - units.scale() - 1;
        String mantissa = units.movePointLeft(exponent)
                .setScale(1, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString();
        return mantissa + "e" + exponent;
    }
}
