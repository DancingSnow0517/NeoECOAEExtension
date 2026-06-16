package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.NETextures;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class NECraftingModulePreviewElement extends UIElement {
    private static final ResourceLocation CORE_SIDE = NeoECOAE.id("textures/block/crafting/core/core_side.png");
    private static final ResourceLocation PARALLEL_FRONT = NeoECOAE.id("textures/block/crafting/core/parallel_core_north.png");
    private static final ResourceLocation LIGHT_L4 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_a.png");
    private static final ResourceLocation LIGHT_L6 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_b.png");
    private static final ResourceLocation LIGHT_L9 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_c.png");

    private final Supplier<List<NECraftingModuleCell>> cells;
    private final IntSupplier workerCount;
    private final IntSupplier parallelCount;

    public NECraftingModulePreviewElement(
        Supplier<List<NECraftingModuleCell>> cells,
        IntSupplier workerCount,
        IntSupplier parallelCount
    ) {
        this.cells = cells;
        this.workerCount = workerCount;
        this.parallelCount = parallelCount;
        layout(layout -> layout.width(184).height(70));
        style(style -> style.backgroundTexture(NETextures.CARD_BACKGROUND));
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            NECraftingModuleCell cell = cellAt(event.x, event.y);
            if (cell != null) {
                event.hoverTooltips = new HoverTooltips(tooltip(cell), null, null, null);
            }
        });
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        float x = getContentX() + 4;
        float y = getContentY() + 4;
        float width = getContentWidth() - 8;
        NEHostDraw.text(context, Component.translatable("gui.neoecoae.crafting.module_preview"), x, y, NEHostDraw.TEXT);
        NEHostDraw.rightText(context, "FT " + parallelCount.getAsInt() + "  FX " + workerCount.getAsInt(), x + width, y, NEHostDraw.TEXT);
        Grid grid = grid();
        if (grid.columns <= 0) {
            NEHostDraw.centeredText(context, Component.translatable("gui.neoecoae.crafting.no_worker_cores"),
                getContentX(), getContentY() + 38, getContentWidth(), NEHostDraw.MUTED);
            return;
        }
        for (int column = 0; column < grid.columns; column++) {
            drawCell(context, column, NECraftingModuleCell.Row.UPPER_PARALLEL, grid);
            drawCell(context, column, NECraftingModuleCell.Row.WORKER, grid);
            drawCell(context, column, NECraftingModuleCell.Row.LOWER_PARALLEL, grid);
        }
    }

    private void drawCell(GUIContext context, int column, NECraftingModuleCell.Row row, Grid grid) {
        NECraftingModuleCell cell = findCell(column, row);
        float x = grid.x + column * grid.size;
        float y = grid.y + rowIndex(row) * grid.size;
        boolean active = cell != null;
        NEHostDraw.inset(context, x, y, grid.size, grid.size);
        float inner = Math.max(1, grid.size - 4);
        float ix = x + 2;
        float iy = y + 2;
        NEHostDraw.rect(context, ix, iy, inner, inner, 0xaa17141e);
        if (active) {
            ResourceLocation base = row == NECraftingModuleCell.Row.WORKER ? CORE_SIDE : PARALLEL_FRONT;
            context.graphics.blit(base, Math.round(ix), Math.round(iy), Math.round(inner), Math.round(inner), 0, 0, 16, 16, 16, 16);
            if (row != NECraftingModuleCell.Row.WORKER) {
                context.graphics.blit(lightForTier(cell.tier()), Math.round(ix), Math.round(iy), Math.round(inner), Math.round(inner), 0, 0, 16, 16, 16, 16);
            }
        } else {
            NEHostDraw.rect(context, ix + 1, iy + 1, Math.max(0, inner - 2), Math.max(0, inner - 2), 0x66000000);
        }
    }

    private Grid grid() {
        int maxColumn = -1;
        for (NECraftingModuleCell cell : cells.get()) {
            maxColumn = Math.max(maxColumn, cell.column());
        }
        int columns = Math.max(maxColumn + 1, workerCount.getAsInt());
        if (columns <= 0) {
            return new Grid(getContentX(), getContentY(), 0, 18);
        }
        float areaX = getContentX() + 8;
        float areaY = getContentY() + 19;
        float areaW = Math.max(1, getContentWidth() - 16);
        float areaH = Math.max(1, getContentHeight() - 23);
        float size = Math.min(18, Math.max(6, Math.min(areaW / columns, areaH / 3)));
        float totalW = columns * size;
        float x = areaX + Math.max(0, (areaW - totalW) / 2);
        float y = areaY + Math.max(0, (areaH - size * 3) / 2);
        return new Grid(x, y, columns, size);
    }

    private NECraftingModuleCell cellAt(double mouseX, double mouseY) {
        Grid grid = grid();
        if (grid.columns <= 0) {
            return null;
        }
        for (NECraftingModuleCell cell : cells.get()) {
            float x = grid.x + cell.column() * grid.size;
            float y = grid.y + rowIndex(cell.row()) * grid.size;
            if (NEHostDraw.contains(x, y, grid.size, grid.size, mouseX, mouseY)) {
                return cell;
            }
        }
        return null;
    }

    private NECraftingModuleCell findCell(int column, NECraftingModuleCell.Row row) {
        for (NECraftingModuleCell cell : cells.get()) {
            if (cell.column() == column && cell.row() == row) {
                return cell;
            }
        }
        return null;
    }

    private static List<Component> tooltip(NECraftingModuleCell cell) {
        Component name = cell.row() == NECraftingModuleCell.Row.WORKER
            ? Component.translatable("block.neoecoae.crafting_worker")
            : Component.translatable(parallelCoreKey(cell.tier()));
        return List.of(
            name,
            Component.literal("x=" + cell.pos().getX() + ", y=" + cell.pos().getY() + ", z=" + cell.pos().getZ())
                .withStyle(ChatFormatting.GRAY)
        );
    }

    private static int rowIndex(NECraftingModuleCell.Row row) {
        return switch (row) {
            case UPPER_PARALLEL -> 0;
            case WORKER -> 1;
            case LOWER_PARALLEL -> 2;
        };
    }

    private static ResourceLocation lightForTier(int tier) {
        return switch (tier) {
            case 3 -> LIGHT_L9;
            case 2 -> LIGHT_L6;
            default -> LIGHT_L4;
        };
    }

    private static String parallelCoreKey(int tier) {
        return switch (tier) {
            case 3 -> "block.neoecoae.crafting_parallel_core_l9";
            case 2 -> "block.neoecoae.crafting_parallel_core_l6";
            default -> "block.neoecoae.crafting_parallel_core_l4";
        };
    }

    private record Grid(float x, float y, int columns, float size) {
    }
}
