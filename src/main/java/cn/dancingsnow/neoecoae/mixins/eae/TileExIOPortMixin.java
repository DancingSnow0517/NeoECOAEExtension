package cn.dancingsnow.neoecoae.mixins.eae;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import com.glodblock.github.extendedae.common.tileentities.TileExIOPort;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TileExIOPort.class)
@Pseudo
public class TileExIOPortMixin {
    @WrapOperation(method = "tickingRequest", at = @At(value = "INVOKE", target = "Lappeng/api/storage/StorageCells;getCellInventory(Lnet/minecraft/world/item/ItemStack;Lappeng/api/storage/cells/ISaveProvider;)Lappeng/api/storage/cells/StorageCell;"))
    private StorageCell wrapGetCellInventory(ItemStack is, ISaveProvider host, Operation<StorageCell> original) {
        StorageCell invOriginal = original.call(is, host);
        if (invOriginal != null) {
            return invOriginal;
        }
        return ECOStorageCells.getCellInventory(is, host);
    }
}
