package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;

public record CompiledInput(
    IPatternDetails.IInput source,
    AEKey key,
    PlannerAmount amountPerPattern,
    boolean fastSupported,
    String unsupportedReason,
    AEKey remainderKey,
    PlannerAmount remainderAmountPerPattern
) {
    /** Exact one-item, same-key return that can be held as working stock across a batch. */
    public boolean reusable() {
        return remainderKey != null && remainderKey.equals(key)
            && amountPerPattern.fitsLong() && remainderAmountPerPattern.fitsLong()
            && amountPerPattern.longValueExact() == 1L
            && remainderAmountPerPattern.longValueExact() == 1L;
    }

    public CompiledInput(IPatternDetails.IInput source, AEKey key, long amountPerPattern,
            boolean fastSupported, String unsupportedReason) {
        this(source, key, PlannerAmount.of(amountPerPattern), fastSupported, unsupportedReason,
            null, PlannerAmount.ZERO);
    }

    public CompiledInput(IPatternDetails.IInput source, AEKey key, long amountPerPattern,
            boolean fastSupported, String unsupportedReason, AEKey remainderKey,
            long remainderAmountPerPattern) {
        this(source, key, PlannerAmount.of(amountPerPattern), fastSupported, unsupportedReason,
            remainderKey, PlannerAmount.of(remainderAmountPerPattern));
    }

    public CompiledInput(IPatternDetails.IInput source, AEKey key, PlannerAmount amountPerPattern,
            boolean fastSupported, String unsupportedReason) {
        this(source, key, amountPerPattern, fastSupported, unsupportedReason, null, PlannerAmount.ZERO);
    }
}
