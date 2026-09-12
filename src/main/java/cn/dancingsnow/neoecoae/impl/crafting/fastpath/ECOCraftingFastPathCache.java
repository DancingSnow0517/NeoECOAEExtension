package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Recipe-level fast-path knowledge.
 *
 * <p>Scope: one cache instance is owned by a {@code NECraftingCluster}, and a {@code NECraftingNetworkCluster}
 * owns one shared instance that supersedes its members' local caches while they are grouped by a Network
 * Switch. An entry therefore states "assembling this pattern with these concrete inputs in this dimension
 * produced exactly these outputs/remainders/inputs", which is worker-independent: every ECO FX worker runs the
 * same {@code IMolecularAssemblerSupportedPattern.assemble()} against the same level. Worker capacity and
 * worker eligibility are deliberately <em>not</em> stored here; they are re-evaluated per dispatch.
 *
 * <p>Invalidation: the {@link ECOFastPathKey} carries the reload generation and the dimension, so recipe,
 * datapack and server reloads can never match an older entry; {@link #clearAllCaches()} additionally frees
 * their memory. Losing a cluster (rebuild, block removal, chunk/world unload) drops the cache with it, and a
 * network group whose membership changes clears its shared cache.
 */
public final class ECOCraftingFastPathCache {
    public static final int MIN_CACHE_SIZE = 16;
    public static final int MAX_CACHE_SIZE = 16_384;

    private static final long NEGATIVE_CACHE_TTL_TICKS = 1_200L;
    private static final Set<ECOCraftingFastPathCache> ACTIVE_CACHES = Collections.newSetFromMap(new WeakHashMap<>());

    private final int limit;
    private final Map<ECOFastPathKey, ECOFastPathResult> entries;
    private final Map<ECOFastPathPatternKey, ECOPatternEligibility> patternEntries;
    private long credentialEpoch;

    public ECOCraftingFastPathCache() {
        this(NEConfig.ecoFastPathCacheSize);
    }

    public ECOCraftingFastPathCache(int limit) {
        this.limit = Math.clamp(limit, MIN_CACHE_SIZE, MAX_CACHE_SIZE);
        int initialCapacity = Math.min(this.limit, 1_024);
        this.entries = new LinkedHashMap<>(initialCapacity, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<ECOFastPathKey, ECOFastPathResult> eldest) {
                return size() > ECOCraftingFastPathCache.this.limit;
            }
        };
        this.patternEntries = new LinkedHashMap<>(initialCapacity, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<ECOFastPathPatternKey, ECOPatternEligibility> eldest) {
                return size() > ECOCraftingFastPathCache.this.limit;
            }
        };
        synchronized (ACTIVE_CACHES) {
            ACTIVE_CACHES.add(this);
        }
    }

    @Nullable
    public ECOFastPathResult get(ECOFastPathKey key, long tick) {
        ECOFastPathResult result = entries.get(key);
        if (result == null) {
            return null;
        }
        if (result.isNegative() && isNegativeExpired(result, tick)) {
            entries.remove(key);
            return null;
        }
        return result;
    }

    public void putPositive(
        ECOFastPathKey key,
        List<GenericStack> outputs,
        List<GenericStack> remaining,
        List<GenericStack> inputs,
        long tick
    ) {
        putPositive(key, outputs, remaining, inputs, tick, null);
    }

    public void putPositive(
        ECOFastPathKey key,
        List<GenericStack> outputs,
        List<GenericStack> remaining,
        List<GenericStack> inputs,
        long tick,
        @Nullable ECOReusableStateModel reusableStateModel
    ) {
        ECOFastPathStacks.ItemStackValidation resultValidation = reusableStateModel == null
            ? ECOFastPathStacks.ItemStackValidation.FAST_PATH
            : ECOFastPathStacks.ItemStackValidation.FAST_PATH_MUTATION;
        if (!ECOFastPathStacks.areValidItemStacks(
                outputs, Integer.MAX_VALUE, true, resultValidation)
            || !ECOFastPathStacks.areValidItemStacks(
                remaining, Integer.MAX_VALUE, false, resultValidation)
            || !ECOFastPathStacks.areValidItemStacks(
                inputs, Integer.MAX_VALUE, false, ECOFastPathStacks.ItemStackValidation.FAST_PATH_INPUT)) {
            putNegative(key, tick, "VERIFIED_STACK_VALIDATION_FAILED");
            return;
        }
        entries.put(key, ECOFastPathResult.positive(outputs, remaining, inputs, tick, reusableStateModel));
    }

    public void putNegative(ECOFastPathKey key, long tick) {
        putNegative(key, tick, "VERIFICATION_REJECTED");
    }

    public void putNegative(ECOFastPathKey key, long tick, String reason) {
        entries.put(key, ECOFastPathResult.negative(tick, reason));
    }

    /**
     * The single place in the whole dispatch chain that performs the full value comparison between a cached
     * result and the current execution context. A match mints an {@link ECOVerifiedFastPathRecipe}, which every
     * later stage passes around instead of comparing the three {@code List<GenericStack>} again.
     */
    public ECOFastPathLookup lookup(ECOExtractedPatternExecution execution, long tick, long reloadGeneration) {
        ECOFastPathKey key = execution.key();
        if (key == null) {
            return ECOFastPathLookup.miss();
        }
        // Do not mint a current credential from an execution context constructed before the latest reload,
        // even if an obsolete entry somehow survived memory clearing.
        if (!key.isForReloadGeneration(reloadGeneration)) {
            entries.remove(key);
            return ECOFastPathLookup.mismatch();
        }
        ECOPatternEligibility eligibility = patternEntries.computeIfAbsent(
            key.patternKey(), ignored -> execution.patternEligibility());
        if (!eligibility.supported()) {
            return ECOFastPathLookup.negative(eligibility.rejectReason());
        }
        ECOFastPathResult result = get(key, tick);
        if (result == null) {
            ECOFastPathLookup rebased = lookupDurabilityState(execution, key, tick);
            if (rebased != null) return rebased;
            return ECOFastPathLookup.miss();
        }
        if (result.isNegative()) {
            return ECOFastPathLookup.negative(result.rejectReason());
        }
        if (!result.matchesExecution(execution)) {
            // A positive entry is only a memoized proof for the exact observed execution. If that proof no
            // longer matches, discard it so the caller can run the assembler again and replace it with fresh
            // evidence. Turning this into a negative entry would suppress re-verification for the full TTL.
            entries.remove(key);
            return ECOFastPathLookup.mismatch();
        }
        return ECOFastPathLookup.verified(
            ECOVerifiedFastPathRecipe.trusted(this, execution, key, result, reloadGeneration)
        );
    }

    /**
     * A durability tool deliberately changes its concrete AEKey after every craft. Reuse only a positive
     * durability proof from the same pattern/dimension/reload scope, and only after rebasing every transition
     * plus the exact ordinary input/remainder counters. Any ambiguity simply remains a cold miss.
     */
    @Nullable
    private ECOFastPathLookup lookupDurabilityState(
        ECOExtractedPatternExecution execution,
        ECOFastPathKey currentKey,
        long tick
    ) {
        ECOFastPathResult rebasedResult = null;
        for (Map.Entry<ECOFastPathKey, ECOFastPathResult> entry : entries.entrySet()) {
            ECOFastPathKey cachedKey = entry.getKey();
            ECOFastPathResult cached = entry.getValue();
            if (!cachedKey.hasSamePatternScope(currentKey)
                    || !cachedKey.hasSameSlotShapeIgnoringDamage(currentKey)
                    || cached.isNegative()
                    || cached.durabilityModel() == null) {
                continue;
            }
            ECODurabilityBatchModel model = cached.durabilityModel();
            if (!cached.outputEntries().equals(execution.expectedOutputs())) continue;
            Optional<ECODurabilityBatchModel> rebased;
            try {
                rebased = model.rebase(
                    cached.inputEntries(), cached.remainingEntries(),
                    execution.inputItems(), execution.expectedContainerItems());
            } catch (RuntimeException rejected) {
                continue;
            }
            if (rebased.isEmpty()) continue;

            rebasedResult = ECOFastPathResult.positive(
                execution.expectedOutputs(),
                execution.expectedContainerItems(),
                execution.inputItems(),
                tick,
                rebased.get()
            );
            break;
        }
        if (rebasedResult == null) return null;
        entries.put(currentKey, rebasedResult);
        return ECOFastPathLookup.verified(
            ECOVerifiedFastPathRecipe.trusted(this, execution, currentKey, rebasedResult,
                currentKey.reloadGeneration())
        );
    }

    public void clear() {
        entries.clear();
        patternEntries.clear();
        credentialEpoch++;
    }

    long currentCredentialEpoch() {
        return credentialEpoch;
    }

    boolean isCredentialEpochCurrent(long candidate) {
        return credentialEpoch == candidate;
    }

    public static void clearAllCaches() {
        // Reload generations prevent stale entries from matching; clearing also frees their capacity immediately.
        synchronized (ACTIVE_CACHES) {
            for (ECOCraftingFastPathCache cache : ACTIVE_CACHES) {
                cache.clear();
            }
        }
    }

    private static boolean isNegativeExpired(ECOFastPathResult result, long tick) {
        long age = tick - result.getCreatedTick();
        return age < 0L || age >= NEGATIVE_CACHE_TTL_TICKS;
    }
}
