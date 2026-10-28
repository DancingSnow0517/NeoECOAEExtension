package cn.dancingsnow.neoecoae.menu;

import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.StorageCellWorkbench;
import cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public record StorageCellWorkbenchLocator(BlockPos controller, BlockPos drive, UUID installation)
    implements MenuHostLocator {
    public static void register() {
        MenuLocators.register(StorageCellWorkbenchLocator.class, (locator, buffer) -> {
            buffer.writeBlockPos(locator.controller());
            buffer.writeBlockPos(locator.drive());
            buffer.writeUUID(locator.installation());
        }, buffer -> new StorageCellWorkbenchLocator(buffer.readBlockPos(), buffer.readBlockPos(), buffer.readUUID()));
    }

    @Override
    @Nullable
    public <T> T locate(Player player, Class<T> hostInterface) {
        if (!(player.level().getBlockEntity(controller) instanceof ECOStorageSystemBlockEntity host)) return null;
        if (!player.level().isClientSide && (!host.isFormed() || !host.canPlayerInteract(player)
            || host.getStorageDrivesForIntegration().stream().noneMatch(candidate ->
                !candidate.isRemoved() && candidate.getBlockPos().equals(drive)
                    && installation.equals(candidate.getCellInteractionId()) && candidate.canExtractCell()
                    && candidate.getCellStack() != null && candidate.getCellStack().getCount() == 1
                    && ECOStorageCellSettings.canConfigure(candidate.getCellStack())))) return null;
        var workbench = new StorageCellWorkbench(host, drive, installation, player);
        return hostInterface.isInstance(workbench) && workbench.isValidFor(player)
            ? hostInterface.cast(workbench) : null;
    }
}
