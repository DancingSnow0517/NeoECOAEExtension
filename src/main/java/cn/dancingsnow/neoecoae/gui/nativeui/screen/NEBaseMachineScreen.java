package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEBaseMachineMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NETexturedButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base screen for all ECO machine native UIs.
 * <p>
 * Uses nine-slice project GUI assets for background and buttons.
 * No LDLib dependency.
 * </p>
 *
 * @param <T> the menu type
 */
public abstract class NEBaseMachineScreen<T extends NEBaseMachineMenu>
    extends AbstractContainerScreen<T> {

    private static final Logger LOG = LoggerFactory.getLogger(NENativeUiConstants.LOGGER_NAME);
    private static final ResourceLocation TEX_BACKGROUND = NeoECOAE.id("textures/gui/background.png");
    private static final int TEX_BG_SIZE = 16;
    private static final int BG_LEFT = 2;
    private static final int BG_TOP = 2;
    private static final int BG_RIGHT = 2;
    private static final int BG_BOTTOM = 4;

    protected final NEMachineScreenConfig config;

    protected NEBaseMachineScreen(T menu, Inventory playerInv, Component title,
                                  NEMachineScreenConfig config) {
        super(menu, playerInv, title);
        this.config = config;
        this.imageWidth = NENativeUiConstants.UI_WIDTH;
        this.imageHeight = NENativeUiConstants.UI_HEIGHT;
    }

    /** Returns a translatable yes/no component for boolean status display. */
    protected Component boolText(boolean value) {
        return Component.translatable(value ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no");
    }

    // ── Buttons ──

    @Override
    protected void init() {
        super.init();
        if (config.showTestButton()) {
            addRenderableWidget(new NETexturedButton(
                leftPos + NENativeUiConstants.BUTTON_X_OFFSET,
                topPos + NENativeUiConstants.BUTTON_Y_OFFSET,
                NENativeUiConstants.BUTTON_WIDTH,
                NENativeUiConstants.BUTTON_HEIGHT,
                Component.translatable("gui.neoecoae.machine.test"),
                btn -> LOG.info(getTestLogMessage())
            ));
        }

        if (shouldShowCraftingEntryButton()) {
            addRenderableWidget(new NETexturedButton(
                leftPos - 28, topPos + 4, 24, 20,
                Component.translatable("gui.neoecoae.machine.open_crafting"),
                btn -> NENetwork.CHANNEL.sendToServer(
                    new NENetwork.NEOpenCraftingUiPacket(menu.getMachinePos()))
            ));
        }
    }

    protected boolean shouldShowCraftingEntryButton() {
        return false;
    }

    protected String getTestLogMessage() {
        return config.buildLogMessage();
    }

    // ── Render pipeline ──

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        NENineSliceRenderer.drawPanel(guiGraphics, TEX_BACKGROUND,
            leftPos, topPos, imageWidth, imageHeight,
            TEX_BG_SIZE, TEX_BG_SIZE,
            BG_LEFT, BG_TOP, BG_RIGHT, BG_BOTTOM);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        String titleText = title.getString();
        Component displayTitle = title;
        int maxTitleWidth = imageWidth - 16;
        if (font.width(titleText) > maxTitleWidth) {
            String truncated = font.plainSubstrByWidth(titleText, maxTitleWidth - 10) + "…";
            displayTitle = Component.literal(truncated);
        }
        guiGraphics.drawString(font, displayTitle,
            NENativeUiConstants.TITLE_X, NENativeUiConstants.TITLE_Y,
            NENativeUiConstants.TITLE_COLOR);
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.ui_rebuilding"),
            NENativeUiConstants.TITLE_X, NENativeUiConstants.REBUILDING_Y,
            NENativeUiConstants.REBUILDING_TEXT_COLOR);
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.native_ui_active"),
            NENativeUiConstants.TITLE_X, NENativeUiConstants.ACTIVE_Y,
            NENativeUiConstants.ACTIVE_TEXT_COLOR);
        renderAdditionalLabels(guiGraphics, mouseX, mouseY);
    }

    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
}
