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
import org.jetbrains.annotations.Nullable;

/**
 * Menu for the ECO Storage Controller with live read-only status.
 * <p>
 * Extends {@link NEUiStateMachineMenu} to periodically push an
 * {@link NEStorageUiState} snapshot to the client.
 * </p>
 */
public class NEStorageControllerMenu extends NEUiStateMachineMenu<NEStorageUiState> {

    public NEStorageControllerMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.STORAGE_CONTROLLER.get(), containerId, playerInv, machinePos);
    }

    @Override
    @Nullable
    protected NEStorageUiState createState(ServerPlayer player) {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (be instanceof ECOStorageSystemBlockEntity storage) {
            return storage.createStorageUiState();
        }
        return null;
    }

    @Override
    protected void sendState(ServerPlayer player, NEStorageUiState state) {
        NENetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new NENetwork.NEStorageUiStatePacket(state)
        );
    }
}
