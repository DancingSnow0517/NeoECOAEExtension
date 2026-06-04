package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.storage.IOPortBlockEntity;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = IOPortBlockEntity.class, remap = false)
public class IOPortBlockEntityMixin120 {
    @WrapOperation(
            method = "tickingRequest",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/storage/StorageCells;getCellInventory(Lnet/minecraft/world/item/ItemStack;Lappeng/api/storage/cells/ISaveProvider;)Lappeng/api/storage/cells/StorageCell;"))
    private StorageCell neoecoae$getEcoCellInventory(
            ItemStack stack, ISaveProvider saveProvider, Operation<StorageCell> original) {
        StorageCell aeCell = original.call(stack, saveProvider);
        return aeCell != null ? aeCell : ECOStorageCells.getCellInventory(stack, saveProvider);
    }
}
