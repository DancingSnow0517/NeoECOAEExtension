package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessDynamicOutputBridge;
import cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells.crafting.OmniCpuJobAccessor;
import cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells.crafting.OmniCpuTaskAccessor;
import cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells.crafting.OmniCpuTimeTrackerAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Adapter for Omni Cells' ordinary AE2 CPU engine. No second loop is installed over managed CPU engines. */
public final class ECOExternalCpuFastPath {
    private final ECOCraftingEnergyTransaction energy;
    private final Runnable markDirty;

    public ECOExternalCpuFastPath(Runnable markDirty) {
        this.markDirty = markDirty;
        energy = new ECOCraftingEnergyTransaction(markDirty, () -> TickHandler.instance().getCurrentTick());
    }

    public void read(CompoundTag tag) { energy.readFromNBT(tag); }
    public void write(CompoundTag tag) { energy.writeToNBT(tag); }

    public int execute(Object owner, Object job, ListCraftingInventory inventory, int maxPatterns,
            CraftingService crafting, IEnergyService power, Level level) {
        energy.returnIdleCredit(power);
        if (!(job instanceof OmniCpuJobAccessor access) || access.neoecoae$suspended() || maxPatterns < 2) return 0;
        var iterator = access.neoecoae$tasks().entrySet().iterator();
        while (iterator.hasNext()) {
            var task = iterator.next();
            var progress = (OmniCpuTaskAccessor) task.getValue();
            long limit = Math.min(maxPatterns, progress.neoecoae$value());
            if (limit < 2) continue;
            var preview = new ListCraftingInventory(ignored -> {});
            preview.list.addAll(inventory.list);
            var outputs = new KeyCounter();
            var remainders = new KeyCounter();
            var inputs = CraftingCpuHelper.extractPatternInputs(task.getKey(), preview, level, outputs, remainders);
            if (inputs == null) continue;
            for (var provider : crafting.getProviders(task.getKey())) {
                if (provider.isBusy()) continue;
                var batch = ECOFastPathFacade.prepare(provider, task.getKey(), inputs, outputs, remainders,
                    inventory, limit, CraftingCpuHelper.calculatePatternPower(inputs), power, level,
                    access.neoecoae$link().getCraftingID());
                if (batch == null || batch.craftCount() < 2) continue;
                var registration = ECOUselessDynamicOutputBridge.prepare(owner, task.getKey(), batch.craftCount());
                if (registration == null) continue;
                try {
                    if (!batch.submit(ignored -> energy.reserve(power, CraftingCpuHelper.calculatePatternPower(inputs), batch.craftCount()))) continue;
                } catch (ECOIndeterminateBatchException failure) {
                    access.neoecoae$suspended(true);
                    markDirty.run();
                    throw failure;
                }
                try {
                    progress.neoecoae$value(progress.neoecoae$value() - batch.craftCount());
                    for (var output : batch.outputs()) {
                        access.neoecoae$waitingFor().insert(output.what(), output.amount(), Actionable.MODULATE);
                    }
                    for (var remainder : batch.remainders()) {
                        access.neoecoae$waitingFor().insert(remainder.what(), remainder.amount(), Actionable.MODULATE);
                        ((OmniCpuTimeTrackerAccessor) access.neoecoae$timeTracker())
                            .neoecoae$addMaxItems(remainder.amount(), remainder.what().getType());
                    }
                    registration.commit(access.neoecoae$link().getCraftingID(),
                        access.neoecoae$finalOutput() == null ? null : access.neoecoae$finalOutput().what());
                    if (progress.neoecoae$value() <= 0) iterator.remove();
                    markDirty.run();
                    return Math.toIntExact(batch.craftCount());
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
