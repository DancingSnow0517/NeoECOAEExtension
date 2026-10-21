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
public abstract class Ae2CpuJobAccessor
        implements cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob {
    @Accessor("tasks")
    public abstract Map<IPatternDetails, Object> neoecoae$tasks();

    @Accessor("waitingFor")
    public abstract ListCraftingInventory neoecoae$waitingFor();

    @Accessor("timeTracker")
    public abstract ElapsedTimeTracker neoecoae$timeTracker();

    @Accessor("link")
    public abstract CraftingLink neoecoae$link();

    @Accessor("finalOutput")
    public abstract GenericStack neoecoae$finalOutput();

    @org.spongepowered.asm.mixin.Unique private boolean neoecoae$suspended;
    public boolean neoecoae$suspended(){return neoecoae$suspended;}
    public void neoecoae$suspended(boolean value){neoecoae$suspended=value;}

    @org.spongepowered.asm.mixin.injection.Inject(method="writeToNBT",at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void neoecoae$writeSuspended(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.nbt.CompoundTag> cir){
        cir.getReturnValue().putBoolean("neoecoaeSuspended",neoecoae$suspended);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="<init>(Lnet/minecraft/nbt/CompoundTag;Lappeng/crafting/execution/ExecutingCraftingJob$CraftingDifferenceListener;Lappeng/crafting/execution/CraftingCpuLogic;)V",at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void neoecoae$readSuspended(net.minecraft.nbt.CompoundTag data,@org.spongepowered.asm.mixin.injection.Coerce Object listener,appeng.crafting.execution.CraftingCpuLogic cpu,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        neoecoae$suspended=data.getBoolean("neoecoaeSuspended");
    }

    @Override
    public void neoecoae$addRemainderItems(long amount, appeng.api.stacks.AEKeyType type) {
        ((Ae2CpuTimeTrackerAccessor) neoecoae$timeTracker()).neoecoae$addMaxItems(amount, type);
    }
}
