package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PatternMenuActions {
    private PatternMenuActions() {}

    public static boolean reopen(ServerPlayer player, BlockPos pos) {
        var oldMenu = player.containerMenu;
        ItemStack carried = oldMenu.getCarried();
        oldMenu.setCarried(ItemStack.EMPTY);
        try {
            return BlockUIMenuType.openUI(player, pos);
        } finally {
            player.containerMenu.setCarried(carried);
            player.containerMenu.broadcastChanges();
        }
    }

    public static boolean isRemainder(ItemStack original, ItemStack next) {
        return next.isEmpty() || (next.getCount() <= original.getCount()
            && ItemStack.isSameItemSameTags(original, next));
    }

    static ItemStack toPlayer(Player player, Slot source, int button) {
        if ((button != 0 && button != 1) || !player.containerMenu.getCarried().isEmpty()
                || !source.mayPickup(player) || !source.hasItem()) return ItemStack.EMPTY;
        ItemStack original = source.getItem().copy();
        ItemStack remainder = original.copy();
        var destinations = player.containerMenu.slots.stream()
            .filter(slot -> slot.container == player.getInventory()).toList();
        if (ModularUIContainer.mergeItemStack(remainder, destinations, false)) {
            source.setByPlayer(remainder);
            source.onTake(player, original.copyWithCount(original.getCount() - remainder.getCount()));
        }
        return ItemStack.EMPTY;
    }
}
