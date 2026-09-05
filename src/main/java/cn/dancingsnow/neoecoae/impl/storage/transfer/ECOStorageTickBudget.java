package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.util.Map;
import java.util.WeakHashMap;

/** Main-thread cooperative server budget. Deferred owners get the next available turn. */
public final class ECOStorageTickBudget {
    private static final Map<Object, Budget> SERVERS = new WeakHashMap<>();
    private static final class Budget {
        long tick = Long.MIN_VALUE;
        long spent;
        final Map<Object, Long> waiting = new java.util.LinkedHashMap<>();
    }

    public static long allowance(Object server, Object owner, long tick, long requested) {
        long serverNanos = cn.dancingsnow.neoecoae.config.NEConfig.storageServerNanosPerTick;
        Budget budget = SERVERS.computeIfAbsent(server, ignored -> new Budget());
        if (budget.tick != tick) {
            budget.tick = tick;
            budget.spent = 0;
            budget.waiting.values().removeIf(lastSeen -> tick - lastSeen > 2L);
        }
        budget.waiting.put(owner, tick);
        Object first = budget.waiting.keySet().iterator().next();
        if (first != owner || budget.spent >= serverNanos) return 0L;
        budget.waiting.remove(owner);
        return Math.min(requested, serverNanos - budget.spent);
    }

    public static void spent(Object server, long nanos) {
        Budget budget = SERVERS.get(server);
        if (budget != null) budget.spent = Math.min(Long.MAX_VALUE - nanos, budget.spent) + nanos;
    }

    public static void clear(Object server) { SERVERS.remove(server); }
}
