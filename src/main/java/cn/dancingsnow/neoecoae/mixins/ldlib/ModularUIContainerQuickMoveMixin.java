package cn.dancingsnow.neoecoae.mixins.ldlib;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ModularUIContainer.class)
public abstract class ModularUIContainerQuickMoveMixin {
    @WrapMethod(method = "quickMoveStack")
    private ItemStack neoecoae$quickMoveIntoHost(Player player, int slotId, Operation<ItemStack> original) {
        var menu = (ModularUIContainer) (Object) this;
        var ui = ModularUI.from(menu);
        if (ui == null || player.level().isClientSide || player.containerMenu != menu || !menu.stillValid(player)
                || !menu.getCarried().isEmpty() || slotId < 0 || slotId >= menu.slots.size()) {
            return original.call(player, slotId);
        }

        var source = menu.getSlot(slotId);
        ItemStack stack = source.getItem();
        if (source.container != player.getInventory() || !source.mayPickup(player) || stack.isEmpty()) {
            return original.call(player, slotId);
        }

        ItemStack remainder = ui.quickMoveIntoHost(stack.copy(), false);
        if (remainder.getCount() >= stack.getCount()) return original.call(player, slotId);
        source.setByPlayer(remainder);
        if (!remainder.isEmpty()) original.call(player, slotId);
        return stack;
    }
}
