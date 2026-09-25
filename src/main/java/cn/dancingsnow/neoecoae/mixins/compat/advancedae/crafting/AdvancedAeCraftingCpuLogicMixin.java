package cn.dancingsnow.neoecoae.mixins.compat.advancedae.crafting;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.output.ECOJobOutputReceiver;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import java.util.UUID;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor.AdvancedAeCraftingJobAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks AdvancedAE's dynamically-created quantum-computer CPUs into ECO's verified batch path. */
@Pseudo
@Mixin(value = AdvCraftingCPULogic.class, remap = false)
public abstract class AdvancedAeCraftingCpuLogicMixin implements ECOJobOutputReceiver {
    @Unique
    private static final Logger NEOECOAE$LOGGER = LoggerFactory.getLogger("neoecoae");

    @Shadow @Final AdvCraftingCPU cpu;
    @Shadow @Final private ListCraftingInventory inventory;
    @Shadow private ExecutingCraftingJob job;
    @Shadow private boolean markedForDeletion;
    @Shadow public abstract long insert(AEKey what, long amount, Actionable type);

    @Override
    public long neoecoae$insertWorkerOutput(UUID jobId, AEKey what, long amount, Actionable type) {
        if (what == null || amount <= 0L || !(job instanceof AdvancedAeCraftingJobAccessor access)
                || !access.neoecoae$getLink().getCraftingID().equals(jobId)) return 0L;
        boolean wasMarkedForDeletion = markedForDeletion;
        // Keep the CPU discoverable if insert finishes the job before its physical remainder is retained.
        if (type == Actionable.MODULATE) markedForDeletion = true;
        long inserted;
        try {
            inserted = insert(what, amount, type);
        } finally {
            if (job != null || type == Actionable.SIMULATE) markedForDeletion = wasMarkedForDeletion;
        }
        if (inserted < 0L || inserted > amount) {
            throw new IllegalStateException("Invalid AdvancedAE insertion amount: " + inserted + " for " + amount);
        }
        // AdvancedAE can finish the job even when its requester returns zero. Retain that physical remainder.
        if (type == Actionable.MODULATE && inserted < amount) {
            inventory.insert(what, amount - inserted, Actionable.MODULATE);
            cpu.markDirty();
        }
        return amount;
    }

    @Inject(method = "finishJob", at = @At("HEAD"), remap = false)
    private void neoecoae$releaseCompletedWorkerOutputs(boolean success, CallbackInfo ci) {
        if (!success || !(job instanceof AdvancedAeCraftingJobAccessor access)) return;
        var grid = cpu.getGrid();
        if (grid == null) return;
        for (var worker : grid.getMachines(ECOCraftingWorkerBlockEntity.class)) {
            worker.releaseCompletedJobOutputs(access.neoecoae$getLink().getCraftingID());
        }
    }

    @Unique private cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuFastPath neoecoae$batchDispatcher;

    @Unique private cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuFastPath neoecoae$batchDispatcher() {
        if (neoecoae$batchDispatcher == null) neoecoae$batchDispatcher =
                new cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuFastPath(cpu::markDirty);
        return neoecoae$batchDispatcher;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, remap = false)
    private void neoecoae$tryFastPath(int maxPatterns, appeng.me.service.CraftingService craftingService,
            IEnergyService energyService, @Nullable Level level, CallbackInfoReturnable<Integer> cir) {
        // Keep OmniSequence's extraction/accounting owner consistent with the AE2 adapter.
        if (level == null || net.neoforged.fml.ModList.get().isLoaded("molecularmanipulator")) return;
        try {
            int pushed = neoecoae$batchDispatcher().execute(this, job, inventory,
                    maxPatterns, craftingService, energyService, level);
            if (pushed > 0) cir.setReturnValue(pushed);
        } catch (RuntimeException failure) {
            if (job instanceof AdvancedAeCraftingJobAccessor access) access.neoecoae$suspended(true);
            cpu.markDirty();
            NEOECOAE$LOGGER.error("Suspended AdvancedAE CPU after batch dispatch failure", failure);
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true, remap = false)
    private void neoecoae$validatePlan(appeng.api.networking.IGrid grid,
            appeng.api.networking.crafting.ICraftingPlan plan,
            appeng.api.networking.security.IActionSource source,
            appeng.api.networking.crafting.ICraftingRequester requester,
            CallbackInfoReturnable<appeng.api.networking.crafting.ICraftingSubmitResult> cir) {
        if (!cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuSupport.supportsPlan(plan))
            cir.setReturnValue(appeng.crafting.execution.CraftingSubmitResult.NO_CPU_FOUND);
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), remap = false)
    private void neoecoae$refundCredit(IEnergyService energy, appeng.me.service.CraftingService crafting, CallbackInfo ci) {
        if (neoecoae$batchDispatcher != null) neoecoae$batchDispatcher.refundIdleCredit(energy);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void neoecoae$readBatchEnergy(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        neoecoae$batchDispatcher().read(tag.getCompound("neoecoaeFastPath"));
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
    private void neoecoae$writeBatchEnergy(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        if (neoecoae$batchDispatcher == null) return;
        var ledger = new net.minecraft.nbt.CompoundTag();
        neoecoae$batchDispatcher.write(ledger);
        tag.put("neoecoaeFastPath", ledger);
    }

    /** Keeps the existing ECO worker's job-directed output routing for native single-craft fallback. */
    @WrapOperation(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean neoecoae$pushPatternWithJob(
            ICraftingProvider provider,
            appeng.api.crafting.IPatternDetails details,
            KeyCounter[] inputHolder,
            Operation<Boolean> original) {
        if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                && this.job instanceof AdvancedAeCraftingJobAccessor jobAccess) {
            return patternBus.pushPattern(
                    details,
                    inputHolder,
                    jobAccess.neoecoae$getLink().getCraftingID());
        }
        return original.call(provider, details, inputHolder);
    }
}
