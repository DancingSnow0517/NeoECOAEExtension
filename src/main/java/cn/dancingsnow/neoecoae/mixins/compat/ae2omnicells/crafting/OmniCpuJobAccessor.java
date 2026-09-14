package cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells.crafting;

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
public interface OmniCpuJobAccessor {
    @Accessor("tasks") Map<IPatternDetails, Object> neoecoae$tasks();
    @Accessor("waitingFor") ListCraftingInventory neoecoae$waitingFor();
    @Accessor("timeTracker") ElapsedTimeTracker neoecoae$timeTracker();
    @Accessor("link") CraftingLink neoecoae$link();
    @Accessor("finalOutput") GenericStack neoecoae$finalOutput();
    @Accessor("suspended") boolean neoecoae$suspended();
    @Accessor("suspended") void neoecoae$suspended(boolean value);
}
