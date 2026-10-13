package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuFastPath;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuMixinSupport;
import cn.dancingsnow.neoecoae.integration.ModIds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
        if (ModIds.isLoaded(ModIds.MOLECULAR_MANIPULATOR)) return;
        if (ECOExternalCpuMixinSupport.dispatch(this, neoecoae$fastPath(), job, inventory, maxPatterns, crafting,
                energy, level, cir, "native")) cluster.markDirty();
    }

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void neoecoae$validatePlan(appeng.api.networking.IGrid grid,
            appeng.api.networking.crafting.ICraftingPlan plan,
            appeng.api.networking.security.IActionSource source,
            appeng.api.networking.crafting.ICraftingRequester requester,
            CallbackInfoReturnable<appeng.api.networking.crafting.ICraftingSubmitResult> cir) {
        ECOExternalCpuMixinSupport.validatePlan(plan, cir);
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void neoecoae$refundCredit(IEnergyService energy, CraftingService crafting, CallbackInfo ci) {
        ECOExternalCpuMixinSupport.refundIdleCredit(neoecoae$fastPath, energy);
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void neoecoae$releaseWorkerOutput(boolean success, CallbackInfo ci) {
        ECOExternalCpuMixinSupport.releaseOnFinish(job, success, cluster::getLevel, cluster::getGrid);
    }

    @WrapOperation(method = "executeCrafting",
            at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean neoecoae$pushWithJob(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs,
            Operation<Boolean> original) {
        if (provider instanceof ECOCraftingPatternBusBlockEntity bus && job instanceof ECOExternalCpuJob access)
            return bus.pushPattern(pattern, inputs, access.neoecoae$link().getCraftingID());
        return original.call(provider, pattern, inputs);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void neoecoae$read(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ECOExternalCpuMixinSupport.readFastPath(neoecoae$fastPath(), tag);
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void neoecoae$write(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ECOExternalCpuMixinSupport.writeFastPath(neoecoae$fastPath, tag);
    }
}
