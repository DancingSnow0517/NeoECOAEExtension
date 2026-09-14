package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.*;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationCapacityPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationHeaderPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationTaskPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostSideButtonRenderer;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTextures;
import cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * Coordinates computation state, server actions, inventory slots, and focused client-side host panels.
 */
public class NEComputationControllerWidget extends NELDLibSyncedStateWidget<NEComputationUiState> {
    public static final int UI_WIDTH = NEComputationLayout.UI_WIDTH;
    public static final int UI_HEIGHT = NEComputationLayout.UI_HEIGHT;

    private final ECOComputationSystemBlockEntity computation;
    private final Inventory playerInventory;
    private final Player player;
    private final NEComputationHeaderPanel headerPanel = new NEComputationHeaderPanel();
    private final NEComputationCapacityPanel capacityPanel = new NEComputationCapacityPanel();
    private final NEComputationTaskPanel taskPanel = new NEComputationTaskPanel();
    private NEAe2IconButtonWidget cpuModeButton;
    private NEAe2IconButtonWidget fastTaskPlanningButton;

    public NEComputationControllerWidget(ECOComputationSystemBlockEntity computation, Player player) {
        super(
                computation.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                NEComputationUiState.empty(computation.getBlockPos()),
                computation::createComputationUiState,
                NELDLibStateCodecs::writeComputation,
                NELDLibStateCodecs::readComputation,
                20);
        this.computation = computation;
        this.playerInventory = player.getInventory();
        this.player = player;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    /**
     * Inserting a field generator raises the accelerator limit, and the auto-max that follows is a
     * direct answer to what the player just did -- letting the 20-tick interval sit on it for up to a
     * second makes the upgrade look inert.
     */
    @Override
    protected long stateRevision() {
        return computation.getConfigRevision();
    }

    @Override
    protected boolean shouldDrawBasePanel() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new NEAe2IconButtonWidget(-17, 3, 16, 16, Icon.HELP, click -> {
                    if (!click.isRemote && net.minecraftforge.fml.ModList.get().isLoaded("guideme")) {
                        guideme.GuidesCommon.openGuide(
                                player,
                                appeng.core.AppEng.makeId("guide"),
                                guideme.PageAnchor.parse("neoecoae:neoecoae_intro/computation_system.md"));
                    }
                })
                .useEcoButton());
        fastTaskPlanningButton = new NEAe2IconButtonWidget(
                        FAST_TASK_PLANNING_BUTTON_X,
                        FAST_TASK_PLANNING_BUTTON_Y,
                        FAST_TASK_PLANNING_BUTTON_W,
                        FAST_TASK_PLANNING_BUTTON_H,
                        fastTaskPlanningIcon(),
                        click -> {
                            if (!click.isRemote) {
                                computation.toggleFastTaskPlanning();
                                syncStateNow();
                            }
                        })
                .useEcoButton();
        addWidget(fastTaskPlanningButton);
        addWidget(new NEAe2IconButtonWidget(-17, 69, 16, 16, Icon.LEVEL_ENERGY, click -> {
                    if (!click.isRemote) {
                        computation.toggleCyclePlanning();
                        syncStateNow();
                    }
                })
                .useEcoButton());
        addWidget(new NEAe2IconButtonWidget(-17, 47, 16, 16, Icon.POWER_UNIT_AE, click -> {
                    if (!click.isRemote) {
                        computation.toggleIgnoringSubstitutions();
                        syncStateNow();
                    }
                })
                .useEcoButton());
        addWidget(new NEAe2IconButtonWidget(
                        NETWORK_FREQUENCY_BUTTON_X,
                        NETWORK_FREQUENCY_BUTTON_Y,
                        NETWORK_FREQUENCY_BUTTON_W,
                        NETWORK_FREQUENCY_BUTTON_H,
                        Icon.SCHEDULING_ROUND_ROBIN,
                        click -> {
                            if (!click.isRemote && (click.button == 0 || click.button == 1)) {
                                computation.adjustNetworkFrequency(click.button == 0 ? 1 : -1);
                                syncStateNow();
                            }
                        })
                .useEcoButton());
        cpuModeButton = new NEAe2IconButtonWidget(
                        mainX(CPU_BUTTON_X), CPU_BUTTON_Y, CPU_BUTTON_W, CPU_BUTTON_H, cpuModeIcon(), click -> {
                            if (!click.isRemote && (click.button == 0 || click.button == 1)) {
                                NEComputationCluster cluster = computation.getCluster();
                                int direction = click.button == 0 ? 1 : -1;
                                if (cluster != null) {
                                    cluster.setLocalSelectionMode(
                                            nextCpuSelectionMode(cluster.getLocalSelectionMode(), direction));
                                } else {
                                    computation.setCpuSelectionMode(
                                            nextCpuSelectionMode(computation.getCpuSelectionMode(), direction));
                                }
                                computation.markComputationStatsDirty();
                                computation.updateInfos();
                                syncStateNow();
                            }
                        })
                .useEcoButton();
        addWidget(cpuModeButton);
        NEPlayerInventoryWidgets.addPlayerInventorySlots(
                this, playerInventory, mainX(PLAYER_INV_X), PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NEHostTextures.drawHostBackground(graphics, absX(MAIN_X), absY(0), BASE_UI_WIDTH, UI_HEIGHT);
        NEHostSideButtonRenderer.drawLeft(graphics, absX(MAIN_X), absY(0), 6, mouseX, mouseY);
        cpuModeButton.setIcon(cpuModeIcon());
        fastTaskPlanningButton.setIcon(fastTaskPlanningIcon());
        capacityPanel.drawBackground(graphics, mainScreenX(), this::absY, currentState(), mouseX, mouseY);
        taskPanel.drawBackground(graphics, mainScreenX(), this::absY, mouseX, mouseY);
        NEPlayerInventoryWidgets.drawPlayerInventoryFrames(
                graphics, mainScreenX(), this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
        NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                graphics, mainScreenX(), this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        headerPanel.draw(graphics, font(), title, currentState(), mainScreenX(), this::absY);
        capacityPanel.drawForeground(graphics, font(), mainScreenX(), this::absY, currentState());
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.common.inventory"),
                mainX(PLAYER_INV_X),
                PLAYER_INV_LABEL_Y,
                TEXT_MUTED);
        taskPanel.draw(graphics, font(), mainScreenX(), this::absY, currentState());
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(-17, 3, 16, 16, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(
                            ButtonToolTips.OpenGuide.text().withStyle(style -> style.withColor(0xFFFFFF)),
                            ButtonToolTips.OpenGuideDetail.text().withStyle(net.minecraft.ChatFormatting.GRAY)),
                    mouseX,
                    mouseY);
            return;
        }
        if (isMouseIn(-17, 25, 16, 16, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(
                            ButtonToolTips.CpuSelectionMode.text(),
                            switch (currentState().cpuSelectionMode()) {
                                case ANY -> ButtonToolTips.CpuSelectionModeAny.text();
                                case PLAYER_ONLY -> ButtonToolTips.CpuSelectionModePlayersOnly.text();
                                case MACHINE_ONLY -> ButtonToolTips.CpuSelectionModeAutomationOnly.text();
                            }),
                    mouseX,
                    mouseY);
            return;
        }
        if (isMouseIn(-17, 69, 16, 16, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable(
                            currentState().cyclePlanningEnabled()
                                    ? "gui.neoecoae.crafting.cycle_planning.on"
                                    : "gui.neoecoae.crafting.cycle_planning.off")),
                    mouseX,
                    mouseY);
            return;
        }
        if (isMouseIn(-17, 47, 16, 16, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(
                            Component.translatable(
                                    currentState().ignoringSubstitutions()
                                            ? "gui.neoecoae.crafting.planning.ignore_substitutions.on"
                                            : "gui.neoecoae.crafting.planning.ignore_substitutions.off"),
                            Component.translatable(
                                    "gui.neoecoae.crafting.planning.substitution_pattern_count",
                                    currentState().substitutionPatternCount())),
                    mouseX,
                    mouseY);
            return;
        }
        if (drawFastTaskPlanningTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (drawNetworkFrequencyTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (taskPanel.drawTooltip(graphics, font(), mainScreenX(), this::absY, currentState(), mouseX, mouseY)) {
            return;
        }
        if (headerPanel.drawTooltip(graphics, font(), currentState(), mainScreenX(), this::absY, mouseX, mouseY)) {
            return;
        }
        capacityPanel.drawTooltip(graphics, font(), mainScreenX(), this::absY, currentState(), mouseX, mouseY);
    }

    private boolean drawFastTaskPlanningTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseIn(
                FAST_TASK_PLANNING_BUTTON_X,
                FAST_TASK_PLANNING_BUTTON_Y,
                FAST_TASK_PLANNING_BUTTON_W,
                FAST_TASK_PLANNING_BUTTON_H,
                mouseX,
                mouseY)) {
            return false;
        }
        graphics.renderComponentTooltip(
                font(),
                List.of(Component.translatable(
                        currentState().fastTaskPlanningEnabled()
                                ? "gui.neoecoae.crafting.fast_planner.on"
                                : "gui.neoecoae.crafting.fast_planner.off")),
                mouseX,
                mouseY);
        return true;
    }

    private boolean drawNetworkFrequencyTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseIn(
                NETWORK_FREQUENCY_BUTTON_X,
                NETWORK_FREQUENCY_BUTTON_Y,
                NETWORK_FREQUENCY_BUTTON_W,
                NETWORK_FREQUENCY_BUTTON_H,
                mouseX,
                mouseY)) {
            return false;
        }
        int frequency = currentState().networkFrequency();
        graphics.renderComponentTooltip(
                font(),
                List.of(Component.translatable("gui.neoecoae.host.network_frequency.cycle", frequency)),
                mouseX,
                mouseY);
        return true;
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (taskPanel.mouseWheel(mainScreenX(), this::absY, currentState(), mouseX, mouseY, wheelDelta)) {
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    private int mainX(int localX) {
        return MAIN_X + localX;
    }

    private IntUnaryOperator mainScreenX() {
        return localX -> absX(mainX(localX));
    }

    private Icon cpuModeIcon() {
        return switch (currentState().cpuSelectionMode()) {
            case PLAYER_ONLY -> Icon.CRAFT_HAMMER;
            case MACHINE_ONLY -> Icon.BACKGROUND_WIRELESS_TERM;
            case ANY -> Icon.TYPE_FILTER_ALL;
        };
    }

    private Icon fastTaskPlanningIcon() {
        return currentState().fastTaskPlanningEnabled() ? Icon.LEVEL_ENERGY : Icon.POWER_UNIT_AE;
    }

    static CpuSelectionMode nextCpuSelectionMode(CpuSelectionMode mode) {
        return nextCpuSelectionMode(mode, 1);
    }

    static CpuSelectionMode nextCpuSelectionMode(CpuSelectionMode mode, int direction) {
        if (direction < 0) {
            return switch (mode) {
                case ANY -> CpuSelectionMode.MACHINE_ONLY;
                case PLAYER_ONLY -> CpuSelectionMode.ANY;
                case MACHINE_ONLY -> CpuSelectionMode.PLAYER_ONLY;
            };
        }
        return switch (mode) {
            case ANY -> CpuSelectionMode.PLAYER_ONLY;
            case PLAYER_ONLY -> CpuSelectionMode.MACHINE_ONLY;
            case MACHINE_ONLY -> CpuSelectionMode.ANY;
        };
    }
}
