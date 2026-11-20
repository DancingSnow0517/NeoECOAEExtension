package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.blocks.entity.DriveCellUiTransfer;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class StorageCellUi {
    private StorageCellUi() {}

    static boolean insert(ECOStorageSystemBlockEntity host, Player player, BlockPos position, ItemStack expected) {
        if (player.level().isClientSide || !host.isFormed() || host.getCluster() == null || expected.isEmpty()) return false;
        ItemStack carried = player.containerMenu.getCarried();
        if (!ItemStack.matches(expected, carried)) return false;
        ECODriveBlockEntity drive = findDrive(host, position);
        return drive != null && DriveCellUiTransfer.insert(drive, player, carried);
    }

    static ItemStack quickMove(ECOStorageSystemBlockEntity host, Player player, ItemStack stack) {
        if (!host.canPlayerInteract(player) || !host.isFormed()) return stack;
        for (var drive : host.getStorageUiDrives()) {
            if (drive == null || drive.isRemoved()) continue;
            stack = drive.HANDLER.insertItem(0, stack, false);
            if (stack.isEmpty()) break;
        }
        return stack;
    }

    static boolean extract(ECOStorageSystemBlockEntity host, Player player, BlockPos position,
        UUID expected, boolean toInventory) {
        if (player.level().isClientSide || !host.isFormed() || host.getCluster() == null || expected == null) return false;
        ECODriveBlockEntity drive = findDrive(host, position);
        if (drive == null || !expected.equals(drive.getCellInteractionId())) return false;
        ItemStack cell = drive.getCellStack();
        return cell != null && !cell.isEmpty() && cell.getCount() == 1
            && DriveCellUiTransfer.extract(drive, player, cell, toInventory);
    }

    @Nullable
    private static ECODriveBlockEntity findDrive(ECOStorageSystemBlockEntity host, BlockPos position) {
        return host.getCluster().getDrives().stream()
            .filter(candidate -> !candidate.isRemoved() && Objects.equals(candidate.getBlockPos(), position))
            .findFirst().orElse(null);
    }
}
