package cn.dancingsnow.neoecoae.mixins;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOExactInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin {
    /** Multiple finite mounts must not turn the compatibility projection negative. */
    @Inject(method = "getAvailableStacks", at = @At("RETURN"))
    private void neoecoae$saturateExactAmounts(KeyCounter out, CallbackInfo ci) {
        ECOExactInventory.hugeAmounts((MEStorage) (Object) this).keySet().forEach(key -> out.set(key, Long.MAX_VALUE));
    }
}
