package cn.dancingsnow.neoecoae.crafting.display.format;

import java.math.BigInteger;
import java.math.RoundingMode;

/** Formats CPU co-processor counts for the ECO crafting confirmation screens. */
public final class NEByteFormatter {
    private NEByteFormatter() {
    }

    /**
     * Formats the co-processor count using AE2's decimal unit conventions without exposing a
     * potentially very large raw integer in the confirmation screen.
     */
    public static String formatCpuCoProcessors(long coProcessors) {
        return BigNumberFormatter.compactFixed(BigInteger.valueOf(coProcessors), 3, RoundingMode.HALF_UP, true);
    }
}
