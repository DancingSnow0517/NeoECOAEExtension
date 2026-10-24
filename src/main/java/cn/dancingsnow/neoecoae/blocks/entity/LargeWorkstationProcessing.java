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

    /** A multiplied pattern occupies the same lanes for proportionally longer. */
    static int duration(int ticks, long multiplier) {
        return Math.toIntExact(Math.multiplyExact((long) ticks, multiplier));
    }
}
