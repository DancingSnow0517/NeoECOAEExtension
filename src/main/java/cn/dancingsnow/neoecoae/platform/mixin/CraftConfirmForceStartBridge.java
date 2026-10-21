package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public interface CraftConfirmForceStartBridge {
    void neoecoae$submitWithForceIntent(CallbackInfo callback);
}
