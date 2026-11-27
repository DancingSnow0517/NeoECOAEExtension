package cn.dancingsnow.neoecoae.mixins.ae2_pattern_disk;

import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.integration.ae2pattern.ECOPatternDiskMatrixItem;
import io.github.lounode.ae2pattern.common.block.entity.BatchAssemblerBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refuses ECO pattern disk matrices at the batch assembler's disk inventory.
 *
 * <p>The batch assembler keeps its own disk inventory rather than going through {@code DiskHostSupport}, so
 * {@link DiskHostSupportMixin} does not reach it. The reason for a filter is the same: the management terminal and
 * automation insert through {@code insertItem}. Replacing the filter is safe only while AE2 Pattern Disk sets none on
 * this inventory - today it filters the cell inventory, not this one.</p>
 */
@Mixin(value = BatchAssemblerBlockEntity.class, remap = false)
public abstract class BatchAssemblerBlockEntityMixin {
    @Shadow
    @Final
    private AppEngInternalInventory diskInv;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void neoecoae$rejectEcoMatrix(CallbackInfo ci) {
        diskInv.setFilter(ECOPatternDiskMatrixItem.AEPD_HOST_FILTER);
    }
}
