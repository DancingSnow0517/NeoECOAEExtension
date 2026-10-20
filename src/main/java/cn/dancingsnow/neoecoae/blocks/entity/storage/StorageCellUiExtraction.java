package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Moves a single physical cell after validating its installation identity and destination. */
final class StorageCellUiExtraction {
    private StorageCellUiExtraction() {}

    static boolean extract(ECOStorageSystemBlockEntity host, Player player, BlockPos position,
        UUID expected, boolean toInventory) {
        if (player.level().isClientSide || !host.isFormed() || host.getCluster() == null
            || expected == null) return false;
        ECODriveBlockEntity drive = host.getCluster().getDrives().stream()
            .filter(candidate -> !candidate.isRemoved() && Objects.equals(candidate.getBlockPos(), position))
            .findFirst().orElse(null);
        if (drive == null || !expected.equals(drive.getCellInteractionId())
            || drive.getCellStack() == null || drive.getCellStack().isEmpty()
            || drive.getCellStack().getCount() != 1) return false;
        var reason = drive.getCellExtractionBlockReasonText();
        if (reason != null) {
            player.displayClientMessage(reason, true);
            return false;
        }
        if (!drive.canExtractCell()) return false;

        var inventory = player.getInventory();
        var menu = player.containerMenu;
        int destination = -1;
        if (toInventory) {
            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack existing = inventory.getItem(i);
                if (existing.isEmpty()) {
                    destination = i;
                    break;
                }
            }
            if (destination < 0) return false;
        } else {
            if (!menu.getCarried().isEmpty()) return false;
        }

        // Retain the actual stack: unmounting may persist its latest cell components in place.
        ItemStack cell = drive.getCellStack();
        drive.setCellStack(null);
        if (drive.getCellStack() != null) return false;
        if (toInventory) {
            inventory.setItem(destination, cell);
            inventory.setChanged();
        } else {
            menu.setCarried(cell);
        }
        menu.broadcastChanges();
        return true;
    }
}
