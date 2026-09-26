package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.inventories.ISegmentedInventory;
import appeng.blockentity.misc.CellWorkbenchBlockEntity;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaLongBulkStorageCellItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CellWorkbenchBlockEntity.class)
public class CellWorkbenchBlockEntityMixin {
    @Shadow
    private ConfigInventory cacheConfig;

    @Inject(method = "getCellConfigInventory", at = @At("HEAD"))
    private void neoecoae$refreshBulkCapacity(CallbackInfoReturnable<ConfigInventory> cir) {
        var workbench = (CellWorkbenchBlockEntity) (Object) this;
        var cells = workbench.getSubInventory(ISegmentedInventory.CELLS);
        if (cacheConfig != null && cells != null) {
            var stack = cells.getStackInSlot(0);
            if (stack.getItem() instanceof ECOMegaLongBulkStorageCellItem item) {
                var current = item.getConfigInventory(stack);
                // AE2 caches the config while the cell stays inserted. Upgrade changes
                // must take effect before the workbench copies edits back into the cell.
                if (cacheConfig.size() != current.size()) {
                    cacheConfig = current;
                    // Removing the card compacts remaining markers into the first page.
                    // Refresh the workbench copy before it can write the old positions back.
                    CellWorkbenchBlockEntity.copy(current, workbench.getConfig());
                }
            }
        }
    }
}
