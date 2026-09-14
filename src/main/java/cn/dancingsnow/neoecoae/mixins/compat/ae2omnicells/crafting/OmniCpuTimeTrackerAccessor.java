package cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells.crafting;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface OmniCpuTimeTrackerAccessor {
    @Invoker("addMaxItems") void neoecoae$addMaxItems(long amount, AEKeyType type);
}
