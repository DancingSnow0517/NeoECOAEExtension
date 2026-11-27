package cn.dancingsnow.neoecoae.mixins.ae2_pattern_disk;

import cn.dancingsnow.neoecoae.integration.ae2pattern.ECOPatternDiskMatrixItem;
import io.github.lounode.ae2pattern.common.menu.pattern.PatternDiskProviderMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps ECO pattern disk matrices out of the disk slots of AE2 Pattern Disk's provider screen.
 *
 * <p>The slot overrides {@code mayPlace} without asking its inventory, so the filter
 * {@link DiskHostSupportMixin} installs never reaches a click or a shift-move here; this covers that path, on both
 * logical sides.</p>
 */
@Mixin(value = PatternDiskProviderMenu.DiskSlot.class, remap = false)
public abstract class PatternDiskProviderDiskSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void neoecoae$rejectEcoMatrix(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ECOPatternDiskMatrixItem.isRestrictedToEco(stack)) {
            cir.setReturnValue(false);
        }
    }
}
