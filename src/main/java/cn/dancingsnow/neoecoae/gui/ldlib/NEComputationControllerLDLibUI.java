package cn.dancingsnow.neoecoae.gui.ldlib;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEComputationControllerMenu;
import cn.dancingsnow.neoecoae.network.NEComputationUiState;
import cn.dancingsnow.neoecoae.network.NENetwork;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * LDLib1 display for the Computation Controller. It consumes the existing
 * {@link NEComputationUiState} stream and keeps button actions on the existing
 * C2S packet path.
 */
public class NEComputationControllerLDLibUI extends NELDLibMachineScreen<NEComputationControllerMenu> {
    private static final int PANEL_X = 8;
    private static final int PANEL_Y = 26;
    private static final int PANEL_W = 284;
    private static final int PANEL_H = 102;
    private static final int STATUS_Y = 136;
    private static final int STATUS_H = 24;
    private static final int CPU_BUTTON_X = 226;
    private static final int CPU_BUTTON_Y = 7;
    private static final int CPU_BUTTON_W = 66;
    private static final int CPU_BUTTON_H = 18;

    private boolean hasComputationState;
    private NEComputationUiState computationState;

    public NEComputationControllerLDLibUI(NEComputationControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, 300, 170);
        this.computationState = NEComputationUiState.empty(menu.getMachinePos());
    }

    public void setComputationUiState(NEComputationUiState state) {
        this.hasComputationState = true;
        this.computationState = state;
    }

    @Override
    protected void initLdWidgets() {
        addLdWidget(new ButtonWidget(
                        CPU_BUTTON_X,
                        CPU_BUTTON_Y,
                        CPU_BUTTON_W,
                        CPU_BUTTON_H,
                        buttonTexture(0xFFF1F3F6, 0xFF8A96A8),
                        click -> NENetwork.CHANNEL.sendToServer(
                                new NENetwork.NEComputationCpuSelectionModePacket(menu.getMachinePos()))))
                .setHoverTexture(new ColorRectAndBorderTexture(0x00000000, 0xFF4F7FB6, 1.0F));
        addText(
                CPU_BUTTON_X,
                CPU_BUTTON_Y + 5,
                CPU_BUTTON_W,
                8,
                () -> Component.literal(cpuModeShortName(currentState().cpuSelectionMode())),
                TEXT_VALUE,
                TextTexture.TextType.NORMAL);

        addText(18, 38, 88, 9, () -> Component.literal("Threads"), TEXT_PRIMARY, TextTexture.TextType.LEFT_HIDE);
        addText(
                112,
                38,
                170,
                9,
                () -> Component.literal(fmt(currentState().usedThreads()) + " / "
                        + fmt(currentState().maxThreads())),
                TEXT_VALUE,
                TextTexture.TextType.LEFT_HIDE);
        addProgress(
                112,
                50,
                170,
                12,
                () -> percent(currentState().usedThreads(), currentState().maxThreads()),
                0xFF49A36E,
                ProgressTexture.FillDirection.LEFT_TO_RIGHT);

        addText(
                18,
                72,
                88,
                9,
                () -> Component.literal("Available Storage"),
                TEXT_PRIMARY,
                TextTexture.TextType.LEFT_HIDE);
        addText(
                112,
                72,
                170,
                9,
                () -> Component.literal(fmt(currentState().availableStorage()) + " / "
                        + fmt(currentState().totalStorage()) + " bytes"),
                TEXT_VALUE,
                TextTexture.TextType.LEFT_HIDE);
        addProgress(
                112,
                84,
                170,
                12,
                () -> percent(currentState().availableStorage(), currentState().totalStorage()),
                0xFF4F78C6,
                ProgressTexture.FillDirection.LEFT_TO_RIGHT);

        addText(
                18,
                108,
                120,
                9,
                () -> Component.literal("Parallel Count: " + fmt(currentState().parallelCount())),
                TEXT_PRIMARY,
                TextTexture.TextType.LEFT_HIDE);
        addText(
                152,
                108,
                120,
                9,
                () -> Component.literal("Accelerators: " + fmt(currentState().accelerators())),
                TEXT_PRIMARY,
                TextTexture.TextType.LEFT_HIDE);
    }

    @Override
    protected void renderLdBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawPanel(guiGraphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        drawPanel(guiGraphics, PANEL_X, STATUS_Y, PANEL_W, STATUS_H);

        NEComputationUiState state = currentState();
        drawCenteredLocalString(
                guiGraphics,
                Component.translatable("gui.neoecoae.machine.formed")
                        .append(": ")
                        .append(boolText(state.formed())),
                PANEL_X,
                STATUS_Y + 6,
                PANEL_W / 2,
                state.formed() ? TEXT_SUCCESS : TEXT_ERROR);
        drawCenteredLocalString(
                guiGraphics,
                Component.translatable("gui.neoecoae.machine.active")
                        .append(": ")
                        .append(boolText(state.active())),
                PANEL_X + PANEL_W / 2,
                STATUS_Y + 6,
                PANEL_W / 2,
                state.active() ? TEXT_SUCCESS : TEXT_MUTED);
    }

    @Override
    protected void renderLdTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!isMouseIn(CPU_BUTTON_X, CPU_BUTTON_Y, CPU_BUTTON_W, CPU_BUTTON_H, mouseX, mouseY)) {
            return;
        }
        CpuSelectionMode mode = currentState().cpuSelectionMode();
        guiGraphics.renderComponentTooltip(
                font,
                List.of(
                        Component.translatable("gui.neoecoae.computation.cpu_selection_mode"),
                        cpuModeTooltip(mode),
                        Component.literal("Click to cycle")),
                mouseX,
                mouseY);
    }

    private NEComputationUiState currentState() {
        if (hasComputationState) {
            return computationState;
        }
        if (minecraft == null || minecraft.level == null) {
            return computationState;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.getMachinePos());
        if (be instanceof ECOComputationSystemBlockEntity comp) {
            return new NEComputationUiState(
                    menu.getMachinePos(),
                    comp.isFormed(),
                    false,
                    comp.getUsedThread(),
                    comp.getTotalThread(),
                    comp.getAvailableBytes(),
                    comp.getTotalBytes(),
                    comp.getParallelCount(),
                    comp.getAcceleratorCount(),
                    comp.getCpuSelectionMode());
        }
        return computationState;
    }

    private static IGuiTexture buttonTexture(int fillColor, int borderColor) {
        return new GuiTextureGroup(new ColorRectAndBorderTexture(fillColor, borderColor, 1.0F));
    }

    private static String cpuModeShortName(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> "Player";
            case MACHINE_ONLY -> "Machine";
            case ANY -> "Any";
        };
    }

    private static Component cpuModeTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.player_only");
            case MACHINE_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.machine_only");
            case ANY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.any");
        };
    }

    public NEComputationControllerMenu getMenu() {
        return menu;
    }
}
