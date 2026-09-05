package cn.dancingsnow.neoecoae.client;

import appeng.util.ReadableNumberConverter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Preserves AE2 number formatting for exact planner amounts beyond the long range. */
public final class ECOPlannerAmountFormatter {
    private ECOPlannerAmountFormatter() {}

    public static String ae2Amount(long value) {
        return ReadableNumberConverter.format(Math.max(0L, value), 4);
    }

    public static String ae2Amount(BigInteger value) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        if (safe.bitLength() < Long.SIZE) return ae2Amount(safe.longValue());
        String[] suffixes = {"K", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q"};
        BigInteger base = safe;
        BigInteger last = safe;
        int index = -1;
        int length = safe.toString().length();
        while (length > 4 && index + 1 < suffixes.length) {
            last = base;
            base = base.divide(BigInteger.valueOf(1000));
            index++;
            length = base.toString().length() + 1;
        }
        if (index < 0) return safe.toString();
        String precise = new BigDecimal(last)
                        .divide(BigDecimal.valueOf(1000), 1, RoundingMode.DOWN)
                        .stripTrailingZeros()
                        .toPlainString()
                + suffixes[index];
        return precise.length() <= 4 ? precise : base + suffixes[index];
    }
}
