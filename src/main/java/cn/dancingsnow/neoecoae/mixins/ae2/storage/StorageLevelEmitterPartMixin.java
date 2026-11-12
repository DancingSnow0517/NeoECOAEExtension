package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartItem;
import appeng.core.definitions.AEItems;
import appeng.parts.automation.AbstractLevelEmitterPart;
import appeng.parts.automation.StorageLevelEmitterPart;
import cn.dancingsnow.neoecoae.storage.StorageThresholdCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StorageLevelEmitterPart.class)
public abstract class StorageLevelEmitterPartMixin extends AbstractLevelEmitterPart {
    @Unique private StorageThresholdCache.Query neoecoae$query;

    protected StorageLevelEmitterPartMixin(IPartItem<?> item) { super(item); }

    @Inject(method = "updateReportingValue", at = @At("HEAD"), cancellable = true)
    private void neoecoae$sharedThreshold(IGrid grid, CallbackInfo ci) {
        if (!(grid.getStorageService() instanceof StorageThresholdCache.Source source)) return;
        var filter = ((StorageLevelEmitterPart) (Object) this).getConfig().getKey(0);
        boolean fuzzy = isUpgradedWith(AEItems.FUZZY_CARD);
        if (filter != null && !fuzzy) return; // Exact observers already use a single counter lookup.
        var mode = filter == null ? null : getConfigManager().getSetting(Settings.FUZZY_MODE);
        long threshold = getReportingValue();
        if (neoecoae$query == null || neoecoae$query.filter() != filter || neoecoae$query.fuzzy() != mode
                || neoecoae$query.threshold() != threshold) {
            neoecoae$query = new StorageThresholdCache.Query(filter, mode, threshold);
        }
        // The getter may refresh the inventory and invalidate queries. Obtain it before querying the cache.
        var inventory = grid.getStorageService().getCachedInventory();
        lastReportedValue = source.neoecoae$thresholdCache().amount(inventory, neoecoae$query);
        updateState();
        ci.cancel();
    }
}
