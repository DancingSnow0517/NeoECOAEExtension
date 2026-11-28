package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.PinnedKeys;
import appeng.core.AEConfig;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** Stores crafting pins and pending crafting job state isolated by runtime ME network id. */
@OnlyIn(Dist.CLIENT)
public final class ECONetworkPinnedJobs {
    private static final Comparator<Map.Entry<AEKey, PinnedKeys.PinInfo>> TIME_COMPARATOR =
        Comparator.comparing(entry -> entry.getValue().since);

    private static final Map<UUID, Long> JOB_NETWORKS = new HashMap<>();
    private static final Map<Long, Map<AEKey, PinnedKeys.PinInfo>> PINNED_BY_NETWORK = new HashMap<>();
    private static final Map<UUID, PendingJob> PENDING_JOBS = new HashMap<>();
    private static long revision;

    private ECONetworkPinnedJobs() {}

    public static void bindJobNetwork(UUID jobId, long networkId) {
        if (jobId != null && networkId > 0L) {
            JOB_NETWORKS.put(jobId, networkId);
        }
    }

    public static boolean hasBoundNetwork(UUID jobId) {
        Long networkId = JOB_NETWORKS.get(jobId);
        return networkId != null && networkId > 0L;
    }

    public static void handleJobStatus(UUID jobId, AEKey what, CraftingJobStatusPacket.Status status) {
        Long networkId = JOB_NETWORKS.get(jobId);
        if (networkId == null || networkId <= 0L) {
            return;
        }
        switch (status) {
            case STARTED -> {
                PENDING_JOBS.put(jobId, new PendingJob(jobId, networkId, what));
                if (AEConfig.instance().isPinAutoCraftedItems()) {
                    pin(networkId, what);
                }
                revision++;
            }
            case CANCELLED, FINISHED -> {
                PENDING_JOBS.remove(jobId);
                JOB_NETWORKS.remove(jobId);
                revision++;
            }
        }
    }

    private static void pin(long networkId, AEKey key) {
        var pins = PINNED_BY_NETWORK.computeIfAbsent(networkId, ignored -> new HashMap<>(PinnedKeys.MAX_PINNED));
        var existing = pins.get(key);
        if (existing != null) {
            existing.since = java.time.Instant.now();
            existing.canPrune = false;
        } else {
            pins.put(key, new PinnedKeys.PinInfo(PinnedKeys.PinReason.CRAFTING));
        }

        if (pins.size() > PinnedKeys.MAX_PINNED) {
            var toRemove = new ArrayList<>(pins.entrySet());
            toRemove.sort(TIME_COMPARATOR);
            for (var entry : toRemove.subList(0, pins.size() - PinnedKeys.MAX_PINNED)) {
                pins.remove(entry.getKey());
            }
        }
    }

    public static boolean hasPins(long networkId) {
        if (networkId <= 0L) {
            return false;
        }
        var pins = PINNED_BY_NETWORK.get(networkId);
        return pins != null && !pins.isEmpty();
    }

    public static boolean isPinned(long networkId, AEKey key) {
        return getPinInfo(networkId, key) != null;
    }

    public static @Nullable PinnedKeys.PinInfo getPinInfo(long networkId, AEKey key) {
        if (networkId <= 0L) {
            return null;
        }
        var pins = PINNED_BY_NETWORK.get(networkId);
        return pins == null ? null : pins.get(key);
    }

    public static boolean hasPendingJob(long networkId, AEKey key) {
        if (networkId <= 0L) {
            return false;
        }
        for (var job : PENDING_JOBS.values()) {
            if (job.networkId == networkId && job.what.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean tracksPendingJob(AEKey key) {
        for (var job : PENDING_JOBS.values()) {
            if (job.what.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static void markPrunable(long networkId, AEKey key) {
        var info = getPinInfo(networkId, key);
        if (info != null && !hasPendingJob(networkId, key)) {
            info.canPrune = true;
        }
    }

    public static void prune() {
        var iterator = PINNED_BY_NETWORK.values().iterator();
        boolean changed = false;
        while (iterator.hasNext()) {
            var pins = iterator.next();
            if (pins.values().removeIf(info -> info.canPrune)) {
                changed = true;
            }
            if (pins.isEmpty()) {
                iterator.remove();
            }
        }
        if (changed) {
            revision++;
        }
    }

    public static void clear() {
        JOB_NETWORKS.clear();
        PINNED_BY_NETWORK.clear();
        PENDING_JOBS.clear();
        revision++;
    }

    public static long revision() {
        return revision;
    }

    private record PendingJob(UUID jobId, long networkId, AEKey what) {}
}
