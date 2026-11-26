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
                    move(player, element, itemSlots);
                }
                return ItemStack.EMPTY;
            });
        }
        return ui;
    }

    private static boolean playerSide(ItemSlot element, Player player) {
        return element.isPlayerSideSlot() || element.getMenuSlot().container == player.getInventory();
    }

    private static void move(Player player, ItemSlot sourceElement, List<ItemSlot> itemSlots) {
        Slot source = sourceElement.getMenuSlot();
        if (!source.hasItem() || !source.mayPickup(player)) return;

        boolean fromPlayer = playerSide(sourceElement, player);
        var destinations = itemSlots.stream()
            .filter(element -> playerSide(element, player) != fromPlayer)
            .sorted(Comparator.comparingInt(ItemSlot::quickMovePriority).reversed()
                .thenComparingInt(element -> fromPlayer && element.quickMovePriority() == 0
                    ? -element.getMenuSlot().index : element.getMenuSlot().index))
            .toList();

        ItemStack original = source.getItem().copy();
        ItemStack remainder = original.copy();
        // Finish both merge and empty-slot passes in a priority group before using a lower group.
        for (int start = 0; start < destinations.size() && !remainder.isEmpty();) {
            int priority = destinations.get(start).quickMovePriority();
            int end = start + 1;
            while (end < destinations.size() && destinations.get(end).quickMovePriority() == priority) end++;
            remainder = insert(destinations.subList(start, end), remainder);
            start = end;
        }
        int moved = original.getCount() - remainder.getCount();
        if (moved > 0) {
            source.setByPlayer(remainder);
            source.onTake(player, original.copyWithCount(moved));
        }
    }

    private static ItemStack insert(List<ItemSlot> destinations, ItemStack remainder) {
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
        return remainder;
    }
}
