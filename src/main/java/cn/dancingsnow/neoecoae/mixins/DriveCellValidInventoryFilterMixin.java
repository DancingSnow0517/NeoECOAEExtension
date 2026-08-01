package cn.dancingsnow.neoecoae.mixins;

import appeng.api.storage.StorageCells;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "appeng.blockentity.storage.DriveBlockEntity$CellValidInventoryFilter")
public class DriveCellValidInventoryFilterMixin {
    @WrapOperation(
        method = "allowInsert",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/StorageCells;isCellHandled(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private boolean disallowECOCellsInAE2Drive(
        ItemStack stack,
        Operation<Boolean> original
    ) {
        if (ECOStorageCells.isCellHandled(stack)) {
            return false;
        }
        return original.call(stack);
    }
}
