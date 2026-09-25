package cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress", remap = false)
public interface AdvancedAeTaskProgressAccessor extends cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob.Task {
    @Accessor("value")
    long neoecoae$getValue();

    @Accessor("value")
    void neoecoae$setValue(long value);
    default long neoecoae$value() { return neoecoae$getValue(); }
    default void neoecoae$value(long value) { neoecoae$setValue(value); }
}
