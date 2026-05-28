package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEComputationControllerScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEStorageControllerScreen;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.Minecraft;

/**
 * Client-only packet handlers for the mod's UI network channel.
 * <p>
 * This class is only loaded on the physical client via
 * {@link net.minecraftforge.fml.DistExecutor}. It must never be referenced
 * directly from common-side code.
 * </p>
 */
public final class NEClientUiPacketHandlers {

    private NEClientUiPacketHandlers() {
    }

    /**
     * Handles an incoming {@link NENetwork.NEStorageUiStatePacket} by pushing
     * the state to the currently open {@link NEStorageControllerScreen} when
     * the machine position matches.
     */
    public static void handleStorageUiState(NENetwork.NEStorageUiStatePacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NEStorageControllerScreen screen) {
            if (screen.getMenu().getMachinePos().equals(pkt.state().pos())) {
                screen.setStorageUiState(pkt.state());
            }
        }
    }

    /**
     * Handles an incoming {@link NENetwork.NEComputationUiStatePacket} by pushing
     * the state to the currently open {@link NEComputationControllerScreen} when
     * the machine position matches.
     */
    public static void handleComputationUiState(NENetwork.NEComputationUiStatePacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NEComputationControllerScreen screen) {
            if (screen.getMenu().getMachinePos().equals(pkt.state().pos())) {
                screen.setComputationUiState(pkt.state());
            }
        }
    }
}
