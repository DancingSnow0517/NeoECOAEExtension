package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class Ae2CraftingCpuFastPathMixin implements ECOJobOutputReceiver {
    @Unique private static final Logger NEOECOAE_LOGGER = LoggerFactory.getLogger("neoecoae");

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Shadow
    private ExecutingCraftingJob job;

    @Unique private ECOExternalCpuFastPath neoecoae$fastPath;

    @Unique private ECOExternalCpuFastPath neoecoae$fastPath() {
        if (neoecoae$fastPath == null) neoecoae$fastPath = new ECOExternalCpuFastPath(cluster::markDirty);
        return neoecoae$fastPath;
    }

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void neoecoae$validatePlan(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (!ECOExternalCpuSupport.supportsPlan(plan)) cir.setReturnValue(CraftingSubmitResult.NO_CPU_FOUND);
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void neoecoae$dispatch(
            int maxPatterns,
            CraftingService crafting,
            IEnergyService power,
            Level level,
            CallbackInfoReturnable<Integer> cir) {
        if (!(job instanceof ECOExternalCpuJob access)) return;
        if (access.neoecoae$suspended()) {
            cir.setReturnValue(0);
            return;
        }
        try {
            int pushed = neoecoae$fastPath().execute(this, job, inventory, maxPatterns, crafting, power, level);
            if (pushed > 0) cir.setReturnValue(pushed);
        } catch (RuntimeException failure) {
            access.neoecoae$suspended(true);
            cluster.markDirty();
            NEOECOAE_LOGGER.error("Suspended AE2 CPU after batch dispatch failure", failure);
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void neoecoae$refundCredit(IEnergyService power, CraftingService crafting, CallbackInfo ci) {
        if (job instanceof ECOExternalCpuJob access
                && ECOCraftingJobLifecycle.isTerminated(
                        cluster.getLevel(), access.neoecoae$link().getCraftingID())) {
            ((CraftingCpuLogic) (Object) this).cancel();
        }
        if (neoecoae$fastPath == null) return;
        try {
            neoecoae$fastPath.refundIdleCredit(power);
        } catch (RuntimeException failure) {
            NEOECOAE_LOGGER.warn("AE2 CPU energy refund deferred", failure);
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void neoecoae$finishWorkerOwnership(boolean success, CallbackInfo ci) {
        if (!(job instanceof ECOExternalCpuJob access)) return;
        UUID jobId = access.neoecoae$link().getCraftingID();
        if (!success) {
            ECOCraftingJobLifecycle.cancelAndRecover(cluster.getLevel(), jobId);
            return;
        }
        ECOCraftingJobLifecycle.finish(cluster.getLevel(), jobId, true);
        IGrid grid = cluster.getGrid();
        if (grid == null) return;
        for (var worker : grid.getMachines(ECOCraftingWorkerBlockEntity.class)) {
            worker.releaseCompletedJobOutputs(jobId);
        }
    }

    @WrapOperation(
            method = "executeCrafting",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean neoecoae$pushWithJob(
            appeng.api.networking.crafting.ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] inputs,
            Operation<Boolean> original) {
        if (provider instanceof ECOCraftingPatternBusBlockEntity bus && job instanceof ECOExternalCpuJob access) {
            return bus.pushPattern(pattern, inputs, access.neoecoae$link().getCraftingID());
        }
        return original.call(provider, pattern, inputs);
    }

    @Override
    public long neoecoae$insertWorkerOutput(UUID jobId, AEKey what, long amount, Actionable type) {
        if (jobId == null
                || what == null
                || amount <= 0L
                || !(job instanceof ECOExternalCpuJob access)
                || !jobId.equals(access.neoecoae$link().getCraftingID())
                || ECOCraftingJobLifecycle.isTerminated(cluster.getLevel(), jobId)) return 0L;
        long offered = Math.min(amount, ((CraftingCpuLogic) (Object) this).getWaitingFor(what));
        if (offered <= 0L) return 0L;
        long inserted = ((CraftingCpuLogic) (Object) this).insert(what, offered, type);
        if (type == Actionable.MODULATE && inserted < offered) {
            inventory.insert(what, offered - inserted, Actionable.MODULATE);
            cluster.markDirty();
            return offered;
        }
        return inserted;
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void neoecoae$read(CompoundTag data, CallbackInfo ci) {
        CompoundTag saved = data.getCompound("neoecoaeFastPath");
        boolean invalidLedger = false;
        try {
            neoecoae$fastPath().read(saved);
        } catch (RuntimeException failure) {
            invalidLedger = true;
            NEOECOAE_LOGGER.error("Invalid AE2 CPU batch ledger; job suspended", failure);
        }
        if (job instanceof ECOExternalCpuJob access) {
            access.neoecoae$suspended(invalidLedger || saved.getBoolean("suspended"));
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void neoecoae$write(CompoundTag data, CallbackInfo ci) {
        if (neoecoae$fastPath == null && job == null) return;
        var saved = new CompoundTag();
        if (neoecoae$fastPath != null) neoecoae$fastPath.write(saved);
        if (job instanceof ECOExternalCpuJob access) saved.putBoolean("suspended", access.neoecoae$suspended());
        data.put("neoecoaeFastPath", saved);
    }
}
