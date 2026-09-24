package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import com.google.common.collect.MapMaker;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Reload-aware memoization of the recipe's remainder for a concrete input key. */
final class ECOCraftingRemainderCache {
    private static final ECOCraftingRemainderCache SHARED = new ECOCraftingRemainderCache();

    private final Map<IPatternDetails.IInput, Map<AEKey, CachedRemainder>> byInput =
            new MapMaker().weakKeys().makeMap();
    private final Map<IPatternDetails.IInput, Map<AEKey, CachedRemainder>> recentInputs =
            new Reference2ObjectOpenHashMap<>();
    private long reloadGeneration = Long.MIN_VALUE;

    static ECOCraftingRemainderCache shared() {
        return SHARED;
    }

    @Nullable
    synchronized AEKey get(IPatternDetails.IInput input, AEKey key) {
        long generation = AE2PatternIntrospection.reloadGeneration();
        if (reloadGeneration != generation) {
            byInput.clear();
            recentInputs.clear();
            reloadGeneration = generation;
        }

        Map<AEKey, CachedRemainder> byKey = recentInputs.get(input);
        if (byKey == null) {
            byKey = byInput.computeIfAbsent(input, ignored -> new Object2ObjectOpenHashMap<>());
            if (recentInputs.size() >= 128) recentInputs.clear();
            recentInputs.put(input, byKey);
        }
        CachedRemainder cached = byKey.get(key);
        if (cached != null) return cached.key();

        AEKey remainder = input.getRemainingKey(key);
        byKey.put(key, new CachedRemainder(remainder));
        return remainder;
    }

    private record CachedRemainder(@Nullable AEKey key) {}
}
