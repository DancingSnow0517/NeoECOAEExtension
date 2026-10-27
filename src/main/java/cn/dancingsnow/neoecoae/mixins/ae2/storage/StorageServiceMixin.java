package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.StorageService;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.impl.storage.NetworkInventoryCache;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2 rebuilds the whole cached network inventory every tick while any watcher exists. Re-list only
 * mounts that may have changed and notify watchers for changed keys only.
 */
@Mixin(value = StorageService.class, remap = false)
public abstract class StorageServiceMixin {
    @Unique private final NetworkInventoryCache neoecoae$inventoryCache = new NetworkInventoryCache();

    @Shadow @Final private NetworkStorage storage;
    @Shadow @Final private KeyCounter cachedAvailableStacks;
    @Shadow @Final private Object2LongMap<AEKey> cachedAvailableAmounts;
    @Shadow private boolean cachedStacksNeedUpdate;

    @Shadow private void postWatcherUpdate(AEKey what, long newAmount) { throw new AssertionError(); }

    @Inject(method = "updateCachedStacks", at = @At("HEAD"), cancellable = true)
    private void neoecoae$updateChangedMounts(CallbackInfo ci) {
        // Crafting calculations may read the cached inventory from a worker thread; keep AE2's path there.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            neoecoae$inventoryCache.invalidate();
            return;
        }
        cachedStacksNeedUpdate = false;
        if (neoecoae$inventoryCache.refresh((NetworkInventoryCache.Mounts) storage, cachedAvailableStacks,
                cachedAvailableAmounts, this::postWatcherUpdate)) {
            ci.cancel();
        }
    }
}
