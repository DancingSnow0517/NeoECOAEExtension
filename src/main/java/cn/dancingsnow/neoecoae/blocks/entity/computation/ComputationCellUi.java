package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.blocks.entity.DriveCellUiTransfer;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class ComputationCellUi {
    private ComputationCellUi() {}

    static boolean insert(ECOComputationSystemBlockEntity host, Player player, BlockPos position, ItemStack expected) {
        if (player.level().isClientSide || !host.isFormed() || host.getCluster() == null || expected.isEmpty()) return false;
        ItemStack carried = player.containerMenu.getCarried();
        if (!ItemStack.matches(expected, carried)) return false;
        ECOComputationDriveBlockEntity drive = findDrive(host, position);
        return drive != null && DriveCellUiTransfer.insert(drive, player, carried);
    }

    static boolean extract(ECOComputationSystemBlockEntity host, Player player, BlockPos position,
        ItemStack expected, boolean toInventory) {
        if (player.level().isClientSide || !host.isFormed() || host.getCluster() == null
            || expected.isEmpty() || expected.getCount() != 1) return false;
        ECOComputationDriveBlockEntity drive = findDrive(host, position);
        if (drive == null) return false;
        ItemStack cell = drive.getCellStack();
        return cell != null && ItemStack.matches(expected, cell)
            && DriveCellUiTransfer.extract(drive, player, cell, toInventory);
    }

    @Nullable
    private static ECOComputationDriveBlockEntity findDrive(ECOComputationSystemBlockEntity host, BlockPos position) {
        var cluster = host.getCluster();
        return Stream.concat(cluster.getUpperDrives().stream(), cluster.getLowerDrives().stream())
            .filter(candidate -> !candidate.isRemoved() && Objects.equals(candidate.getBlockPos(), position))
            .findFirst().orElse(null);
    }
}
