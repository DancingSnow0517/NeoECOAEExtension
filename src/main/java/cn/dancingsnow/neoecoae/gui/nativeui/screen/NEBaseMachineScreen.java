package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEBaseMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base screen for all ECO machine native UIs.
 * <p>
 * Provides the complete boilerplate: window size, background fill,
 * title / "UI rebuilding" / "Native UI active" labels,
 * a Test button, and the standard {@link #render} pipeline.
 * </p>
 * <p>
 * Concrete subclasses only supply a {@link NEMachineScreenConfig} so
 * the test-button log message is machine-specific.
 * </p>
 *
 * @param <T> the menu type
 */
public abstract class NEBaseMachineScreen<T extends NEBaseMachineMenu>
    extends AbstractContainerScreen<T> {

    private static final Logger LOG = LoggerFactory.getLogger(NENativeUiConstants.LOGGER_NAME);

    protected final NEMachineScreenConfig config;

    protected NEBaseMachineScreen(T menu, Inventory playerInv, Component title,
                                  NEMachineScreenConfig config) {
        super(menu, playerInv, title);
        this.config = config;
        this.imageWidth = NENativeUiConstants.UI_WIDTH;
        this.imageHeight = NENativeUiConstants.UI_HEIGHT;
    }

    // ── Test button ──

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(
            Component.literal(NENativeUiConstants.TEST_BUTTON_TEXT),
            btn -> LOG.info(getTestLogMessage())
        ).pos(
            leftPos + NENativeUiConstants.BUTTON_X_OFFSET,
            topPos + NENativeUiConstants.BUTTON_Y_OFFSET
        ).size(
            NENativeUiConstants.BUTTON_WIDTH,
            NENativeUiConstants.BUTTON_HEIGHT
        ).build());
    }

    /**
     * Subclasses can override to customise the test-button log message.
     * Default uses {@link NEMachineScreenConfig#buildLogMessage()}.
     */
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
        guiGraphics.fill(leftPos, topPos,
            leftPos + imageWidth, topPos + imageHeight,
            NENativeUiConstants.BG_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title,
            NENativeUiConstants.TITLE_X, NENativeUiConstants.TITLE_Y,
            NENativeUiConstants.TITLE_COLOR);
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.ui.rebuilding"),
            NENativeUiConstants.TITLE_X, NENativeUiConstants.REBUILDING_Y,
            NENativeUiConstants.REBUILDING_TEXT_COLOR);
        guiGraphics.drawString(font,
            Component.literal(NENativeUiConstants.ACTIVE_TEXT),
            NENativeUiConstants.TITLE_X, NENativeUiConstants.ACTIVE_Y,
            NENativeUiConstants.ACTIVE_TEXT_COLOR);
    }
}
