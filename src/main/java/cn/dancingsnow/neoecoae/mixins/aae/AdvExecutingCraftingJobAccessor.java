package cn.dancingsnow.neoecoae.mixins.aae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Map;
import net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob", remap = false)
public interface AdvExecutingCraftingJobAccessor {
    @Accessor("tasks")
    Map<IPatternDetails, Object> neoecoae$getTasks();

    @Accessor("waitingFor")
    ListCraftingInventory neoecoae$getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker neoecoae$getTimeTracker();

    @Accessor("link")
    CraftingLink neoecoae$getLink();

    @Accessor("finalOutput")
    GenericStack neoecoae$getFinalOutput();

    @Accessor("remainingAmount")
    long neoecoae$getRemainingAmount();
}
