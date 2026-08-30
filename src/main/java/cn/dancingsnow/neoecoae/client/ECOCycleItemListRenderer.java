package cn.dancingsnow.neoecoae.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.PaletteColor;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.api.me.ECOCycleItemList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Renders cycle members in the narrow list beside the crafting report. */
final class ECOCycleItemListRenderer {
    private static final int VISIBLE_ROWS = 7;
    private static final int ROW_HEIGHT = 23;
    private static final int CELL_WIDTH = 67;

    private final AEBaseScreen<?> screen;
    private final Blitter itemBackground;
    private final Blitter hoveredItemBackground;
    private final int x;
    private final int y;
    private StackWithBounds hoveredStack;
    private List<Component> hoveredTooltip;
    private int hoveredMouseX;
    private int hoveredMouseY;

    ECOCycleItemListRenderer(AEBaseScreen<?> screen, int x, int y) {
        this.screen = screen;
        this.itemBackground = screen.getStyle().getImage("cycleItem");
        this.hoveredItemBackground = screen.getStyle().getImage("cycleItemHovered");
        this.x = x;
        this.y = y;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, List<ECOCycleItemList.Entry> items,
            int scrollOffset) {
        int localMouseX = mouseX - screen.getGuiLeft();
        int localMouseY = mouseY - screen.getGuiTop();
        hoveredStack = null;
        hoveredTooltip = null;

        int end = Math.min(items.size(), scrollOffset + VISIBLE_ROWS);
        for (int index = scrollOffset; index < end; index++) {
            ECOCycleItemList.Entry entry = items.get(index);
            AEKey item = entry.what();
            int cellY = y + (index - scrollOffset) * ROW_HEIGHT;
            boolean hovered = localMouseX >= x && localMouseX < x + CELL_WIDTH
                && localMouseY >= cellY && localMouseY < cellY + ROW_HEIGHT - 1;
            (hovered ? hoveredItemBackground : itemBackground).dest(x, cellY).blit(graphics);
            int itemX = x + CELL_WIDTH - 19;
            int itemY = cellY + 3;
            List<net.minecraft.network.chat.Component> lines = List.of(
                GuiText.FromStorage.text(item.formatAmount(entry.availableAmount(), AmountFormat.SLOT)),
                net.minecraft.network.chat.Component.translatable(
                    "gui.neoecoae.crafting_report.net_output",
                    formatNetOutput(item, entry.netOutput(), AmountFormat.SLOT)));
            var pose = graphics.pose();
            pose.pushPose();
            pose.scale(0.5f, 0.5f, 1.0f);
            int textColor = screen.getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
            var font = Minecraft.getInstance().font;
            float textY = Math.round(cellY + 6.0f);
            for (var line : lines) {
                int lineWidth = font.width(line);
                graphics.drawString(font, line,
                    (int) ((itemX - 2 - lineWidth * 0.5f) * 2), (int) (textY * 2), textColor, false);
                textY += font.lineHeight * 0.5f + 1;
            }
            pose.popPose();
            AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, itemX, itemY, item);

            if (hovered) {
                hoveredStack = new StackWithBounds(new GenericStack(item, 0),
                    new Rect2i(screen.getGuiLeft() + x, screen.getGuiTop() + cellY,
                        CELL_WIDTH, ROW_HEIGHT - 1));
                var tooltip = AEKeyRendering.getTooltip(item);
                tooltip.add(GuiText.FromStorage.text(item.formatAmount(entry.availableAmount(), AmountFormat.FULL)));
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                    "gui.neoecoae.crafting_report.net_output",
                    formatNetOutput(item, entry.netOutput(), AmountFormat.FULL)));
                hoveredTooltip = tooltip;
                hoveredMouseX = localMouseX;
                hoveredMouseY = localMouseY;
            }
        }
    }

    void renderTooltip(GuiGraphics graphics) {
        if (hoveredTooltip != null) {
            screen.drawTooltipWithHeader(graphics, hoveredMouseX, hoveredMouseY, hoveredTooltip);
        }
    }

    private static String formatNetOutput(AEKey item, long amount, AmountFormat format) {
        String formatted = item.formatAmount(amount == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(amount),
            format);
        return amount > 0 ? "+" + formatted : amount < 0 ? "-" + formatted : "0";
    }

    int getScrollableRows(int size) {
        return Math.max(0, size - VISIBLE_ROWS);
    }

    @Nullable StackWithBounds getHoveredStack() {
        return hoveredStack;
    }
}
