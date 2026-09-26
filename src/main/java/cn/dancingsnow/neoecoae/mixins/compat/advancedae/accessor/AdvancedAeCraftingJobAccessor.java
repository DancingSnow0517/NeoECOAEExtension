package cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Map;
import net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = ExecutingCraftingJob.class, remap = false)
// Concrete bridge methods must be merged into the target, not exposed as an accessor-only interface.
public abstract class AdvancedAeCraftingJobAccessor
        implements cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob {
    @Accessor("link")
    public abstract CraftingLink neoecoae$getLink();

    @Accessor("waitingFor")
    public abstract ListCraftingInventory neoecoae$getWaitingFor();

    @Accessor("tasks")
    public abstract Map<IPatternDetails, ?> neoecoae$getTasks();

    @Accessor("timeTracker")
    public abstract ElapsedTimeTracker neoecoae$getTimeTracker();

    @Accessor("suspended")
    public abstract boolean neoecoae$suspended();

    @Accessor("suspended")
    public abstract void neoecoae$suspended(boolean value);

    @Accessor("finalOutput")
    public abstract appeng.api.stacks.GenericStack neoecoae$finalOutput();

    @Override
    public Map<IPatternDetails, ?> neoecoae$tasks() { return neoecoae$getTasks(); }

    @Override
    public ListCraftingInventory neoecoae$waitingFor() { return neoecoae$getWaitingFor(); }

    @Override
    public CraftingLink neoecoae$link() { return neoecoae$getLink(); }

    @Override
    public void neoecoae$addRemainderItems(long amount, appeng.api.stacks.AEKeyType type) {
        ((AdvancedAeElapsedTimeTrackerInvoker) neoecoae$getTimeTracker()).neoecoae$addMaxItems(amount, type);
    }
}
