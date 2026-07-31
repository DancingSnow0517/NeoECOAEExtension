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
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
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
import net.minecraft.world.level.Level;

/** Coordinates computation state, server actions, inventory slots, and focused client-side host panels. */
public class NEComputationControllerWidget extends NELDLibSyncedStateWidget<NEComputationUiState> {
    public static final int UI_WIDTH = NEComputationLayout.UI_WIDTH;
    public static final int UI_HEIGHT = NEComputationLayout.UI_HEIGHT;

    private final ECOComputationSystemBlockEntity computation;
    private final Inventory playerInventory;
    private final NEComputationHeaderPanel headerPanel = new NEComputationHeaderPanel();
    private final NEComputationCapacityPanel capacityPanel = new NEComputationCapacityPanel();
    private final NEComputationTaskPanel taskPanel = new NEComputationTaskPanel();
    private NEAe2TextButtonWidget networkFrequencyButton;
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
        networkFrequencyButton = new NEAe2TextButtonWidget(
                NETWORK_FREQUENCY_BUTTON_X,
                NETWORK_FREQUENCY_BUTTON_Y,
                NETWORK_FREQUENCY_BUTTON_W,
                NETWORK_FREQUENCY_BUTTON_H,
                () -> Component.literal(Integer.toString(Math.max(0, currentState().networkFrequency()) + 1)),
                click -> {
                    if (!click.isRemote) {
                        computation.cycleNetworkFrequency();
                        syncStateNow();
                    }
                },
                () -> currentState().networkMemberCount() > 1,
                NEAe2TextButtonWidget.BackgroundStyle.TOOLBAR);
        networkFrequencyButton.setTextColors(
                NELDLibStyle.DARK_TEXT_PRIMARY,
                NELDLibStyle.DARK_TEXT_SUCCESS,
                NELDLibStyle.DARK_TEXT_MUTED);
        addWidget(networkFrequencyButton);
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
        parallelConfigurator = new NEGtceuConfiguratorTabWidget(
                PARALLEL_PANEL_X,
                PARALLEL_PANEL_Y,
                Component.translatable("gui.neoecoae.computation.parallel_control"),
                () -> currentState().configuredAccelerators(),
                this::setParallelAcceleratorsFromWidget,
                this::parallelAcceleratorLimit);
        addWidget(parallelConfigurator);

        if (hasUpgradeLayout()) {
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
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
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
        if (drawNetworkFrequencyTooltip(graphics, mouseX, mouseY)) {
            return;
        }
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
        int frequency = Math.max(0, currentState().networkFrequency()) + 1;
        graphics.renderComponentTooltip(
                font(),
                List.of(
                        Component.translatable("gui.neoecoae.host.network.frequency", frequency),
                        Component.translatable("gui.neoecoae.host.network.frequency.tooltip")),
                mouseX,
                mouseY);
        return true;
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
                Component.translatable("gui.neoecoae.computation.upgrade_slot")
                        .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_BLUE)),
                upgradeFieldGeneratorLine(),
                upgradeInfiniteComponentLine());
        graphics.renderTooltip(font(), lines, Optional.empty(), mouseX, mouseY);
        return true;
    }

    private static Component upgradeFieldGeneratorLine() {
        Component tiers = Component.empty()
                .append(coloredTier("IV", NELDLibStyle.DARK_TEXT_BLUE))
                .append(separator(" / "))
                .append(coloredTier("LuV", NELDLibStyle.DARK_TEXT_VALUE))
                .append(separator(" / "))
                .append(coloredTier("ZPM", NELDLibStyle.DARK_TEXT_ORANGE))
                .append(separator(" / "))
                .append(coloredTier("UV", NELDLibStyle.DARK_TEXT_SUCCESS));
        return Component.translatable(
                        "gui.neoecoae.computation.upgrade_slot.field_generators",
                        tiers,
                        coloredValue(NEComputationUpgradeRules.FIELD_GENERATOR_COUNT))
                .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED));
    }

    private static Component upgradeInfiniteComponentLine() {
        return Component.translatable(
                        "gui.neoecoae.computation.upgrade_slot.infinite_component",
                        coloredValue(NEComputationUpgradeRules.INFINITE_COMPONENT_COUNT))
                .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED));
    }

    private static Component coloredTier(String tier, int color) {
        return Component.literal(tier).withStyle(style -> style.withColor(color));
    }

    private static Component coloredValue(int value) {
        return Component.literal(Integer.toString(value))
                .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_WARNING));
    }

    private static Component separator(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED));
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
        if (parallelConfigurator == null) {
            return false;
        }
        int limit = parallelAcceleratorLimit();
        if (parallelConfigurator.isToggleHovered(mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.computation.parallel_control"),
                            limit > 0
                                    ? Component.translatable("gui.neoecoae.computation.parallel_control.enabled")
                                    : Component.translatable(
                                            "gui.neoecoae.computation.parallel_control.requires_infinite")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (parallelConfigurator.isInputHovered(mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.computation.parallel_input.tooltip", limit)),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private void setParallelAcceleratorsFromWidget(int value) {
        Level level = computation.getLevel();
        if (level == null || level.isClientSide) {
            // The configured value is owned by the server; the client only mirrors synced state.
            return;
        }
        // Sync either way. On success this publishes the value the cluster applied. On rejection --
        // which happens when the client clamped against an accelerator limit that has since shrunk --
        // it overwrites the value the input optimistically displays with the one the server holds,
        // so the field cannot keep showing a number that was never accepted. The snap-back is the
        // rejection feedback: the text field fires per keystroke, so a message or flash here would
        // repeat for every character typed.
        computation.setParallelAccelerators(value);
        syncStateNow();
    }

    private int parallelAcceleratorLimit() {
        return Math.max(
                0,
                Math.min(
                        NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS,
                        currentState().acceleratorLimit()));
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
