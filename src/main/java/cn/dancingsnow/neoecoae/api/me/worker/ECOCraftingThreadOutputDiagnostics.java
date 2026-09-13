package cn.dancingsnow.neoecoae.api.me.worker;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.NEMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Aggregates blocked output observations across worker threads without owning recovery behavior. */
final class ECOCraftingThreadOutputDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");
    private static final long GRACE_TICKS = 200L;
    private static final long LOG_INTERVAL_TICKS = 1_200L;
    private static final long ACTIVE_LANE_TICKS = 40L;
    private static final long STALE_JOB_TICKS = 2_400L;
    private static final int POSITION_LIMIT = 8;
    private static final Object LOCK = new Object();
    private static final Map<UUID, JobDiagnostic> JOBS = new HashMap<>();
    private static volatile boolean enabled;

    private final String laneId = Integer.toHexString(System.identityHashCode(this));
    private long unownedSinceTick = Long.MIN_VALUE;
    private long lastUnownedLogTick = Long.MIN_VALUE;

    void blocked(ECOCraftingWorkerBlockEntity worker, @Nullable UUID jobId, String reason,
            @Nullable KeyCounter pending, int progress, int maxProgress, int batchCrafts,
            long craftCount, boolean virtualBatch, long tick) {
        if (!NEConfig.ecoCraftingOutputDeliveryDebug) {
            disable();
            resetUnowned();
            return;
        }
        enabled = true;
        if (jobId != null) {
            blockedJob(worker, jobId, reason, pending, tick);
            return;
        }
        if (unownedSinceTick == Long.MIN_VALUE || tick < unownedSinceTick) {
            unownedSinceTick = tick;
            lastUnownedLogTick = Long.MIN_VALUE;
        }
        long blockedTicks = tick - unownedSinceTick;
        long sinceLastLog = tick - lastUnownedLogTick;
        if (blockedTicks < GRACE_TICKS || lastUnownedLogTick != Long.MIN_VALUE
                && sinceLastLog >= 0L && sinceLastLog < LOG_INTERVAL_TICKS) return;
        lastUnownedLogTick = tick;
        LOGGER.warn("ECO crafting output delivery blocked: worker={} reason={} job=null blockedTicks={} "
                        + "progress={}/{} pending={} batchCrafts={} craftCount={} virtualBatch={}",
                worker.getBlockPos(), reason, blockedTicks, progress, maxProgress,
                pending == null ? "unknown" : pending, batchCrafts, craftCount, virtualBatch);
    }

    void finished(ECOCraftingWorkerBlockEntity worker, @Nullable UUID jobId, long tick) {
        if (jobId == null) {
            resetUnowned();
            return;
        }
        Long blockedTicks = null;
        synchronized (LOCK) {
            JobDiagnostic diagnostic = JOBS.get(jobId);
            if (diagnostic == null) return;
            diagnostic.lanes.remove(position(worker) + "#" + laneId);
            if (diagnostic.lanes.isEmpty()) {
                JOBS.remove(jobId);
                if (NEConfig.ecoCraftingOutputDeliveryDebug && diagnostic.hasLogged) {
                    blockedTicks = Math.max(0L, tick - diagnostic.firstBlockedTick);
                }
            }
        }
        if (blockedTicks != null) {
            LOGGER.info("ECO crafting output delivery wait ended: job={} blockedTicks={}", jobId, blockedTicks);
        }
    }

    void reset() {
        resetUnowned();
    }

    private void blockedJob(ECOCraftingWorkerBlockEntity worker, UUID jobId, String reason,
            @Nullable KeyCounter pending, long tick) {
        Aggregated log = null;
        synchronized (LOCK) {
            prune(tick);
            JobDiagnostic diagnostic = JOBS.computeIfAbsent(jobId, ignored -> new JobDiagnostic(tick));
            diagnostic.lastSeenTick = tick;
            diagnostic.lanes.values().removeIf(lane -> stale(tick, lane.lastSeenTick(), STALE_JOB_TICKS));
            String position = position(worker);
            diagnostic.lanes.put(position + "#" + laneId, lane(position, reason, pending, tick));
            long blockedTicks = tick - diagnostic.firstBlockedTick;
            long sinceLastLog = tick - diagnostic.lastLogTick;
            if (blockedTicks >= GRACE_TICKS && (diagnostic.lastLogTick == Long.MIN_VALUE
                    || sinceLastLog < 0L || sinceLastLog >= LOG_INTERVAL_TICKS)) {
                log = aggregate(jobId, diagnostic, tick, blockedTicks);
                diagnostic.lastLogTick = tick;
                diagnostic.hasLogged = true;
            }
        }
        if (log != null) {
            LOGGER.warn("ECO crafting output delivery blocked: job={} blockedTicks={} reasons={} workers={} "
                            + "threads={} workerPositions={} pendingKeyEntries={} pendingAmount={} pendingUnknown={}",
                    log.jobId(), log.blockedTicks(), log.reasons(), log.workerCount(), log.threadCount(),
                    log.workerPositions(), log.pendingKeys(), log.pendingAmount(), log.pendingUnknown());
        }
    }

    private static Lane lane(String position, String reason, @Nullable KeyCounter pending, long tick) {
        if (pending == null) return new Lane(position, reason, tick, 0, 0L, true);
        int keys = 0;
        long amount = 0L;
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            if (entry.getLongValue() <= 0L) continue;
            keys = keys == Integer.MAX_VALUE ? keys : keys + 1;
            amount = NEMath.saturatingAdd(amount, entry.getLongValue());
        }
        return new Lane(position, reason, tick, keys, amount, false);
    }

    private static Aggregated aggregate(UUID jobId, JobDiagnostic diagnostic, long tick, long blockedTicks) {
        Set<String> reasons = new LinkedHashSet<>();
        Set<String> positions = new LinkedHashSet<>();
        int threads = 0;
        int keys = 0;
        long amount = 0L;
        boolean unknown = false;
        for (Lane lane : diagnostic.lanes.values()) {
            if (stale(tick, lane.lastSeenTick(), ACTIVE_LANE_TICKS)) continue;
            threads = threads == Integer.MAX_VALUE ? threads : threads + 1;
            reasons.add(lane.reason());
            positions.add(lane.workerPosition());
            keys = lane.pendingKeys() > 0 && keys > Integer.MAX_VALUE - lane.pendingKeys()
                    ? Integer.MAX_VALUE : keys + lane.pendingKeys();
            amount = NEMath.saturatingAdd(amount, lane.pendingAmount());
            unknown |= lane.pendingUnknown();
        }
        return new Aggregated(jobId, blockedTicks, List.copyOf(reasons), positions.size(), threads,
                summarize(positions), keys, amount, unknown);
    }

    private static List<String> summarize(Set<String> positions) {
        List<String> result = new ArrayList<>(Math.min(positions.size(), POSITION_LIMIT) + 1);
        int added = 0;
        for (String position : positions) {
            if (added++ >= POSITION_LIMIT) break;
            result.add(position);
        }
        if (positions.size() > result.size()) result.add("+" + (positions.size() - result.size()) + " more");
        return List.copyOf(result);
    }

    private static String position(ECOCraftingWorkerBlockEntity worker) {
        return worker.getLevel() == null ? worker.getBlockPos().toShortString()
                : worker.getLevel().dimension().location() + "@" + worker.getBlockPos().toShortString();
    }

    private static boolean stale(long now, long then, long limit) {
        long age = now - then;
        return age < 0L || age > limit;
    }

    private static void prune(long tick) {
        JOBS.entrySet().removeIf(entry -> stale(tick, entry.getValue().lastSeenTick, STALE_JOB_TICKS));
    }

    private static void disable() {
        if (!enabled) return;
        synchronized (LOCK) {
            if (!NEConfig.ecoCraftingOutputDeliveryDebug) {
                JOBS.clear();
                enabled = false;
            }
        }
    }

    private void resetUnowned() {
        unownedSinceTick = Long.MIN_VALUE;
        lastUnownedLogTick = Long.MIN_VALUE;
    }

    private static final class JobDiagnostic {
        private final long firstBlockedTick;
        private long lastSeenTick;
        private long lastLogTick = Long.MIN_VALUE;
        private boolean hasLogged;
        private final Map<String, Lane> lanes = new HashMap<>();

        private JobDiagnostic(long tick) {
            firstBlockedTick = tick;
            lastSeenTick = tick;
        }
    }

    private record Lane(String workerPosition, String reason, long lastSeenTick, int pendingKeys,
            long pendingAmount, boolean pendingUnknown) {
    }

    private record Aggregated(UUID jobId, long blockedTicks, List<String> reasons, int workerCount,
            int threadCount, List<String> workerPositions, int pendingKeys, long pendingAmount,
            boolean pendingUnknown) {
    }
}
