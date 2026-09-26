package cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress", remap = false)
public abstract class AdvancedAeTaskProgressAccessor
        implements cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob.Task {
    @Accessor("value")
    public abstract long neoecoae$getValue();

    @Accessor("value")
    public abstract void neoecoae$setValue(long value);

    @Override
    public long neoecoae$value() { return neoecoae$getValue(); }

    @Override
    public void neoecoae$value(long value) { neoecoae$setValue(value); }
}
