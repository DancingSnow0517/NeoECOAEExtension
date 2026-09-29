package cn.dancingsnow.neoecoae.crafting.display.format;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.math.BigInteger;

public final class ExactAmountFormatter {
    private ExactAmountFormatter() {}
    /**
     * Formats a terminal slot amount using the resource's unit size. This overload deliberately
     * accepts only the primitive unit size so the crafting display module can be compiled without
     * AE2 on its class path.
     */
    public static String slot(ExactAmount amount, int amountPerUnit) {
        return amount == null
                ? "0"
                : amount.infinite() ? "∞" : BigNumberFormatter.formatSI(amount.value(), amountPerUnit);
    }

    public static String slot(ExactAmount amount) {
        return slot(amount, 1);
    }

    /** Formats a complete, comma-grouped terminal tooltip amount. */
    public static String full(ExactAmount amount, int amountPerUnit) {
        return amount == null
                ? "0"
                : amount.infinite() ? "∞" : BigNumberFormatter.formatTooltip(amount.value(), amountPerUnit);
    }

    public static String full(ExactAmount amount) {
        return full(amount, 1);
    }

    public static String full(BigInteger amount, int amountPerUnit) { return BigNumberFormatter.formatTooltip(amount, amountPerUnit); }
    public static String compact(BigInteger amount, int amountPerUnit) { return BigNumberFormatter.formatSI(amount, amountPerUnit); }
}
