package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOIOPortInputFilter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.blockentity.OverloadedIOPortBlockEntity", remap = false)
public class Ae2LtIOPortBlockEntityMixin {
    // The first filter belongs to the input queue; filter components and throughput matrices keep theirs.
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lappeng/util/inv/AppEngInternalInventory;setFilter(Lappeng/util/inv/filter/IAEItemFilter;)V", ordinal = 0), index = 0)
    private IAEItemFilter neoecoae$acceptEcoCells(IAEItemFilter original) {
        return new ECOIOPortInputFilter(original);
    }

    @WrapOperation(method = "tickingRequest", at = @At(value = "INVOKE", target = "Lappeng/api/storage/StorageCells;getCellInventory(Lnet/minecraft/world/item/ItemStack;Lappeng/api/storage/cells/ISaveProvider;)Lappeng/api/storage/cells/StorageCell;"))
    private StorageCell neoecoae$getCellInventory(ItemStack stack, ISaveProvider host, Operation<StorageCell> original) {
        StorageCell cell = original.call(stack, host);
        return cell != null ? cell : ECOStorageCells.getCellInventory(stack, host);
    }
}
