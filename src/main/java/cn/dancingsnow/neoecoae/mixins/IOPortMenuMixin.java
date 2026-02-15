package cn.dancingsnow.neoecoae.mixins;

import appeng.api.inventories.InternalInventory;
import appeng.menu.SlotSemantic;
import appeng.menu.implementations.IOPortMenu;
import appeng.menu.slot.RestrictedInputSlot;
import cn.dancingsnow.neoecoae.menu.WithECORestrictedInputSlot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IOPortMenu.class)
public class IOPortMenuMixin {

    @WrapOperation(method = "setupConfig", at = @At(value = "INVOKE", target = "Lappeng/menu/implementations/IOPortMenu;addSlot(Lnet/minecraft/world/inventory/Slot;Lappeng/menu/SlotSemantic;)Lnet/minecraft/world/inventory/Slot;", ordinal = 0))
    private Slot wrapSlot(IOPortMenu instance, Slot slot, SlotSemantic slotSemantic, Operation<Slot> original, @Local(name = "cells") InternalInventory cells, @Local(name = "i") int i) {
        return original.call(instance, new WithECORestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_CELLS, cells, i), slotSemantic);
    }
}
