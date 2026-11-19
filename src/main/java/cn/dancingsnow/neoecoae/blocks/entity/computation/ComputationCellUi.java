package cn.dancingsnow.neoecoae.blocks.entity.computation;

import java.util.List;
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
        if (drive == null) return false;
        ItemStack remaining = drive.getItemHandler().insertItem(0, carried, false);
        if (remaining == carried) return false;
        menu.setCarried(remaining);
        menu.broadcastChanges();
        return true;
    }

    static ItemStack quickMove(ECOComputationSystemBlockEntity host, Player player, ItemStack stack, boolean mirrored) {
        var cluster = host.getCluster();
        if (cluster == null || !host.isFormed() || !host.canPlayerInteract(player)) return stack;
        for (var row : List.of(cluster.getUpperDrives(), cluster.getLowerDrives())) {
            var drives = ComputationDriveLayout.nearest(row, host.getBlockPos());
            for (var drive : mirrored ? drives.reversed() : drives) {
                if (drive.isRemoved()) continue;
                stack = drive.getItemHandler().insertItem(0, stack, false);
                if (stack.isEmpty()) return stack;
            }
        }
        return stack;
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
        if (!menu.getCarried().isEmpty()) return false;
        int destination = -1;
        if (toInventory) {
            destination = inventory.getSlotWithRemainingSpace(drive.getCellStack());
            if (destination < 0 || destination >= inventory.items.size()) {
                destination = inventory.getFreeSlot();
            }
            if (destination < 0) return false;
        }

        // Retain the actual stack: unmounting may persist its latest cell components in place.
        ItemStack cell = drive.getCellStack();
        drive.setCellStack(null);
        if (drive.getCellStack() != null) return false;
        if (toInventory) {
            ItemStack existing = inventory.getItem(destination);
            inventory.setItem(destination, existing.isEmpty() ? cell : existing.copyWithCount(existing.getCount() + 1));
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
