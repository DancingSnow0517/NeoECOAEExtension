package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Installs one cursor-held disk into a verified empty physical drive. */
final class StorageCellUiInsertion {
    private StorageCellUiInsertion() {}

    static boolean insert(ECOStorageSystemBlockEntity host, Player player, BlockPos position, ItemStack expected) {
        if (player.level().isClientSide || !host.isFormed() || host.getCluster() == null || expected.isEmpty()) return false;
        var menu = player.containerMenu;
        ItemStack carried = menu.getCarried();
        if (!ItemStack.matches(expected, carried)) return false;
        ECODriveBlockEntity drive = host.getCluster().getDrives().stream()
            .filter(candidate -> !candidate.isRemoved() && Objects.equals(candidate.getBlockPos(), position))
            .findFirst().orElse(null);
        if (drive == null) return false;
        ItemStack remaining = drive.HANDLER.insertItem(0, carried, false);
        if (remaining == carried) return false;
        menu.setCarried(remaining);
        menu.broadcastChanges();
        return true;
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
}
