package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOTier;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class NECraftingModulePreview extends NESnapshotElement {
    private static final float MODULE_TEXT_SCALE = 0.82F;
    private static final int EDGE = 7;
    private static final int GRID_X = EDGE;
    private static final int GRID_Y = 24;
    private static final int GRID_H = 37;
    private static final int SCROLLBAR_Y = GRID_Y + GRID_H + 2;
    private static final int SCROLLBAR_H = 3;
    private static final int STATS_Y = SCROLLBAR_Y + SCROLLBAR_H + 3;
    private static final int PROGRESS_H = 4;
    private static final ResourceLocation CORE_SIDE = NeoECOAE.id("textures/block/crafting/core/core_side.png");
    private static final ResourceLocation PARALLEL_FRONT = NeoECOAE.id("textures/block/crafting/core/parallel_core_north.png");
    private static final ResourceLocation LIGHT_L4 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_a.png");
    private static final ResourceLocation LIGHT_L6 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_b.png");
    private static final ResourceLocation LIGHT_L9 = NeoECOAE.id("textures/block/crafting/core/parallel_core_light_c.png");

    private final ScrollModel moduleScroll = new ScrollModel();
    private List<NECraftingModuleCell> moduleCells = List.of();
    private List<ItemStack> workerOutputs = List.of();
    private int workerCount;
    private int parallelCount;
    private int runningThreadCount;
    private int threadCount;
    private int availableThreads;
    private boolean overclocked;
    private float mouseX;
    private float mouseY;

    NECraftingModulePreview(Supplier<byte[]> serverSnapshot) {
        super(serverSnapshot);
        layout(layout -> layout.widthPercent(100).heightPercent(100));
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            HoverTooltips tooltip = tooltipAt(mouseX, mouseY);
            if (tooltip != null) {
                event.hoverTooltips = tooltip;
                event.stopPropagation();
            }
        });
    }

    @Override
    protected void acceptSnapshot(byte[] snapshotData) {
        NEHostSnapshots.decode(snapshotData, buf -> {
            workerCount = Math.max(0, buf.readVarInt());
            parallelCount = Math.max(0, buf.readVarInt());
            runningThreadCount = Math.max(0, buf.readVarInt());
            threadCount = Math.max(0, buf.readVarInt());
            availableThreads = Math.max(0, buf.readVarInt());
            overclocked = buf.readBoolean();
            moduleCells = NEHostSnapshots.readModuleCells(buf);
            workerOutputs = NEHostSnapshots.readItemStacks(buf);
        });
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        this.mouseX = context.mouseX;
        this.mouseY = context.mouseY;
        String moduleCounts = "FT " + NEHostFormat.number(parallelCount) + "   FX " + NEHostFormat.number(workerCount);
        int countWidth = Math.round(context.mc.font.width(moduleCounts) * MODULE_TEXT_SCALE);
        NEHostUiPrimitives.scaledFittedText(this, context, NEHostUiPrimitives.tr("gui.neoecoae.crafting.module_preview", "Structure Preview"),
            EDGE, EDGE, MODULE_TEXT_SCALE, Math.round(getSizeWidth()) - EDGE * 2 - countWidth - 5, NEHostUiPrimitives.TEXT_PRIMARY);
        NEHostUiPrimitives.scaledRightText(this, context, moduleCounts, getSizeWidth() - EDGE, EDGE, MODULE_TEXT_SCALE, NEHostUiPrimitives.TEXT_VALUE);

        Grid grid = moduleGrid();
        if (grid.columns <= 0) {
            NEHostUiPrimitives.scaledCenteredText(this, context, NEHostUiPrimitives.tr("gui.neoecoae.crafting.no_worker_cores", "No worker cores"),
                0, 47, getSizeWidth(), MODULE_TEXT_SCALE, NEHostUiPrimitives.TEXT_MUTED);
            drawModuleStats(context);
            return;
        }
        int gridW = gridWidth();
        if (grid.contentW > gridW) {
            float thumbW = Math.max(12.0F, gridW * gridW / grid.contentW);
            float thumbX = GRID_X + (gridW - thumbW) * moduleScroll.offset() / Math.max(1.0F, moduleScroll.max());
            NEHostUiPrimitives.rect(this, context, GRID_X, SCROLLBAR_Y, gridW, SCROLLBAR_H, NEHostUiPrimitives.PANEL_OUTER);
            NEHostUiPrimitives.rect(this, context, thumbX, SCROLLBAR_Y, thumbW, SCROLLBAR_H, NEHostUiPrimitives.PANEL_MIDDLE);
            NEHostUiPrimitives.rect(this, context, thumbX, SCROLLBAR_Y, thumbW, 1, NEHostUiPrimitives.PANEL_EDGE);
        }
        context.graphics.flush();
        context.enableScissor(NEHostUiPrimitives.ax(this, GRID_X), NEHostUiPrimitives.ay(this, GRID_Y), gridW, GRID_H);
        for (int column = 0; column < grid.columns; column++) {
            float x = grid.x + column * grid.size;
            if (x + grid.size <= GRID_X || x >= GRID_X + gridW) {
                continue;
            }
            drawModuleCell(context, column, NECraftingModuleCell.Row.UPPER_PARALLEL, grid);
            drawModuleCell(context, column, NECraftingModuleCell.Row.WORKER, grid);
            drawModuleCell(context, column, NECraftingModuleCell.Row.LOWER_PARALLEL, grid);
        }
        context.graphics.flush();
        context.disableScissor();
        drawModuleStats(context);
    }

    private void drawModuleCell(GUIContext context, int column, NECraftingModuleCell.Row row, Grid grid) {
        NECraftingModuleCell cell = moduleCellAt(column, row);
        boolean active = cell != null;
        float x = grid.x + column * grid.size;
        float y = grid.y + rowIndex(row) * grid.size;
        if (grid.size >= 10.0F) {
            NEHostUiPrimitives.insetRect(this, context, x, y, grid.size, grid.size);
        } else {
            NEHostUiPrimitives.rect(this, context, x, y, grid.size, grid.size, 0xFF1B1822);
        }
        float pad = grid.size >= 10.0F ? 2.0F : 1.0F;
        float inner = Math.max(1.0F, grid.size - pad * 2.0F);
        float ix = x + pad;
        float iy = y + pad;
        NEHostUiPrimitives.rect(this, context, ix, iy, inner, inner, 0xAA17141E);
        if (active) {
            ResourceLocation base = row == NECraftingModuleCell.Row.WORKER ? CORE_SIDE : PARALLEL_FRONT;
            context.graphics.blit(base, Math.round(NEHostUiPrimitives.ax(this, ix)), Math.round(NEHostUiPrimitives.ay(this, iy)), Math.round(inner), Math.round(inner), 0, 0, 16, 16, 16, 16);
            if (row != NECraftingModuleCell.Row.WORKER) {
                context.graphics.blit(lightForTier(cell.tier()), Math.round(NEHostUiPrimitives.ax(this, ix)), Math.round(NEHostUiPrimitives.ay(this, iy)), Math.round(inner), Math.round(inner), 0, 0, 16, 16, 16, 16);
            }
        } else {
            NEHostUiPrimitives.rect(this, context, ix + 1.0F, iy + 1.0F, Math.max(0.0F, inner - 2.0F), Math.max(0.0F, inner - 2.0F), 0x66000000);
        }
    }

    private void drawModuleStats(GUIContext context) {
        int width = Math.round(getSizeWidth());
        String tasks = NEHostUiPrimitives.trString("gui.neoecoae.crafting.recipe_slots", "Task Slots") + " "
            + NEHostFormat.usedTotal(runningThreadCount, threadCount);
        String free = NEHostUiPrimitives.trString("gui.neoecoae.crafting.batch_parallel", "Free Parallel") + " "
            + NEHostFormat.number(availableThreads);
        int freeWidth = Math.round(context.mc.font.width(free) * MODULE_TEXT_SCALE);
        NEHostUiPrimitives.scaledFittedText(this, context, Component.literal(tasks), EDGE, STATS_Y, MODULE_TEXT_SCALE,
            width - EDGE * 2 - freeWidth - 5, NEHostUiPrimitives.TEXT_MUTED);
        NEHostUiPrimitives.scaledRightText(this, context, free, width - EDGE, STATS_Y, MODULE_TEXT_SCALE, NEHostUiPrimitives.TEXT_VALUE);
        int progressY = Math.round(getSizeHeight()) - 8;
        int progressW = width - EDGE * 2;
        NEHostUiPrimitives.rect(this, context, EDGE, progressY, progressW, PROGRESS_H, 0xAA17141E);
        int fill = NEHostUiPrimitives.ratioWidth(runningThreadCount, threadCount, progressW);
        if (fill > 0) {
            NEHostUiPrimitives.rect(this, context, EDGE, progressY, fill, PROGRESS_H, NEHostUiPrimitives.TEXT_SUCCESS);
            NEHostUiPrimitives.rect(this, context, EDGE, progressY, fill, 1, 0x70FFFFFF);
        }
    }

    private void onMouseWheel(UIEvent event) {
        Grid grid = moduleGrid();
        if (grid.contentW > gridWidth() && UIElement.isMouseOverRect(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), mouseX, mouseY)) {
            moduleScroll.scrollBy((float) -event.deltaY * Math.max(8.0F, grid.size), grid.contentW, gridWidth(), true);
            event.stopPropagation();
        }
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        Grid grid = moduleGrid();
        if (grid.columns <= 0) {
            return null;
        }
        for (NECraftingModuleCell cell : moduleCells) {
            float x = grid.x + cell.column() * grid.size;
            float y = grid.y + rowIndex(cell.row()) * grid.size;
            float clippedX = Math.max(x, GRID_X);
            float clippedW = Math.min(x + grid.size, GRID_X + gridWidth()) - clippedX;
            if (clippedW <= 0.0F || !NEHostUiPrimitives.contains(this, clippedX, y, clippedW, grid.size, mouseX, mouseY)) {
                continue;
            }
            if (cell.row() == NECraftingModuleCell.Row.WORKER) {
                ItemStack output = workerOutputAt(cell.column());
                if (!output.isEmpty()) {
                    List<Component> lines = NEHostUiPrimitives.itemTooltip(output);
                    lines.add(Component.translatable("block.neoecoae.crafting_worker").withStyle(ChatFormatting.GRAY));
                    lines.add(Component.literal(modulePos(cell)).withStyle(ChatFormatting.GRAY));
                    return new HoverTooltips(lines, output.getTooltipImage().orElse(null), null, output);
                }
            }
            Component name = cell.row() == NECraftingModuleCell.Row.WORKER
                ? Component.translatable("block.neoecoae.crafting_worker")
                : Component.translatable(parallelCoreNameKey(cell.tier()));
            List<Component> lines = new ArrayList<>();
            lines.add(name);
            if (cell.row() != NECraftingModuleCell.Row.WORKER) {
                lines.add(Component.translatable("gui.neoecoae.crafting.parallel_per_core",
                    NEHostFormat.number(parallelPerCore(cell.tier(), overclocked))));
            }
            lines.add(Component.literal(modulePos(cell)).withStyle(ChatFormatting.GRAY));
            return new HoverTooltips(lines, null, null, null);
        }
        return null;
    }

    private Grid moduleGrid() {
        int maxColumn = -1;
        for (NECraftingModuleCell cell : moduleCells) {
            maxColumn = Math.max(maxColumn, cell.column());
        }
        int columns = Math.max(maxColumn + 1, workerCount);
        int gridW = gridWidth();
        if (columns <= 0) {
            moduleScroll.update(0.0F, gridW);
            return new Grid(GRID_X, GRID_Y, 0, 18.0F, 0.0F);
        }
        float size = Math.min(18.0F, Math.max(6.0F, Math.min((float) gridW / columns, GRID_H / 3.0F)));
        float contentW = columns * size;
        moduleScroll.update(contentW, gridW);
        float x = contentW <= gridW ? GRID_X + Math.max(0.0F, (gridW - contentW) / 2.0F) : GRID_X - moduleScroll.offset();
        float y = GRID_Y + Math.max(0.0F, (GRID_H - size * 3.0F) / 2.0F);
        return new Grid(x, y, columns, size, contentW);
    }

    private int gridWidth() {
        return Math.max(0, Math.round(getSizeWidth()) - EDGE * 2);
    }

    private NECraftingModuleCell moduleCellAt(int column, NECraftingModuleCell.Row row) {
        for (NECraftingModuleCell cell : moduleCells) {
            if (cell.column() == column && cell.row() == row) {
                return cell;
            }
        }
        return null;
    }

    private ItemStack workerOutputAt(int column) {
        if (column < 0 || column >= workerOutputs.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = workerOutputs.get(column);
        return stack == null ? ItemStack.EMPTY : stack;
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

    private static ECOTier ecoTier(int tier) {
        return switch (tier) {
            case 3 -> ECOTier.L9;
            case 2 -> ECOTier.L6;
            default -> ECOTier.L4;
        };
    }

    private static int parallelPerCore(int tier, boolean overclocked) {
        ECOTier ecoTier = ecoTier(tier);
        return ecoTier.getCrafterParallel() + (overclocked ? ecoTier.getOverclockedCrafterParallel() : 0);
    }

    private static String modulePos(NECraftingModuleCell cell) {
        return "x=" + cell.pos().getX() + ", y=" + cell.pos().getY() + ", z=" + cell.pos().getZ();
    }

    private static String parallelCoreNameKey(int tier) {
        return switch (tier) {
            case 3 -> "block.neoecoae.crafting_parallel_core_l9";
            case 2 -> "block.neoecoae.crafting_parallel_core_l6";
            default -> "block.neoecoae.crafting_parallel_core_l4";
        };
    }

    private static float approach(float current, float target) {
        float next = Mth.lerp(0.16F, current, target);
        return Math.abs(next - target) < 0.05F ? target : next;
    }

    private record Grid(float x, float y, int columns, float size, float contentW) {
    }

    private static final class ScrollModel {
        private float offset;
        private float target;
        private float max;

        void update(float contentSize, float viewportSize) {
            max = Math.max(0.0F, contentSize - viewportSize);
            target = Mth.clamp(target, 0.0F, max);
            offset = max <= 0.0F ? 0.0F : approach(offset, target);
            if (max <= 0.0F) {
                target = 0.0F;
            }
        }

        void scrollBy(float delta, float contentSize, float viewportSize, boolean snap) {
            update(contentSize, viewportSize);
            target = Mth.clamp(target + delta, 0.0F, max);
            if (snap) {
                offset = target;
            }
        }

        float offset() {
            return offset;
        }

        float max() {
            return max;
        }
    }
}
