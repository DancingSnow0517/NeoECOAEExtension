package cn.dancingsnow.neoecoae.mixins.compat.advancedae.crafting;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOAdvancedAeCraftingOutputRouter;
import cn.dancingsnow.neoecoae.api.me.ECOJobOutputReceiver;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster;
import net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Adds AdvancedAE job-directed output routing without coupling the base CraftingService mixin to AdvancedAE. */
@Pseudo
@Mixin(value = CraftingService.class, remap = false)
public abstract class AdvancedAeCraftingServiceMixin implements ECOAdvancedAeCraftingOutputRouter {
    @Shadow @Final private IGrid grid;

    @Override
    @Unique
    public long neoecoae$insertIntoAdvancedAeCpuForJob(
            UUID craftingJobId, AEKey what, long amount, Actionable type) {
        if (craftingJobId == null || what == null || amount <= 0L) {
            return 0L;
        }

        Set<AdvCraftingCPUCluster> clusters = new HashSet<>();
        for (AdvCraftingBlockEntity blockEntity : this.grid.getMachines(AdvCraftingBlockEntity.class)) {
            AdvCraftingCPUCluster cluster = blockEntity.getCluster();
            if (cluster != null) {
                clusters.add(cluster);
            }
        }
        for (AdvCraftingCPUCluster cluster : clusters) {
            for (var cpu : cluster.getActiveCPUs()) {
                ICraftingLink link = cpu.craftingLogic.getLastLink();
                if (link != null && craftingJobId.equals(link.getCraftingID())) {
                    if (cpu.craftingLogic instanceof ECOJobOutputReceiver receiver) {
                        return receiver.neoecoae$insertWorkerOutput(craftingJobId, what, amount, type);
                    }
                    return cpu.craftingLogic.insert(what, amount, type);
                }
            }
        }
        return 0L;
    }
}
