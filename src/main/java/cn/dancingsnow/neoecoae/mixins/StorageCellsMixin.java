package cn.dancingsnow.neoecoae.mixins;

import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StorageCells.class, remap = false)
public class StorageCellsMixin {
    @Inject(method = "getCellInventory", at = @At("RETURN"), cancellable = true)
    private static void neoecoae$getEcoCellInventory(
            ItemStack stack, ISaveProvider saveProvider, CallbackInfoReturnable<StorageCell> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(ECOStorageCells.getCellInventory(stack, saveProvider));
        }
    }
}
