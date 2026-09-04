package cn.dancingsnow.neoecoae.mixins.sophisticated;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOSophisticatedSourceRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.me.storage.ExternalStorageFacade$ItemHandlerFacade", remap = false)
public abstract class AE2ItemHandlerFacadeMixin {
    @Shadow @Final private IItemHandler handler;

    @Inject(method = "getAvailableStacks", at = @At("HEAD"), require = 0)
    private void neoecoae$observeSnapshot(KeyCounter out, CallbackInfo ci) {
        ECOSophisticatedSourceRegistry.observeHandler(handler);
    }

    @Inject(method = "extractExternal", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoecoae$indexedExtract(AEKey what, int amount, Actionable mode,
                                         CallbackInfoReturnable<Integer> cir) {
        ECOSophisticatedSourceRegistry.observeHandler(handler);
        if (!NEConfig.enableSophisticatedTransferOptimization || !(what instanceof AEItemKey itemKey) || amount <= 0) {
            return;
        }
        try {
            ItemStack extracted = ECOSophisticatedSourceRegistry.indexedExtract(
                handler, itemKey.toStack(amount), mode == Actionable.SIMULATE);
            if (extracted != null) {
                int count = itemKey.matches(extracted) ? Math.min(amount, extracted.getCount()) : 0;
                cir.setReturnValue(count);
            }
        } catch (RuntimeException ignored) {
            // Compatibility failures deliberately fall through to AE2's slot scan.
            ECOSophisticatedSourceRegistry.reportCompatibilityFailure();
        }
    }
}
