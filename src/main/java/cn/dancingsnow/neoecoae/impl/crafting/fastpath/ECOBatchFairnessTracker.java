package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Experimental per-job turn state for virtual F-series exchange tasks. */
public final class ECOBatchFairnessTracker {
    private static final long WAITING_JOB_TTL_TICKS = 20L;

    private final Set<UUID> inFlightBatchJobs = new HashSet<>();
    private final Map<UUID, Long> waitingBatchJobs = new LinkedHashMap<>();
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            inFlightBatchJobs.clear();
            waitingBatchJobs.clear();
        }
    }

    public boolean shouldDefer(@Nullable UUID craftingJobId, long currentTick) {
        if (!isActive(craftingJobId)) {
            clearWhenDisabled();
            return false;
        }
        expireStaleWaitingJobs(currentTick);
        if (inFlightBatchJobs.contains(craftingJobId)) {
            return true;
        }
        for (UUID waitingJobId : waitingBatchJobs.keySet()) {
            if (!inFlightBatchJobs.contains(waitingJobId)) {
                return !waitingJobId.equals(craftingJobId);
            }
        }
        return false;
    }

    public void noteWaiting(@Nullable UUID craftingJobId, long currentTick) {
        if (!isActive(craftingJobId)) {
            clearWhenDisabled();
            return;
        }
        expireStaleWaitingJobs(currentTick);
        waitingBatchJobs.put(craftingJobId, currentTick);
    }

    public void noteAccepted(@Nullable UUID craftingJobId, long currentTick) {
        if (!isActive(craftingJobId)) {
            clearWhenDisabled();
            return;
        }
        expireStaleWaitingJobs(currentTick);
        waitingBatchJobs.remove(craftingJobId);
        inFlightBatchJobs.add(craftingJobId);
    }

    public void noteRejected(@Nullable UUID craftingJobId) {
        if (!isActive(craftingJobId)) {
            clearWhenDisabled();
            return;
        }
        waitingBatchJobs.remove(craftingJobId);
    }

    public void noteCompleted(@Nullable UUID craftingJobId) {
        if (!isActive(craftingJobId)) {
            clearWhenDisabled();
            return;
        }
        inFlightBatchJobs.remove(craftingJobId);
    }

    private boolean isActive(@Nullable UUID craftingJobId) {
        return enabled && craftingJobId != null;
    }

    private void clearWhenDisabled() {
        if (!enabled) {
            inFlightBatchJobs.clear();
            waitingBatchJobs.clear();
        }
    }

    private void expireStaleWaitingJobs(long currentTick) {
        waitingBatchJobs.entrySet().removeIf(entry -> currentTick - entry.getValue() > WAITING_JOB_TTL_TICKS);
    }
}
