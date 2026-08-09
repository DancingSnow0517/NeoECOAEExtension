package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface ElapsedTimeTrackerAccessor {
    @Invoker("addMaxItems")
    void neoecoae$addMaxItems(long amount, AEKeyType keyType);
}
