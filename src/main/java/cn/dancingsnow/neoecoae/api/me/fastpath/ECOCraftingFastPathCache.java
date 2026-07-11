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
        this.limit = Math.max(16, limit);
        this.entries = new LinkedHashMap<>(this.limit, 0.75f, true) {
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
        if (result.isNegative() && tick - result.getLastAccessTick() >= NEGATIVE_CACHE_TTL_TICKS) {
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
        if (!NEConfig.debugEcoFastPath || tick - lastStatsLogTick < 100) {
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
}
