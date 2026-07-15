package cn.dancingsnow.neoecoae.client.gui.ldlib.crafting;

import static cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.*;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTextures;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import net.minecraft.network.chat.Component;

/** Owns the crafting mode status panel and its three indicator rows. */
public final class NECraftingStatusPanel {
    public void drawBackground(NECraftingRenderContext context, int mouseX, int mouseY) {
        NEHostTextures.drawPanel(
                context.graphics(),
                context.x(STATUS_AREA_X),
                context.y(STATUS_AREA_Y),
                STATUS_AREA_W,
                STATUS_AREA_H,
                mouseX,
                mouseY);
    }

    public void draw(NECraftingRenderContext context, NECraftingUiState state) {
        context.draw(
                Component.translatable("gui.neoecoae.crafting.status"),
                STATUS_AREA_X + 8,
                STATUS_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        int y = STATUS_AREA_Y + 21;
        drawRow(context, Component.translatable("gui.neoecoae.crafting.overclock"), state.overclocked(), y);
        drawRow(context, Component.translatable("gui.neoecoae.crafting.cooling_short"), state.activeCooling(), y + 15);
        drawRow(
                context,
                Component.translatable("gui.neoecoae.crafting.waste_short"),
                state.autoClearCoolingWaste(),
                y + 30);
    }

    private void drawRow(NECraftingRenderContext context, Component label, boolean enabled, int localY) {
        int x = context.x(STATUS_ROW_X);
        int y = context.y(localY);
        NELDLibClientStyle.drawDarkInsetRect(context.graphics(), x, y - 3, 13, 13);
        int color = enabled ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR;
        context.graphics().fill(x + 4, y + 1, x + 9, y + 6, color);
        Component value = Component.translatable(enabled ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off");
        int valueRight = context.x(STATUS_AREA_X + STATUS_AREA_W - STATUS_VALUE_RIGHT_PAD);
        int labelX = x + STATUS_TEXT_GAP;
        int labelMaxWidth = Math.max(8, valueRight - context.scaledWidth(value) - 4 - labelX);
        context.drawFittedAbsolute(label, labelX, y, labelMaxWidth, NELDLibStyle.DARK_TEXT_MUTED);
        context.drawRightAbsolute(value, valueRight, y, color);
    }
}
