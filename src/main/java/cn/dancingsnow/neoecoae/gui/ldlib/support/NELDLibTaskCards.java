package cn.dancingsnow.neoecoae.gui.ldlib.support;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class NELDLibTaskCards {
    private static final int PROGRESS_BACKGROUND = 0xAA1F2F34;
    private static final int PROGRESS_FILL = 0xFF26A6BD;

    private NELDLibTaskCards() {}

    public static int statusColor(NECraftingRecipeUiEntry.Status status) {
        return switch (status) {
            case RUNNING -> NELDLibStyle.DARK_TEXT_SUCCESS;
            case QUEUED -> NELDLibStyle.DARK_TEXT_WARNING;
            case WAITING_OUTPUT -> NELDLibStyle.DARK_TEXT_BLUE;
        };
    }

    public static String statusKey(NECraftingRecipeUiEntry.Status status) {
        return switch (status) {
            case RUNNING -> "gui.neoecoae.crafting.task.status.running";
            case QUEUED -> "gui.neoecoae.crafting.task.status.queued";
            case WAITING_OUTPUT -> "gui.neoecoae.crafting.task.status.waiting_output";
        };
    }

    public static void drawCardRect(GuiGraphics g, int x, int y, int w, int h, int accentColor) {
        drawCardRect(g, x, y, w, h, 1.0F, accentColor);
    }

    public static void drawCardRect(GuiGraphics g, int x, int y, int w, int h, float alpha, int accentColor) {
        g.fill(x, y, x + w, y + h, withAlpha(0xFFD8D3E4, alpha));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, withAlpha(0xFF121016, alpha));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, withAlpha(0xFF4D4855, alpha));
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, withAlpha(0xFF2C2735, alpha));
        g.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, withAlpha(accentColor, alpha));
    }

    public static void drawCraftingCardRect(GuiGraphics g, int x, int y, int w, int h, float alpha, int accentColor) {
        g.fill(x, y, x + w, y + h, withAlpha(0xFFD8D3E4, alpha));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, withAlpha(0xFF17141E, alpha));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, withAlpha(0xFF2C2735, alpha));
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, withAlpha(accentColor, alpha));
    }

    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, NECraftingRecipeUiEntry entry) {
        drawProgressBar(g, x, y, w, h, entry, 1.0F);
    }

    public static void drawProgressBar(
            GuiGraphics g, int x, int y, int w, int h, NECraftingRecipeUiEntry entry, float alpha) {
        g.fill(x, y, x + w, y + h, withAlpha(PROGRESS_BACKGROUND, alpha));
        int fillW = progressWidth(entry, w);
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + h, withAlpha(PROGRESS_FILL, alpha));
        }
    }

    public static int ratioWidth(long current, long max, int fullWidth) {
        if (fullWidth <= 0 || max <= 0 || current <= 0) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(current, max));
        return (int) Math.max(1L, Math.min(fullWidth, clamped * fullWidth / max));
    }

    public static int withAlpha(int color, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        int baseAlpha = (color >>> 24) & 0xFF;
        int outAlpha = Mth.clamp(Math.round(baseAlpha * clamped), 0, 255);
        return (outAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int progressWidth(NECraftingRecipeUiEntry entry, int width) {
        int fillW = ratioWidth(Math.max(0L, entry.totalTicks() - entry.remainingTicks()), entry.totalTicks(), width);
        if (entry.status() == NECraftingRecipeUiEntry.Status.WAITING_OUTPUT) {
            return width;
        }
        if (entry.status() == NECraftingRecipeUiEntry.Status.QUEUED) {
            return 1;
        }
        return fillW;
    }
}
