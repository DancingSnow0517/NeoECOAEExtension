package cn.dancingsnow.neoecoae.recipe;

import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Converts recipe budgets in the same units passed to AE2's configured energy payment. */
public final class WorkstationEnergy {
    private WorkstationEnergy() {}

    private static BigDecimal fePerAe() {
        double ratio = PowerUnits.AE.convertTo(PowerUnits.FE, PowerMultiplier.CONFIG.multiply(1));
        if (!Double.isFinite(ratio) || ratio <= 0) throw new IllegalArgumentException("Invalid FE conversion");
        return BigDecimal.valueOf(ratio);
    }

    public static BigDecimal aeEquivalent(long fe) {
        if (fe < 0) throw new IllegalArgumentException("Negative FE budget");
        return BigDecimal.valueOf(fe).divide(fePerAe(), MathContext.DECIMAL128);
    }

    public static long feEquivalent(long ae) {
        if (ae < 0) throw new IllegalArgumentException("Negative AE budget");
        return BigDecimal.valueOf(ae).multiply(fePerAe()).setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
