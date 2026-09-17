package cn.dancingsnow.neoecoae.terminal.bigamount;

import java.math.BigInteger;
import java.text.NumberFormat;

public final class ExactAmountFormatter {
    private static final String[] UNITS = { "", "k", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q" };

    private ExactAmountFormatter() {}

    public static String slot(ExactAmount amount) {
        if (amount.infinite()) return "∞";
        String digits = amount.value().toString();
        int group = (digits.length() - 1) / 3;
        if (group <= 0) return digits;
        if (group >= UNITS.length) return digits.charAt(0) + "e" + (digits.length() - 1);
        int leading = digits.length() - group * 3;
        int fractionLength = Math.max(0, 3 - leading);
        String fraction = digits.substring(leading, Math.min(leading + fractionLength, digits.length()));
        fraction = fraction.replaceFirst("0+$", "");
        return digits.substring(0, leading) + (fraction.isEmpty() ? "" : "." + fraction) + UNITS[group];
    }

    public static String full(ExactAmount amount) {
        return amount.infinite() ? "∞" : NumberFormat.getIntegerInstance().format(amount.value());
    }
}
