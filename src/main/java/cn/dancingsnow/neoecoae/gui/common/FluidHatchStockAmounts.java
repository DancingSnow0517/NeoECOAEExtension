package cn.dancingsnow.neoecoae.gui.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

/** Exact conversion of the native number widget's displayed units to stored units. */
public final class FluidHatchStockAmounts {
    private FluidHatchStockAmounts() {}

    public static BigDecimal externalValue(BigDecimal value, int amountPerUnit) {
        return value.multiply(BigDecimal.valueOf(amountPerUnit)).setScale(0, RoundingMode.UP);
    }

    public static OptionalLong checkedValue(BigDecimal value, int amountPerUnit, long min, long max) {
        if (amountPerUnit <= 0 || value.signum() < 0
            || (amountPerUnit == 1 && value.scale() > 0)) return OptionalLong.empty();
        try {
            long amount = externalValue(value, amountPerUnit).longValueExact();
            return amount < min || amount > max ? OptionalLong.empty() : OptionalLong.of(amount);
        } catch (ArithmeticException ignored) {
            return OptionalLong.empty();
        }
    }
}
