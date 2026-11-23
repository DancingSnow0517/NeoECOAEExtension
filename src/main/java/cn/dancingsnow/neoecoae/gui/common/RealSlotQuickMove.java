package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Opt-in quick move for retained menus whose item widgets all represent real inventory slots. */
public final class RealSlotQuickMove {
    private RealSlotQuickMove() {}

    public static ModularUI install(ModularUI ui) {
        var itemSlots = ui.getElementsByType(ItemSlot.class);
        for (ItemSlot element : itemSlots) {
            element.nativeClickHandler((button, type, player) -> {
                if (type != ClickType.QUICK_MOVE) return null;
                if ((button == 0 || button == 1) && !player.level().isClientSide
                    && ui.getMenu().getCarried().isEmpty()) {
                    move(player, element.getMenuSlot(), itemSlots);
                }
                return ItemStack.EMPTY;
            });
        }
        return ui;
    }

    private static void move(Player player, Slot source, List<ItemSlot> itemSlots) {
        if (!source.hasItem() || !source.mayPickup(player)) return;

        boolean fromPlayer = source.container == player.getInventory();
        Comparator<ItemSlot> menuOrder = Comparator.comparingInt(element -> element.getMenuSlot().index);
        var destinations = itemSlots.stream()
            .filter(element -> (element.getMenuSlot().container == player.getInventory()) != fromPlayer)
            .sorted(fromPlayer ? menuOrder.reversed() : menuOrder)
            .toList();

        ItemStack original = source.getItem().copy();
        ItemStack remainder = original.copy();
        for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++) {
            for (ItemSlot element : destinations) {
                Slot destination = element.getMenuSlot();
                if ((pass == 0) != destination.hasItem() || !destination.mayPlace(remainder)) continue;
                if (element.getSlot() instanceof SlotItemHandler handlerSlot) {
                    remainder = handlerSlot.getItemHandler().insertItem(handlerSlot.getContainerSlot(), remainder, false);
                } else if (destination.getItem().getCount() < destination.getMaxStackSize(remainder)) {
                    remainder = destination.safeInsert(remainder);
                }
                if (remainder.isEmpty()) break;
            }
        }

        int moved = original.getCount() - remainder.getCount();
        if (moved > 0) {
            source.setByPlayer(remainder);
            source.onTake(player, original.copyWithCount(moved));
        }
    }
}
