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
}
