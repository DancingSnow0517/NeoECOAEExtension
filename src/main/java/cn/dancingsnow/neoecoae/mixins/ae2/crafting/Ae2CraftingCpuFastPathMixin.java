package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuFastPath;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class Ae2CraftingCpuFastPathMixin {
    @Unique private static final org.slf4j.Logger NEOECOAE_BATCH_LOG = org.slf4j.LoggerFactory.getLogger("neoecoae");
    @Shadow @Final private ListCraftingInventory inventory;
    @Shadow @Final CraftingCPUCluster cluster;
    @Shadow private ExecutingCraftingJob job;
    @Unique private ECOExternalCpuFastPath neoecoae$fastPath;

    @Unique private ECOExternalCpuFastPath neoecoae$fastPath() {
        if (neoecoae$fastPath == null) neoecoae$fastPath = new ECOExternalCpuFastPath(cluster::markDirty);
        return neoecoae$fastPath;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void neoecoae$dispatch(int maxPatterns, CraftingService crafting, IEnergyService energy,
            Level level, CallbackInfoReturnable<Integer> cir) {
        // OmniSequence owns extraction/accounting and has an ECO provider adapter.
        // Thunderbolt's outer batch loop falls back here for ECO providers, which
        // deliberately do not implement its foreign batch-provider interface.
        if (net.neoforged.fml.ModList.get().isLoaded("molecularmanipulator")) return;
        try {
            int consumed = neoecoae$fastPath().execute(this, job, inventory, maxPatterns, crafting, energy, level);
            if (consumed > 0) cir.setReturnValue(consumed);
        } catch (RuntimeException failure) {
            if (job instanceof cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob access)
                access.neoecoae$suspended(true);
            cluster.markDirty();
            NEOECOAE_BATCH_LOG.error("Suspended native CPU after batch dispatch failure", failure);
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void neoecoae$validatePlan(appeng.api.networking.IGrid grid,
            appeng.api.networking.crafting.ICraftingPlan plan,
            appeng.api.networking.security.IActionSource source,
            appeng.api.networking.crafting.ICraftingRequester requester,
            CallbackInfoReturnable<appeng.api.networking.crafting.ICraftingSubmitResult> cir) {
        if (!cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuSupport.supportsPlan(plan))
            cir.setReturnValue(appeng.crafting.execution.CraftingSubmitResult.NO_CPU_FOUND);
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void neoecoae$refundCredit(IEnergyService energy, CraftingService crafting, CallbackInfo ci) {
        if (neoecoae$fastPath != null) neoecoae$fastPath.refundIdleCredit(energy);
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void neoecoae$releaseWorkerOutput(boolean success, CallbackInfo ci) {
        if (!success || !(job instanceof cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob access)) return;
        var grid = cluster.getGrid();
        if (grid == null) return;
        for (var worker : grid.getMachines(cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity.class))
            worker.releaseCompletedJobOutputs(access.neoecoae$link().getCraftingID());
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeCrafting",
            at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean neoecoae$pushWithJob(appeng.api.networking.crafting.ICraftingProvider provider,
            appeng.api.crafting.IPatternDetails pattern, appeng.api.stacks.KeyCounter[] inputs,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        if (provider instanceof cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity bus
                && job instanceof cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob access)
            return bus.pushPattern(pattern, inputs, access.neoecoae$link().getCraftingID());
        return original.call(provider, pattern, inputs);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void neoecoae$read(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        neoecoae$fastPath().read(tag.getCompound("neoecoaeFastPath"));
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void neoecoae$write(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (neoecoae$fastPath == null) return;
        var ledger = new CompoundTag();
        neoecoae$fastPath.write(ledger);
        tag.put("neoecoaeFastPath", ledger);
    }
}
