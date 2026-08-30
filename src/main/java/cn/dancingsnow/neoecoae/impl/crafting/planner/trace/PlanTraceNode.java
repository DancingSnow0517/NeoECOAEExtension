package cn.dancingsnow.neoecoae.impl.crafting.planner.trace;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
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
    @Nullable String reason,
    BigInteger exactRequested,
    BigInteger exactFromInventory,
    BigInteger exactToCraft,
    BigInteger exactMissing,
    BigInteger exactFiringCount
) {
    public PlanTraceNode(Kind kind, @Nullable AEKey key, @Nullable IPatternDetails pattern,
            long requested, long fromInventory, long toCraft, long missing, long firingCount,
            Selection selection, @Nullable String reason) {
        this(kind, key, pattern, requested, fromInventory, toCraft, missing, firingCount, selection, reason,
            BigInteger.valueOf(requested), BigInteger.valueOf(fromInventory), BigInteger.valueOf(toCraft),
            BigInteger.valueOf(missing), BigInteger.valueOf(firingCount));
    }

    public PlanTraceNode withExact(BigInteger requested, BigInteger fromInventory, BigInteger toCraft,
            BigInteger missing, BigInteger firingCount) {
        return new PlanTraceNode(kind, key, pattern, compatibilityLong(requested), compatibilityLong(fromInventory),
            compatibilityLong(toCraft), compatibilityLong(missing), compatibilityLong(firingCount), selection, reason,
            requested, fromInventory, toCraft, missing, firingCount);
    }

    private static long compatibilityLong(BigInteger value) {
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return Long.MAX_VALUE;
        if (value.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) return Long.MIN_VALUE;
        return value.longValue();
    }
    public enum Kind { GOAL, MATERIAL, PATTERN, CYCLE_GROUP }
    public enum Selection { NOT_APPLICABLE, SELECTED, REJECTED, UNSUPPORTED }
}
