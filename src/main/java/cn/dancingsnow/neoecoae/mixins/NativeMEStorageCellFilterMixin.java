package cn.dancingsnow.neoecoae.mixins;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep ECO cells in ECO hosts, which understand their storage and migration semantics. */
@Mixin(
        targets = {
            "appeng.blockentity.storage.ChestBlockEntity$CellInventoryFilter",
            "appeng.blockentity.storage.DriveBlockEntity$CellValidInventoryFilter"
        })
public abstract class NativeMEStorageCellFilterMixin {
    @Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true, remap = false)
    private void neoecoae$rejectEcoCells(
            InternalInventory inventory, int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!stack.isEmpty() && stack.getItem() instanceof ECOStorageCellItem) {
            cir.setReturnValue(false);
        }
    }
}
