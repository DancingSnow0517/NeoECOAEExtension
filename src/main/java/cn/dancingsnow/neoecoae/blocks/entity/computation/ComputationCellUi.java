package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.blocks.entity.DriveCellUiTransfer;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    static ItemStack quickMove(ECOComputationSystemBlockEntity host, Player player, ItemStack stack,
        boolean mirrored) {
        var cluster = host.getCluster();
        if (cluster == null || !host.isFormed() || !host.canPlayerInteract(player)) return stack;
        for (var row : List.of(cluster.getUpperDrives(), cluster.getLowerDrives())) {
            List<ECOComputationDriveBlockEntity> drives = new ArrayList<>(row);
            drives.sort(Comparator.comparingDouble(drive -> drive.getBlockPos().distSqr(host.getBlockPos())));
            for (int index = mirrored ? drives.size() - 1 : 0;
                mirrored ? index >= 0 : index < drives.size(); index += mirrored ? -1 : 1) {
                var drive = drives.get(index);
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
