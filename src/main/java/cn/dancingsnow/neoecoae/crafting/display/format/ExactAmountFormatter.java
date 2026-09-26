package cn.dancingsnow.neoecoae.crafting.display.format;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;

public final class ExactAmountFormatter {
    private ExactAmountFormatter() {}

    public static String slot(AEKey key, ExactAmount amount) {
        return amount.infinite() ? "∞" : BigNumberFormatter.format(amount.value(), key.getAmountPerUnit(), false);
    }

    /** Compatibility overload for callers that already have values in raw units. */
    public static String slot(ExactAmount amount) {
        return amount.infinite() ? "∞" : BigNumberFormatter.format(amount.value(), 1, false);
    }

    public static String full(AEKey key, ExactAmount amount) {
        return amount.infinite() ? "∞" : BigNumberFormatter.formatTooltip(amount.value(), key.getAmountPerUnit());
    }

    /** Compatibility overload for callers that already have values in raw units. */
    public static String full(ExactAmount amount) {
        return amount.infinite() ? "∞" : BigNumberFormatter.format(amount.value(), 1, true);
    }
}
