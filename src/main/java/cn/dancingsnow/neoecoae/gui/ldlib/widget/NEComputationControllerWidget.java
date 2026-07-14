package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.*;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationCapacityPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationHeaderPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationTaskPanel;
import cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

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
    protected void initLdWidgets() {
        cpuModeButton = new NEAe2IconButtonWidget(
                CPU_BUTTON_X, CPU_BUTTON_Y, CPU_BUTTON_W, CPU_BUTTON_H, cpuModeIcon(), click -> {
                    if (!click.isRemote) {
                        NEComputationCluster cluster = computation.getCluster();
                        if (cluster != null) {
                            cluster.cycleSelectionMode();
                            computation.markComputationStatsDirty();
                            computation.updateInfos();
                            syncStateNow();
                        }
                    }
                });
        addWidget(cpuModeButton);
        NEPlayerInventoryWidgets.addPlayerInventorySlots(
                this, playerInventory, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        cpuModeButton.setIcon(cpuModeIcon());
        capacityPanel.drawBackground(graphics, this::absX, this::absY, currentState(), mouseX, mouseY);
        taskPanel.drawBackground(graphics, this::absX, this::absY, mouseX, mouseY);
        NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                graphics, this::absX, this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        headerPanel.draw(graphics, font(), title, currentState(), this::absX, this::absY);
        capacityPanel.drawForeground(graphics, font(), this::absX, this::absY, currentState());
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.common.inventory"),
                PLAYER_INV_X,
                PLAYER_INV_LABEL_Y,
                TEXT_MUTED);
        taskPanel.draw(graphics, font(), this::absX, this::absY, currentState());
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (taskPanel.drawTooltip(graphics, font(), this::absX, this::absY, currentState(), mouseX, mouseY)) {
            return;
        }
        if (headerPanel.drawTooltip(graphics, font(), currentState(), this::absX, this::absY, mouseX, mouseY)) {
            return;
        }
        capacityPanel.drawTooltip(graphics, font(), this::absX, this::absY, currentState(), mouseX, mouseY);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (taskPanel.mouseWheel(this::absX, this::absY, currentState(), mouseX, mouseY, wheelDelta)) {
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    private Icon cpuModeIcon() {
        return switch (currentState().cpuSelectionMode()) {
            case PLAYER_ONLY -> Icon.CRAFT_HAMMER;
            case MACHINE_ONLY -> Icon.BACKGROUND_WIRELESS_TERM;
            case ANY -> Icon.TYPE_FILTER_ALL;
        };
    }
}
