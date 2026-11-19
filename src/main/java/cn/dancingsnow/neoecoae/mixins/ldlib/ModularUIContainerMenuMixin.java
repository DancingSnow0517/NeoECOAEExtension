package cn.dancingsnow.neoecoae.mixins.ldlib;

import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ModularUIContainerMenu.class, remap = false)
public abstract class ModularUIContainerMenuMixin {
    @WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE", target =
        "Lcom/lowdragmc/lowdraglib2/gui/holder/ModularUIContainerMenu;performQuickMoveStack(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack neoecoae$quickMoveIntoHost(ModularUIContainerMenu menu, ItemStack stack, boolean fromPlayerSide,
        Operation<ItemStack> original) {
        if (fromPlayerSide && menu.getModularUI() instanceof ECOModularUI ecoUI
            && menu.inventory.player.containerMenu == menu && menu.stillValid(menu.inventory.player)) {
            stack = ecoUI.quickMoveIntoHost(stack);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return original.call(menu, stack, fromPlayerSide);
    }
}
