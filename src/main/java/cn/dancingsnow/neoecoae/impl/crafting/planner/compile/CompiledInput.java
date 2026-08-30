package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

public record CompiledInput(
    IPatternDetails.IInput source,
    AEKey key,
    long amountPerPattern,
    boolean fastSupported,
    String unsupportedReason,
    AEKey remainderKey,
    long remainderAmountPerPattern
) {
    public CompiledInput(IPatternDetails.IInput source, AEKey key, long amountPerPattern,
            boolean fastSupported, String unsupportedReason) {
        this(source, key, amountPerPattern, fastSupported, unsupportedReason, null, 0L);
    }
}
