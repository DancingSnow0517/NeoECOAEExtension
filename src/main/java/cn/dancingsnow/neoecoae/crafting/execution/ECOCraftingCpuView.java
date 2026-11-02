package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressSnapshot;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressView;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;

import java.util.Set;

/** Read-only CPU projections used by menus and integrations. */
final class ECOCraftingCpuView {
    private final ECOCraftingCPULogic host;

    ECOCraftingCpuView(ECOCraftingCPULogic host) {
        this.host = host;
    }

    long getStored(AEKey template) {
        return host.getInventory().extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    long getWaitingFor(AEKey template) {
        var job = host.getJob();
        return job == null
            ? 0L
            : job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    boolean isBatchedOutput(AEKey template) {
        var job = host.getJob();
        return job != null && job.batchedOutputs.contains(template);
    }

    void getAllWaitingFor(Set<AEKey> waitingFor) {
        var job = host.getJob();
        if (job != null) {
            for (var entry : job.waitingFor.list) {
                waitingFor.add(entry.getKey());
            }
        }
    }

    long getPendingOutputs(AEKey template) {
        return pendingOutputs().get(template);
    }

    /*
     * The CPU menu and computation panel query every changed key separately, so pending outputs are aggregated once
     * and reused while the job, its posted changes and the server tick stay the same.
     */
    private KeyCounter cachedPending;
    private KeyCounter cachedTaskOutputs;
    private Object cachedJob;
    private long cachedRevision = -1;
    private long cachedTick = Long.MIN_VALUE;

    private void validateCache() {
        var job = host.getJob();
        long revision = host.viewRevision();
        long tick = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
        if (cachedJob != job || cachedRevision != revision || cachedTick != tick) {
            cachedPending = null;
            cachedTaskOutputs = null;
            cachedJob = job;
            cachedRevision = revision;
            cachedTick = tick;
        }
    }

    private KeyCounter taskOutputs() {
        validateCache();
        if (cachedTaskOutputs == null) {
            var result = new KeyCounter();
            var job = host.getJob();
            if (job != null) {
                for (var task : job.tasks.entrySet()) {
                    for (var output : task.getKey().getOutputs()) {
                        long amount = NEMath.saturatingMultiply(output.amount(), task.getValue().value);
                        result.set(output.what(), NEMath.saturatingAdd(result.get(output.what()), amount));
                    }
                }
            }
            cachedTaskOutputs = result;
        }
        return cachedTaskOutputs;
    }

    private KeyCounter pendingOutputs() {
        validateCache();
        if (cachedPending == null) {
            var result = new KeyCounter();
            var job = host.getJob();
            if (job != null && job.exactOrder) {
                host.getExactPendingPreview().forEach((key, amount) -> result.set(key,
                    cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(amount)));
            } else {
                host.bigOrder.collectPendingPreview(result);
                addAllSaturating(result, taskOutputs());
            }
            cachedPending = result;
        }
        return cachedPending;
    }

    /** Collects planned, waiting and locally stored items for the CPU menu. */
    void getAllItems(KeyCounter out) {
        host.bigOrder.collectPendingPreview(out);
        addAllSaturating(out, host.getInventory().list);
        var job = host.getJob();
        if (job != null) {
            addAllSaturating(out, job.waitingFor.list);
            job.deferredEmitted.forEach((key, amount) -> out.set(key, Math.max(out.get(key),
                cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(amount))));
            addAllSaturating(out, taskOutputs());
        }
    }

    void getOwnedItems(KeyCounter out) {
        out.addAll(host.getInventory().list);
    }

    boolean hasOwnedItems() {
        return !host.getInventory().list.isEmpty();
    }

    boolean hasJob() {
        return host.getJob() != null;
    }

    GenericStack getFinalJobOutput() {
        var job = host.getJob();
        return job == null ? null : job.finalOutput;
    }

    long getRemainingJobOutputAmount() {
        var job = host.getJob();
        return job == null ? 0L : job.remainingAmount;
    }

    ElapsedTimeTracker getElapsedTimeTracker() {
        var job = host.getJob();
        return job == null ? new ElapsedTimeTracker() : job.timeTracker;
    }

    ECOCraftingProgressView getProgressView() {
        var job = host.getJob();
        return job == null ? ECOCraftingProgressSnapshot.empty() : job.timeTracker.snapshot();
    }

    private static void addAllSaturating(KeyCounter target, KeyCounter source) {
        for (var entry : source) {
            target.set(entry.getKey(), NEMath.saturatingAdd(target.get(entry.getKey()), entry.getLongValue()));
        }
    }
}
