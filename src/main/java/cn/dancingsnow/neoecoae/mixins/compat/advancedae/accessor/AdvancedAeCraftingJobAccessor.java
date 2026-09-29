package cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob;
import java.util.Map;
import net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = ExecutingCraftingJob.class, remap = false)
public abstract class AdvancedAeCraftingJobAccessor implements ECOExternalCpuJob {
    @Unique private boolean neoecoae$suspended;

    @Accessor("link")
    public abstract CraftingLink neoecoae$link();

    @Accessor("waitingFor")
    public abstract ListCraftingInventory neoecoae$waitingFor();

    @Accessor("tasks")
    public abstract Map<IPatternDetails, ?> neoecoae$tasks();

    @Accessor("timeTracker")
    public abstract ElapsedTimeTracker neoecoae$getTimeTracker();

    @Accessor("finalOutput")
    public abstract GenericStack neoecoae$finalOutput();

    @Override
    public boolean neoecoae$suspended() {
        return neoecoae$suspended;
    }

    @Override
    public void neoecoae$suspended(boolean value) {
        neoecoae$suspended = value;
    }

    @Override
    public void neoecoae$addRemainderItems(long amount, AEKeyType type) {
        ((AdvancedAeElapsedTimeTrackerInvoker) neoecoae$getTimeTracker()).neoecoae$addMaxItems(amount, type);
    }
}
