package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEComputationControllerScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NECraftingControllerScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEStorageControllerScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEStructureTerminalScreen;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fluids.capability.templates.FluidTank;

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

    /**
     * Handles an incoming {@link NENetwork.NECraftingUiStatePacket} by pushing
     * the state to the currently open {@link NECraftingControllerScreen} when
     * the machine position matches.
     */
    public static void handleCraftingUiState(NENetwork.NECraftingUiStatePacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NECraftingControllerScreen screen) {
            if (screen.getMenu().getMachinePos().equals(pkt.state().pos())) {
                screen.setCraftingUiState(pkt.state());
            }
        }
    }

    /**
     * Handles the Structure Terminal config state packet.
     */
    public static void handleStructureTerminalConfig(NENetwork.NEStructureTerminalConfigPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NEStructureTerminalScreen screen) {
            screen.setConfig(pkt.currentLength(), pkt.minLength(), pkt.maxLength(), pkt.hostType(), pkt.operationMode(), pkt.materials());
        }
    }

    /**
     * Handles an incoming {@link NENetwork.NEStructureTerminalUiStatePacket}
     * by pushing the state to the currently open {@link NEStructureTerminalScreen}.
     *
     * @deprecated The config UI no longer uses the full host-bound state.
     */
    @Deprecated
    public static void handleStructureTerminalUiState(NENetwork.NEStructureTerminalUiStatePacket pkt) {
        // No-op: the config UI now uses NEStructureTerminalConfigPacket instead.
        // Kept for backward compatibility with old host-bound packets.
    }

    /** Handles the IWS state sync packet from server to client. */
    public static void handleIwsStatePacket(NENetwork.NEIWSStatePacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof NEIntegratedWorkingStationMenu menu)) return;
        if (!menu.getMachinePos().equals(pkt.pos())) return;

        var inputTank = new FluidTank(16000);
        var outputTank = new FluidTank(16000);
        if (pkt.inputTankTag() != null) inputTank.readFromNBT(pkt.inputTankTag());
        if (pkt.outputTankTag() != null) outputTank.readFromNBT(pkt.outputTankTag());
        menu.updateClientState(inputTank.getFluid(), outputTank.getFluid(), pkt.autoExport());
    }
}
