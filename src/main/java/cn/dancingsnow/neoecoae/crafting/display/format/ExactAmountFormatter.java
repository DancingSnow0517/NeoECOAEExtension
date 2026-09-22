package cn.dancingsnow.neoecoae.crafting.display.format;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;

public final class ExactAmountFormatter {
    private ExactAmountFormatter() {}

    public static String slot(ExactAmount amount) {
        return amount.infinite() ? "∞" : BigNumberFormatter.format(amount.value(), 1, false);
    }

    public static String full(ExactAmount amount) {
        return amount.infinite() ? "∞" : BigNumberFormatter.format(amount.value(), 1, true);
    }
}
