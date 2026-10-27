package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.api.storage.INativeMEStorageRestrictedItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enforces the public native ME storage restriction contract for ECO and addon Items. */
@Mixin(targets = {
        "appeng.blockentity.storage.MEChestBlockEntity$CellInventoryFilter",
        "appeng.blockentity.storage.DriveBlockEntity$CellValidInventoryFilter"
})
public abstract class NativeMEStorageCellFilterMixin {
    @Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true)
    private void neoecoae$rejectEcoStorageCells(InternalInventory inventory, int slot, ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (!stack.isEmpty() && stack.getItem() instanceof INativeMEStorageRestrictedItem item
                && item.isNativeMEStorageRestricted(stack)) {
            cir.setReturnValue(false);
        }
    }
}
