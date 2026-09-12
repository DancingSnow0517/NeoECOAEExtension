package cn.dancingsnow.neoecoae.api.me;

import java.util.function.Consumer;

import appeng.api.stacks.AEKey;

/** Applies an accepted ordinary or batch result to the CPU-owned job ledger. */
final class ECOCraftingDispatchAccounting {
    private final Consumer<AEKey> postChange;
    private final Runnable markDirty;

    ECOCraftingDispatchAccounting(Consumer<AEKey> postChange, Runnable markDirty) {
        this.postChange = postChange;
        this.markDirty = markDirty;
    }

    /**
     * Applies the same task/output/runtime accounting for one ordinary craft or an accepted batch.
     * The hook runs after runtime progress and before menu notifications, matching the existing
     * dynamic-output registration order.
     */
    void apply(ECOCraftingDispatchRequest request, ECOCraftingDispatchResult result,
            Runnable beforeNotifications) {
        var job = request.job();
        for (var output : result.outputs()) {
            job.waitingFor.insert(output.what(), output.amount(), appeng.api.config.Actionable.MODULATE);
        }
        for (var remainder : result.remainders()) {
            job.waitingFor.insert(remainder.what(), remainder.amount(), appeng.api.config.Actionable.MODULATE);
            job.timeTracker.addMaxItems(remainder.amount(), remainder.what().getType());
        }

        request.job().tasks.get(request.pattern()).value -= result.acceptedCrafts();
        if (job.executionRuntime != null) {
            job.executionRuntime.onAccepted(request.candidate(), result.acceptedCrafts(), request.inputs());
        }
        beforeNotifications.run();
        for (var output : request.pattern().getOutputs()) {
            postChange.accept(output.what());
        }
        markDirty.run();
    }
}
