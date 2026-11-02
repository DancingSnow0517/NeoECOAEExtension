package cn.dancingsnow.neoecoae.blocks.entity.computation;

import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Installs one cursor-held cell into a verified empty computation drive. */
final class ComputationCellUi {
    private ComputationCellUi() {}

    static boolean insert(ECOComputationSystemBlockEntity host, Player player, BlockPos position, ItemStack expected) {
        if (player.level().isClientSide || !host.isFormed() || host.getCluster() == null || expected.isEmpty()) return false;
        var menu = player.containerMenu;
        ItemStack carried = menu.getCarried();
        if (!ItemStack.matches(expected, carried)) return false;
        ECOComputationDriveBlockEntity drive = findDrive(host, position);
        if (drive == null || drive.getCellStack() != null && !drive.getCellStack().isEmpty()
            || !drive.canExtractCell() || !drive.isItemValid(carried)) return false;

        ItemStack installed = carried.copyWithCount(1);
        drive.setCellStack(installed);
        // The real setter retains this reference; a rejected installation must not consume the cursor item.
        if (drive.getCellStack() != installed) return false;
        carried.shrink(1);
        if (carried.isEmpty()) menu.setCarried(ItemStack.EMPTY);
        menu.broadcastChanges();
        return true;
    }

    static boolean extract(ECOComputationSystemBlockEntity host, Player player, BlockPos position,
        ItemStack expected, boolean toInventory) {
        if (player.level().isClientSide || !host.isFormed() || host.getCluster() == null
            || expected.isEmpty() || expected.getCount() != 1) return false;
        ECOComputationDriveBlockEntity drive = findDrive(host, position);
        if (drive == null || drive.getCellStack() == null || !ItemStack.matches(expected, drive.getCellStack())) return false;
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

    @Nullable
    private static ECOComputationDriveBlockEntity findDrive(ECOComputationSystemBlockEntity host, BlockPos position) {
        var cluster = host.getCluster();
        return Stream.concat(cluster.getUpperDrives().stream(), cluster.getLowerDrives().stream())
            .filter(candidate -> !candidate.isRemoved() && Objects.equals(candidate.getBlockPos(), position))
            .findFirst().orElse(null);
    }
}
