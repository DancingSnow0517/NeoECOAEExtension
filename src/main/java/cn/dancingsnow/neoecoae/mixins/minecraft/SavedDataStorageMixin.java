package cn.dancingsnow.neoecoae.mixins.minecraft;

import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves acknowledged domain writes while vanilla still owns save and shutdown scheduling. */
@Mixin(SavedDataStorage.class)
public abstract class SavedDataStorageMixin {
    @Shadow @Final private Map<SavedDataType<?>, Optional<SavedData>> cache;
    @Unique private CompletableFuture<?> neoecoae$domainWrites = CompletableFuture.completedFuture(null);

    @Inject(method = "collectDirtyTagsToSave", at = @At("HEAD"))
    private void neoecoae$saveDomains(CallbackInfoReturnable<?> cir) {
        var writes = new ArrayList<CompletableFuture<?>>();
        for (var entry : cache.values()) {
            if (entry.orElse(null) instanceof ECOInfiniteStorageData data) {
                // Serialization and dirty-state capture happen here on the server thread. Failed writes remain
                // visible to transfer barriers and are retried at the next ordinary save.
                writes.add(data.savePendingSnapshot().exceptionally(failure -> null));
                data.setDirty(false);
            }
        }
        neoecoae$domainWrites = CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Inject(method = "scheduleSave", at = @At("RETURN"), cancellable = true)
    private void neoecoae$joinDomainWrites(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        cir.setReturnValue(CompletableFuture.allOf(cir.getReturnValue(), neoecoae$domainWrites));
    }
}
