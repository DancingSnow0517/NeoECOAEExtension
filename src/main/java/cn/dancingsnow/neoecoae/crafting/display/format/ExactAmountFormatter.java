package cn.dancingsnow.neoecoae.crafting.display.format;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;

import cn.dancingsnow.neoecoae.crafting.display.format.ExtendedDecimalUnits;
import java.text.NumberFormat;

public final class ExactAmountFormatter {
    private ExactAmountFormatter() {}

    public static String slot(ExactAmount amount) {
        if (amount.infinite()) return "∞";
        String digits = amount.value().toString();
        int group = (digits.length() - 1) / 3;
        if (group <= 0) return digits;
        int leading = digits.length() - group * 3;
        int fractionLength = Math.max(0, 3 - leading);
        String fraction = digits.substring(leading, Math.min(leading + fractionLength, digits.length()));
        fraction = fraction.replaceFirst("0+$", "");
        return digits.substring(0, leading) + (fraction.isEmpty() ? "" : "." + fraction)
            + ExtendedDecimalUnits.suffix(group);
    }

    public static String full(ExactAmount amount) {
        return amount.infinite() ? "∞" : NumberFormat.getIntegerInstance(java.util.Locale.US).format(amount.value());
    }
}
