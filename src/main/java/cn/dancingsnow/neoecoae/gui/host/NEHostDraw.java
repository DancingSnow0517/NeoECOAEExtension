package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class NEHostDraw {
    static final int TEXT = 0x263238;
    static final int MUTED = 0x7d8a91;
    static final int PANEL = 0xff241f2b;
    static final int PANEL_DARK = 0xff17141e;
    static final int BORDER = 0xffd8d3e4;
    static final int SUCCESS = 0xff1f9d55;
    static final int WARNING = 0xffd58a18;
    static final int BLUE = 0xff2688c9;
    static final int ERROR = 0xffd13f3f;

    private NEHostDraw() {
    }

    static void rect(GUIContext context, float x, float y, float w, float h, int color) {
        context.graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    static void inset(GUIContext context, float x, float y, float w, float h) {
        rect(context, x, y, w, h, BORDER);
        rect(context, x + 1, y + 1, w - 2, h - 2, PANEL);
        rect(context, x + 2, y + 2, w - 4, h - 4, PANEL_DARK);
    }

    static void text(GUIContext context, Component text, float x, float y, int color) {
        context.graphics.drawString(context.mc.font, text, Math.round(x), Math.round(y), color, false);
    }

    static void text(GUIContext context, String text, float x, float y, int color) {
        context.graphics.drawString(context.mc.font, text, Math.round(x), Math.round(y), color, false);
    }

    static void rightText(GUIContext context, String text, float rightX, float y, int color) {
        text(context, text, rightX - context.mc.font.width(text), y, color);
    }

    static void centeredText(GUIContext context, Component text, float x, float y, float width, int color) {
        text(context, text, x + (width - context.mc.font.width(text)) / 2.0f, y, color);
    }

    static void progress(GUIContext context, float x, float y, float w, float h, long used, long total, int color) {
        inset(context, x, y, w, h);
        float innerX = x + 3;
        float innerY = y + 3;
        float innerW = Math.max(0, w - 6);
        float innerH = Math.max(0, h - 6);
        rect(context, innerX, innerY, innerW, innerH, 0xaa17141e);
        int fill = ratioWidth(used, total, Math.round(innerW));
        if (fill > 0) {
            rect(context, innerX, innerY, fill, innerH, color);
            rect(context, innerX, innerY, fill, 1, 0x70ffffff);
        }
    }

    static int ratioWidth(long used, long total, int width) {
        if (width <= 0 || total <= 0 || used <= 0) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(used, total));
        return (int) Math.max(1L, Math.min(width, clamped * width / total));
    }

    static String fit(GUIContext context, String text, int maxWidth) {
        if (context.mc.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = context.mc.font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && context.mc.font.width(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    static void item(GUIContext context, ItemStack stack, float x, float y) {
        if (!stack.isEmpty()) {
            DrawerHelper.drawItemStack(context.graphics, stack, Math.round(x), Math.round(y), -1, null);
        }
    }

    static boolean contains(float x, float y, float w, float h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    static float ratio(long used, long total) {
        if (used <= 0 || total <= 0) {
            return 0.0f;
        }
        return Mth.clamp((float) used / (float) total, 0.0f, 1.0f);
    }

    static <T> T get(List<T> values, int index, T fallback) {
        return index >= 0 && index < values.size() ? values.get(index) : fallback;
    }
}
