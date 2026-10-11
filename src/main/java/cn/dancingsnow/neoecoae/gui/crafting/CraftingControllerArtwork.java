package cn.dancingsnow.neoecoae.gui.crafting;

import static cn.dancingsnow.neoecoae.gui.crafting.CraftingHostPanelUI.UI_HEIGHT;
import static cn.dancingsnow.neoecoae.gui.crafting.CraftingHostPanelUI.UI_WIDTH;
import cn.dancingsnow.neoecoae.gui.common.HostPanelElements;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostPanelUI.Config;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

final class CraftingControllerArtwork extends UIElement {
    private static final int[] PLUS_X = {104, 116, 128};
    private static final int[] STATUS_CORNER_X = {58, 74, 90};
    private static final int PRIMARY = 0xFFE4E4EE;
    private static final int SECONDARY = 0xFFB4B6CC;
    private static final int QUEUE_VALUE_COLOR = 0xB4E2FF;
    private static final int PARALLEL_VALUE_COLOR = 0xFFC2ED;
    private static final int OVERFLOW_VALUE_COLOR = 0xFFDB99;
    private static final float INFO_TEXT_SCALE = 0.75F;
    private static final float INFO_TEXT_GAP = 4.0F;
    private static final int INFO_TEXT_X = 196;
    private static final int INFO_TEXT_WIDTH = 48;
    private static final int INFO_ROW_HEIGHT = 24;
    private static final int GAUGE_INTERIOR_HEIGHT = 46;
    private static final float SECTION_TEXT_SCALE = 0.75F;
    private static final float STAT_TEXT_SCALE = 0.75F;
    private static final int STAT_TEXT_Y = 81;
    private static final int STAT_LINE_SPACING = 8;
    private static final int QUEUE_COLUMN_X = 65;
    private static final int PARALLEL_COLUMN_X = 118;
    private static final int STAT_COLUMN_WIDTH = 49;
    private static final int PROGRESS_BAR_WIDTH = 44;
    private static final int PROGRESS_BAR_HEIGHT = 7;
    private static final String[] NUMBER_UNITS = {"", "K", "M", "G", "T", "P", "E"};
    private final Config config;
    private final ItemStack hostItem;
    private final CraftingOutputSlot[] workerSlots = new CraftingOutputSlot[CraftingControllerPanelState.SLOT_COUNT];
    private final CraftingControllerViewState view = new CraftingControllerViewState();
    private final CraftingControllerTooltips tooltips = new CraftingControllerTooltips(view);

    CraftingControllerArtwork(Config config) {
        this.config = config;
        hostItem = config.hostItem().get().copyWithCount(1);
        for (int cell = 0; cell < workerSlots.length; cell++) {
            workerSlots[cell] = new CraftingOutputSlot();
            int x = 49 + cell * 18;
            workerSlots[cell].layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(x).top(38).width(16).height(16).paddingAll(0));
        }
        CraftingControllerSync.attach(this, config, view, this::updateWorkerSlots);
        layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(UI_WIDTH).height(UI_HEIGHT));

        addChild(tooltipRegion(64, 79, 51, 45, tooltips::queueTooltip));
        addChild(tooltipRegion(117, 79, 51, 45, tooltips::parallelTooltip));
        addChild(tooltipRegion(176, 78, 8, 46, tooltips::energyTooltip));
        addChild(tooltipRegion(195, 78, 20, 46, tooltips::coolantTooltip));
        addChild(tooltipRegion(226, 78, 20, 46, tooltips::outputTooltip));
        addChild(tooltipRegion(196, 130, 48, 22, tooltips::fxTooltip));
        addChild(tooltipRegion(196, 155, 48, 23, tooltips::coolantRowTooltip));
        addChild(tooltipRegion(196, 180, 48, 24, tooltips::switchTooltip));
        for (CraftingOutputSlot slot : workerSlots) addChild(slot);
    }

    private UIElement tooltipRegion(int x, int y, int width, int height, Supplier<List<Component>> lines) {
        return new UIElement().layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(x).top(y).width(width).height(height))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                lines.get(), null, null, null));
    }

    private void updateWorkerSlots() {
        for (int cell = 0; cell < workerSlots.length; cell++) {
            workerSlots[cell].setOutput(view.workerOutputs.get(cell));
            workerSlots[cell].setDisplay(view.state.formed() && view.state.hasFx(cell));
        }
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        context.drawTexture(NETextures.Crafting.CONTROLLER, getPositionX(), getPositionY(), UI_WIDTH, UI_HEIGHT);
        drawHeader(context);
        if (!hostItem.isEmpty()) {
            context.graphics.renderItem(hostItem, Math.round(getPositionX()) + 9,
                Math.round(getPositionY()) + 38);
        }
        if (!view.state.formed()) return;
        drawTier(context, view.state.hostTier());
        drawStatusIcons(context);
        drawCoreCells(context);
        drawBars(context);
        drawInfoRows(context);
    }

    private void drawHeader(GUIContext context) {
        drawText(context, config.title().get(), 40, 10, 105, PRIMARY);
        drawCenteredText(context, Component.literal(HostPanelElements.formatPerformanceCornerValue(view.performanceAverageNanos)),
            210, 10, 39, PRIMARY);
        drawSectionHeadings(context);
    }

    private void drawSectionHeadings(GUIContext context) {
        drawSectionHeading(context, "control_heading", 9, 68, 43);
        drawSectionHeading(context, "cooling_short", 10, 84, 15);
        drawSectionHeading(context, "overclock_short", 37, 84, 15);
        drawSectionHeading(context, "queue_heading", QUEUE_COLUMN_X, 68, STAT_COLUMN_WIDTH);
        drawSectionHeading(context, "parallel_heading", PARALLEL_COLUMN_X, 68, STAT_COLUMN_WIDTH);
        drawSectionHeading(context, "energy_heading", 169, 68, 22);
        drawSectionHeading(context, "coolant_heading", 192, 68, 26);
        drawSectionHeading(context, "output_heading", 223, 68, 26);
    }

    private void drawSectionHeading(GUIContext context, String key, int x, int y, int width) {
        drawStatisticText(context, Component.translatable("gui.neoecoae.crafting.ui." + key),
            x, y, width, SECTION_TEXT_SCALE, true);
    }

    private void drawInfoRows(GUIContext context) {
        drawInfoRow(context,
            statisticNumbers(INFO_TEXT_WIDTH, INFO_TEXT_SCALE, values -> Component.translatable(
                "gui.neoecoae.crafting.ui.fx_short", values[0], values[1]), view.activeWorkerCores, view.workerCores),
            statisticNumbers(INFO_TEXT_WIDTH, INFO_TEXT_SCALE, values -> Component.translatable(
                "gui.neoecoae.crafting.ui.ft_short", values[0]), view.ftParallelCapacity), 130);
        Component coolantName = tooltips.coolantVisible()
            ? view.coolantFluid.getHoverName() : Component.translatable("ldlib.fluid.empty");
        drawInfoRow(context, coolantName,
            Component.translatable("gui.neoecoae.crafting.ui.coolant_overclock", Math.max(0, view.coolantMaxOverclock)), 155);
        if (view.normalSwitchCount == 0 && view.highEnergySwitchCount == 0) {
            drawInfoRow(context,
                Component.translatable("gui.neoecoae.crafting.ui.switch_none"),
                Component.translatable("gui.neoecoae.crafting.ui.network_multiplier", view.networkMultiplier), 180);
        } else {
            drawInfoRow(context,
                Component.translatable("gui.neoecoae.crafting.ui.switch_normal", view.normalSwitchCount),
                Component.translatable("gui.neoecoae.crafting.ui.switch_high_energy", view.highEnergySwitchCount), 180);
        }
    }

    private void drawInfoRow(GUIContext context, Component first, Component second, int y) {
        Font font = Minecraft.getInstance().font;
        float textHeight = (font.lineHeight - 1) * INFO_TEXT_SCALE;
        float firstY = y + (INFO_ROW_HEIGHT - textHeight * 2 - INFO_TEXT_GAP) / 2.0F;
        drawInfoText(context, font, first, firstY, PRIMARY);
        drawInfoText(context, font, second, firstY + textHeight + INFO_TEXT_GAP, SECONDARY);
    }

    private void drawInfoText(GUIContext context, Font font, Component text, float y, int color) {
        String value = text == null ? "" : text.getString();
        if (value.isEmpty()) return;
        float scale = Math.min(INFO_TEXT_SCALE, (INFO_TEXT_WIDTH - 1.0F) / Math.max(1, font.width(value)));
        float textY = y + (font.lineHeight - 1) * (INFO_TEXT_SCALE - scale) / 2.0F;
        var pose = context.graphics.pose();
        pose.pushPose();
        try {
            pose.translate(getPositionX() + INFO_TEXT_X, (double) getPositionY() + textY, 0.0D);
            pose.scale(scale, scale, 1.0F);
            context.graphics.drawString(font, value, 0, 0, color, true);
        } finally {
            pose.popPose();
        }
    }

    private void drawText(GUIContext context, Component text, int x, int y, int width, int color) {
        String value = text == null ? "" : text.getString();
        if (value.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        value = font.plainSubstrByWidth(value, width);
        context.graphics.drawString(font, value, Math.round(getPositionX()) + x,
            Math.round(getPositionY()) + y, color, true);
    }

    private void drawCenteredText(GUIContext context, Component text, int x, int y, int width, int color) {
        Font font = Minecraft.getInstance().font;
        String value = font.plainSubstrByWidth(text.getString(), width);
        int left = x + Math.max(0, (width - font.width(value)) / 2);
        context.graphics.drawString(font, value, Math.round(getPositionX()) + left,
            Math.round(getPositionY()) + y, color, true);
    }

    private void drawTier(GUIContext context, int tier) {
        int sourceX = switch (tier) { case 1 -> 0; case 2 -> 13; case 3 -> 26; default -> -1; };
        if (sourceX >= 0) sprite(context, sourceX, 214, 13, 18, 23, 5, 13, 18);
    }

    private void drawStatusIcons(GUIContext context) {
        drawStatusLamp(context);
        if (view.state.fxCells() != 0 && view.overclocked) {
            sprite(context, 0, view.virtualMode ? 244 : 232, 12, 12, 180, 136, 12, 12);
        }
        if (view.activeCooling) {
            sprite(context, 12, view.virtualMode ? 244 : 232, 12, 12, 180, 161, 12, 12);
        }
        if (view.state.switchOverlay()) {
            if (view.virtualMode) {
                sprite(context, 24, 244, 12, 12, 180, 186, 12, 12);
            } else if (view.highEnergySwitchCount > 0) {
                sprite(context, 139, 236, 12, 12, 180, 186, 12, 12);
            } else {
                sprite(context, 24, 232, 12, 12, 180, 186, 12, 12);
            }
        }
    }

    /** Draw the host-tier markers in the two dark bands of the left status lamp. */
    private void drawStatusLamp(GUIContext context) {
        int tier = view.state.hostTier();
        if (tier < 1 || tier > STATUS_CORNER_X.length) return;

        int sourceX = STATUS_CORNER_X[tier - 1];
        sprite(context, sourceX, 217, 10, 7, 12, 31, 10, 7);
        sprite(context, sourceX, 242, 10, 7, 12, 54, 10, 7);
    }

    private void drawCoreCells(GUIContext context) {
        for (int cell = 0; cell < CraftingControllerPanelState.SLOT_COUNT; cell++) {
            if (!view.state.hasFx(cell)) continue;
            int x = 49 + cell * 18;
            sprite(context, 39, 216, 16, 34, x, 29, 16, 34);
            drawPlus(context, view.state.upperTier(cell), x + 3, 32, true);
            drawPlus(context, view.state.lowerTier(cell), x + 3, 55, false);
        }
    }

    private void drawPlus(GUIContext context, int tier, int x, int y, boolean upper) {
        if (tier < 1 || tier > 3) return;
        int sourceY = upper ? 237 : 243;
        sprite(context, PLUS_X[tier - 1], sourceY, 10, upper ? 6 : 5, x, y, 10, upper ? 6 : 5);
    }

    private void drawBars(GUIContext context) {
        drawStatistics(context);
        drawHorizontalProgress(context, 66, 114, PROGRESS_BAR_WIDTH,
            view.state.queuePermille(), 104, 215);
        drawHorizontalProgress(context, 121, 114, PROGRESS_BAR_WIDTH,
            CraftingControllerPanelState.permille(1, view.executionTicks), 104, 223);
        int height = Math.round(46 * view.state.energyPermille() / (float) CraftingControllerPanelState.FULL);
        if (height > 0) sprite(context, 254, 1 + 46 - height, 8, height, 176, 78 + 46 - height, 8, height);
        drawFluidGauge(context, 195, view.coolantFluid, view.coolantAmount, view.coolantCapacity, 209);
        drawFluidGauge(context, 226, view.outputFluid, view.outputFluid.getAmount(), view.outputCapacity, 240);
    }

    private void drawStatistics(GUIContext context) {
        // Every row uses the same scale and spacing; compact numbers instead of shrinking text.
        drawStatisticLines(context, QUEUE_COLUMN_X, List.of(
            statisticNumbers(STAT_COLUMN_WIDTH, STAT_TEXT_SCALE,
                values -> Component.translatable("gui.neoecoae.crafting.ui.queue_count_short", values[0]),
                view.queuedCraftCount),
            statisticNumbers(STAT_COLUMN_WIDTH, STAT_TEXT_SCALE,
                values -> Component.translatable("gui.neoecoae.crafting.ui.queue_capacity_short",
                    view.batchCapacity == Long.MAX_VALUE ? "∞" : values[0]), view.batchCapacity),
            Component.translatable("gui.neoecoae.crafting.ui.queue_used_short",
                Component.literal(tooltips.percent(view.state.queuePermille())).withColor(QUEUE_VALUE_COLOR))));
        drawStatisticLines(context, PARALLEL_COLUMN_X, List.of(
            statisticNumbers(STAT_COLUMN_WIDTH, STAT_TEXT_SCALE,
                values -> Component.translatable("gui.neoecoae.crafting.ui.parallel_count_short",
                    Component.literal(values[0]).withColor(PARALLEL_VALUE_COLOR)),
                view.ftParallelCapacity),
            statisticNumbers(STAT_COLUMN_WIDTH, STAT_TEXT_SCALE,
                values -> Component.translatable("gui.neoecoae.crafting.ui.parallel_limit_short", values[0]),
                tooltips.parallelLimit()),
            statisticNumbers(STAT_COLUMN_WIDTH, STAT_TEXT_SCALE,
                values -> Component.translatable("gui.neoecoae.crafting.ui.parallel_overflow_short", values[0]),
                view.overflowCapacity),
            Component.translatable("gui.neoecoae.crafting.ui.parallel_overflow_percent_short",
                view.overflowCapacity > 0
                    ? Component.literal(tooltips.overflowPercent()).withColor(OVERFLOW_VALUE_COLOR)
                    : Component.literal(tooltips.overflowPercent()))));
    }

    private void drawStatisticLines(GUIContext context, int x, List<Component> lines) {
        Font font = Minecraft.getInstance().font;
        var pose = context.graphics.pose();
        for (int row = 0; row < lines.size(); row++) {
            pose.pushPose();
            try {
                pose.translate(getPositionX() + x, getPositionY() + STAT_TEXT_Y + row * STAT_LINE_SPACING, 0.0D);
                pose.scale(STAT_TEXT_SCALE, STAT_TEXT_SCALE, 1.0F);
                context.graphics.drawString(font, lines.get(row), 0, 0, PRIMARY, true);
            } finally {
                pose.popPose();
            }
        }
    }

    private Component statisticNumbers(int width, float scale, Function<String[], Component> formatter, long... numbers) {
        Font font = Minecraft.getInstance().font;
        String[] values = new String[numbers.length];
        Component text = Component.empty();
        for (int decimalPlaces = 2; decimalPlaces >= 0; decimalPlaces--) {
            for (int i = 0; i < numbers.length; i++) {
                values[i] = decimalPlaces == 2 ? Long.toString(numbers[i]) : compactNumber(numbers[i], decimalPlaces);
            }
            text = formatter.apply(values);
            if (font.width(text) * scale <= width - 1) break;
        }
        return text;
    }

    private void drawStatisticText(GUIContext context, Component text, int x, int y, int width,
                                   float preferredScale, boolean centered) {
        Font font = Minecraft.getInstance().font;
        float scale = Math.min(preferredScale, (width - 1.0F) / Math.max(1, font.width(text)));
        float left = x + (centered ? (width - font.width(text) * scale) / 2.0F : 0);
        var pose = context.graphics.pose();
        pose.pushPose();
        try {
            pose.translate(getPositionX() + left, getPositionY() + y, 0.0D);
            pose.scale(scale, scale, 1.0F);
            context.graphics.drawString(font, text, 0, 0, PRIMARY, true);
        } finally {
            pose.popPose();
        }
    }

    private String compactNumber(long value, int decimalPlaces) {
        long safe = Math.max(0L, value);
        long divisor = 1L;
        int unit = 0;
        while (unit < NUMBER_UNITS.length - 1 && safe / divisor >= 1_000L) {
            divisor *= 1_000L;
            unit++;
        }
        return BigDecimal.valueOf(safe).divide(BigDecimal.valueOf(divisor), decimalPlaces, RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString() + NUMBER_UNITS[unit];
    }

    private void drawFluidGauge(GUIContext context, int x, FluidStack fluid, int amount, int capacity, int tickX) {
        int safeCapacity = Math.max(0, capacity);
        int safeAmount = Math.clamp(amount, 0, safeCapacity);
        int height = safeCapacity <= 0 ? 0 : Math.round(GAUGE_INTERIOR_HEIGHT * safeAmount / (float) safeCapacity);
        if (height > 0 && fluid != null && !fluid.isEmpty()) {
            FluidStack renderStack = fluid.copyWithAmount(Math.max(1, safeAmount));
            context.graphics.flush();
            DrawerHelper.drawFluidForGui(context.graphics, renderStack,
                Math.round(getPositionX()) + x + 1, Math.round(getPositionY()) + 124 - height,
                18, height, -1);
            context.graphics.flush();
        }
        drawGaugeTicks(context, tickX);
    }

    private void drawGaugeTicks(GUIContext context, int tickX) {
        for (int y = 84; y <= 114; y += 10) {
            sprite(context, tickX, y, 5, 1, tickX, y, 5, 1);
        }
        for (int y = 89; y <= 119; y += 10) {
            sprite(context, tickX + 2, y, 3, 1, tickX + 2, y, 3, 1);
        }
    }

    private void sprite(GUIContext context, int u, int v, int sourceWidth, int sourceHeight,
                        int x, int y, int width, int height) {
        context.drawTexture(SpriteTexture.of(NETextures.Crafting.CONTROLLER_ATLAS)
                .setSprite(u, v, sourceWidth, sourceHeight),
            getPositionX() + x, getPositionY() + y, width, height);
    }

    private void drawHorizontalProgress(GUIContext context, int x, int y, int width, int permille,
                                        int sourceX, int sourceY) {
        int fill = Math.round(width * Math.clamp(permille, 0, CraftingControllerPanelState.FULL)
            / (float) CraftingControllerPanelState.FULL);
        if (fill > 0) sprite(context, sourceX, sourceY, fill, PROGRESS_BAR_HEIGHT, x, y, fill, PROGRESS_BAR_HEIGHT);
    }

}
