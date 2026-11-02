package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessDynamicOutputBridge;
import cn.dancingsnow.neoecoae.compat.ae2.ECOExternalProviderBlocking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/** Shared verified-batch dispatcher. One accepted worker batch consumes one native CPU push slot. */
public final class ECOExternalCpuFastPath {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("neoecoae");
    private final ECOCraftingEnergyTransaction energy;
    private final Runnable markDirty;
    private final ECOCraftingInputBuffer inputBuffer = new ECOCraftingInputBuffer();
    private final ECOCraftingRemainderCache remainderCache = ECOCraftingRemainderCache.shared();
    private final Object2LongOpenHashMap<AEKey> waitingTotals = new Object2LongOpenHashMap<>();
    private ListCraftingInventory previewInventory;
    private ECOCraftingInputPreview inputPreview;

    public ECOExternalCpuFastPath(Runnable markDirty) {
        this.markDirty = markDirty;
        energy = new ECOCraftingEnergyTransaction(markDirty, () -> TickHandler.instance().getCurrentTick());
    }

    private boolean fitsWaiting(ECOExternalCpuJob access, ECOFastPathFacade.PreparedBatch batch) {
        waitingTotals.clear();
        try {
            for (var output : batch.outputs()) waitingTotals.put(output.what(),
                    Math.addExact(waitingTotals.getLong(output.what()), output.amount()));
            for (var output : batch.remainders()) waitingTotals.put(output.what(),
                    Math.addExact(waitingTotals.getLong(output.what()), output.amount()));
            for (var iterator = waitingTotals.object2LongEntrySet().fastIterator(); iterator.hasNext();) {
                var entry = iterator.next();
                Math.addExact(access.neoecoae$waitingFor().list.get(entry.getKey()), entry.getLongValue());
            }
            return true;
        } catch (ArithmeticException overflow) { return false; }
    }

    public void refundIdleCredit(IEnergyService power) { energy.returnIdleCredit(power); }
    public void read(CompoundTag tag) { energy.readFromNBT(tag); }
    public void write(CompoundTag tag) { energy.writeToNBT(tag); }

    private ECOCraftingInputPreview preview(ListCraftingInventory inventory) {
        if (previewInventory != inventory) {
            previewInventory = inventory;
            inputPreview = new ECOCraftingInputPreview(inventory);
        } else {
            inputPreview.reset();
        }
        return inputPreview;
    }

    public int execute(Object owner, Object job, ListCraftingInventory inventory, int maxPatterns,
            CraftingService crafting, IEnergyService power, Level level) {
        energy.returnIdleCredit(power);
        if (!(job instanceof ECOExternalCpuJob access) || access.neoecoae$suspended() || maxPatterns <= 0) return 0;
        int dispatched = 0;
        var iterator = access.neoecoae$tasks().entrySet().iterator();
        while (iterator.hasNext() && dispatched < maxPatterns) {
            var task = iterator.next();
            var progress = (ECOExternalCpuJob.Task) task.getValue();
            long limit = progress.neoecoae$value();
            if (limit < 2) continue;
            KeyCounter[] inputs = null;
            double singlePower = 0.0;
            for (var provider : crafting.getProviders(task.getKey())) {
                var parallel = ECOParallelCraftingProviders.find(provider);
                if (!(provider instanceof ECOFastPathDispatchProvider) && parallel == null) continue;
                if (ECOExternalProviderBlocking.isEnabled(provider)) continue;
                if (provider.isBusy()) continue;
                if (inputs == null) {
                    inputs = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(
                            task.getKey(), preview(inventory), level, inputBuffer, remainderCache);
                    if (inputs == null) break;
                    singlePower = CraftingCpuHelper.calculatePatternPower(inputs);
                }
                var batch = parallel != null
                    ? ECOFastPathFacade.prepareParallel(parallel, task.getKey(), inputs,
                        inputBuffer.outputs(), inputBuffer.remainders(),
                        inventory, limit, singlePower, power, level, access.neoecoae$link().getCraftingID())
                    : ECOFastPathFacade.prepare(provider, task.getKey(), inputs,
                        inputBuffer.outputs(), inputBuffer.remainders(),
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
                    dispatched++;
                    break;
                } catch (RuntimeException failure) {
                    access.neoecoae$suspended(true);
                    markDirty.run();
                    throw failure;
                }
            }
        }
        return dispatched;
    }

}
