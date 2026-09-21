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

    void getAllWaitingFor(Set<AEKey> waitingFor) {
        var job = host.getJob();
        if (job != null) {
            for (var entry : job.waitingFor.list) {
                waitingFor.add(entry.getKey());
            }
        }
    }

    long getPendingOutputs(AEKey template) {
        if (host.getJob() != null && host.getJob().exactOrder)
            return cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(
                host.getExactPendingPreview().getOrDefault(template, java.math.BigInteger.ZERO));
        long count = host.bigOrder.pendingPreview(template);
        var job = host.getJob();
        if (job != null) {
            for (var task : job.tasks.entrySet()) {
                for (var output : task.getKey().getOutputs()) {
                    if (template.matches(output)) {
                        count = NEMath.saturatingAdd(count,
                                NEMath.saturatingMultiply(output.amount(), task.getValue().value));
                    }
                }
            }
        }
        return count;
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
            for (var task : job.tasks.entrySet()) {
                for (var output : task.getKey().getOutputs()) {
                    long amount = NEMath.saturatingMultiply(output.amount(), task.getValue().value);
                    out.set(output.what(), NEMath.saturatingAdd(out.get(output.what()), amount));
                }
            }
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
