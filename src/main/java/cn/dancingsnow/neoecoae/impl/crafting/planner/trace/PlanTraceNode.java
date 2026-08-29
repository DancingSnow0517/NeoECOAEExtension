package cn.dancingsnow.neoecoae.impl.crafting.planner.trace;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

public record PlanTraceNode(
    Kind kind,
    @Nullable AEKey key,
    @Nullable IPatternDetails pattern,
    long requested,
    long fromInventory,
    long toCraft,
    long missing,
    long firingCount,
    Selection selection,
    @Nullable String reason
) {
    public enum Kind { GOAL, MATERIAL, PATTERN, CYCLE_GROUP }
    public enum Selection { NOT_APPLICABLE, SELECTED, REJECTED, UNSUPPORTED }
}
