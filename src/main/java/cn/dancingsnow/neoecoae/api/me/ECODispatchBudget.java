package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.config.NEConfig;

/** Token bucket charged exclusively to AE2's generic input resolver. */
final class ECODispatchBudget {
    private static long globalTick = Long.MIN_VALUE;
    private static long globalCreditsNanos;

    private long localTick = Long.MIN_VALUE;
    private long localCreditsNanos;
    private int genericResolutionsRemaining;
    private long operationStartedNanos;
    private boolean operationActive;

    void beginTick(long tick) {
        if (localTick != tick) {
            localCreditsNanos = replenish(localCreditsNanos, localTick, tick,
                NEConfig.ecoGenericCpuNanosPerTick, burst(NEConfig.ecoGenericCpuNanosPerTick));
            localTick = tick;
            genericResolutionsRemaining = NEConfig.ecoGenericResolutionsPerTick;
        }
        replenishGlobal(tick);
    }

    boolean tryBeginGeneric(long tick) {
        beginTick(tick);
        if (operationActive || genericResolutionsRemaining <= 0 || localCreditsNanos <= 0L) return false;
        synchronized (ECODispatchBudget.class) {
            replenishGlobal(tick);
            if (globalCreditsNanos <= 0L) return false;
        }
        genericResolutionsRemaining--;
        operationStartedNanos = System.nanoTime();
        operationActive = true;
        return true;
    }

    void finishGeneric(long tick) {
        if (!operationActive) return;
        long elapsed = Math.max(0L, System.nanoTime() - operationStartedNanos);
        operationActive = false;
        localCreditsNanos = Math.max(0L, localCreditsNanos - elapsed);
        synchronized (ECODispatchBudget.class) {
            replenishGlobal(tick);
            globalCreditsNanos = Math.max(0L, globalCreditsNanos - elapsed);
        }
    }

    boolean genericExhausted(long tick) {
        beginTick(tick);
        if (genericResolutionsRemaining <= 0 || localCreditsNanos <= 0L) return true;
        synchronized (ECODispatchBudget.class) {
            replenishGlobal(tick);
            return globalCreditsNanos <= 0L;
        }
    }

    private static synchronized void replenishGlobal(long tick) {
        if (globalTick == tick) return;
        globalCreditsNanos = replenish(globalCreditsNanos, globalTick, tick,
            NEConfig.ecoGenericServerNanosPerTick, burst(NEConfig.ecoGenericServerNanosPerTick));
        globalTick = tick;
    }

    private static long replenish(long credits, long previousTick, long tick, long sustained, long capacity) {
        if (previousTick == Long.MIN_VALUE || tick < previousTick) return capacity;
        long elapsedTicks = Math.max(0L, tick - previousTick);
        long refill = saturatingMultiply(Math.max(0L, sustained), elapsedTicks);
        return Math.min(capacity, saturatingAdd(Math.max(0L, credits), refill));
    }

    private static long burst(long sustained) {
        return saturatingMultiply(Math.max(0L, sustained), 3L);
    }

    private static long saturatingMultiply(long value, long multiplier) {
        if (value == 0L || multiplier == 0L) return 0L;
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
