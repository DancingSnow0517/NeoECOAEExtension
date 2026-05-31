package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.network.NEComputationUiState;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for the ECO Computation Controller with live read-only status.
 * <p>
 * Extends {@link NEUiStateMachineMenu} to periodically push a
 * {@link NEComputationUiState} snapshot to the client.
 * </p>
 */
public class NEComputationControllerMenu extends NEUiStateMachineMenu<NEComputationUiState> {

    public NEComputationControllerMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.COMPUTATION_CONTROLLER.get(), containerId, playerInv, machinePos);
    }

    @Override
    @Nullable
    protected NEComputationUiState createState(ServerPlayer player) {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (be instanceof ECOComputationSystemBlockEntity comp) {
            return comp.createComputationUiState();
        }
        return null;
    }

    @Override
    protected long getStateRevision(ServerPlayer player) {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (be instanceof ECOComputationSystemBlockEntity comp) {
            return comp.getUiRevision();
        }
        return Long.MIN_VALUE;
    }

    @Override
    protected void sendState(ServerPlayer player, NEComputationUiState state) {
        NENetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new NENetwork.NEComputationUiStatePacket(state)
        );
    }
}
