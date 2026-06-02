package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

final class NEFluidTankUi {
    private static final int HOVER_COLOR = 0x40FFFFFF;

    private NEFluidTankUi() {
    }

    static void draw(GuiGraphics g, int x, int y, int w, int h, FluidStack stack, int amount, int capacity) {
        NENativeAe2StyleRenderer.drawAeFluidTank(g, x, y, w, h, stack, amount, capacity);
    }

    static void drawHover(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        if (contains(mouseX, mouseY, x, y, w, h)) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, HOVER_COLOR);
        }
    }

    static boolean renderTooltip(GuiGraphics g, Font font, FluidStack stack, int amount, int capacity,
                                 int x, int y, int w, int h, int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY, x, y, w, h)) {
            return false;
        }

        int displayedAmount = Math.max(0, amount);
        Component name = stack.isEmpty()
            ? Component.translatable("gui.neoecoae.fluid_tank.empty")
            : stack.getDisplayName();
        Component volume = Component.translatable(
            "gui.neoecoae.fluid_tank.amount",
            format(displayedAmount),
            format(Math.max(0, capacity)));

        g.renderComponentTooltip(font, List.of(name, volume), mouseX, mouseY);
        return true;
    }

    static Component amountText(int amount, int capacity) {
        return Component.translatable(
            "gui.neoecoae.fluid_tank.amount",
            format(Math.max(0, amount)),
            format(Math.max(0, capacity)));
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static String format(int value) {
        return NumberFormat.getNumberInstance(Locale.US).format(value);
    }
}
