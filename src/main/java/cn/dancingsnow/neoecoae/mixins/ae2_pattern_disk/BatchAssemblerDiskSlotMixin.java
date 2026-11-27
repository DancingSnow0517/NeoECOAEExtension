package cn.dancingsnow.neoecoae.mixins.ae2_pattern_disk;

import cn.dancingsnow.neoecoae.integration.ae2pattern.ECOPatternDiskMatrixItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps ECO pattern disk matrices out of the disk slots of AE2 Pattern Disk's batch assembler screen.
 *
 * <p>Named by string because the slot class is private. Like the provider's, it overrides {@code mayPlace} without
 * asking its inventory, so {@link BatchAssemblerBlockEntityMixin}'s filter does not cover clicks here.</p>
 */
@Mixin(targets = "io.github.lounode.ae2pattern.common.menu.assembler.BatchAssemblerMenu$DiskSlot", remap = false)
public abstract class BatchAssemblerDiskSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void neoecoae$rejectEcoMatrix(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ECOPatternDiskMatrixItem.isRestrictedToEco(stack)) {
            cir.setReturnValue(false);
        }
    }
}
