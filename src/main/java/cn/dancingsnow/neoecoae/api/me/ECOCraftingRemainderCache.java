package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import com.google.common.collect.MapMaker;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Weak, reload-aware memoization for AE2 recipe remainders.
 *
 * <p>AE2 rebuilds the complete crafting input whenever it asks one crafting-pattern input what remains after
 * consuming a concrete key. During dispatch the same immutable pattern input and concrete key are queried many
 * times, while inventory availability is still checked separately on every attempt. Caching only this pure
 * mapping removes the repeated ItemStack/component construction without retaining resolved inventory state or
 * keeping obsolete decoded patterns alive.</p>
 */
final class ECOCraftingRemainderCache {
    private static final ECOCraftingRemainderCache SHARED = new ECOCraftingRemainderCache();

    // Weak identity keys match the lifetime of AE2's decoded pattern inputs without retaining obsolete patterns.
    private final Map<IPatternDetails.IInput, Map<AEKey, CachedRemainder>> byInput =
        new MapMaker().weakKeys().makeMap();
    private long reloadGeneration = Long.MIN_VALUE;

    static ECOCraftingRemainderCache shared() {
        return SHARED;
    }

    @Nullable
    synchronized AEKey get(IPatternDetails.IInput input, AEKey key) {
        long currentGeneration = AE2PatternIntrospection.reloadGeneration();
        if (reloadGeneration != currentGeneration) {
            byInput.clear();
            reloadGeneration = currentGeneration;
        }

        Map<AEKey, CachedRemainder> byKey = byInput.computeIfAbsent(input, ignored -> new HashMap<>());
        CachedRemainder cached = byKey.get(key);
        if (cached != null) {
            return cached.key;
        }

        AEKey remainder = input.getRemainingKey(key);
        byKey.put(key, new CachedRemainder(remainder));
        return remainder;
    }

    private record CachedRemainder(@Nullable AEKey key) {
    }
}
