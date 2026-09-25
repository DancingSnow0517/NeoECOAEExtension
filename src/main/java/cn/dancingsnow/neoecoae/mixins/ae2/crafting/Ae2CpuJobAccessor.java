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
public interface Ae2CpuJobAccessor extends cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob {
    @Accessor("tasks") Map<IPatternDetails, Object> neoecoae$tasks();
    @Accessor("waitingFor") ListCraftingInventory neoecoae$waitingFor();
    @Accessor("timeTracker") ElapsedTimeTracker neoecoae$timeTracker();
    @Accessor("link") CraftingLink neoecoae$link();
    @Accessor("finalOutput") GenericStack neoecoae$finalOutput();
    @Accessor("suspended") boolean neoecoae$suspended();
    @Accessor("suspended") void neoecoae$suspended(boolean value);
    default void neoecoae$addRemainderItems(long amount, appeng.api.stacks.AEKeyType type) {
        ((Ae2CpuTimeTrackerAccessor) neoecoae$timeTracker()).neoecoae$addMaxItems(amount, type);
    }
}
