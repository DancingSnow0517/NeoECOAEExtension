package cn.dancingsnow.neoecoae.mixins.ae2;

import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public abstract class TaskProgressMixin implements ECOExternalCpuJob.Task {
    @Override
    public long neoecoae$value() {
        return ((TaskProgressAccessor) this).neoecoae$getValue();
    }

    @Override
    public void neoecoae$value(long value) {
        ((TaskProgressAccessor) this).neoecoae$setValue(value);
    }
}
