package cn.dancingsnow.neoecoae.mixins;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.EmiScreenBase;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = EmiScreenBase.class, remap = false)
public interface EmiScreenBaseAccessor {
    @Invoker("<init>")
    static EmiScreenBase neoecoae$create(Screen screen, Bounds bounds) {
        throw new AssertionError();
    }
}
