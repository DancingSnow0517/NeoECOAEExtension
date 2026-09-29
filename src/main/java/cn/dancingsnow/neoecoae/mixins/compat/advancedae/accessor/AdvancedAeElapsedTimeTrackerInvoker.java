package cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor;

import appeng.api.stacks.AEKeyType;
import net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface AdvancedAeElapsedTimeTrackerInvoker {
    @Invoker("addMaxItems")
    void neoecoae$addMaxItems(long amount, AEKeyType type);
}
