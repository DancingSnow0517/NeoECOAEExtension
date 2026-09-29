package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchEnergyLedger;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingExecutor;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOIndeterminateBatchException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Runs verified ECO batches through one native CPU push slot per accepted batch. */
public final class ECOExternalCpuFastPath {
    private final ECOBatchEnergyLedger energy;
    private final Runnable markDirty;

    public ECOExternalCpuFastPath(Runnable markDirty) {
        this.markDirty = markDirty;
        this.energy = new ECOBatchEnergyLedger(markDirty);
    }

    public void refundIdleCredit(IEnergyService power) {
        var before = energy.pendingRefund();
        energy.refundPending(power);
        if (before.compareTo(energy.pendingRefund()) != 0) markDirty.run();
    }

    public void read(CompoundTag tag) {
        energy.readFromNBT(tag);
    }

    public void write(CompoundTag tag) {
        energy.writeToNBT(tag);
    }

    public int execute(
            Object owner,
            Object job,
            ListCraftingInventory inventory,
            int maxPatterns,
            CraftingService crafting,
            IEnergyService power,
            Level level) {
        if (!(job instanceof ECOExternalCpuJob access) || access.neoecoae$suspended() || maxPatterns <= 0) {
            return 0;
        }
        var tasks = access.neoecoae$tasks().entrySet().iterator();
        while (tasks.hasNext()) {
            var task = tasks.next();
            if (!(task.getValue() instanceof ECOExternalCpuJob.Task progress)) continue;
            long remaining = progress.neoecoae$value();
            if (remaining < 2L) continue;

            var preview = new ListCraftingInventory(ignored -> {});
            preview.list.addAll(inventory.list);
            var outputs = new KeyCounter();
            var remainders = new KeyCounter();
            var inputs = CraftingCpuHelper.extractPatternInputs(task.getKey(), preview, level, outputs, remainders);
            if (inputs == null) continue;

            for (var provider : crafting.getProviders(task.getKey())) {
                if (!ECOBatchCraftingExecutor.canBatch(provider)
                        || provider.isBusy()
                        || provider instanceof PatternProviderLogic logic && logic.isBlocking()) continue;

                var batch = ECOBatchCraftingExecutor.prepare(
                        provider,
                        task.getKey(),
                        inputs,
                        outputs,
                        remainders,
                        inventory,
                        remaining,
                        power,
                        level,
                        access.neoecoae$link().getCraftingID());
                if (batch == null || batch.craftCount() < 2L || !fitsWaiting(access, batch)) continue;
                ECOBatchAdmission admission;
                try {
                    admission = batch.submit(inventory, power, energy);
                } catch (ECOIndeterminateBatchException unknown) {
                    access.neoecoae$suspended(true);
                    markDirty.run();
                    throw unknown;
                }
                if (energy.pendingRefund().signum() > 0) markDirty.run();
                if (admission.status() == ECOBatchAdmission.Status.INDETERMINATE) {
                    access.neoecoae$suspended(true);
                    markDirty.run();
                    throw new IllegalStateException("External CPU batch ownership is indeterminate");
                }
                if (admission.status() != ECOBatchAdmission.Status.ACCEPTED) continue;

                long accepted = admission.acceptedCrafts();
                try {
                    for (var output : batch.outputsForAccepted(accepted)) {
                        access.neoecoae$waitingFor().insert(output.what(), output.amount(), Actionable.MODULATE);
                    }
                    for (var remainder : batch.remaindersForAccepted(accepted)) {
                        access.neoecoae$waitingFor().insert(remainder.what(), remainder.amount(), Actionable.MODULATE);
                        access.neoecoae$addRemainderItems(
                                remainder.amount(), remainder.what().getType());
                    }
                    progress.neoecoae$value(remaining - accepted);
                    if (progress.neoecoae$value() <= 0L) tasks.remove();
                    markDirty.run();
                    return 1;
                } catch (RuntimeException failure) {
                    access.neoecoae$suspended(true);
                    markDirty.run();
                    throw failure;
                }
            }
        }
        return 0;
    }

    private static boolean fitsWaiting(ECOExternalCpuJob job, ECOBatchCraftingExecutor.PreparedBatch batch) {
        var totals = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
        try {
            for (var output : batch.outputs()) {
                totals.merge(output.what(), output.amount(), Math::addExact);
            }
            for (var remainder : batch.remainders()) {
                totals.merge(remainder.what(), remainder.amount(), Math::addExact);
            }
            for (var entry : totals.entrySet()) {
                Math.addExact(job.neoecoae$waitingFor().list.get(entry.getKey()), entry.getValue());
            }
            return true;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }
}
