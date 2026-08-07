package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {
    @Accessor("tasks")
    Map<IPatternDetails, Object> neoecoae$getTasks();

    @Accessor("waitingFor")
    ListCraftingInventory neoecoae$getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker neoecoae$getTimeTracker();

    @Accessor("link")
    CraftingLink neoecoae$getLink();
}
