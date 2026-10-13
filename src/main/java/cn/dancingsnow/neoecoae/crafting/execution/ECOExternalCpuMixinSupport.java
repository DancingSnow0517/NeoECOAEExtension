package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/** Shared bodies of the native and AdvancedAE CPU mixins; receiver-specific values are passed in. */
public final class ECOExternalCpuMixinSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");

    private ECOExternalCpuMixinSupport() {}

    /**
     * Runs the batch dispatcher. On failure it suspends the job, logs, and sets the callback to 0;
     * returns true so the caller can mark its own CPU dirty.
     */
    public static boolean dispatch(Object owner, ECOExternalCpuFastPath fastPath, Object job,
            ListCraftingInventory inventory, int maxPatterns, CraftingService crafting, IEnergyService energy,
            Level level, CallbackInfoReturnable<Integer> cir, String label) {
        try {
            int consumed = fastPath.execute(owner, job, inventory, maxPatterns, crafting, energy, level);
            if (consumed > 0) cir.setReturnValue(consumed);
            return false;
        } catch (RuntimeException failure) {
            if (job instanceof ECOExternalCpuJob access) access.neoecoae$suspended(true);
            LOGGER.error("Suspended {} CPU after batch dispatch failure", label, failure);
            cir.setReturnValue(0);
            return true;
        }
    }

    /** Returns unused batch energy credit, if the dispatcher was ever created. */
    public static void refundIdleCredit(ECOExternalCpuFastPath fastPath, IEnergyService energy) {
        if (fastPath != null) fastPath.refundIdleCredit(energy);
    }

    /** Rejects plans that ECO's runtime rules do not allow on a native CPU. */
    public static void validatePlan(ICraftingPlan plan, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (!ECOExternalCpuSupport.supportsPlan(plan)) cir.setReturnValue(CraftingSubmitResult.NO_CPU_FOUND);
    }

    /** Cancels and recovers the ECO worker job on failure, or releases its completed outputs on success. */
    public static void releaseOnFinish(Object job, boolean success, Supplier<Level> level, Supplier<IGrid> grid) {
        if (!(job instanceof ECOExternalCpuJob access)) return;
        var craftingJobId = access.neoecoae$link().getCraftingID();
        if (!success) {
            ECOCraftingJobLifecycle.cancelAndRecover(level.get(), craftingJobId);
            return;
        }
        var craftingGrid = grid.get();
        if (craftingGrid == null) return;
        for (var worker : craftingGrid.getMachines(ECOCraftingWorkerBlockEntity.class))
            worker.releaseCompletedJobOutputs(craftingJobId);
    }

    /** Loads the persisted batch-energy ledger. */
    public static void readFastPath(ECOExternalCpuFastPath fastPath, CompoundTag tag) {
        fastPath.read(tag.getCompound("neoecoaeFastPath"));
    }

    /** Saves the batch-energy ledger, skipping it when the dispatcher was never created. */
    public static void writeFastPath(ECOExternalCpuFastPath fastPath, CompoundTag tag) {
        if (fastPath == null) return;
        var ledger = new CompoundTag();
        fastPath.write(ledger);
        tag.put("neoecoaeFastPath", ledger);
    }
}
