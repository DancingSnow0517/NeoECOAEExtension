package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.gui.NETextures;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class NEStorageMatrixElement extends UIElement {
    private static final int CELL = 18;
    private static final int GAP = 2;
    private final Supplier<List<NEStorageMatrixCell>> cells;

    public NEStorageMatrixElement(Supplier<List<NEStorageMatrixCell>> cells) {
        this.cells = cells;
        layout(layout -> layout.width(184).height(72));
        style(style -> style.backgroundTexture(NETextures.CARD_BACKGROUND));
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            NEStorageMatrixCell cell = cellAt(event.x, event.y);
            if (cell != null && cell.hasCell()) {
                event.hoverTooltips = new HoverTooltips(tooltip(cell), null, null, null);
            }
        });
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        float x = getContentX() + 4;
        float y = getContentY() + 4;
        List<NEStorageMatrixCell> sorted = cells.get().stream()
            .sorted(Comparator.comparingInt(NEStorageMatrixCell::row).thenComparingInt(NEStorageMatrixCell::column))
            .toList();
        if (sorted.isEmpty()) {
            NEHostDraw.centeredText(context, Component.translatable("gui.neoecoae.storage.matrix.empty"),
                getContentX(), getContentY() + 28, getContentWidth(), NEHostDraw.MUTED);
            return;
        }
        for (NEStorageMatrixCell cell : sorted) {
            float sx = x + Math.max(0, cell.column()) * (CELL + GAP);
            float sy = y + Math.max(0, cell.row()) * (CELL + GAP);
            if (sx + CELL > getContentX() + getContentWidth() || sy + CELL > getContentY() + getContentHeight()) {
                continue;
            }
            drawCell(context, cell, sx, sy);
        }
    }

    private void drawCell(GUIContext context, NEStorageMatrixCell cell, float x, float y) {
        NEHostDraw.inset(context, x, y, CELL, CELL);
        if (!cell.hasCell()) {
            NEHostDraw.rect(context, x + 3, y + 3, CELL - 6, CELL - 6, 0x6617141e);
            return;
        }
        NEHostDraw.item(context, cell.stack(), x + 1, y + 1);
        int usedHeight = Math.max(1, Math.round(14 * NEHostDraw.ratio(cell.usedBytes(), cell.totalBytes())));
        int color = switch (Math.max(0, Math.min(3, cell.tier()))) {
            case 3 -> 0xffd658ff;
            case 2 -> 0xff53c7ff;
            case 1 -> 0xff6cd56f;
            default -> 0xffb1b1b1;
        };
        NEHostDraw.rect(context, x + 15, y + 16 - usedHeight, 2, usedHeight, color);
    }

    private NEStorageMatrixCell cellAt(double mouseX, double mouseY) {
        float x = getContentX() + 4;
        float y = getContentY() + 4;
        for (NEStorageMatrixCell cell : cells.get()) {
            float sx = x + Math.max(0, cell.column()) * (CELL + GAP);
            float sy = y + Math.max(0, cell.row()) * (CELL + GAP);
            if (NEHostDraw.contains(sx, sy, CELL, CELL, mouseX, mouseY)) {
                return cell;
            }
        }
        return null;
    }

    private static List<Component> tooltip(NEStorageMatrixCell cell) {
        return List.of(
            cell.stack().getHoverName(),
            Component.translatable("gui.neoecoae.host.metric.types")
                .append(": ")
                .append(Component.literal(NEHostFormat.usedTotal(cell.usedTypes(), cell.totalTypes())).withStyle(ChatFormatting.WHITE)),
            Component.translatable("gui.neoecoae.host.metric.bytes")
                .append(": ")
                .append(Component.literal(NEHostFormat.usedTotalBytes(cell.usedBytes(), cell.totalBytes())).withStyle(ChatFormatting.WHITE))
        );
    }
}
