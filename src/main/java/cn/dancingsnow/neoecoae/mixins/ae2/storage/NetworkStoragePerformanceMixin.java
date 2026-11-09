package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.storage.StoragePerformance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Installed only when diagnostics are explicitly enabled before Forge starts. */
@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStoragePerformanceMixin {
    @Inject(method = "insert", at = @At("HEAD"))
    private void neoecoae$countInsert(CallbackInfoReturnable<Long> cir) {
        StoragePerformance.count(StoragePerformance.Counter.NETWORK_INSERT);
    }

    @Inject(method = "extract", at = @At("HEAD"))
    private void neoecoae$countExtract(CallbackInfoReturnable<Long> cir) {
        StoragePerformance.count(StoragePerformance.Counter.NETWORK_EXTRACT);
    }

    @Inject(method = "insert", at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"))
    private void neoecoae$countInsertAttempt(CallbackInfoReturnable<Long> cir) {
        StoragePerformance.count(StoragePerformance.Counter.MOUNT_INSERT);
    }

    @Inject(method = "extract", at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"))
    private void neoecoae$countExtractAttempt(CallbackInfoReturnable<Long> cir) {
        StoragePerformance.count(StoragePerformance.Counter.MOUNT_EXTRACT);
    }
}
