package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface Ae2CpuTimeTrackerAccessor {
    @Invoker("addMaxItems") void neoecoae$addMaxItems(long amount, AEKeyType type);
}
