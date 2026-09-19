package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import java.util.Map;
import java.util.WeakHashMap;

/** Share one sampled aggregation between all viewers of the same CPU. Server thread only. */
public record ExactCpuSnapshot(Map<AEKey, ExactAmount> stored, Map<AEKey, ExactAmount> active,
        Map<AEKey, ExactAmount> pending) {
    public static final ExactCpuSnapshot EMPTY = new ExactCpuSnapshot(Map.of(), Map.of(), Map.of());
    private static final Map<ECOCraftingCPULogic, Cached> CACHE = new WeakHashMap<>();

    public static ExactCpuSnapshot sample(ECOCraftingCPULogic logic, long tick) {
        Cached cached = CACHE.get(logic);
        if (cached != null && cached.job.get() == logic.getJob() && tick >= cached.tick
                && tick - cached.tick < MenuDataTransport.UPDATE_INTERVAL) return cached.snapshot;
        var snapshot = new ExactCpuSnapshot(ExactMapSync.finite(logic.getExactStoredPreview()),
            ExactMapSync.finite(logic.getExactActivePreview()), ExactMapSync.finite(logic.getExactPendingPreview()));
        CACHE.put(logic, new Cached(tick, new java.lang.ref.WeakReference<>(logic.getJob()), snapshot));
        return snapshot;
    }

    public static void clear() { CACHE.clear(); }
    private record Cached(long tick, java.lang.ref.WeakReference<Object> job, ExactCpuSnapshot snapshot) {}
}
