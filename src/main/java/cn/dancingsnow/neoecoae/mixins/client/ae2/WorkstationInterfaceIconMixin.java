package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import cn.dancingsnow.neoecoae.client.gui.workstation.WorkstationInterfaceArtwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Icon.class, remap = false)
public abstract class WorkstationInterfaceIconMixin {
    @Inject(method = "getBlitter", at = @At("HEAD"), cancellable = true)
    private void neoecoae$workstationArtwork(CallbackInfoReturnable<Blitter> callback) {
        if (WorkstationInterfaceArtwork.active()) {
            callback.setReturnValue(WorkstationInterfaceArtwork.icon((Icon) (Object) this));
        }
    }
}
