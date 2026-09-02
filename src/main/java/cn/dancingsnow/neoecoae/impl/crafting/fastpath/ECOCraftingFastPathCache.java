package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;
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
    private final Map<ECOFastPathPatternKey, ECOPatternEligibility> patternEntries;
    private final Map<String, Long> ineligibleReasonCounts = new LinkedHashMap<>();
    private final Map<String, String> ineligibleReasonExamples = new LinkedHashMap<>();

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
        this.patternEntries = new LinkedHashMap<>(initialCapacity, 0.75f, true) {
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
        @Nullable ECOReusableStateModel reusableStateModel
    ) {
        putPositive(key, outputs, remaining, inputs, tick, reusableStateModel,
            ECORecipeClassifier.Type.NORMAL, List.of(), List.of(), List.of(), List.of());
    }

    public void putPositive(
        ECOFastPathKey key,
        List<GenericStack> outputs,
        List<GenericStack> remaining,
        List<GenericStack> inputs,
        long tick,
        @Nullable ECOReusableStateModel reusableStateModel,
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
            putNegative(key, tick, "VERIFIED_STACK_VALIDATION_FAILED");
            return;
        }
        if (!reusableInputs.isEmpty() && reusableStateModel == null) {
            putNegative(key, tick, "REUSABLE_STATE_MODEL_MISSING");
            return;
        }
        ECOPatternEligibility eligibility = patternEntries.get(key.patternKey());
        EnumSet<FastPathCapability> capabilities = EnumSet.of(
            eligibility != null && eligibility.hasSubstitutionInput()
                ? FastPathCapability.TAG_RESOLVED_LINEAR
                : FastPathCapability.PURE_LINEAR);
        if (reusableStateModel != null) capabilities.add(reusableStateModel.capability());
        entries.put(key, ECOFastPathResult.positive(outputs, remaining, inputs, tick, reusableStateModel,
            capabilities, type,
            inputComponentChanges, outputComponentChanges, durabilityDeltas, reusableInputs));
        verifySuccessCount++;
    }

    public void putNegative(ECOFastPathKey key, long tick) {
        putNegative(key, tick, "VERIFICATION_REJECTED");
    }

    public void putNegative(ECOFastPathKey key, long tick, String reason) {
        entries.put(key, ECOFastPathResult.negative(tick, reason));
        verifyRejectCount++;
        recordRejectReason(reason, key.toString());
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
        ECOPatternEligibility eligibility = patternEntries.computeIfAbsent(
            key.patternKey(), ignored -> execution.patternEligibility());
        if (!eligibility.supported()) {
            recordRejectReason(eligibility.rejectReason(), execution.expectedOutputs().toString());
            return ECOFastPathLookup.negative();
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

    public void recordDisabled() {
        disabledCount++;
    }

    public void recordDisabled(String reason) {
        recordDisabled();
        recordRejectReason(reason, null);
    }

    public void recordDisabled(ECOExtractedPatternExecution execution) {
        String reason = execution == null ? "UNKNOWN" : execution.fastPathReason();
        recordDisabled(reason);
        if (execution != null) {
            ineligibleReasonExamples.putIfAbsent(reason, execution.expectedOutputs().toString());
        }
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

    @Nullable
    ECOPatternEligibility getPatternEligibility(ECOFastPathPatternKey key) {
        return patternEntries.get(key);
    }

    /** Package-private test seam for registry-independent AEKey fixtures. */
    void putResolvedForTesting(ECOFastPathKey key, ECOFastPathResult result) {
        entries.put(key, result);
    }

    private void recordRejectReason(@Nullable String reason, @Nullable String example) {
        String normalized = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
        ineligibleReasonCounts.merge(normalized, 1L, Long::sum);
        if (example != null) ineligibleReasonExamples.putIfAbsent(normalized, example);
    }

    private static boolean isNegativeExpired(ECOFastPathResult result, long tick) {
        long age = tick - result.getCreatedTick();
        return age < 0L || age >= NEGATIVE_CACHE_TTL_TICKS;
    }
}
