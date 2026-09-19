package cn.dancingsnow.neoecoae.crafting.display.format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class ExactAmountFormatter {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final String[] SIZE_SUFFIXES = {"", "K", "M", "G", "T", "P", "E", "Z", "Y", "B", "N", "D"};

    private ExactAmountFormatter() {}

    public static String full(BigInteger amount, int amountPerUnit) {
        String value = new BigDecimal(amount)
                .divide(BigDecimal.valueOf(Math.max(1, amountPerUnit)), 12, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString();
        int decimalPoint = value.indexOf('.');
        int integerEnd = decimalPoint >= 0 ? decimalPoint : value.length();
        int integerStart = value.startsWith("-") ? 1 : 0;
        StringBuilder grouped = new StringBuilder(value.length() + (integerEnd - integerStart) / 3);
        grouped.append(value, 0, integerStart);
        for (int i = integerStart; i < integerEnd; i++) {
            if (i > integerStart && (integerEnd - i) % 3 == 0) grouped.append(',');
            grouped.append(value.charAt(i));
        }
        grouped.append(value, integerEnd, value.length());
        return grouped.toString();
    }

    /** Uses the storage unit sequence and never falls back to scientific notation. */
    public static String compact(BigInteger amount, int amountPerUnit) {
        BigDecimal units = new BigDecimal(amount)
                .divide(BigDecimal.valueOf(Math.max(1, amountPerUnit)), 12, RoundingMode.DOWN)
                .stripTrailingZeros();
        int suffixIndex = 0;
        while (units.abs().compareTo(THOUSAND) >= 0 && suffixIndex < SIZE_SUFFIXES.length - 1) {
            units = units.divide(THOUSAND);
            suffixIndex++;
        }
        int integerDigits =
                units.signum() == 0 ? 1 : units.abs().precision() - units.abs().scale();
        return units.setScale(3 - integerDigits, RoundingMode.DOWN).toPlainString() + SIZE_SUFFIXES[suffixIndex];
    }
}
