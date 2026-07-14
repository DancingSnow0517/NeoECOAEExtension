package cn.dancingsnow.neoecoae.client.gui.ldlib.storage;

import static cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageLayout.*;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageTextFormatter;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Draws the storage title, formed state, and performance indicator. */
public final class NEStorageHeaderPanel {
    public void draw(
            GuiGraphics g,
            Font font,
            Component title,
            NEStorageUiState state,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY) {
        Component formedLabel =
                Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component formedValue = boolText(state.formed());
        int statusWidth = font.width(formedLabel) + font.width(formedValue);
        int statusX = screenX.applyAsInt(PRIORITY_BUTTON_X - 6 - statusWidth);
        int titleX = screenX.applyAsInt(8);
        int y = screenY.applyAsInt(8);

        g.enableScissor(titleX, y - 1, Math.max(titleX, statusX - 6), y + font.lineHeight + 1);
        g.drawString(font, title, titleX, y, NELDLibStyle.DARK_TEXT_PRIMARY, false);
        g.disableScissor();
        g.drawString(font, formedLabel, statusX, y, 0xFF4A4A4A, false);
        g.drawString(
                font,
                formedValue,
                statusX + font.width(formedLabel),
                y,
                state.formed() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR,
                false);

        drawPerformance(g, font, state, screenX, screenY);
    }

    public boolean drawTooltip(
            GuiGraphics g,
            Font font,
            NEStorageUiState state,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            int mouseX,
            int mouseY) {
        int x = screenX.applyAsInt(LEFT_PANEL_X + 101);
        int y = screenY.applyAsInt(LEFT_PANEL_Y + 5);
        if (mouseX < x || mouseX >= x + 51 || mouseY < y || mouseY >= y + 9) {
            return false;
        }
        g.renderComponentTooltip(
                font,
                List.of(
                        Component.translatable("gui.neoecoae.crafting.performance"),
                        Component.literal(NEStorageTextFormatter.performanceTooltip(state.performanceAverageNanos()))),
                mouseX,
                mouseY);
        return true;
    }

    private static void drawPerformance(
            GuiGraphics g, Font font, NEStorageUiState state, IntUnaryOperator screenX, IntUnaryOperator screenY) {
        String text = NEStorageTextFormatter.performanceCorner(state.performanceAverageNanos());
        float scale = 7.0F / 9.0F;
        int rightX = screenX.applyAsInt(LEFT_PANEL_X + 152);
        int y = screenY.applyAsInt(LEFT_PANEL_Y + 5);
        g.pose().pushPose();
        g.pose().translate(rightX, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, -font.width(text), 0, NELDLibStyle.DARK_TEXT_VALUE, false);
        g.pose().popPose();
    }

    private static Component boolText(boolean value) {
        return Component.translatable(value ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no");
    }
}
