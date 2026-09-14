package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOThunderboltBatchBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retain 1.0.6's provider interface and copy limits; replace only its obsolete reflective ECO bridge. */
@Pseudo
@Mixin(targets = "com.moakiee.thunderbolt.ae2.batch.NeoEcoPatternBusBatchBridge", remap = false)
public abstract class ECOLegacyThunderboltBridgeMixin {
    @Inject(method = "capacity", at = @At("HEAD"), cancellable = true)
    private static void eco$capacity(Object provider, IPatternDetails pattern, CallbackInfoReturnable<Long> cir) {
        if (provider instanceof ECOCraftingPatternBusBlockEntity bus) {
            cir.setReturnValue(ECOThunderboltBatchBridge.capacity(bus, pattern));
        }
    }

    @Inject(method = "pushBatch", at = @At("HEAD"), cancellable = true)
    private static void eco$push(Object provider, @Coerce Object context, CallbackInfoReturnable<Long> cir) {
        if (provider instanceof ECOCraftingPatternBusBlockEntity bus) {
            cir.setReturnValue(ECOThunderboltBatchBridge.pushLegacy(bus, context));
        }
    }
}
