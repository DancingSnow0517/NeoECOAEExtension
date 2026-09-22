package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.function.Consumer;
import java.util.function.Function;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingDispatchEvent;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;

/** Applies an accepted ordinary or batch result to the CPU-owned job ledger. */
final class ECOCraftingDispatchAccounting {
    private final Consumer<AEKey> postChange;
    private final Runnable markDirty;
    private final Function<ExecutingCraftingJob, ECOCraftingJobContext> contextFactory;
    private final Consumer<ECOCraftingDispatchEvent> dispatchEvent;

    ECOCraftingDispatchAccounting(Consumer<AEKey> postChange, Runnable markDirty) {
        this(postChange, markDirty, job -> {
            throw new IllegalStateException("No ECO job context factory was configured");
        }, event -> {});
    }

    ECOCraftingDispatchAccounting(Consumer<AEKey> postChange, Runnable markDirty,
            Function<ExecutingCraftingJob, ECOCraftingJobContext> contextFactory,
            Consumer<ECOCraftingDispatchEvent> dispatchEvent) {
        this.postChange = postChange;
        this.markDirty = markDirty;
        this.contextFactory = contextFactory;
        this.dispatchEvent = dispatchEvent;
    }

    /**
     * Applies the same task/output/runtime accounting for one ordinary craft or an accepted batch.
     * The hook runs after runtime progress and before menu notifications, matching the existing
     * dynamic-output registration order.
     */
    void applyExact(ECOCraftingDispatchRequest request,
            cn.dancingsnow.neoecoae.crafting.execution.batch.ECOExactBatchPlanner.PreparedBatch batch,
            Runnable beforeNotifications, ICraftingProvider provider) {
        var job = request.job();
        if (!job.exactOrder) throw new IllegalStateException("Exact dispatch requires a big order");
        var waiting = (cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory) job.waitingFor;
        waiting.restore(batch.outputs());
        waiting.restore(batch.remainders());
        batch.remainders().forEach((key, amount) -> job.timeTracker.addMaxItems(amount, key.getType()));
        job.tasks.get(request.pattern()).accept(batch.craftCount());
        if (job.executionRuntime != null)
            job.executionRuntime.onAcceptedExact(request.candidate(), batch.craftCount(), request.inputs());
        beforeNotifications.run();
        dispatchEvent.accept(new ECOCraftingDispatchEvent(contextFactory.apply(job), request.pattern(),
            batch.craftCount(), provider));
        batch.outputs().keySet().forEach(postChange);
        batch.remainders().keySet().forEach(postChange);
        markDirty.run();
    }

    void apply(ECOCraftingDispatchRequest request, ECOCraftingDispatchResult result,
            Runnable beforeNotifications) {
        apply(request, result, beforeNotifications, null);
    }

    /** Applies an accepted result and emits the stable dispatch event after ledger mutation. */
    void apply(ECOCraftingDispatchRequest request, ECOCraftingDispatchResult result,
            Runnable beforeNotifications, ICraftingProvider provider) {
        var job = request.job();
        for (var output : result.outputs()) {
            job.waitingFor.insert(output.what(), output.amount(), appeng.api.config.Actionable.MODULATE);
        }
        for (var remainder : result.remainders()) {
            job.waitingFor.insert(remainder.what(), remainder.amount(), appeng.api.config.Actionable.MODULATE);
            job.timeTracker.addMaxItems(remainder.amount(), remainder.what().getType());
        }

        request.job().tasks.get(request.pattern()).accept(result.acceptedCrafts());
        if (job.executionRuntime != null) {
            job.executionRuntime.onAccepted(request.candidate(), result.acceptedCrafts(), request.inputs());
        }
        beforeNotifications.run();
        if (provider != null) {
            dispatchEvent.accept(new ECOCraftingDispatchEvent(
                    contextFactory.apply(request.job()), request.pattern(), result.acceptedCrafts(), provider));
        }
        for (var output : request.pattern().getOutputs()) {
            postChange.accept(output.what());
        }
        markDirty.run();
    }
}
