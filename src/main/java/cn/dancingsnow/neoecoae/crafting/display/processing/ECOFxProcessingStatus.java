package cn.dancingsnow.neoecoae.crafting.display.processing;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

final class ECOFxProcessingStatus implements ECOProcessingStatusReader {
    @Override public boolean supports(BlockEntity machine) { return machine instanceof ECOCraftingWorkerBlockEntity; }
    @Override public Observation read(BlockEntity machine, Context context) {
        var worker = (ECOCraftingWorkerBlockEntity) machine;
        var lanes = new ArrayList<Machine>();
        var controller = worker.getCluster() == null ? null : worker.getCluster().getController();
        boolean available = controller != null && worker.getMainNode().isActive();
        // Thread progress is measured in units of MAX_PROGRESS, not ticks: convert with the controller's current rate.
        boolean oneTick = controller != null && controller.isFullVirtualCraftingMode();
        int perTick = controller == null ? ECOCraftingThread.calculateProgressPerTick(0)
            : ECOCraftingThread.calculateProgressPerTick(controller.getExecutionOverclockTimes());
        for (var thread : worker.getThreadSnapshots()) {
            if (!thread.busy() || !Objects.equals(context.jobId(), thread.craftingJobId())
                    || thread.outputItem().isEmpty() || !context.output().equals(ECOKeyFactory.item(thread.outputItem()))) continue;
            String blocked = thread.pauseReason();
            boolean paused = !available || !blocked.isEmpty();
            State state = thread.outputsReady() ? State.WAITING : paused ? State.PAUSED : State.RUNNING;
            Component reason = thread.outputsReady() ? Component.translatable("gui.neoecoae.processing.output_blocked")
                : blocked.contains("energy-unavailable") ? Component.translatable("gui.neoecoae.processing.energy")
                : blocked.contains("coolant-unavailable") ? Component.translatable("gui.neoecoae.processing.coolant")
                : paused ? Component.translatable("gui.neoecoae.processing.paused") : Component.empty();
            int rate = oneTick && thread.virtualBatch() ? Math.max(1, thread.maxProgress()) : perTick;
            int duration = ticks(thread.maxProgress(), rate);
            int done = thread.outputsReady() ? duration
                : duration - ticks(Math.max(0, thread.maxProgress() - thread.progress()), rate);
            lanes.add(ECOProcessingSamples.cycle(worker, context.output().getId().toString(),
                done, duration, thread.craftCount(), state, reason));
        }
        return lanes.isEmpty() ? Observation.EMPTY : Observation.sample(ECOProcessingSamples.combine(worker, lanes,
            Component.translatable("gui.neoecoae.processing.association.job")));
    }

    /** Whole ticks needed to advance {@code units} of progress at {@code perTick} units per tick. */
    static int ticks(int units, int perTick) {
        return units <= 0 ? 0 : Math.max(1, (units + Math.max(1, perTick) - 1) / Math.max(1, perTick));
    }
}
