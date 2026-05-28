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
 * Periodically (every 20 ticks) sends a S2C {@link NEComputationUiState}
 * packet so the screen shows live server-side stats. Duplicate states
 * with identical values are suppressed.
 * </p>
 */
public class NEComputationControllerMenu extends NEBaseMachineMenu {

    private int tickCounter;
    @Nullable
    private NEComputationUiState lastSentState;

    public NEComputationControllerMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.COMPUTATION_CONTROLLER.get(), containerId, playerInv, machinePos);
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
            if (be instanceof ECOComputationSystemBlockEntity comp) {
                NEComputationUiState state = comp.createComputationUiState();
                // Suppress duplicate sends when nothing changed
                if (tickCounter != 1 && state.equals(lastSentState)) {
                    return;
                }
                lastSentState = state;
                NENetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new NENetwork.NEComputationUiStatePacket(state)
                );
            }
        }
    }
}
