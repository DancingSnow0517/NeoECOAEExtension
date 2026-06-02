package cn.dancingsnow.neoecoae.api.me.fastpath;

import cn.dancingsnow.neoecoae.NeoECOAE;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ECOCraftingFastPathCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final boolean DEBUG_STATS = Boolean.getBoolean("neoecoae.debugEcoFastPath");
    private static final int DEFAULT_LIMIT = Integer.getInteger("neoecoae.ecoFastPathCacheSize", 512);
    private static final Set<ECOCraftingFastPathCache> LIVE_CACHES =
        Collections.newSetFromMap(new WeakHashMap<>());

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
        this(DEFAULT_LIMIT);
    }

    public ECOCraftingFastPathCache(int limit) {
        this.limit = Math.max(16, limit);
        this.entries = new LinkedHashMap<>(this.limit, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ECOFastPathKey, ECOFastPathResult> eldest) {
                return size() > ECOCraftingFastPathCache.this.limit;
            }
        };
        synchronized (LIVE_CACHES) {
            LIVE_CACHES.add(this);
        }
    }

    @Nullable
    public ECOFastPathResult get(ECOFastPathKey key, long tick) {
        ECOFastPathResult result = entries.get(key);
        if (result == null) {
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
        java.util.List<appeng.api.stacks.GenericStack> outputs,
        java.util.List<appeng.api.stacks.GenericStack> remaining,
        java.util.List<appeng.api.stacks.GenericStack> inputs,
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
        synchronized (LIVE_CACHES) {
            for (ECOCraftingFastPathCache cache : LIVE_CACHES) {
                cache.clear();
            }
        }
    }

    public int size() {
        return entries.size();
    }

    public int limit() {
        return limit;
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

    public void maybeLogStats(String owner, long tick) {
        if (!DEBUG_STATS || tick - lastStatsLogTick < 100) {
            return;
        }
        lastStatsLogTick = tick;
        long positiveLookups = hitCount + missCount + negativeHitCount;
        double hitRate = positiveLookups <= 0 ? 0.0D : (hitCount * 100.0D / positiveLookups);
        LOGGER.debug(
            "ECO fast path [{}]: size={}/{} hit={} miss={} hitRate={} negativeHit={} verified={} rejected={} fallbackReason[disabled={} unverified={} expectedMismatch={} containerMismatch={} nonItemKey={} postCraftingEvent={} keyBuildFailed={} exception={}] fastAccepted={} slowAccepted={} coolantReject={} noThreadReject={}",
            owner,
            size(),
            limit,
            hitCount,
            missCount,
            String.format(java.util.Locale.ROOT, "%.1f%%", hitRate),
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
