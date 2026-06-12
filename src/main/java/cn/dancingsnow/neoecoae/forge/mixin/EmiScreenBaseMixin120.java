package cn.dancingsnow.neoecoae.forge.mixin;

import cn.dancingsnow.neoecoae.compat.emi.ECOEmiScreenCompat;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.EmiScreenBase;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenBase.class, remap = false)
public abstract class EmiScreenBaseMixin120 {
    @Inject(
            method = "of(Lnet/minecraft/client/gui/screens/Screen;)Ldev/emi/emi/screen/EmiScreenBase;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private static void neoecoae$provideNativeHostBounds(Screen screen, CallbackInfoReturnable<EmiScreenBase> cir) {
        if (ECOEmiScreenCompat.shouldHideNativeHostEmi(screen)) {
            cir.setReturnValue(EmiScreenBaseAccessor120.neoecoae$create(null, Bounds.EMPTY));
        }
    }
}
