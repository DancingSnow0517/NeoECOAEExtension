package cn.dancingsnow.neoecoae.mixins.advanced_ae;

import appeng.api.stacks.AEKeyType;
import net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface NeoECOAEElapsedTimeTrackerInvoker {
    @Invoker("addMaxItems")
    void neoecoae$addMaxItems(long amount, AEKeyType type);
}
