package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Owns CPU NBT orchestration while preserving the established save keys and migration paths. */
final class ECOCraftingCpuPersistence {
    private final ECOCraftingCPULogic host;

    ECOCraftingCpuPersistence(ECOCraftingCPULogic host) {
        this.host = host;
    }

    void read(CompoundTag data, HolderLookup.Provider registries) {
        host.taskSchedulerForPersistence().reset();
        host.dispatchStrategyForPersistence().reset();
        host.jobAttachmentsForPersistence().reset();
        host.outputDeliveryForPersistence().clearPendingFinalOutputs();
        host.energyTransactionForPersistence().readFromNBT(data);
        host.getInventory().readFromNBT(data.getList("inventory", 10), registries);

        if (!data.contains("job")) {
            host.setJobFromPersistence(null);
            host.bigOrder.clear();
            return;
        }

        var jobData = data.getCompound("job");
        var restoredJob = new ExecutingCraftingJob(jobData, registries, host::postChange, host);
        if (restoredJob.exactOrder && restoredJob.link.isStandalone())
            restoredJob.link.setNexus(new appeng.crafting.CraftingLinkNexus(restoredJob.link.getCraftingID()));
        host.setJobFromPersistence(restoredJob);
        host.bigOrder.read(data, registries);
        host.outputDeliveryForPersistence().loadPendingFinalOutputs(jobData, registries);
        host.loadJobAttachments(jobData, registries);

        IGrid grid = host.cpu.getGrid();
        if (grid != null) {
            // Publish the recovered link only after the complete job has decoded successfully.
            ((CraftingService) grid.getCraftingService()).addLink(restoredJob.link);
        }

        // Migrate the legacy standalone final-output buffer into the shared CPU inventory once.
        long buffered = jobData.getLong("bufferedFinalOutput");
        if (buffered > 0L && restoredJob.finalOutput != null) {
            host.getInventory().insert(restoredJob.finalOutput.what(), buffered, Actionable.MODULATE);
        }
        if (restoredJob.finalOutput == null) {
            host.finishJob(false);
        }
    }

    void write(CompoundTag data, HolderLookup.Provider registries) {
        host.bigOrder.write(data, registries);
        data.put("inventory", host.getInventory().writeToNBT(registries));
        host.energyTransactionForPersistence().writeToNBT(data);
        var job = host.getJob();
        if (job == null) {
            data.remove("job");
            return;
        }

        CompoundTag jobData = job.writeToNBT(registries);
        host.outputDeliveryForPersistence().writePendingFinalOutputs(jobData, registries);
        host.writeJobAttachments(jobData, registries);
        data.put("job", jobData);
    }
}
