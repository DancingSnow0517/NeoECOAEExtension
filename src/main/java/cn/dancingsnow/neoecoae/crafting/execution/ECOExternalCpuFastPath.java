package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessDynamicOutputBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Shared verified-batch dispatcher. One accepted worker batch consumes one native CPU push slot. */
public final class ECOExternalCpuFastPath {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("neoecoae");
    private final ECOCraftingEnergyTransaction energy;
    private final Runnable markDirty;

    public ECOExternalCpuFastPath(Runnable markDirty) {
        this.markDirty = markDirty;
        energy = new ECOCraftingEnergyTransaction(markDirty, () -> TickHandler.instance().getCurrentTick());
    }

    private static boolean fitsWaiting(ECOExternalCpuJob access, ECOFastPathFacade.PreparedBatch batch) {
        var totals = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
        try {
            for (var output : batch.outputs()) totals.merge(output.what(), output.amount(), Math::addExact);
            for (var output : batch.remainders()) totals.merge(output.what(), output.amount(), Math::addExact);
            for (var entry : totals.entrySet()) Math.addExact(
                    access.neoecoae$waitingFor().list.get(entry.getKey()), entry.getValue());
            return true;
        } catch (ArithmeticException overflow) { return false; }
    }

    public void refundIdleCredit(IEnergyService power) { energy.returnIdleCredit(power); }
    public void read(CompoundTag tag) { energy.readFromNBT(tag); }
    public void write(CompoundTag tag) { energy.writeToNBT(tag); }

    /** Native ECO workers and workstation queues own atomic batch admission. */
    private static boolean isEcoFastPathProvider(Object provider) {
        return provider instanceof ECOFastPathDispatchProvider
            || provider instanceof ECOParallelCraftingProvider;
    }

    public int execute(Object owner, Object job, ListCraftingInventory inventory, int maxPatterns,
            CraftingService crafting, IEnergyService power, Level level) {
        energy.returnIdleCredit(power);
        if (!(job instanceof ECOExternalCpuJob access) || access.neoecoae$suspended() || maxPatterns <= 0) return 0;
        var iterator = access.neoecoae$tasks().entrySet().iterator();
        while (iterator.hasNext()) {
            var task = iterator.next();
            var progress = (ECOExternalCpuJob.Task) task.getValue();
            long limit = progress.neoecoae$value();
            if (limit < 2) continue;
            var preview = new ListCraftingInventory(ignored -> {});
            preview.list.addAll(inventory.list);
            var outputs = new KeyCounter();
            var remainders = new KeyCounter();
            var inputs = CraftingCpuHelper.extractPatternInputs(task.getKey(), preview, level, outputs, remainders);
            if (inputs == null) continue;
            for (var provider : crafting.getProviders(task.getKey())) {
                if (!isEcoFastPathProvider(provider)) continue;
                if (provider.isBusy()) continue;
                double singlePower = CraftingCpuHelper.calculatePatternPower(inputs);
                var batch = provider instanceof ECOParallelCraftingProvider parallel
                    ? ECOFastPathFacade.prepareParallel(parallel, task.getKey(), inputs, outputs, remainders,
                        inventory, limit, singlePower, power, level, access.neoecoae$link().getCraftingID())
                    : ECOFastPathFacade.prepare(provider, task.getKey(), inputs, outputs, remainders,
                        inventory, limit, singlePower, power, level, access.neoecoae$link().getCraftingID());
                if (batch == null || batch.craftCount() < 2 || !fitsWaiting(access, batch)) continue;
                var registration = ECOUselessDynamicOutputBridge.prepare(owner, task.getKey(), batch.craftCount());
                if (registration == null) continue;
                try {
                    if (!batch.submit(amount -> energy.reserve(power, amount))) continue;
                } catch (ECOIndeterminateBatchException failure) {
                    access.neoecoae$suspended(true);
                    markDirty.run();
                    throw failure;
                } catch (RuntimeException rejected) {
                    LOGGER.warn("External CPU batch rejected; resources restored", rejected);
                    continue;
                }
                try {
                    progress.neoecoae$value(progress.neoecoae$value() - batch.craftCount());
                    for (var output : batch.outputs()) {
                        access.neoecoae$waitingFor().insert(output.what(), output.amount(), Actionable.MODULATE);
                    }
                    for (var remainder : batch.remainders()) {
                        access.neoecoae$waitingFor().insert(remainder.what(), remainder.amount(), Actionable.MODULATE);
                        access.neoecoae$addRemainderItems(remainder.amount(), remainder.what().getType());
                    }
                    registration.commit(access.neoecoae$link().getCraftingID(),
                        access.neoecoae$finalOutput() == null ? null : access.neoecoae$finalOutput().what());
                    if (progress.neoecoae$value() <= 0) iterator.remove();
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

}
