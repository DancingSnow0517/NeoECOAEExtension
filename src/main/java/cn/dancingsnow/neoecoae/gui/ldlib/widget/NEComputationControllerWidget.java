package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class NEComputationControllerWidget extends NELDLibSyncedStateWidget<NEComputationUiState> {
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

    private final ECOComputationSystemBlockEntity computation;

    public NEComputationControllerWidget(ECOComputationSystemBlockEntity computation) {
        super(
                computation.getBlockState().getBlock().getName(),
                300,
                170,
                NEComputationUiState.empty(computation.getBlockPos()),
                computation::createComputationUiState,
                NELDLibStateCodecs::writeComputation,
                NELDLibStateCodecs::readComputation,
                20);
        this.computation = computation;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new ButtonWidget(
                        CPU_BUTTON_X,
                        CPU_BUTTON_Y,
                        CPU_BUTTON_W,
                        CPU_BUTTON_H,
                        buttonTexture(0xFFF1F3F6, 0xFF8A96A8),
                        click -> {
                            if (!click.isRemote) {
                                NEComputationCluster cluster = computation.getCluster();
                                if (cluster != null) {
                                    cluster.cycleSelectionMode();
                                    computation.markComputationStatsDirty();
                                    computation.updateInfos();
                                    syncStateNow();
                                }
                            }
                        })
                .setHoverTexture(new ColorRectAndBorderTexture(0x00000000, 0xFF4F7FB6, 1.0F)));
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
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawPanel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        drawPanel(graphics, PANEL_X, STATUS_Y, PANEL_W, STATUS_H);

        NEComputationUiState state = currentState();
        drawCenteredLocalString(
                graphics,
                Component.translatable("gui.neoecoae.machine.formed")
                        .append(": ")
                        .append(boolText(state.formed())),
                PANEL_X,
                STATUS_Y + 6,
                PANEL_W / 2,
                state.formed() ? TEXT_SUCCESS : TEXT_ERROR);
        drawCenteredLocalString(
                graphics,
                Component.translatable("gui.neoecoae.machine.active")
                        .append(": ")
                        .append(boolText(state.active())),
                PANEL_X + PANEL_W / 2,
                STATUS_Y + 6,
                PANEL_W / 2,
                state.active() ? TEXT_SUCCESS : TEXT_MUTED);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseIn(CPU_BUTTON_X, CPU_BUTTON_Y, CPU_BUTTON_W, CPU_BUTTON_H, mouseX, mouseY)) {
            return;
        }
        CpuSelectionMode mode = currentState().cpuSelectionMode();
        graphics.renderComponentTooltip(
                font(),
                List.of(
                        Component.translatable("gui.neoecoae.computation.cpu_selection_mode"),
                        cpuModeTooltip(mode),
                        Component.literal("Click to cycle")),
                mouseX,
                mouseY);
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
}
