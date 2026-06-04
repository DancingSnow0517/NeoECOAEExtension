package cn.dancingsnow.neoecoae.forge.mixin;

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

@Mixin(value = IOPortMenu.class, remap = false)
public class IOPortMenuMixin120 {
    @WrapOperation(
        method = "setupConfig",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/menu/implementations/IOPortMenu;addSlot(Lnet/minecraft/world/inventory/Slot;Lappeng/menu/SlotSemantic;)Lnet/minecraft/world/inventory/Slot;",
            ordinal = 0
        )
    )
    private Slot neoecoae$useEcoRestrictedInputSlot(
        IOPortMenu menu,
        Slot slot,
        SlotSemantic slotSemantic,
        Operation<Slot> original,
        @Local(ordinal = 0) InternalInventory cells,
        @Local(ordinal = 0) int index
    ) {
        return original.call(
            menu,
            new WithECORestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_CELLS, cells, index),
            slotSemantic
        );
    }
}
