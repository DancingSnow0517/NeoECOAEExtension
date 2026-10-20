package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public abstract class Ae2CpuJobAccessor
        implements cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob {
    @Accessor("tasks")
    public abstract Map<IPatternDetails, Object> neoecoae$tasks();

    @Accessor("waitingFor")
    public abstract ListCraftingInventory neoecoae$waitingFor();

    @Accessor("timeTracker")
    public abstract ElapsedTimeTracker neoecoae$timeTracker();

    @Accessor("link")
    public abstract CraftingLink neoecoae$link();

    @Accessor("finalOutput")
    public abstract GenericStack neoecoae$finalOutput();

    @Accessor("suspended")
    public abstract boolean neoecoae$suspended();

    @Accessor("suspended")
    public abstract void neoecoae$suspended(boolean value);

    @Override
    public void neoecoae$addRemainderItems(long amount, appeng.api.stacks.AEKeyType type) {
        ((Ae2CpuTimeTrackerAccessor) neoecoae$timeTracker()).neoecoae$addMaxItems(amount, type);
    }
}
