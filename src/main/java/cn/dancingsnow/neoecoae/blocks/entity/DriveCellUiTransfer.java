package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.api.storage.ICellHost;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class DriveCellUiTransfer {
    private DriveCellUiTransfer() {}

    public static boolean insert(ICellHost drive, Player player, ItemStack carried) {
        ItemStack current = drive.getCellStack();
        if ((current != null && !current.isEmpty()) || !drive.canExtractCell() || !drive.isItemValid(carried)) return false;

        var menu = player.containerMenu;
        ItemStack installed = carried.copyWithCount(1);
        drive.setCellStack(installed);
        if (drive.getCellStack() != installed) return false;
        carried.shrink(1);
        if (carried.isEmpty()) menu.setCarried(ItemStack.EMPTY);
        menu.broadcastChanges();
        return true;
    }

    public static boolean extract(ICellHost drive, Player player, ItemStack cell, boolean toInventory) {
        var reason = drive.getCellExtractionBlockReasonText();
        if (reason != null) {
            player.displayClientMessage(reason, true);
            return false;
        }
        if (!drive.canExtractCell()) return false;

        var inventory = player.getInventory();
        var menu = player.containerMenu;
        if (!menu.getCarried().isEmpty()) return false;
        int destination = -1;
        if (toInventory) {
            destination = inventory.getSlotWithRemainingSpace(cell);
            if (destination < 0 || destination >= inventory.items.size()) destination = inventory.getFreeSlot();
            if (destination < 0) return false;
        }

        drive.setCellStack(null);
        if (drive.getCellStack() != null) return false;
        if (toInventory) {
            var existing = inventory.getItem(destination);
            inventory.setItem(destination, existing.isEmpty() ? cell : existing.copyWithCount(existing.getCount() + cell.getCount()));
            inventory.setChanged();
        } else {
            menu.setCarried(cell);
        }
        menu.broadcastChanges();
        return true;
    }
}
