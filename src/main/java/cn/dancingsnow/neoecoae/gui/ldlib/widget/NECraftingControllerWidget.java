package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.*;

import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.crafting.NECraftingGaugePanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.crafting.NECraftingHeaderPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.crafting.NECraftingRenderContext;
import cn.dancingsnow.neoecoae.client.gui.ldlib.crafting.NECraftingStatsPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.crafting.NECraftingStatusPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.crafting.NECraftingTaskPanel;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostSideButtonRenderer;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTextures;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * Coordinates crafting state, server actions, inventory slots, and focused client-side host panels.
 */
public class NECraftingControllerWidget extends NELDLibSyncedStateWidget<NECraftingUiState> {
    public static final int UI_WIDTH = cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.UI_WIDTH;
    public static final int UI_HEIGHT = cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.UI_HEIGHT;
    private static final int SIDE_BUTTON_COUNT = 5;

    private final ECOCraftingSystemBlockEntity crafting;
    private final Inventory playerInventory;
    private final Player player;
    private final NECraftingHeaderPanel headerPanel = new NECraftingHeaderPanel();
    private final NECraftingStatusPanel statusPanel = new NECraftingStatusPanel();
    private final NECraftingStatsPanel statsPanel = new NECraftingStatsPanel();
    private final NECraftingGaugePanel gaugePanel = new NECraftingGaugePanel();
    private final NECraftingTaskPanel taskPanel = new NECraftingTaskPanel();
    private final NEAe2IconButtonWidget[] toolbarButtons = new NEAe2IconButtonWidget[3];

    public NECraftingControllerWidget(ECOCraftingSystemBlockEntity crafting, Player player) {
        super(
                crafting.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                NECraftingUiState.empty(crafting.getBlockPos()),
                crafting::createCraftingUiState,
                NELDLibStateCodecs::writeCrafting,
                NELDLibStateCodecs::readCrafting,
                10);
        this.crafting = crafting;
        this.playerInventory = player.getInventory();
        this.player = player;
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
        addWidget(new NEAe2IconButtonWidget(-17, NEHostSideButtonRenderer.buttonY(0), 16, 16, Icon.HELP, click -> {
                    if (!click.isRemote && net.minecraftforge.fml.ModList.get().isLoaded("guideme")) {
                        guideme.GuidesCommon.openGuide(
                                player,
                                appeng.core.AppEng.makeId("guide"),
                                guideme.PageAnchor.parse("neoecoae:neoecoae_intro/crafting_system.md"));
                    }
                })
                .useEcoButton());
        addWidget(new NEAe2IconButtonWidget(
                        -17, NEHostSideButtonRenderer.buttonY(4), 16, 16, Icon.SCHEDULING_ROUND_ROBIN, click -> {
                            if (!click.isRemote && (click.button == 0 || click.button == 1)) {
                                crafting.adjustNetworkFrequency(click.button == 0 ? 1 : -1);
                                syncStateNow();
                            }
                        })
                .useEcoButton());
        addToolbarButton(0, click -> {
            if (!click.isRemote) {
                crafting.toggleOverclocked();
                syncStateNow();
            }
        });
        addToolbarButton(1, click -> {
            if (!click.isRemote) {
                crafting.toggleActiveCooling();
                syncStateNow();
            }
        });
        addToolbarButton(2, click -> {
            if (!click.isRemote) {
                crafting.toggleAutoClearCoolingWaste();
                syncStateNow();
            }
        });
        NEPlayerInventoryWidgets.addPlayerInventorySlots(
                this, playerInventory, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NEHostTextures.drawHostBackground(graphics, getPositionX(), getPositionY(), UI_WIDTH, UI_HEIGHT);
        NEHostSideButtonRenderer.drawLeft(graphics, getPositionX(), getPositionY(), SIDE_BUTTON_COUNT, mouseX, mouseY);
        NECraftingUiState state = currentState();
        NECraftingRenderContext context = context(graphics);
        updateToolbarIcons(state);
        statusPanel.drawBackground(context, mouseX, mouseY);
        statsPanel.drawBackground(context, state, mouseX, mouseY);
        gaugePanel.drawBackground(context, state, mouseX, mouseY);
        NEPlayerInventoryWidgets.drawPlayerInventoryFrames(
                graphics, this::absX, this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
        NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                graphics, this::absX, this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
        taskPanel.drawBackground(context, mouseX, mouseY);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NECraftingUiState state = currentState();
        NECraftingRenderContext context = context(graphics);
        headerPanel.draw(context, title, state);
        statusPanel.draw(context, state);
        statsPanel.draw(context, state);
        gaugePanel.draw(context);
        context.draw(
                Component.translatable("gui.neoecoae.common.inventory"), PLAYER_INV_X, PLAYER_INV_LABEL_Y, TEXT_MUTED);
        taskPanel.draw(context, state);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        NECraftingRenderContext context = context(graphics);
        if (isMouseIn(-17, NEHostSideButtonRenderer.buttonY(0), 16, 16, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(
                            ButtonToolTips.OpenGuide.text().withStyle(style -> style.withColor(0xFFFFFF)),
                            ButtonToolTips.OpenGuideDetail.text().withStyle(net.minecraft.ChatFormatting.GRAY)),
                    mouseX,
                    mouseY);
            return;
        }
        if (drawToolbarTooltip(graphics, state, mouseX, mouseY)) {
            return;
        }
        if (drawNetworkFrequencyTooltip(graphics, state, mouseX, mouseY)) {
            return;
        }
        if (gaugePanel.drawTooltip(context, state, mouseX, mouseY)) {
            return;
        }
        if (taskPanel.drawTooltip(context, state, mouseX, mouseY)) {
            return;
        }
        statsPanel.drawTooltip(context, state, mouseX, mouseY);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (taskPanel.mouseWheel(getPositionX(), getPositionY(), currentState(), mouseX, mouseY, wheelDelta)) {
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    private void addToolbarButton(int index, Consumer<ClickData> action) {
        toolbarButtons[index] = new NEAe2IconButtonWidget(
                        -17,
                        NEHostSideButtonRenderer.buttonY(index + 1),
                        16,
                        16,
                        toolbarIcon(currentState(), index),
                        action)
                .useEcoButton();
        addWidget(toolbarButtons[index]);
    }

    private void updateToolbarIcons(NECraftingUiState state) {
        for (int index = 0; index < toolbarButtons.length; index++) {
            if (toolbarButtons[index] != null) {
                toolbarButtons[index].setIcon(toolbarIcon(state, index));
            }
        }
    }

    private boolean drawToolbarTooltip(GuiGraphics graphics, NECraftingUiState state, int mouseX, int mouseY) {
        for (int index = 0; index < toolbarButtons.length; index++) {
            int x = -17;
            if (!isMouseIn(x, NEHostSideButtonRenderer.buttonY(index + 1), 16, 16, mouseX, mouseY)) {
                continue;
            }
            graphics.renderComponentTooltip(
                    font(), List.of(Component.translatable(toolbarTooltipKey(state, index))), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private boolean drawNetworkFrequencyTooltip(GuiGraphics graphics, NECraftingUiState state, int mouseX, int mouseY) {
        if (!isMouseIn(-17, NEHostSideButtonRenderer.buttonY(4), 16, 16, mouseX, mouseY)) {
            return false;
        }
        int frequency = state.networkFrequency();
        graphics.renderComponentTooltip(
                font(),
                List.of(Component.translatable("gui.neoecoae.host.network_frequency.cycle", frequency)),
                mouseX,
                mouseY);
        return true;
    }

    private NECraftingRenderContext context(GuiGraphics graphics) {
        return new NECraftingRenderContext(graphics, font(), this::absX, this::absY);
    }

    private static Icon toolbarIcon(NECraftingUiState state, int index) {
        return switch (index) {
            case 0 -> state.overclocked() ? Icon.LEVEL_ENERGY : Icon.POWER_UNIT_AE;
            case 1 -> state.activeCooling() ? Icon.FLUID_SUBSTITUTION_ENABLED : Icon.FLUID_SUBSTITUTION_DISABLED;
            default -> state.autoClearCoolingWaste() ? Icon.CONDENSER_OUTPUT_TRASH : Icon.BACKGROUND_TRASH;
        };
    }

    private static String toolbarTooltipKey(NECraftingUiState state, int index) {
        return switch (index) {
            case 0 -> state.overclocked()
                    ? "gui.neoecoae.crafting.overclock.on"
                    : "gui.neoecoae.crafting.overclock.off";
            case 1 -> state.activeCooling()
                    ? "gui.neoecoae.crafting.active_cooling.on"
                    : "gui.neoecoae.crafting.active_cooling.off";
            default -> state.autoClearCoolingWaste()
                    ? "gui.neoecoae.crafting.auto_clear_coolant.on"
                    : "gui.neoecoae.crafting.auto_clear_coolant.off";
        };
    }
}
