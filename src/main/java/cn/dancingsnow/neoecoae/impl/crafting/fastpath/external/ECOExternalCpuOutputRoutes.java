package cn.dancingsnow.neoecoae.impl.crafting.fastpath.external;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Exact job ownership routes for outputs produced on behalf of AE2-compatible external CPUs. */
public final class ECOExternalCpuOutputRoutes {
    private static final ConcurrentMap<UUID, WeakReference<Sink>> ROUTES = new ConcurrentHashMap<>();

    private ECOExternalCpuOutputRoutes() {}

    public static void register(UUID craftingJobId, Sink sink) {
        if (craftingJobId != null && sink != null) {
            ROUTES.put(craftingJobId, new WeakReference<>(sink));
        }
    }

    /** Removes a route only when it still belongs to the specified CPU. */
    public static void unregister(UUID craftingJobId, Sink sink) {
        if (craftingJobId == null || sink == null) {
            return;
        }
        ROUTES.computeIfPresent(craftingJobId, (ignored, reference) -> {
            Sink registered = reference.get();
            return registered == null || registered == sink ? null : reference;
        });
    }

    public static Delivery deliver(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        WeakReference<Sink> reference = ROUTES.get(craftingJobId);
        Sink sink = reference == null ? null : reference.get();
        if (sink == null || !sink.neoecoae$ownsJob(craftingJobId)) {
            if (reference != null) {
                ROUTES.remove(craftingJobId, reference);
            }
            return Delivery.UNAVAILABLE;
        }
        return new Delivery(true, sink.neoecoae$insertJobOutput(what, amount, type));
    }

    public interface Sink {
        boolean neoecoae$ownsJob(UUID craftingJobId);

        long neoecoae$insertJobOutput(AEKey what, long amount, Actionable type);
    }

    public record Delivery(boolean routeAvailable, long inserted) {
        private static final Delivery UNAVAILABLE = new Delivery(false, 0L);
    }
}
