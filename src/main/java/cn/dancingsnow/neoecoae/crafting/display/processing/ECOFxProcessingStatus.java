package cn.dancingsnow.neoecoae.crafting.display.processing;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
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
        boolean available = worker.getCluster() != null && worker.getCluster().getController() != null
            && worker.getMainNode().isActive();
        for (var thread : worker.getThreadSnapshots()) {
            if (!thread.busy() || !Objects.equals(context.jobId(), thread.craftingJobId())
                    || thread.outputItem().isEmpty() || !context.output().equals(AEItemKey.of(thread.outputItem()))) continue;
            String blocked = thread.pauseReason();
            boolean paused = !available || !blocked.isEmpty();
            State state = thread.outputsReady() ? State.WAITING : paused ? State.PAUSED : State.RUNNING;
            Component reason = thread.outputsReady() ? Component.translatable("gui.neoecoae.processing.output_blocked")
                : blocked.contains("energy-unavailable") ? Component.translatable("gui.neoecoae.processing.energy")
                : blocked.contains("coolant-unavailable") ? Component.translatable("gui.neoecoae.processing.coolant")
                : paused ? Component.translatable("gui.neoecoae.processing.paused") : Component.empty();
            lanes.add(ECOProcessingSamples.cycle(worker, context.output().getId().toString(),
                thread.outputsReady() ? thread.maxProgress() : thread.progress(), thread.maxProgress(),
                thread.craftCount(), state, reason));
        }
        return lanes.isEmpty() ? Observation.EMPTY : Observation.sample(ECOProcessingSamples.combine(worker, lanes,
            Component.translatable("gui.neoecoae.processing.association.job")));
    }
}
