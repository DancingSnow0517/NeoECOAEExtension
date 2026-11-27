package cn.dancingsnow.neoecoae.mixins.ae2_pattern_disk;

import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.integration.ae2pattern.ECOPatternDiskMatrixItem;
import io.github.lounode.ae2pattern.common.logic.DiskHostSupport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refuses ECO pattern disk matrices at the disk inventory of every AE2 Pattern Disk provider.
 *
 * <p>{@code DiskHostSupport} backs the provider block, its cable part and the AE2 Lightning Tech providers. Its
 * inventory is built without a filter, and the paths that move disks without a screen - the management terminal's
 * store action, automation - go through {@code insertItem}, which asks the filter. Setting one here is what keeps a
 * matrix out of those paths; the provider screen's own slot is handled by {@link PatternDiskProviderDiskSlotMixin}.</p>
 *
 * <p>This replaces the filter outright, which is only safe because AE2 Pattern Disk sets none on this inventory.
 * If it starts to, this has to wrap that filter instead.</p>
 */
@Mixin(value = DiskHostSupport.class, remap = false)
public abstract class DiskHostSupportMixin {
    @Shadow
    @Final
    private AppEngInternalInventory diskInventory;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void neoecoae$rejectEcoMatrix(CallbackInfo ci) {
        diskInventory.setFilter(ECOPatternDiskMatrixItem.AEPD_HOST_FILTER);
    }
}
