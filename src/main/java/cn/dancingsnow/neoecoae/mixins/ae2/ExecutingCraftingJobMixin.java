package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public abstract class ExecutingCraftingJobMixin implements ECOExternalCpuJob {
    @Unique private boolean neoecoae$suspended;

    private ExecutingCraftingJobAccessor neoecoae$access() {
        return (ExecutingCraftingJobAccessor) this;
    }

    @Override
    public Map<IPatternDetails, ?> neoecoae$tasks() {
        return neoecoae$access().neoecoae$getTasks();
    }

    @Override
    public ListCraftingInventory neoecoae$waitingFor() {
        return neoecoae$access().neoecoae$getWaitingFor();
    }

    @Override
    public CraftingLink neoecoae$link() {
        return neoecoae$access().neoecoae$getLink();
    }

    @Override
    public GenericStack neoecoae$finalOutput() {
        return neoecoae$access().neoecoae$getFinalOutput();
    }

    @Override
    public void neoecoae$addRemainderItems(long amount, AEKeyType type) {
        ((ElapsedTimeTrackerAccessor) neoecoae$access().neoecoae$getTimeTracker()).neoecoae$addMaxItems(amount, type);
    }

    @Override
    public boolean neoecoae$suspended() {
        return neoecoae$suspended;
    }

    @Override
    public void neoecoae$suspended(boolean value) {
        neoecoae$suspended = value;
    }
}
