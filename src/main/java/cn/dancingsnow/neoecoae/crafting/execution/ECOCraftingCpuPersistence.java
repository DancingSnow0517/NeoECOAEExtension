package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;
import net.minecraft.nbt.CompoundTag;

/** Owns CPU NBT orchestration while preserving the established save keys and migration paths. */
final class ECOCraftingCpuPersistence {
    private final ECOCraftingCPULogic host;

    ECOCraftingCpuPersistence(ECOCraftingCPULogic host) {
        this.host = host;
    }

    void read(CompoundTag data) {
        cn.dancingsnow.neoecoae.compat.thunderbolt.ECOOverloadCpuAccountingBridge.clear(host);
        host.taskSchedulerForPersistence().reset();
        host.dispatchStrategyForPersistence().reset();
        host.jobAttachmentsForPersistence().reset();
        host.outputDeliveryForPersistence().clearPendingFinalOutputs();
        host.energyTransactionForPersistence().readFromNBT(data);
        host.exactInventory().setEnabled(data.getBoolean("ecoExactInventory"));
        host.getInventory().readFromNBT(data.getList("inventory", 10));

        if (!data.contains("job")) {
            host.setJobFromPersistence(null);
            host.bigOrder.clear();
            return;
        }

        var jobData = data.getCompound("job");
        var restoredJob = new ExecutingCraftingJob(jobData, host::postChange, host);
        if (restoredJob.exactOrder && restoredJob.link.isStandalone())
            restoredJob.link.setNexus(new appeng.crafting.CraftingLinkNexus(restoredJob.link.getCraftingID()));
        host.setJobFromPersistence(restoredJob);
        host.bigOrder.read(data);
        host.outputDeliveryForPersistence().loadPendingFinalOutputs(jobData);
        host.loadJobAttachments(jobData);

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

    void write(CompoundTag data) {
        host.bigOrder.write(data);
        data.putBoolean("ecoExactInventory", host.exactInventory().isEnabled());
        data.put("inventory", host.getInventory().writeToNBT());
        host.energyTransactionForPersistence().writeToNBT(data);
        var job = host.getJob();
        if (job == null) {
            data.remove("job");
            return;
        }

        CompoundTag jobData = job.writeToNBT();
        host.outputDeliveryForPersistence().writePendingFinalOutputs(jobData);
        host.writeJobAttachments(jobData);
        data.put("job", jobData);
    }
}
