package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final long NEGATIVE_CACHE_TTL_TICKS = 1_200L;
    private static final Set<ECOCraftingFastPathCache> ACTIVE_CACHES = Collections.newSetFromMap(new WeakHashMap<>());

    private final int limit;
    private final Map<ECOFastPathKey, ECOFastPathResult> entries;

    private long hitCount;
    private long missCount;
    private long verifySuccessCount;
    private long verifyRejectCount;
    private long negativeHitCount;
    private long disabledCount;
    private long fallbackSlowPathCount;
    private long fastPathAcceptedCount;
    private long slowPathAcceptedCount;
    private long coolantRejectCount;
    private long noThreadRejectCount;
    private long expectedMismatchCount;
    private long nonItemKeyCount;
    private long keyBuildFailedCount;
    private long exceptionCount;
    private long lastStatsLogTick = Long.MIN_VALUE;
    private long credentialEpoch;

    public ECOCraftingFastPathCache() {
        this(NEConfig.ecoFastPathCacheSize);
    }

    public ECOCraftingFastPathCache(int limit) {
        this.limit = Math.clamp(limit, MIN_CACHE_SIZE, MAX_CACHE_SIZE);
        int initialCapacity = Math.min(this.limit, 1_024);
        this.entries = new LinkedHashMap<>(initialCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ECOFastPathKey, ECOFastPathResult> eldest) {
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
            missCount++;
            return null;
        }
        if (result.isNegative() && isNegativeExpired(result, tick)) {
            entries.remove(key);
            missCount++;
            return null;
        }
        if (result.isNegative()) {
            negativeHitCount++;
        } else {
            hitCount++;
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
        @Nullable ECODurabilityBatchModel durabilityModel
    ) {
        putPositive(key, outputs, remaining, inputs, tick, durabilityModel,
            ECORecipeClassifier.Type.NORMAL, List.of(), List.of(), List.of(), List.of());
    }

    public void putPositive(
        ECOFastPathKey key,
        List<GenericStack> outputs,
        List<GenericStack> remaining,
        List<GenericStack> inputs,
        long tick,
        @Nullable ECODurabilityBatchModel durabilityModel,
        ECORecipeClassifier.Type type,
        List<ECOFastPathComponentChange> inputComponentChanges,
        List<ECOFastPathComponentChange> outputComponentChanges,
        List<ECOFastPathDurabilityDelta> durabilityDeltas,
        List<GenericStack> reusableInputs
    ) {
        ECOFastPathStacks.ItemStackValidation resultValidation =
            inputComponentChanges.isEmpty() && outputComponentChanges.isEmpty()
                    && durabilityDeltas.isEmpty() && reusableInputs.isEmpty()
                ? ECOFastPathStacks.ItemStackValidation.FAST_PATH
                : ECOFastPathStacks.ItemStackValidation.FAST_PATH_MUTATION;
        if (!ECOFastPathStacks.areValidItemStacks(
                outputs, Integer.MAX_VALUE, true, resultValidation)
            || !ECOFastPathStacks.areValidItemStacks(
                remaining, Integer.MAX_VALUE, false, resultValidation)
            || !ECOFastPathStacks.areValidItemStacks(
                inputs, Integer.MAX_VALUE, false, ECOFastPathStacks.ItemStackValidation.FAST_PATH_INPUT)) {
            putNegative(key, tick);
            return;
        }
        entries.put(key, ECOFastPathResult.positive(outputs, remaining, inputs, tick, durabilityModel, type,
            inputComponentChanges, outputComponentChanges, durabilityDeltas, reusableInputs));
        verifySuccessCount++;
    }

    public void putNegative(ECOFastPathKey key, long tick) {
        entries.put(key, ECOFastPathResult.negative(tick));
        verifyRejectCount++;
    }

    /**
     * The single place in the whole dispatch chain that performs the full value comparison between a cached
     * result and the current execution context. A match mints an {@link ECOVerifiedFastPathRecipe}, which every
     * later stage passes around instead of comparing the three {@code List<GenericStack>} again.
     */
    public ECOFastPathLookup lookup(ECOExtractedPatternExecution execution, long tick, long reloadGeneration) {
        ECOFastPathKey key = execution.key();
        if (key == null) {
            keyBuildFailedCount++;
            return ECOFastPathLookup.miss();
        }
        // Do not mint a current credential from an execution context constructed before the latest reload,
        // even if an obsolete entry somehow survived memory clearing.
        if (!key.isForReloadGeneration(reloadGeneration)) {
            expectedMismatchCount++;
            return ECOFastPathLookup.mismatch();
        }
        ECOFastPathResult result = get(key, tick);
        if (result == null) {
            return ECOFastPathLookup.miss();
        }
        if (result.isNegative()) {
            return ECOFastPathLookup.negative();
        }
        if (!result.matchesExecution(execution)) {
            expectedMismatchCount++;
            return ECOFastPathLookup.mismatch();
        }
        return ECOFastPathLookup.verified(
            ECOVerifiedFastPathRecipe.trusted(this, execution, key, result, reloadGeneration)
        );
    }

    public void clear() {
        entries.clear();
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

    public void recordDisabled() {
        disabledCount++;
    }

    public void recordFallbackSlowPath() {
        fallbackSlowPathCount++;
    }

    public void recordFastPathAccepted() {
        fastPathAcceptedCount++;
    }

    public void recordSlowPathAccepted() {
        slowPathAcceptedCount++;
    }

    public void recordCoolantReject() {
        coolantRejectCount++;
    }

    public void recordNoThreadReject() {
        noThreadRejectCount++;
    }

    public void recordExpectedMismatch() {
        expectedMismatchCount++;
    }

    public void recordNonItemKey() {
        nonItemKeyCount++;
    }

    public void recordKeyBuildFailed() {
        keyBuildFailedCount++;
    }

    public void recordException() {
        exceptionCount++;
    }

    public void maybeLogStats(String owner, long tick) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        if (!isStatsLogDue(lastStatsLogTick, tick)) {
            return;
        }
        lastStatsLogTick = tick;
        long positiveLookups = hitCount + missCount + negativeHitCount;
        double hitRate = positiveLookups <= 0 ? 0.0D : (hitCount * 100.0D / positiveLookups);
        LOGGER.debug(
            "ECO fast path [{}]: size={}/{} hit={} miss={} hitRate={}% negativeHit={} verified={} rejected={} fallback[disabled={} unverified={} expectedMismatch={} nonItemKey={} keyBuildFailed={} exception={}] fastAccepted={} slowAccepted={} coolantReject={} noThreadReject={}",
            owner,
            entries.size(),
            limit,
            hitCount,
            missCount,
            String.format(java.util.Locale.ROOT, "%.1f", hitRate),
            negativeHitCount,
            verifySuccessCount,
            verifyRejectCount,
            disabledCount,
            fallbackSlowPathCount,
            expectedMismatchCount,
            nonItemKeyCount,
            keyBuildFailedCount,
            exceptionCount,
            fastPathAcceptedCount,
            slowPathAcceptedCount,
            coolantRejectCount,
            noThreadRejectCount
        );
    }

    private static boolean isNegativeExpired(ECOFastPathResult result, long tick) {
        long age = tick - result.getCreatedTick();
        return age < 0L || age >= NEGATIVE_CACHE_TTL_TICKS;
    }

    private boolean isStatsLogDue(long previousTick, long tick) {
        if (previousTick == Long.MIN_VALUE) {
            return true;
        }
        long elapsed = tick - previousTick;
        return elapsed < 0L || elapsed >= 100L;
    }
}
