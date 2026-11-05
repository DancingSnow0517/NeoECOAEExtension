package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.parts.automation.StorageLevelEmitterPart;
import cn.dancingsnow.neoecoae.storage.StorageWatcherScope;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "appeng.parts.automation.StorageLevelEmitterPart$1")
public abstract class StorageLevelWatcherMixin implements StorageWatcherScope {
    @Shadow @Final private StorageLevelEmitterPart this$0;

    @Override
    public boolean neoecoae$observes(AEKey changed) {
        return StorageWatcherScope.samePrimary(this$0.getConfig().getKey(0), changed);
    }
}
