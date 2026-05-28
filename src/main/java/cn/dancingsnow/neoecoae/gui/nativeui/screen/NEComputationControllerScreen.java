package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEComputationControllerMenu;
import cn.dancingsnow.neoecoae.network.NEComputationUiState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Screen for the ECO Computation Controller with live read-only status.
 * <p>
 * Primary display path: S2C {@link NEComputationUiState} pushed from the server
 * menu tick. Before the first packet arrives the screen shows a brief fallback
 * read from the client-side BE (opening-time snapshot, not live).
 * </p>
 */
public class NEComputationControllerScreen extends NEBaseMachineScreen<NEComputationControllerMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private boolean hasComputationState;
    private NEComputationUiState computationState;

    public NEComputationControllerScreen(NEComputationControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.COMPUTATION_CONTROLLER);
        this.imageWidth = 300;
        this.imageHeight = 160;
        this.computationState = NEComputationUiState.empty(menu.getMachinePos());
    }

    /** Called from the network thread via {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}. */
    public void setComputationUiState(NEComputationUiState state) {
        this.hasComputationState = true;
        this.computationState = state;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected boolean shouldShowCraftingEntryButton() {
        return true;
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NEComputationUiState s;

        if (hasComputationState) {
            s = this.computationState;
        } else {
            // Opening-time fallback: read client BE once while waiting for the
            // first S2C packet. Not used for live refresh.
            ECOComputationSystemBlockEntity be = getComputationBE();
            if (be != null) {
                s = new NEComputationUiState(
                    menu.getMachinePos(),
                    be.isFormed(),
                    false,  // active not reliably readable from client BE
                    be.getUsedThread(),
                    be.getTotalThread(),
                    be.getAvailableBytes(),
                    be.getTotalBytes(),
                    be.getParallelCount(),
                    be.getAcceleratorCount()
                );
            } else {
                s = this.computationState;
            }
        }

        final int x = NENativeUiConstants.TITLE_X;
        final int valueColor = NENativeUiConstants.PANEL_TEXT_SECONDARY;
        int y = 50;

        // Formed
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.formed").append(": ").append(boolText(s.formed())),
            x, y, valueColor);
        y += 14;

        // Active
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.active").append(": ").append(boolText(s.active())),
            x, y, valueColor);
        y += 14;

        // Storage
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.storage_bytes", fmt(s.availableStorage()), fmt(s.totalStorage())),
            x, y, valueColor);
        y += 14;

        // Threads
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.threads_value", s.usedThreads(), s.maxThreads()),
            x, y, valueColor);
        y += 14;

        // Parallel Cores
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.parallel_cores", s.parallelCount()),
            x, y, valueColor);
        y += 14;

        // Accelerators
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.accelerators", s.accelerators()),
            x, y, valueColor);
    }

    private ECOComputationSystemBlockEntity getComputationBE() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.getMachinePos());
        if (be instanceof ECOComputationSystemBlockEntity comp) {
            return comp;
        }
        return null;
    }

    private static String fmt(long value) {
        return NUMBER_FORMAT.format(value);
    }

    public NEComputationControllerMenu getMenu() {
        return menu;
    }
}
