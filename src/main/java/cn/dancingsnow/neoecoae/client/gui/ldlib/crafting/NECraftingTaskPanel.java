package cn.dancingsnow.neoecoae.client.gui.ldlib.crafting;

import static cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.*;

import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTaskListRenderer;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTextures;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Owns crafting task list rendering, scrolling, tooltips, and card animation state. */
public final class NECraftingTaskPanel {
    private static final long FADE_MS = 360L;
    private static final long MOVE_MS = 140L;

    private final Map<String, CardAnimation> animations = new LinkedHashMap<>();
    private int scrollOffset;
    private int lastScrollOffset;

    public void drawBackground(NECraftingRenderContext context, int mouseX, int mouseY) {
        NEHostTextures.drawPanel(
                context.graphics(),
                context.x(TASK_PANEL_X),
                context.y(TASK_PANEL_Y),
                TASK_PANEL_W,
                TASK_PANEL_H,
                mouseX,
                mouseY);
    }

    public void draw(NECraftingRenderContext context, NECraftingUiState state) {
        context.draw(
                Component.translatable("gui.neoecoae.crafting.tasks"),
                TASK_PANEL_X + 8,
                TASK_PANEL_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        context.drawRight(
                Component.literal(NELDLibText.number(state.recipeEntries().size())),
                TASK_PANEL_X + TASK_PANEL_W - 8,
                TASK_PANEL_Y + 5,
                NELDLibStyle.DARK_TEXT_VALUE);

        scrollOffset = clamp(scrollOffset, state.recipeEntries().size());
        List<CardAnimation> cards = updateAnimations(state);
        if (cards.isEmpty() && state.recipeEntries().isEmpty()) {
            Component text = Component.translatable("gui.neoecoae.crafting.no_tasks");
            int width = TASK_PANEL_W - 12;
            context.drawAbsolute(
                    text,
                    context.x(TASK_PANEL_X + 6) + (width - context.scaledWidth(text)) / 2,
                    context.y(TASK_PANEL_Y + 42),
                    NELDLibStyle.DARK_TEXT_MUTED);
            return;
        }

        context.graphics()
                .enableScissor(
                        context.x(TASK_CARD_X),
                        context.y(TASK_CARD_Y),
                        context.x(TASK_CARD_X + TASK_CARD_W),
                        context.y(TASK_LIST_BOTTOM_Y + 1));
        try {
            for (CardAnimation card : cards) {
                drawCard(context, card);
            }
        } finally {
            context.graphics().disableScissor();
        }
        NEHostTaskListRenderer.drawScrollbar(
                context.graphics(),
                context.x(TASK_PANEL_X + TASK_PANEL_W - 5),
                context.y(TASK_CARD_Y),
                TASK_SCROLLBAR_W,
                Math.max(1, TASK_LIST_BOTTOM_Y - TASK_CARD_Y - 1),
                state.recipeEntries().size(),
                visibleCount(),
                scrollOffset);
    }

    public boolean drawTooltip(NECraftingRenderContext context, NECraftingUiState state, int mouseX, int mouseY) {
        scrollOffset = clamp(scrollOffset, state.recipeEntries().size());
        for (CardAnimation card : animations.values()) {
            int y = Math.round(card.y);
            if (card.alpha < 0.35F
                    || card.exiting
                    || !contains(context, TASK_CARD_X, y, TASK_CARD_W, TASK_CARD_H, mouseX, mouseY)) {
                continue;
            }
            NECraftingRecipeUiEntry entry = card.entry;
            List<Component> lines =
                    NEHostTaskListRenderer.tooltipLines(entry, true, false, NECraftingTaskPanel::formatTime);
            context.graphics()
                    .renderTooltip(
                            context.font(), lines, entry.output().getTooltipImage(), entry.output(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseWheel(
            int originX, int originY, NECraftingUiState state, double mouseX, double mouseY, double delta) {
        int total = state.recipeEntries().size();
        if (mouseX >= originX + TASK_PANEL_X
                && mouseX < originX + TASK_PANEL_X + TASK_PANEL_W
                && mouseY >= originY + TASK_PANEL_Y
                && mouseY < originY + TASK_PANEL_Y + TASK_PANEL_H
                && total > visibleCount()) {
            scrollOffset = clamp(scrollOffset + (delta < 0 ? 1 : -1), total);
            return true;
        }
        return false;
    }

    private List<CardAnimation> updateAnimations(NECraftingUiState state) {
        long now = Util.getMillis();
        Set<String> activeKeys = new HashSet<>();
        int total = state.recipeEntries().size();
        int visible = visibleCount();
        int scrollDelta = scrollOffset - lastScrollOffset;
        lastScrollOffset = scrollOffset;
        int first = Math.max(0, scrollOffset - 1);
        int last = Math.min(total, scrollOffset + visible + 1);
        for (int index = first; index < last; index++) {
            NECraftingRecipeUiEntry entry = state.recipeEntries().get(index);
            String key = entry.id() == null || entry.id().isBlank() ? "task:" + index : entry.id();
            activeKeys.add(key);
            int targetY = TASK_CARD_Y + (index - scrollOffset) * TASK_CARD_STRIDE;
            CardAnimation animation = animations.get(key);
            if (animation == null) {
                float offset = scrollDelta > 0 ? TASK_CARD_STRIDE : scrollDelta < 0 ? -TASK_CARD_STRIDE : 5.0F;
                animation = new CardAnimation(entry, targetY, offset);
                animations.put(key, animation);
            }
            animation.entry = entry;
            animation.targetY = targetY;
            animation.exiting = false;
        }
        Iterator<Map.Entry<String, CardAnimation>> iterator =
                animations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CardAnimation> entry = iterator.next();
            CardAnimation animation = entry.getValue();
            if (!activeKeys.contains(entry.getKey()) && !animation.exiting) {
                animation.exiting = true;
                animation.exitStartedMs = now;
            }
            animation.update(now);
            if (animation.exiting && animation.alpha <= 0.02F) {
                iterator.remove();
            }
        }
        List<CardAnimation> cards = new ArrayList<>(animations.values());
        cards.sort(Comparator.comparingDouble(card -> card.y));
        return cards;
    }

    private void drawCard(NECraftingRenderContext context, CardAnimation card) {
        int y = Math.round(card.y);
        if (y + TASK_CARD_H < TASK_CARD_Y || y > TASK_LIST_BOTTOM_Y) {
            return;
        }
        NEHostTaskListRenderer.drawCard(
                context.graphics(),
                context.font(),
                card.entry,
                context.x(TASK_CARD_X),
                context.y(y),
                TASK_CARD_W,
                TASK_CARD_H,
                new NEHostTaskListRenderer.CardStyle(
                        3,
                        0,
                        20,
                        3,
                        3,
                        4,
                        20,
                        TASK_CARD_H - 4,
                        25,
                        2,
                        28,
                        NECraftingRenderContext.TEXT_SCALE,
                        Mth.clamp(card.alpha, 0.0F, 1.0F),
                        0.22F,
                        NEHostTaskListRenderer.CardVariant.CRAFTING));
    }

    private int visibleCount() {
        return NEHostTaskListRenderer.visibleCardCount(TASK_CARD_Y, TASK_LIST_BOTTOM_Y, TASK_CARD_H, TASK_CARD_STRIDE);
    }

    private int clamp(int value, int total) {
        return NEHostTaskListRenderer.clampScrollOffset(
                value, total, TASK_CARD_Y, TASK_LIST_BOTTOM_Y, TASK_CARD_H, TASK_CARD_STRIDE);
    }

    private boolean contains(
            NECraftingRenderContext context, int localX, int localY, int width, int height, int mouseX, int mouseY) {
        int x = context.x(localX);
        int y = context.y(localY);
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String formatTime(long ticks) {
        long safe = Math.max(0L, ticks);
        if (safe < 20L) {
            return safe + "t";
        }
        double seconds = safe / 20.0D;
        if (seconds < 60.0D) {
            return String.format(Locale.US, "%.1fs", seconds);
        }
        long wholeSeconds = Math.round(seconds);
        return (wholeSeconds / 60L) + "m " + (wholeSeconds % 60L) + "s";
    }

    private static final class CardAnimation {
        private NECraftingRecipeUiEntry entry;
        private float y;
        private int targetY;
        private float alpha;
        private long lastUpdateMs;
        private boolean exiting;
        private long exitStartedMs;

        private CardAnimation(NECraftingRecipeUiEntry entry, int targetY, float entryOffset) {
            this.entry = entry;
            this.targetY = targetY;
            this.lastUpdateMs = Util.getMillis();
            this.y = targetY + entryOffset;
        }

        private void update(long nowMs) {
            long elapsed = Math.max(0L, Math.min(1000L, nowMs - lastUpdateMs));
            lastUpdateMs = nowMs;
            float move = MOVE_MS <= 0L ? 1.0F : Mth.clamp((float) elapsed / MOVE_MS, 0.0F, 1.0F);
            y += (targetY - y) * move;
            if (Math.abs(targetY - y) < 0.25F) {
                y = targetY;
            }
            if (exiting) {
                alpha = 1.0F - Mth.clamp((float) (nowMs - exitStartedMs) / FADE_MS, 0.0F, 1.0F);
            } else {
                alpha = Math.min(1.0F, alpha + Mth.clamp((float) elapsed / FADE_MS, 0.0F, 1.0F));
            }
        }
    }
}
