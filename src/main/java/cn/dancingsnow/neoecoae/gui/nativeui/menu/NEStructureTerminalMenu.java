package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for the Structure Terminal UI.
 * <p>
 * Periodically pushes {@link NEStructureTerminalUiState} snapshots
 * to the client via S2C packets. C2S action packets handle
 * build length changes, preview, and auto-build.
 * </p>
 */
public class NEStructureTerminalMenu extends NEUiStateMachineMenu<NEStructureTerminalUiState> {

    public NEStructureTerminalMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.STRUCTURE_TERMINAL.get(), containerId, playerInv, machinePos);
    }

    @Override
    @Nullable
    protected NEStructureTerminalUiState createState(ServerPlayer player) {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (be instanceof INEMultiblockBuildHost host) {
            return host.createBuildUiState();
        }
        return null;
    }

    @Override
    protected void sendState(ServerPlayer player, NEStructureTerminalUiState state) {
        NENetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new NENetwork.NEStructureTerminalUiStatePacket(state)
        );
    }

    /**
     * Returns the host at the machine position, or null.
     */
    @Nullable
    public INEMultiblockBuildHost getHost(ServerPlayer player) {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (be instanceof INEMultiblockBuildHost host) {
            return host;
        }
        return null;
    }
}
