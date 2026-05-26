package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin120 {
    @Unique
    private static final Logger NEOECOAE_LOGGER = LogUtils.getLogger();
    @Unique
    private static final Comparator<NEComputationCluster> NEOECOAE_FAST_FIRST = Comparator
        .comparingInt(NEComputationCluster::getCPUAccelerators)
        .reversed()
        .thenComparingLong(NEComputationCluster::getAvailableStorage);

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    @Final
    private IEnergyService energyGrid;

    @Shadow
    @Final
    private Set<AEKey> currentlyCrafting;

    @Shadow
    private boolean updateList;

    @Inject(method = "addNode", at = @At("TAIL"))
    private void neoecoae$onAddNode(IGridNode gridNode, net.minecraft.nbt.CompoundTag savedData, CallbackInfo ci) {
        if (gridNode.getOwner() instanceof NEBlockEntity<?, ?> blockEntity
            && blockEntity.getCluster() instanceof NEComputationCluster) {
            this.updateList = true;
        }
    }

    @Inject(method = "removeNode", at = @At("TAIL"))
    private void neoecoae$onRemoveNode(IGridNode gridNode, CallbackInfo ci) {
        if (gridNode.getOwner() instanceof NEBlockEntity<?, ?> blockEntity
            && blockEntity.getCluster() instanceof NEComputationCluster) {
            this.updateList = true;
        }
    }

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void neoecoae$tickComputationCpus(CallbackInfo ci) {
        for (NEComputationCluster cluster : neoecoae$getComputationClusters()) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                cpu.getLogic().tickCraftingLogic(this.energyGrid, (CraftingService) (Object) this);
                cpu.getLogic().getAllWaitingFor(this.currentlyCrafting);
            }
        }
    }

    @Inject(method = "getCpus", at = @At("RETURN"), cancellable = true)
    private void neoecoae$getCpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir) {
        ImmutableSet.Builder<ICraftingCPU> cpus = ImmutableSet.builder();
        cpus.addAll(cir.getReturnValue());
        for (NEComputationCluster cluster : neoecoae$getComputationClusters()) {
            List<ECOCraftingCPU> activeCpus = cluster.getActiveCPUs();
            cpus.addAll(activeCpus);
            if (cluster.isActive() && activeCpus.size() < cluster.getMaxThreads()) {
                cpus.add(cluster.getFakeCPU());
            }
        }
        cir.setReturnValue(cpus.build());
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void neoecoae$submitJob(
        ICraftingPlan job,
        ICraftingRequester requestingMachine,
        ICraftingCPU target,
        boolean prioritizePower,
        IActionSource src,
        CallbackInfoReturnable<ICraftingSubmitResult> cir
    ) {
        if (target instanceof ECOCraftingCPU ecoCpu) {
            NEOECOAE_LOGGER.info("NE computation CPU submit targeted: storage={}, coprocessors={}", ecoCpu.getAvailableStorage(), ecoCpu.getCoProcessors());
            cir.setReturnValue(ecoCpu.getCluster().submitJob(this.grid, job, src, requestingMachine));
            return;
        }

        NEComputationCluster cluster = neoecoae$findSuitableComputationCluster(job, src);
        if (cluster != null) {
            ICraftingSubmitResult result = cluster.submitJob(this.grid, job, src, requestingMachine);
            if (result.successful()) {
                NEOECOAE_LOGGER.info("NE computation CPU submit auto-selected: storage={}, coprocessors={}", cluster.getAvailableStorage(), cluster.getCPUAccelerators());
                this.updateList = true;
                cir.setReturnValue(result);
            }
        }
    }

    @Inject(method = "insertIntoCpus", at = @At("RETURN"), cancellable = true)
    private void neoecoae$insertIntoCpus(AEKey what, long amount, Actionable type, CallbackInfoReturnable<Long> cir) {
        long inserted = cir.getReturnValue();
        for (NEComputationCluster cluster : neoecoae$getComputationClusters()) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                inserted += cpu.getLogic().insert(what, amount - inserted, type);
            }
        }
        cir.setReturnValue(inserted);
    }

    @Inject(method = "getRequestedAmount", at = @At("RETURN"), cancellable = true)
    private void neoecoae$getRequestedAmount(AEKey what, CallbackInfoReturnable<Long> cir) {
        long requested = cir.getReturnValue();
        for (NEComputationCluster cluster : neoecoae$getComputationClusters()) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                requested += cpu.getLogic().getWaitingFor(what);
            }
        }
        cir.setReturnValue(requested);
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void neoecoae$hasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        for (NEComputationCluster cluster : neoecoae$getComputationClusters()) {
            if (cluster.getFakeCPU() == cpu) {
                cir.setReturnValue(true);
                return;
            }
            for (ECOCraftingCPU activeCpu : cluster.getActiveCPUs()) {
                if (activeCpu == cpu) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Unique
    private List<NEComputationCluster> neoecoae$getComputationClusters() {
        List<NEComputationCluster> clusters = new ArrayList<>();
        for (ECOComputationSystemBlockEntity blockEntity : this.grid.getMachines(ECOComputationSystemBlockEntity.class)) {
            NEComputationCluster cluster = blockEntity.getCluster();
            if (cluster != null && blockEntity.isFormed()) {
                clusters.add(cluster);
            }
        }
        return clusters;
    }

    @Unique
    private NEComputationCluster neoecoae$findSuitableComputationCluster(ICraftingPlan job, IActionSource src) {
        List<NEComputationCluster> candidates = new ArrayList<>();
        for (NEComputationCluster cluster : neoecoae$getComputationClusters()) {
            if (cluster.isActive()
                && cluster.getAvailableStorage() >= job.bytes()
                && cluster.canBeAutoSelectedFor(src)) {
                candidates.add(cluster);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(NEOECOAE_FAST_FIRST);
        return candidates.get(0);
    }
}
