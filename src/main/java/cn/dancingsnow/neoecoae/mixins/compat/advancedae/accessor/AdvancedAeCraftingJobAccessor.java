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
public interface AdvancedAeCraftingJobAccessor {
    @Accessor("link")
    CraftingLink neoecoae$getLink();

    @Accessor("waitingFor")
    ListCraftingInventory neoecoae$getWaitingFor();

    @Accessor("tasks")
    Map<IPatternDetails, ?> neoecoae$getTasks();

    @Accessor("timeTracker")
    ElapsedTimeTracker neoecoae$getTimeTracker();
}
