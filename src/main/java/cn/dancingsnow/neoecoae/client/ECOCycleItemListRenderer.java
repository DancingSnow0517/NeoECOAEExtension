package cn.dancingsnow.neoecoae.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.PaletteColor;
import cn.dancingsnow.neoecoae.api.me.ECOCycleItemList;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.util.List;
import java.math.BigInteger;
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
        render(graphics, mouseX, mouseY, items, scrollOffset, null);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, List<ECOCycleItemList.Entry> items,
            int scrollOffset, @Nullable Integer selectedComponentId) {
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
            boolean selected = selectedComponentId != null && selectedComponentId == entry.componentId();
            (hovered || selected ? hoveredItemBackground : itemBackground).dest(x, cellY).blit(graphics);
            int itemX = x + CELL_WIDTH - 19;
            int itemY = cellY + 3;
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.translatable("gui.neoecoae.crafting_report.single_net_output",
                formatNetOutput(entry.exactSingleNetOutput(), AmountFormat.SLOT)));
            lines.add(totalNetOutputLine(item, entry, AmountFormat.SLOT));
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
                if (entry.componentId() >= 0) {
                    tooltip.add(net.minecraft.network.chat.Component.literal("Cycle #" + entry.componentId()));
                }
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                    "gui.neoecoae.crafting_report.single_net_output",
                    formatNetOutput(entry.exactSingleNetOutput(), AmountFormat.FULL)));
                tooltip.add(totalNetOutputLine(item, entry, AmountFormat.FULL));
                if (isMissingStartupSeed(entry)) {
                    tooltip.add(Component.translatable("gui.neoecoae.crafting_report.missing_startup_seed"));
                }
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

    private static String formatNetOutput(BigInteger amount, AmountFormat format) {
        if (amount.signum() == 0) return "0";
        BigInteger absolute = amount.abs();
        String formatted = format == AmountFormat.FULL ? absolute.toString() : HostText.ae2Amount(absolute);
        return amount.signum() > 0 ? "+" + formatted : "-" + formatted;
    }

    private static Component totalNetOutputLine(AEKey item, ECOCycleItemList.Entry entry, AmountFormat format) {
        return entry.totalNetOutputKnown()
            ? Component.translatable("gui.neoecoae.crafting_report.total_net_output",
                formatNetOutput(entry.exactTotalNetOutput(), format))
            : Component.translatable("gui.neoecoae.crafting_report.total_net_output",
                unknownReason(entry.solveStatus()));
    }

    private static boolean isMissingStartupSeed(ECOCycleItemList.Entry entry) {
        return entry.solveStatus() == cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT
            && entry.exactSingleNetOutput().signum() == 0 && entry.exactTotalNetOutput().signum() == 0;
    }

    private static String unknownReason(cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus status) {
        return switch (status) {
            case UNKNOWN_BUDGET -> "未知（预算不足）";
            case TOO_COMPLEX -> "未知（超出求解范围）";
            case UNSUPPORTED_PATTERN -> "未知（配方不支持）";
            case CANCELLED -> "未知（计算被取消）";
            default -> "未知";
        };
    }

    int getScrollableRows(int size) {
        return Math.max(0, size - VISIBLE_ROWS);
    }

    @Nullable ECOCycleItemList.Entry entryAt(double mouseX, double mouseY,
            List<ECOCycleItemList.Entry> items, int scrollOffset) {
        int localMouseX = (int) mouseX - screen.getGuiLeft();
        int localMouseY = (int) mouseY - screen.getGuiTop();
        if (localMouseX < x || localMouseX >= x + CELL_WIDTH) return null;
        if (localMouseY < y) return null;
        int row = (localMouseY - y) / ROW_HEIGHT;
        if (row < 0 || row >= VISIBLE_ROWS) return null;
        int index = scrollOffset + row;
        if (index < 0 || index >= items.size()) return null;
        int cellY = y + row * ROW_HEIGHT;
        return localMouseY < cellY + ROW_HEIGHT - 1 ? items.get(index) : null;
    }

    @Nullable StackWithBounds getHoveredStack() {
        return hoveredStack;
    }
}
