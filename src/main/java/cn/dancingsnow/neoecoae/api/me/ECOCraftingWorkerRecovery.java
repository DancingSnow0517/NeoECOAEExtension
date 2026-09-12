package cn.dancingsnow.neoecoae.api.me;

import java.util.UUID;

import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import net.minecraft.world.level.Level;

/** Worker ownership operations invoked by CPU job lifecycle transitions. */
final class ECOCraftingWorkerRecovery {
    private ECOCraftingWorkerRecovery() {
    }

    static void releaseCompletedOutputs(IGrid grid, UUID craftingJobId) {
        if (grid == null) return;
        for (var worker : grid.getMachines(ECOCraftingWorkerBlockEntity.class)) {
            worker.releaseCompletedJobOutputs(craftingJobId);
        }
    }

    static void recoverTerminatedInputs(Level level, UUID craftingJobId) {
        if (level == null || level.getServer() == null) return;
        // Loaded workers may already have left this grid. Unloaded workers reconcile the durable decision later.
        for (var worker : ECOCraftingWorkerBlockEntity.getLoadedServerWorkers()) {
            if (worker.getLevel() != null && worker.getLevel().getServer() == level.getServer()) {
                worker.recoverTerminatedJob(craftingJobId);
            }
        }
    }
}
