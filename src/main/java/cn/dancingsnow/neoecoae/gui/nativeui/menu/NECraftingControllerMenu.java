package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.network.NECraftingUiState;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for the ECO Crafting Controller with live read-only status.
 * <p>
 * Extends {@link NEUiStateMachineMenu} to periodically push a
 * {@link NECraftingUiState} snapshot to the client.
 * </p>
 */
public class NECraftingControllerMenu extends NEUiStateMachineMenu<NECraftingUiState> {

    public NECraftingControllerMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.CRAFTING_CONTROLLER.get(), containerId, playerInv, machinePos);
    }

    @Override
    @Nullable protected NECraftingUiState createState(ServerPlayer player) {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (be instanceof ECOCraftingSystemBlockEntity crafting) {
            return crafting.createCraftingUiState();
        }
        return null;
    }

    @Override
    protected long getStateRevision(ServerPlayer player) {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (be instanceof ECOCraftingSystemBlockEntity crafting) {
            return crafting.getUiRevision();
        }
        return Long.MIN_VALUE;
    }

    @Override
    protected void sendState(ServerPlayer player, NECraftingUiState state) {
        NENetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player), new NENetwork.NECraftingUiStatePacket(state));
    }
}
