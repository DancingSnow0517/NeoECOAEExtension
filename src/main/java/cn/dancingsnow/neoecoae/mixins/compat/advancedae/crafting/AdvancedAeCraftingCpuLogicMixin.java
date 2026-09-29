package cn.dancingsnow.neoecoae.mixins.compat.advancedae.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOJobOutputReceiver;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuFastPath;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuSupport;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AdvancedAE's dynamic CPUs share the verified-batch dispatcher with native AE2 CPUs. */
@Pseudo
@Mixin(value = AdvCraftingCPULogic.class, remap = false)
public abstract class AdvancedAeCraftingCpuLogicMixin implements ECOJobOutputReceiver {
    @Unique private static final Logger NEOECOAE$LOGGER = LoggerFactory.getLogger("neoecoae");

    @Shadow
    @Final
    AdvCraftingCPU cpu;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    private boolean markedForDeletion;

    @Shadow
    public abstract long insert(AEKey what, long amount, Actionable type);

    @Unique private ECOExternalCpuFastPath neoecoae$fastPath;

    @Unique private ECOExternalCpuFastPath neoecoae$fastPath() {
        if (neoecoae$fastPath == null) neoecoae$fastPath = new ECOExternalCpuFastPath(cpu::markDirty);
        return neoecoae$fastPath;
    }

    @Override
    public long neoecoae$insertWorkerOutput(UUID jobId, AEKey what, long amount, Actionable type) {
        if (jobId == null
                || what == null
                || amount <= 0
                || !(job instanceof ECOExternalCpuJob access)
                || !access.neoecoae$link().getCraftingID().equals(jobId)
                || ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), jobId)) return 0;
        long offered = Math.min(amount, access.neoecoae$waitingFor().extract(what, amount, Actionable.SIMULATE));
        if (offered <= 0) return 0;

        // A final output can complete the job during insert. Keep the dynamic CPU discoverable
        // until the requester remainder is safely retained in its physical inventory.
        boolean previouslyMarked = markedForDeletion;
        if (type == Actionable.MODULATE) markedForDeletion = true;
        long inserted;
        try {
            inserted = insert(what, offered, type);
        } finally {
            if (job != null || type == Actionable.SIMULATE) markedForDeletion = previouslyMarked;
        }
        if (inserted < 0 || inserted > offered) {
            throw new IllegalStateException("Invalid AdvancedAE output acceptance: " + inserted + " of " + offered);
        }
        if (type == Actionable.MODULATE && inserted < offered) {
            inventory.insert(what, offered - inserted, Actionable.MODULATE);
            cpu.markDirty();
            return offered;
        }
        return inserted;
    }

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true, remap = false)
    private void neoecoae$validatePlan(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (!ECOExternalCpuSupport.supportsPlan(plan)) cir.setReturnValue(CraftingSubmitResult.NO_CPU_FOUND);
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, remap = false)
    private void neoecoae$executeBatch(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            CallbackInfoReturnable<Integer> cir) {
        if (job instanceof ECOExternalCpuJob access && access.neoecoae$suspended()) {
            cir.setReturnValue(0);
            return;
        }
        if (job == null || level == null) return;
        try {
            int pushed = neoecoae$fastPath()
                    .execute(this, job, inventory, maxPatterns, craftingService, energyService, level);
            if (pushed > 0) cir.setReturnValue(pushed);
        } catch (RuntimeException failure) {
            if (job instanceof ECOExternalCpuJob access) access.neoecoae$suspended(true);
            cpu.markDirty();
            NEOECOAE$LOGGER.error("Suspended AdvancedAE CPU after batch dispatch failure", failure);
            cir.setReturnValue(0);
        }
    }

    @WrapOperation(
            method = "executeCrafting",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"),
            remap = false)
    private boolean neoecoae$pushPatternWithJob(
            ICraftingProvider provider,
            IPatternDetails details,
            KeyCounter[] inputHolder,
            Operation<Boolean> original) {
        if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                && job instanceof ECOExternalCpuJob access) {
            return patternBus.pushPattern(
                    details, inputHolder, access.neoecoae$link().getCraftingID());
        }
        return original.call(provider, details, inputHolder);
    }

    @Inject(method = "finishJob", at = @At("HEAD"), remap = false)
    private void neoecoae$finishWorkerOwnership(boolean success, CallbackInfo ci) {
        if (!(job instanceof ECOExternalCpuJob access)) return;
        UUID jobId = access.neoecoae$link().getCraftingID();
        if (!success) {
            ECOCraftingJobLifecycle.cancelAndRecover(cpu.getLevel(), jobId);
            return;
        }
        ECOCraftingJobLifecycle.finish(cpu.getLevel(), jobId, true);
        IGrid grid = cpu.getGrid();
        if (grid != null) {
            for (var worker : grid.getMachines(ECOCraftingWorkerBlockEntity.class)) {
                worker.releaseCompletedJobOutputs(jobId);
            }
        }
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), remap = false)
    private void neoecoae$refundCredit(IEnergyService energy, CraftingService crafting, CallbackInfo ci) {
        if (neoecoae$fastPath == null) return;
        try {
            neoecoae$fastPath.refundIdleCredit(energy);
        } catch (RuntimeException failure) {
            NEOECOAE$LOGGER.warn("AdvancedAE CPU energy refund deferred", failure);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void neoecoae$readBatchState(CompoundTag tag, CallbackInfo ci) {
        CompoundTag state = tag.getCompound("neoecoaeFastPath");
        boolean invalidLedger = false;
        try {
            neoecoae$fastPath().read(state);
        } catch (RuntimeException failure) {
            invalidLedger = true;
            NEOECOAE$LOGGER.error("Invalid AdvancedAE CPU batch ledger; job suspended", failure);
        }
        if (job instanceof ECOExternalCpuJob access) {
            access.neoecoae$suspended(access.neoecoae$suspended() || invalidLedger || state.getBoolean("suspended"));
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
    private void neoecoae$writeBatchState(CompoundTag tag, CallbackInfo ci) {
        if (neoecoae$fastPath == null && !(job instanceof ECOExternalCpuJob)) return;
        CompoundTag state = new CompoundTag();
        if (neoecoae$fastPath != null) neoecoae$fastPath.write(state);
        if (job instanceof ECOExternalCpuJob access) state.putBoolean("suspended", access.neoecoae$suspended());
        tag.put("neoecoaeFastPath", state);
    }
}
