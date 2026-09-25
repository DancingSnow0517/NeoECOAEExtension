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
public interface AdvancedAeCraftingJobAccessor extends cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob {
    @Accessor("link")
    CraftingLink neoecoae$getLink();

    @Accessor("waitingFor")
    ListCraftingInventory neoecoae$getWaitingFor();

    @Accessor("tasks")
    Map<IPatternDetails, ?> neoecoae$getTasks();

    @Accessor("timeTracker")
    ElapsedTimeTracker neoecoae$getTimeTracker();
    @Accessor("suspended") boolean neoecoae$suspended();
    @Accessor("suspended") void neoecoae$suspended(boolean value);
    @Accessor("finalOutput") appeng.api.stacks.GenericStack neoecoae$finalOutput();
    default Map<IPatternDetails, ?> neoecoae$tasks() { return neoecoae$getTasks(); }
    default ListCraftingInventory neoecoae$waitingFor() { return neoecoae$getWaitingFor(); }
    default CraftingLink neoecoae$link() { return neoecoae$getLink(); }
    default void neoecoae$addRemainderItems(long amount, appeng.api.stacks.AEKeyType type) {
        ((AdvancedAeElapsedTimeTrackerInvoker) neoecoae$getTimeTracker()).neoecoae$addMaxItems(amount, type);
    }
}
