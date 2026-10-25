package cn.dancingsnow.neoecoae.blocks.entity;

import java.math.BigInteger;

/** Integer FE accounting, with fractional FE carried across ticks, refills, portions and saves. */
final class LargeWorkstationProcessing {
    private LargeWorkstationProcessing() {}

    record FluxPayment(long amount, long remainder) {}

    static FluxPayment fluxPayment(long energyPerCraft, long work, int ticks, long remainder) {
        if (energyPerCraft < 0 || work < 0 || ticks <= 0 || remainder < 0 || remainder >= ticks) {
            throw new IllegalArgumentException("Invalid processing payment");
        }
        var result = BigInteger.valueOf(energyPerCraft).multiply(BigInteger.valueOf(work))
            .add(BigInteger.valueOf(remainder)).divideAndRemainder(BigInteger.valueOf(ticks));
        return new FluxPayment(result[0].longValueExact(), result[1].longValueExact());
    }

    /** Apply the locked batch tier after pattern scaling; tier 9 always takes one tick. */
    static int duration(int ticks, long multiplier, LargeWorkstationOverclock profile) {
        if (profile.coolingTier() == 9) return 1;
        int divisor = switch (profile.coolingTier()) {
            case 2 -> 2; // Displayed as tier 4; shared cooling recipes use max_overclock = 2.
            case 6 -> 5;
            default -> 1;
        };
        long scaled = Math.multiplyExact((long) ticks, multiplier);
        return Math.toIntExact(Math.max(1L, Math.ceilDiv(scaled, divisor)));
    }
}
