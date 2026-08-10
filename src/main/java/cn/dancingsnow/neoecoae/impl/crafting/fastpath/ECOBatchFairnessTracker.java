package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Experimental per-job turn state for virtual F-series exchange tasks. */
public final class ECOBatchFairnessTracker {
    private final Set<UUID> inFlightBatchJobs = new HashSet<>();

    public boolean shouldDefer(@Nullable UUID craftingJobId, long currentTick) {
        if (!isActive(craftingJobId)) {
            clearWhenDisabled();
            return false;
        }
        return inFlightBatchJobs.contains(craftingJobId);
    }

    public void noteWaiting(@Nullable UUID craftingJobId, long currentTick) {
        if (!isActive(craftingJobId)) {
            clearWhenDisabled();
            return;
        }
        // Intentionally empty. Waiting is discovered naturally when the task retries.
    }

    public void noteAccepted(@Nullable UUID craftingJobId, long currentTick) {
        if (!isActive(craftingJobId)) {
            clearWhenDisabled();
            return;
        }
        inFlightBatchJobs.add(craftingJobId);
    }

    public void noteCompleted(@Nullable UUID craftingJobId) {
        if (!isActive(craftingJobId)) {
            clearWhenDisabled();
            return;
        }
        inFlightBatchJobs.remove(craftingJobId);
    }

    private static boolean isActive(@Nullable UUID craftingJobId) {
        return NEConfig.debugEcoBatchFairScheduling && craftingJobId != null;
    }

    private void clearWhenDisabled() {
        if (!NEConfig.debugEcoBatchFairScheduling) {
            inFlightBatchJobs.clear();
        }
    }
}
