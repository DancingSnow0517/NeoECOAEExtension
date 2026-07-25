package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.*;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.NEComputationUpgradeRules;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationCapacityPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationHeaderPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationTaskPanel;
import cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Coordinates computation state, server actions, inventory slots, and focused client-side host panels. */
public class NEComputationControllerWidget extends NELDLibSyncedStateWidget<NEComputationUiState> {
    public static final int UI_WIDTH = NEComputationLayout.UI_WIDTH;
    public static final int UI_HEIGHT = NEComputationLayout.UI_HEIGHT;

    private final ECOComputationSystemBlockEntity computation;
    private final Inventory playerInventory;
    private final NEComputationHeaderPanel headerPanel = new NEComputationHeaderPanel();
    private final NEComputationCapacityPanel capacityPanel = new NEComputationCapacityPanel();
    private final NEComputationTaskPanel taskPanel = new NEComputationTaskPanel();
    private NEAe2IconButtonWidget cpuModeButton;
    private NEGtceuConfiguratorTabWidget parallelConfigurator;

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
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected boolean shouldDrawBasePanel() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        cpuModeButton = new NEAe2IconButtonWidget(
                mainX(CPU_BUTTON_X), CPU_BUTTON_Y, CPU_BUTTON_W, CPU_BUTTON_H, cpuModeIcon(), click -> {
                    if (!click.isRemote) {
                        NEComputationCluster cluster = computation.getCluster();
                        if (cluster != null) {
                            cluster.cycleSelectionMode();
                        } else {
                            computation.setCpuSelectionMode(nextCpuSelectionMode(computation.getCpuSelectionMode()));
                        }
                        computation.markComputationStatsDirty();
                        computation.updateInfos();
                        syncStateNow();
                    }
                });
        addWidget(cpuModeButton);
        if (hasUpgradeLayout()) {
            parallelConfigurator = new NEGtceuConfiguratorTabWidget(
                    PARALLEL_PANEL_X,
                    PARALLEL_PANEL_Y,
                    Component.translatable("gui.neoecoae.computation.parallel_control"),
                    () -> currentState().configuredAccelerators(),
                    computation::setParallelAccelerators,
                    () -> currentState().infiniteCapacity());
            addWidget(parallelConfigurator);

            addWidget(new SlotWidget(
                            new NEForgeItemTransfer(
                                    computation.getComputationUpgradeItemHandler(),
                                    computation::onComputationUpgradeSlotChanged),
                            0,
                            mainX(COMPUTATION_UPGRADE_SLOT_X),
                            COMPUTATION_UPGRADE_SLOT_Y,
                            true,
                            true)
                    .setBackgroundTexture(IGuiTexture.EMPTY));
        }
        NEPlayerInventoryWidgets.addPlayerInventorySlots(
                this, playerInventory, mainX(PLAYER_INV_X), PLAYER_INV_Y, PLAYER_HOTBAR_Y);
        updateParallelControls();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateParallelControls();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        updateParallelControls();
        NELDLibAe2StyleRenderer.drawAeMainPanel(graphics, absX(MAIN_X), absY(0), BASE_UI_WIDTH, UI_HEIGHT);
        cpuModeButton.setIcon(cpuModeIcon());
        capacityPanel.drawBackground(graphics, mainScreenX(), this::absY, currentState(), mouseX, mouseY);
        if (hasUpgradeLayout()) {
            NELDLibAe2StyleRenderer.drawAeSlot(
                    graphics, absX(mainX(COMPUTATION_UPGRADE_SLOT_X)), absY(COMPUTATION_UPGRADE_SLOT_Y));
        }
        taskPanel.drawBackground(graphics, mainScreenX(), this::absY, mouseX, mouseY);
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
        if (drawParallelTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (drawUpgradeTooltip(graphics, mouseX, mouseY)) {
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

    private boolean drawUpgradeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!hasUpgradeLayout()
                || !isMouseIn(mainX(COMPUTATION_UPGRADE_SLOT_X), COMPUTATION_UPGRADE_SLOT_Y, 18, 18, mouseX, mouseY)) {
            return false;
        }
        ItemStack stack = computation.getComputationUpgradeItemHandler().getStackInSlot(0);
        if (!stack.isEmpty() && NEComputationUpgradeRules.isValid(stack)) {
            return false;
        }
        List<Component> lines = List.of(
                Component.translatable("gui.neoecoae.computation.upgrade_slot"),
                Component.translatable("gui.neoecoae.computation.upgrade_slot.field_generators"),
                Component.translatable("gui.neoecoae.computation.upgrade_slot.infinite_component"));
        graphics.renderTooltip(font(), lines, Optional.empty(), mouseX, mouseY);
        return true;
    }

    private boolean hasUpgradeLayout() {
        return NEComputationUpgradeRules.isGregTechAvailable();
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (taskPanel.mouseWheel(mainScreenX(), this::absY, currentState(), mouseX, mouseY, wheelDelta)) {
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    private boolean drawParallelTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (parallelConfigurator != null && parallelConfigurator.isToggleHovered(mouseX, mouseY)) {
            Component status = currentState().infiniteCapacity()
                    ? Component.translatable("gui.neoecoae.computation.parallel_control.enabled")
                    : Component.translatable("gui.neoecoae.computation.parallel_control.requires_infinite");
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.computation.parallel_control"), status),
                    mouseX,
                    mouseY);
            return true;
        }
        if (parallelConfigurator != null && parallelConfigurator.isInputHovered(mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.computation.parallel_input.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private void updateParallelControls() {
        boolean available = hasUpgradeLayout() && currentState().infiniteCapacity();
        if (parallelConfigurator != null) {
            parallelConfigurator.setEnabled(available);
        }
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

    static CpuSelectionMode nextCpuSelectionMode(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> CpuSelectionMode.PLAYER_ONLY;
            case PLAYER_ONLY -> CpuSelectionMode.MACHINE_ONLY;
            case MACHINE_ONLY -> CpuSelectionMode.ANY;
        };
    }
}
