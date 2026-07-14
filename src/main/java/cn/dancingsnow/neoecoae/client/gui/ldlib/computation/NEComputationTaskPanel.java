package cn.dancingsnow.neoecoae.client.gui.ldlib.computation;

import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.*;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTaskListRenderer;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTextures;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Stateful task list matching the LDLib2 computation host card geometry. */
public final class NEComputationTaskPanel {
    private int scrollOffset;

    public void drawBackground(
            GuiGraphics g, IntUnaryOperator screenX, IntUnaryOperator screenY, int mouseX, int mouseY) {
        NEHostTextures.drawPanel(
                g,
                screenX.applyAsInt(TASK_PANEL_X),
                screenY.applyAsInt(TASK_PANEL_Y),
                TASK_PANEL_W,
                TASK_PANEL_H,
                mouseX,
                mouseY);
    }

    public void draw(
            GuiGraphics g, Font font, IntUnaryOperator screenX, IntUnaryOperator screenY, NEComputationUiState state) {
        g.drawString(
                font,
                Component.translatable("gui.neoecoae.crafting.tasks"),
                screenX.applyAsInt(TASK_PANEL_X + 8),
                screenY.applyAsInt(TASK_PANEL_Y + 6),
                NELDLibStyle.DARK_TEXT_PRIMARY,
                false);
        Component count =
                Component.literal(NELDLibText.number(state.recipeEntries().size()));
        NELDLibClientStyle.drawRight(
                g,
                font,
                count,
                screenX.applyAsInt(TASK_PANEL_X + TASK_PANEL_W - 8),
                screenY.applyAsInt(TASK_PANEL_Y + 6),
                NELDLibStyle.DARK_TEXT_VALUE);

        scrollOffset = clampScrollOffset(scrollOffset, state.recipeEntries().size());
        if (state.recipeEntries().isEmpty()) {
            NELDLibClientStyle.drawCentered(
                    g,
                    font,
                    Component.translatable("gui.neoecoae.crafting.no_tasks"),
                    screenX.applyAsInt(TASK_PANEL_X),
                    screenY.applyAsInt(TASK_PANEL_Y + TASK_PANEL_H / 2 - 4),
                    TASK_PANEL_W,
                    NELDLibStyle.DARK_TEXT_MUTED);
            return;
        }

        g.enableScissor(
                screenX.applyAsInt(TASK_PANEL_X + 6),
                screenY.applyAsInt(TASK_CARD_Y),
                screenX.applyAsInt(TASK_PANEL_X + TASK_PANEL_W - 6),
                screenY.applyAsInt(TASK_LIST_BOTTOM_Y + 1));
        int visible = Math.min(visibleCardCount(), state.recipeEntries().size() - scrollOffset);
        for (int i = 0; i < visible; i++) {
            drawCard(
                    g,
                    font,
                    screenX,
                    screenY,
                    state.recipeEntries().get(scrollOffset + i),
                    TASK_CARD_Y + i * TASK_CARD_STRIDE);
        }
        g.disableScissor();
        NEHostTaskListRenderer.drawScrollbar(
                g,
                screenX.applyAsInt(TASK_SCROLLBAR_X),
                screenY.applyAsInt(TASK_CARD_Y),
                TASK_SCROLLBAR_W,
                Math.max(1, TASK_LIST_BOTTOM_Y - TASK_CARD_Y),
                state.recipeEntries().size(),
                visibleCardCount(),
                scrollOffset);
    }

    public boolean drawTooltip(
            GuiGraphics g,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEComputationUiState state,
            int mouseX,
            int mouseY) {
        List<NECraftingRecipeUiEntry> entries = state.recipeEntries();
        scrollOffset = clampScrollOffset(scrollOffset, entries.size());
        int visible = Math.min(visibleCardCount(), entries.size() - scrollOffset);
        for (int i = 0; i < visible; i++) {
            int y = TASK_CARD_Y + i * TASK_CARD_STRIDE;
            if (!Widget.isMouseOver(
                    screenX.applyAsInt(TASK_CARD_X), screenY.applyAsInt(y), TASK_CARD_W, TASK_CARD_H, mouseX, mouseY)) {
                continue;
            }
            NECraftingRecipeUiEntry entry = entries.get(scrollOffset + i);
            g.renderTooltip(
                    font,
                    NEHostTaskListRenderer.computationTaskTooltipLines(entry),
                    entry.output().getTooltipImage(),
                    entry.output(),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseWheel(
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEComputationUiState state,
            double mouseX,
            double mouseY,
            double wheelDelta) {
        int total = state.recipeEntries().size();
        int visible = visibleCardCount();
        if (total <= visible
                || !Widget.isMouseOver(
                        screenX.applyAsInt(TASK_PANEL_X),
                        screenY.applyAsInt(TASK_PANEL_Y),
                        TASK_PANEL_W,
                        TASK_PANEL_H,
                        mouseX,
                        mouseY)) {
            return false;
        }
        scrollOffset = clampScrollOffset(scrollOffset + (wheelDelta < 0 ? 1 : -1), total);
        return true;
    }

    public int scrollOffset() {
        return scrollOffset;
    }

    public static int visibleCardCount() {
        return NEHostTaskListRenderer.visibleCardCount(TASK_CARD_Y, TASK_LIST_BOTTOM_Y, TASK_CARD_H, TASK_CARD_STRIDE);
    }

    public static int clampScrollOffset(int value, int total) {
        return NEHostTaskListRenderer.clampScrollOffset(
                value, total, TASK_CARD_Y, TASK_LIST_BOTTOM_Y, TASK_CARD_H, TASK_CARD_STRIDE);
    }

    private static void drawCard(
            GuiGraphics g,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NECraftingRecipeUiEntry entry,
            int y) {
        NEHostTaskListRenderer.drawCard(
                g,
                font,
                entry,
                screenX.applyAsInt(TASK_CARD_X),
                screenY.applyAsInt(y),
                TASK_CARD_W,
                TASK_CARD_H,
                new NEHostTaskListRenderer.CardStyle(
                        4,
                        4,
                        24,
                        4,
                        11,
                        5,
                        24,
                        19,
                        29,
                        4,
                        34,
                        1.0F,
                        1.0F,
                        0.0F,
                        NEHostTaskListRenderer.CardVariant.COMPUTATION));
    }
}
