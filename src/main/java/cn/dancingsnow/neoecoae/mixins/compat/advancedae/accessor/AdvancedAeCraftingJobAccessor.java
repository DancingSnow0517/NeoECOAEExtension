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

    @org.spongepowered.asm.mixin.Unique private boolean neoecoae$suspended;
    public boolean neoecoae$suspended(){return neoecoae$suspended;}
    public void neoecoae$suspended(boolean value){neoecoae$suspended=value;}

    @org.spongepowered.asm.mixin.injection.Inject(method="writeToNBT",at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void neoecoae$writeSuspended(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.nbt.CompoundTag> cir){
        cir.getReturnValue().putBoolean("neoecoaeSuspended",neoecoae$suspended);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob$CraftingDifferenceListener;Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;)V",at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void neoecoae$readSuspended(net.minecraft.nbt.CompoundTag data,@org.spongepowered.asm.mixin.injection.Coerce Object listener,net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic cpu,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        neoecoae$suspended=data.getBoolean("neoecoaeSuspended");
    }

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
