package cn.dancingsnow.neoecoae.api.storage;

import appeng.menu.MenuOpener;
import appeng.menu.implementations.CellWorkbenchMenu;
import cn.dancingsnow.neoecoae.menu.StorageCellWorkbenchLocator;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Implement on the placed host block entity to use the shared installed-cell workbench. */
public interface IECOConfigurableCellHost {
    BlockEntity getCellWorkbenchBlockEntity();

    boolean isFormed();

    /** Check the player's level, distance and player/network permissions on both logical sides. */
    boolean canPlayerInteract(Player player);

    /** Only the current drives belonging to this host; never resolve arbitrary world positions. */
    List<? extends IECOCellWorkbenchDrive> getStorageDrivesForIntegration();

    /** Wire to CellActions.openConfiguration. Call on the owning server thread. */
    default void openCellConfiguration(Player player, BlockPos drive, UUID installation) {
        var host = getCellWorkbenchBlockEntity();
        if (host.getLevel() == null || host.getLevel().isClientSide() || player.level() != host.getLevel()
            || !player.containerMenu.getCarried().isEmpty()) return;
        // The locator checks formation, permissions, host membership and installation before opening.
        MenuOpener.open(CellWorkbenchMenu.TYPE, player,
            new StorageCellWorkbenchLocator(host.getBlockPos(), drive, installation));
    }

    /** Override when the host uses a menu other than LDLib's block UI. */
    default void returnToCellWorkbenchHost(ServerPlayer player) {
        BlockUIMenuType.openUI(player, getCellWorkbenchBlockEntity().getBlockPos());
    }
}
