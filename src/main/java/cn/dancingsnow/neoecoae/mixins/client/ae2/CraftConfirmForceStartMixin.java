package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import cn.dancingsnow.neoecoae.platform.mixin.CraftConfirmForceStartBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source order=500 sends the combined force intent before normal addon start handlers. */
@Mixin(value = CraftConfirmScreen.class, priority = 1500, remap = false)
public abstract class CraftConfirmForceStartMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void neoecoae$startEarly(CallbackInfo callback) {
        ((CraftConfirmForceStartBridge) this).neoecoae$submitWithForceIntent(callback);
    }
}
