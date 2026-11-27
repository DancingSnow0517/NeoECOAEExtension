package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.parts.automation.StorageLevelEmitterPart;
import cn.dancingsnow.neoecoae.storage.StorageWatcherScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.parts.automation.StorageLevelEmitterPart$1", remap = false)
public abstract class StorageLevelWatcherMixin implements StorageWatcherScope {
    @Unique private StorageLevelEmitterPart neoecoae$emitter;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neoecoae$captureEmitter(StorageLevelEmitterPart emitter, CallbackInfo ci) {
        this.neoecoae$emitter = emitter;
    }

    @Override
    public boolean neoecoae$observes(AEKey changed) {
        return neoecoae$emitter == null || StorageWatcherScope.samePrimary(neoecoae$emitter.getConfig().getKey(0), changed);
    }
}
