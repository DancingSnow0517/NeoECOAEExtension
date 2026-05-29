package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEBaseMachineMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NEAe2TextButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
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
            addRenderableWidget(new NEAe2TextButton(
                leftPos + NENativeUiConstants.BUTTON_X_OFFSET,
                topPos + NENativeUiConstants.BUTTON_Y_OFFSET,
                NENativeUiConstants.BUTTON_WIDTH,
                NENativeUiConstants.BUTTON_HEIGHT,
                Component.translatable("gui.neoecoae.machine.test"),
                btn -> LOG.info(getTestLogMessage())
            ));
        }
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
        NENativeAe2StyleRenderer.drawAeMainPanel(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
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
            NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
        renderAdditionalLabels(guiGraphics, mouseX, mouseY);
    }

    // ── Label-value drawing helpers ──

    /** Draw a plain Component at (x, y) with a specific color. */
    protected void drawText(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    /** Draw "label: value" — label in light-gray, number value in blue-violet. */
    protected void drawLabelNumber(GuiGraphics g, Component label, long value, int x, int y) {
        Component labelColon = label.copy().append(": ");
        g.drawString(font, labelColon, x, y, NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
        int labelWidth = font.width(labelColon);
        g.drawString(font, Component.literal(formatNumber(value)),
            x + labelWidth, y, NENativeUiConstants.MACHINE_TEXT_VALUE, false);
    }

    /** Draw "label: current / max" — numbers in blue-violet, slash in light-gray. */
    protected void drawLabelNumberPair(GuiGraphics g, Component label, long current, long max, int x, int y) {
        Component labelColon = label.copy().append(": ");
        g.drawString(font, labelColon, x, y, NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
        int curX = x + font.width(labelColon);
        // current (blue-violet)
        String curStr = formatNumber(current);
        g.drawString(font, Component.literal(curStr), curX, y, NENativeUiConstants.MACHINE_TEXT_VALUE, false);
        int slashX = curX + font.width(curStr);
        // " / " (light-gray)
        g.drawString(font, Component.literal(" / "), slashX, y, NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
        int maxX = slashX + font.width(" / ");
        // max (blue-violet)
        g.drawString(font, Component.literal(formatNumber(max)), maxX, y, NENativeUiConstants.MACHINE_TEXT_VALUE, false);
    }

    /** Draw "label: current / max unit" — numbers blue-violet, slash/space/unit light-gray. */
    protected void drawLabelNumberPairUnit(GuiGraphics g, Component label, long current, long max,
                                           Component unit, int x, int y) {
        Component labelColon = label.copy().append(": ");
        g.drawString(font, labelColon, x, y, NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
        int curX = x + font.width(labelColon);
        // current (blue-violet)
        String curStr = formatNumber(current);
        g.drawString(font, Component.literal(curStr), curX, y, NENativeUiConstants.MACHINE_TEXT_VALUE, false);
        int slashX = curX + font.width(curStr);
        // " / " (light-gray)
        g.drawString(font, Component.literal(" / "), slashX, y, NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
        int maxX = slashX + font.width(" / ");
        // max (blue-violet)
        String maxStr = formatNumber(max);
        g.drawString(font, Component.literal(maxStr), maxX, y, NENativeUiConstants.MACHINE_TEXT_VALUE, false);
        int unitX = maxX + font.width(maxStr);
        // " unit" (light-gray)
        Component spacedUnit = Component.literal(" ").append(unit);
        g.drawString(font, spacedUnit, unitX, y, NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
    }

    /** Draw "label: yes/no" — label in light-gray, yes in green, no in light-gray. */
    protected void drawLabelBoolean(GuiGraphics g, Component label, boolean value, int x, int y) {
        Component labelColon = label.copy().append(": ");
        g.drawString(font, labelColon, x, y, NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
        int labelWidth = font.width(labelColon);
        Component boolText = Component.translatable(value ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no");
        int color = value ? NENativeUiConstants.MACHINE_TEXT_SUCCESS : NENativeUiConstants.MACHINE_TEXT_SECONDARY;
        g.drawString(font, boolText, x + labelWidth, y, color, false);
    }

    /** Draw a hint line in blue. */
    protected void drawHint(GuiGraphics g, Component hint, int x, int y) {
        g.drawString(font, hint, x, y, NENativeUiConstants.MACHINE_TEXT_HINT, false);
    }

    /** Format long with commas. */
    protected static String formatNumber(long value) {
        return java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(value);
    }

    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
}
