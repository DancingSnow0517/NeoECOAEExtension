package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.network.NEStorageUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;

/**
 * Menu for the ECO Storage Controller.
 * <p>
 * Periodically sends a S2C {@link cn.dancingsnow.neoecoae.network.NEStorageUiState}
 * packet so the screen always shows live server-side stats.
 * </p>
 */
public class NEStorageControllerMenu extends NEBaseMachineMenu {

    private int tickCounter;

    public NEStorageControllerMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.STORAGE_CONTROLLER.get(), containerId, playerInv, machinePos);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        tickCounter++;

        // Send immediately on first tick, then every 20 ticks
        if (tickCounter == 1 || tickCounter % 20 == 0) {
            BlockEntity be = serverPlayer.level().getBlockEntity(machinePos);
            if (be instanceof ECOStorageSystemBlockEntity storage) {
                NEStorageUiState state = storage.createStorageUiState();
                NENetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new NENetwork.NEStorageUiStatePacket(state)
                );
            }
        }
    }
}
