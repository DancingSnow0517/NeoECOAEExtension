package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.RestrictedInputSlot;
import cn.dancingsnow.neoecoae.menu.WithECORestrictedInputSlot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.menu.OverloadedIOPortMenu", remap = false)
public class Ae2LtIOPortMenuMixin {
    @WrapOperation(method = "setupConfig", at = @At(value = "NEW", target = "appeng/menu/slot/RestrictedInputSlot"))
    private RestrictedInputSlot neoecoae$cellSlot(RestrictedInputSlot.PlacableItemType type,
            InternalInventory inventory, int slot, Operation<RestrictedInputSlot> original) {
        return new WithECORestrictedInputSlot(type, inventory, slot);
    }
}
