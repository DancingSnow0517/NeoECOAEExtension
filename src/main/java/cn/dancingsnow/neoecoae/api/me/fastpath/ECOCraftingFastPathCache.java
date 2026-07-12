package cn.dancingsnow.neoecoae.api.me.fastpath;

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
    private long containerMismatchCount;
    private long nonItemKeyCount;
    private long postCraftingEventCount;
    private long keyBuildFailedCount;
    private long exceptionCount;
    private long lastStatsLogTick = Long.MIN_VALUE;

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
        result.touch(tick);
        if (result.isNegative()) {
            negativeHitCount++;
        } else {
            hitCount++;
        }
        return result;
    }

    @Nullable
    public ECOFastPathResult peek(ECOFastPathKey key) {
        return entries.get(key);
    }

    public void putPositive(
        ECOFastPathKey key,
        List<GenericStack> outputs,
        List<GenericStack> remaining,
        List<GenericStack> inputs,
        long tick
    ) {
        if (!ECOBatchCraftingHelper.areValidItemStacks(outputs, Integer.MAX_VALUE, true)
            || !ECOBatchCraftingHelper.areValidItemStacks(remaining, Integer.MAX_VALUE, false)
            || !ECOBatchCraftingHelper.areValidItemStacks(inputs, Integer.MAX_VALUE, false)
            || !ECOFastPathStacks.isSafeForFastPath(outputs, false)
            || !ECOFastPathStacks.isSafeForFastPath(remaining, false)
            || !ECOFastPathStacks.isSafeForFastPath(inputs, true)) {
            putNegative(key, tick);
            return;
        }
        entries.put(key, ECOFastPathResult.positive(outputs, remaining, inputs, tick));
        verifySuccessCount++;
    }

    public void putNegative(ECOFastPathKey key, long tick) {
        entries.put(key, ECOFastPathResult.negative(tick));
        verifyRejectCount++;
    }

    public void clear() {
        entries.clear();
    }

    public static void clearAllCaches() {
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

    public void recordContainerMismatch() {
        containerMismatchCount++;
    }

    public void recordNonItemKey() {
        nonItemKeyCount++;
    }

    public void recordPostCraftingEvent() {
        postCraftingEventCount++;
    }

    public void recordKeyBuildFailed() {
        keyBuildFailedCount++;
    }

    public void recordException() {
        exceptionCount++;
    }

    public boolean matchesExecution(ECOFastPathKey key, ECOExtractedPatternExecution execution) {
        ECOFastPathResult result = peek(key);
        return result != null && result.matchesExecution(execution);
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
            "ECO fast path [{}]: size={}/{} hit={} miss={} hitRate={}% negativeHit={} verified={} rejected={} fallback[disabled={} unverified={} expectedMismatch={} containerMismatch={} nonItemKey={} postCraftingEvent={} keyBuildFailed={} exception={}] fastAccepted={} slowAccepted={} coolantReject={} noThreadReject={}",
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
            containerMismatchCount,
            nonItemKeyCount,
            postCraftingEventCount,
            keyBuildFailedCount,
            exceptionCount,
            fastPathAcceptedCount,
            slowPathAcceptedCount,
            coolantRejectCount,
            noThreadRejectCount
        );
    }

    int size() {
        return entries.size();
    }

    int limit() {
        return limit;
    }

    private static boolean isNegativeExpired(ECOFastPathResult result, long tick) {
        long age = tick - result.getCreatedTick();
        return age < 0L || age >= NEGATIVE_CACHE_TTL_TICKS;
    }

    static boolean isStatsLogDue(long previousTick, long tick) {
        if (previousTick == Long.MIN_VALUE) {
            return true;
        }
        long elapsed = tick - previousTick;
        return elapsed < 0L || elapsed >= 100L;
    }
}
