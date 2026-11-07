package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

/** Integer FE accounting, with fractional FE carried across ticks, refills, portions and saves. */
final class LargeWorkstationProcessing {
    private LargeWorkstationProcessing() {}

    record FluxPayment(long amount, long remainder) {}

    static FluxPayment fluxPayment(long energyPerCraft, long work, int ticks, long remainder) {
        if (energyPerCraft < 0 || work < 0 || ticks <= 0 || remainder < 0 || remainder >= ticks) {
            throw new IllegalArgumentException("Invalid processing payment");
        }
        var result = PlannerAmount.of(energyPerCraft).multiply(work)
            .add(remainder).divideAndRemainder(PlannerAmount.of(ticks));
        return new FluxPayment(result[0].longValueExact(), result[1].longValueExact());
    }

    /** A multiplied pattern occupies the same lanes for proportionally longer. */
    static int duration(int ticks, long multiplier) {
        return PlannerAmount.of(ticks).multiply(multiplier).intValueExact();
    }
}
