package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class NECraftingControllerWidget extends NELDLibSyncedStateWidget<NECraftingUiState> {
    private static final int WIDTH = 372;
    private static final int HEIGHT = 240;
    private static final long MAX_ENERGY_USAGE = 563200L;

    private static final ResourceLocation MODULE_CORE_SIDE =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/core_side.png");
    private static final ResourceLocation MODULE_PARALLEL_CORE_FRONT =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/parallel_core_north.png");
    private static final ResourceLocation MODULE_PARALLEL_CORE_LIGHT_L4 =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/parallel_core_light_a.png");
    private static final ResourceLocation MODULE_PARALLEL_CORE_LIGHT_L6 =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/parallel_core_light_b.png");
    private static final ResourceLocation MODULE_PARALLEL_CORE_LIGHT_L9 =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/block/crafting/core/parallel_core_light_c.png");

    private static final int PANEL_MARGIN = 7;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 24;
    private static final int MAIN_PANEL_W = 358;
    private static final int MAIN_PANEL_H = 176;
    private static final int TOOLBAR_X = -22;
    private static final int TOOLBAR_Y = 3;
    private static final int TOOLBAR_BUTTON_SIZE = 14;
    private static final int TOOLBAR_BUTTON_STRIDE = TOOLBAR_BUTTON_SIZE + 7;
    private static final int TOOLBAR_AREA_H = TOOLBAR_BUTTON_SIZE * 3 + 7 * 2;

    private static final int MODULE_AREA_X = MAIN_PANEL_X + 7;
    private static final int MODULE_AREA_Y = MAIN_PANEL_Y + 7;
    private static final int MODULE_AREA_W = MAIN_PANEL_W - 14;
    private static final int MODULE_AREA_H = 84;
    private static final int MIDDLE_AREA_Y = MODULE_AREA_Y + MODULE_AREA_H + 8;
    private static final int STATUS_AREA_X = MODULE_AREA_X;
    private static final int STATUS_AREA_Y = MIDDLE_AREA_Y;
    private static final int STATUS_AREA_W = 74;
    private static final int STATUS_AREA_H = 70;
    private static final int STATS_AREA_X = STATUS_AREA_X + STATUS_AREA_W + 7;
    private static final int STATS_AREA_Y = MIDDLE_AREA_Y;
    private static final int STATS_AREA_W = 168;
    private static final int STATS_AREA_H = 70;
    private static final int GAUGE_AREA_X = STATS_AREA_X + STATS_AREA_W + 7;
    private static final int GAUGE_AREA_Y = MIDDLE_AREA_Y;
    private static final int GAUGE_AREA_W = MODULE_AREA_X + MODULE_AREA_W - GAUGE_AREA_X;
    private static final int GAUGE_AREA_H = 70;
    private static final int FORMED_BAR_H = 25;
    private static final int FORMED_BAR_BOTTOM_GAP = 7;

    private final ECOCraftingSystemBlockEntity crafting;

    public NECraftingControllerWidget(ECOCraftingSystemBlockEntity crafting) {
        super(
                crafting.getBlockState().getBlock().getName(),
                WIDTH,
                HEIGHT,
                NECraftingUiState.empty(crafting.getBlockPos()),
                crafting::createCraftingUiState,
                NELDLibStateCodecs::writeCrafting,
                NELDLibStateCodecs::readCrafting,
                10);
        this.crafting = crafting;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addToolbarButton(0, click -> {
            if (!click.isRemote) {
                crafting.toggleOverclocked();
                syncStateNow();
            }
        });
        addToolbarButton(1, click -> {
            if (!click.isRemote) {
                crafting.toggleActiveCooling();
                syncStateNow();
            }
        });
        addToolbarButton(2, click -> {
            if (!click.isRemote) {
                crafting.toggleAutoClearCoolingWaste();
                syncStateNow();
            }
        });
    }

    @Override
    public List<Rect2i> getGuiExtraAreas(Rect2i guiRect, List<Rect2i> list) {
        List<Rect2i> areas = new ArrayList<>(super.getGuiExtraAreas(guiRect, list));
        areas.add(new Rect2i(absX(TOOLBAR_X), absY(TOOLBAR_Y), TOOLBAR_BUTTON_SIZE, TOOLBAR_AREA_H));
        return areas;
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int ox = getPositionX();
        int oy = getPositionY();
        NECraftingUiState state = currentState();

        NELDLibStyle.drawDarkInsetRect(graphics, ox + MAIN_PANEL_X, oy + MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + MODULE_AREA_X, oy + MODULE_AREA_Y, MODULE_AREA_W, MODULE_AREA_H);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + STATUS_AREA_X, oy + STATUS_AREA_Y, STATUS_AREA_W, STATUS_AREA_H);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + STATS_AREA_X, oy + STATS_AREA_Y, STATS_AREA_W, STATS_AREA_H);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + GAUGE_AREA_X, oy + GAUGE_AREA_Y, GAUGE_AREA_W, GAUGE_AREA_H);
        NELDLibStyle.drawDarkInsetRect(
                graphics,
                ox + PANEL_MARGIN,
                oy + HEIGHT - FORMED_BAR_BOTTOM_GAP - FORMED_BAR_H,
                WIDTH - PANEL_MARGIN * 2,
                FORMED_BAR_H);

        drawModuleArea(graphics, state);
        drawThreadUsageBar(
                graphics,
                absX(STATS_AREA_X + 8),
                absY(STATS_AREA_Y + 31),
                STATS_AREA_W - 16,
                9,
                state.runningThreadCount(),
                state.availableThreads());
        drawGaugeArea(graphics, state);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NECraftingUiState state = currentState();
        drawLocalString(graphics, title, 8, 8, TEXT_PRIMARY);
        drawToolbarIcons(graphics, state);
        drawModuleLabels(graphics, state);
        drawStatusArea(graphics, state);
        drawStatsArea(graphics, state);
        drawGaugeLabels(graphics);
        drawFormedStatusBar(graphics, state);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderToolbarTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (renderGaugeTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (renderModuleTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        renderStatsTooltip(graphics, mouseX, mouseY);
    }

    private void addToolbarButton(
            int index, java.util.function.Consumer<com.lowdragmc.lowdraglib.gui.util.ClickData> action) {
        addWidget(new ButtonWidget(
                TOOLBAR_X,
                TOOLBAR_Y + index * TOOLBAR_BUTTON_STRIDE,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                NELDLibStyle.aeToolbarButton(),
                action));
    }

    private void drawModuleArea(GuiGraphics g, NECraftingUiState state) {
        int cols = visibleWorkerColumns(state.workerCount());
        if (cols <= 0) {
            return;
        }

        int slotSize = 18;
        int totalW = cols * slotSize;
        int startX = absX(MODULE_AREA_X + (MODULE_AREA_W - totalW) / 2);
        int topY = absY(MODULE_AREA_Y + 16);
        int middleY = topY + slotSize;
        int bottomY = middleY + slotSize;

        ResourceLocation controllerLight = resolveParallelCoreLightTexture();
        List<Integer> coreTiers = state.parallelCoreTiers();
        int parallelSlots = Math.max(0, state.parallelCount());
        int activeTopFtSlots = Math.min(parallelSlots, cols);
        int activeBottomFtSlots = Math.min(Math.max(0, parallelSlots - cols), cols);

        for (int col = 0; col < cols; col++) {
            int x = startX + col * slotSize;
            boolean activeTop = col < activeTopFtSlots;
            boolean activeMiddle = col < state.workerCount();
            boolean activeBottom = col < activeBottomFtSlots;
            ResourceLocation topLight = col < coreTiers.size() ? lightForTier(coreTiers.get(col)) : controllerLight;
            ResourceLocation bottomLight =
                    col + cols < coreTiers.size() ? lightForTier(coreTiers.get(col + cols)) : controllerLight;

            NELDLibStyle.drawTexturedModuleSlot(g, x, topY, slotSize, MODULE_PARALLEL_CORE_FRONT, topLight, activeTop);
            NELDLibStyle.drawTexturedModuleSlot(g, x, middleY, slotSize, MODULE_CORE_SIDE, null, activeMiddle);
            NELDLibStyle.drawTexturedModuleSlot(
                    g, x, bottomY, slotSize, MODULE_PARALLEL_CORE_FRONT, bottomLight, activeBottom);
        }
    }

    private void drawModuleLabels(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.module_preview"),
                MODULE_AREA_X + 8,
                MODULE_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        NELDLibStyle.drawRight(
                g,
                font(),
                Component.literal("FT " + NELDLibText.number(state.parallelCount()) + "   FX "
                        + NELDLibText.number(state.workerCount())),
                absX(MODULE_AREA_X + MODULE_AREA_W - 8),
                absY(MODULE_AREA_Y + 5),
                NELDLibStyle.DARK_TEXT_VALUE);
        if (visibleWorkerColumns(state.workerCount()) <= 0) {
            NELDLibStyle.drawCentered(
                    g,
                    font(),
                    Component.translatable("gui.neoecoae.crafting.no_worker_cores"),
                    absX(MODULE_AREA_X),
                    absY(MODULE_AREA_Y + 39),
                    MODULE_AREA_W,
                    NELDLibStyle.DARK_TEXT_MUTED);
        }
    }

    private void drawStatusArea(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.status"),
                STATUS_AREA_X + 8,
                STATUS_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        int y = STATUS_AREA_Y + 21;
        drawStatusRow(
                g,
                Component.translatable("gui.neoecoae.crafting.overclock"),
                state.overclocked(),
                STATUS_AREA_X + 4,
                y);
        y += 15;
        drawStatusRow(
                g,
                Component.translatable("gui.neoecoae.crafting.cooling_short"),
                state.activeCooling(),
                STATUS_AREA_X + 4,
                y);
        y += 15;
        drawStatusRow(
                g,
                Component.translatable("gui.neoecoae.crafting.waste_short"),
                state.autoClearCoolingWaste(),
                STATUS_AREA_X + 4,
                y);
    }

    private void drawStatsArea(GuiGraphics g, NECraftingUiState state) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.stats"),
                STATS_AREA_X + 8,
                STATS_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        int x = STATS_AREA_X + 8;
        int y = STATS_AREA_Y + 19;
        drawCompactPairLine(
                g,
                Component.translatable("gui.neoecoae.common.parallel").getString() + ": ",
                state.runningThreadCount(),
                state.availableThreads(),
                x,
                y);
        y += 25;
        drawInlineValueLine(
                g,
                Component.translatable("gui.neoecoae.crafting.max_parallel").getString() + ": ",
                state.threadCount(),
                x,
                y);
        y += 11;
        drawInlineValueLine(
                g,
                Component.translatable("gui.neoecoae.crafting.patterns_short").getString() + ": ",
                state.patternBusCount(),
                x,
                y);
        drawInlineValueLine(
                g,
                Component.translatable("gui.neoecoae.crafting.workers_short").getString() + ": ",
                state.workerCount(),
                x + 76,
                y);
    }

    private void drawGaugeArea(GuiGraphics g, NECraftingUiState state) {
        int gaugeY = GAUGE_AREA_Y + 16;
        int gaugeH = 34;
        int gaugeW = 25;
        int energyX = GAUGE_AREA_X + 13;
        int coolantX = GAUGE_AREA_X + GAUGE_AREA_W - 13 - gaugeW;
        double energyRatio = clampRatio(state.energyUsage(), MAX_ENERGY_USAGE);
        double coolantRatio = clampRatio(state.coolantAmount(), state.coolantCapacity());

        drawVerticalReserveGauge(
                g, absX(energyX), absY(gaugeY), gaugeW, gaugeH, energyGaugeColor(energyRatio), energyRatio);
        drawVerticalReserveGauge(
                g, absX(coolantX), absY(gaugeY), gaugeW, gaugeH, NELDLibStyle.DARK_TEXT_BLUE, coolantRatio);
    }

    private void drawGaugeLabels(GuiGraphics g) {
        drawLine(
                g,
                Component.translatable("gui.neoecoae.crafting.energy_cooling"),
                GAUGE_AREA_X + 8,
                GAUGE_AREA_Y + 5,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        int gaugeY = GAUGE_AREA_Y + 16;
        int gaugeH = 34;
        int gaugeW = 25;
        int energyX = GAUGE_AREA_X + 13;
        int coolantX = GAUGE_AREA_X + GAUGE_AREA_W - 13 - gaugeW;
        NELDLibStyle.drawCentered(
                g,
                font(),
                Component.translatable("gui.neoecoae.crafting.energy_short"),
                absX(energyX - 8),
                absY(gaugeY + gaugeH + 1),
                gaugeW + 16,
                NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibStyle.drawCentered(
                g,
                font(),
                Component.translatable("gui.neoecoae.crafting.cooling_short"),
                absX(coolantX - 8),
                absY(gaugeY + gaugeH + 1),
                gaugeW + 16,
                NELDLibStyle.DARK_TEXT_MUTED);
    }

    private void drawToolbarIcons(GuiGraphics graphics, NECraftingUiState state) {
        drawToolbarIcon(graphics, 0, state.overclocked() ? Icon.LEVEL_ENERGY : Icon.POWER_UNIT_AE);
        drawToolbarIcon(
                graphics,
                1,
                state.activeCooling() ? Icon.FLUID_SUBSTITUTION_ENABLED : Icon.FLUID_SUBSTITUTION_DISABLED);
        drawToolbarIcon(
                graphics, 2, state.autoClearCoolingWaste() ? Icon.CONDENSER_OUTPUT_TRASH : Icon.BACKGROUND_TRASH);
    }

    private void drawToolbarIcon(GuiGraphics graphics, int index, Icon icon) {
        NELDLibAe2StyleRenderer.drawAeIcon(
                graphics,
                icon,
                absX(TOOLBAR_X + (TOOLBAR_BUTTON_SIZE - icon.width) / 2),
                absY(TOOLBAR_Y + index * TOOLBAR_BUTTON_STRIDE + (TOOLBAR_BUTTON_SIZE - icon.height) / 2));
    }

    private void drawStatusRow(GuiGraphics g, Component label, boolean enabled, int x, int y) {
        int absX = absX(x);
        int absY = absY(y);
        NELDLibStyle.drawDarkInsetRect(g, absX, absY - 3, 13, 13);
        int light = enabled ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR;
        g.fill(absX + 4, absY + 1, absX + 9, absY + 6, light);
        g.drawString(font(), label, absX + 18, absY, NELDLibStyle.DARK_TEXT_MUTED, false);
        g.drawString(
                font(),
                Component.translatable(enabled ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off"),
                absX + 44,
                absY,
                enabled ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR,
                false);
    }

    private void drawFormedStatusBar(GuiGraphics g, NECraftingUiState state) {
        Component formedLabel =
                Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component formedValue = boolText(state.formed());
        Component activeLabel = Component.literal("    ")
                .append(Component.translatable("gui.neoecoae.machine.active"))
                .append(": ");
        Component activeValue = boolText(state.active());
        int textW = font().width(formedLabel)
                + font().width(formedValue)
                + font().width(activeLabel)
                + font().width(activeValue);
        int x = absX(PANEL_MARGIN);
        int y = absY(HEIGHT - FORMED_BAR_BOTTOM_GAP - FORMED_BAR_H);
        int textX = x + (WIDTH - PANEL_MARGIN * 2 - textW) / 2;
        int textY = y + (FORMED_BAR_H - font().lineHeight) / 2;
        g.drawString(font(), formedLabel, textX, textY, NELDLibStyle.DARK_TEXT_PRIMARY, false);
        textX += font().width(formedLabel);
        g.drawString(
                font(),
                formedValue,
                textX,
                textY,
                state.formed() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR,
                false);
        textX += font().width(formedValue);
        g.drawString(font(), activeLabel, textX, textY, NELDLibStyle.DARK_TEXT_PRIMARY, false);
        textX += font().width(activeLabel);
        g.drawString(
                font(),
                activeValue,
                textX,
                textY,
                state.active() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_MUTED,
                false);
    }

    private void drawVerticalReserveGauge(
            GuiGraphics g, int x, int y, int w, int h, int accentColor, double fillRatio) {
        NELDLibStyle.drawDarkInsetRect(g, x, y, w, h);
        int ix = x + 7;
        int iy = y + 7;
        int iw = w - 14;
        int ih = h - 14;
        int fillH = (int) Math.round(ih * Math.max(0.0D, Math.min(1.0D, fillRatio)));
        int fillY = iy + ih - fillH;
        g.fill(ix, iy, ix + iw, iy + ih, 0xAA17141E);
        if (fillH > 0) {
            g.fill(ix, fillY, ix + iw, iy + ih, accentColor);
            g.fill(ix, fillY, ix + iw, Math.min(fillY + 2, iy + ih), 0x70FFFFFF);
        }
    }

    private void drawThreadUsageBar(GuiGraphics g, int x, int y, int w, int h, long current, long max) {
        NELDLibStyle.drawDarkInsetRect(g, x, y, w, h);
        int ix = x + 3;
        int iy = y + 3;
        int iw = Math.max(0, w - 6);
        int ih = Math.max(0, h - 6);
        int fillW = ratioWidth(current, max, iw);
        if (iw <= 0 || ih <= 0) {
            return;
        }
        g.fill(ix, iy, ix + iw, iy + ih, 0xAA17141E);
        if (fillW > 0) {
            g.fill(ix, iy, ix + fillW, iy + ih, NELDLibStyle.DARK_TEXT_SUCCESS);
        }
    }

    private boolean renderToolbarTooltip(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        if (isMouseIn(TOOLBAR_X, TOOLBAR_Y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, mouseX, mouseY)) {
            g.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable(
                            state.overclocked()
                                    ? "gui.neoecoae.crafting.overclock.on"
                                    : "gui.neoecoae.crafting.overclock.off")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                TOOLBAR_X,
                TOOLBAR_Y + TOOLBAR_BUTTON_STRIDE,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                mouseX,
                mouseY)) {
            g.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable(
                            state.activeCooling()
                                    ? "gui.neoecoae.crafting.active_cooling.on"
                                    : "gui.neoecoae.crafting.active_cooling.off")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                TOOLBAR_X,
                TOOLBAR_Y + TOOLBAR_BUTTON_STRIDE * 2,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                mouseX,
                mouseY)) {
            g.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable(
                            state.autoClearCoolingWaste()
                                    ? "gui.neoecoae.crafting.auto_clear_coolant.on"
                                    : "gui.neoecoae.crafting.auto_clear_coolant.off")),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private boolean renderGaugeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        int gaugeY = GAUGE_AREA_Y + 16;
        int gaugeH = 34;
        int gaugeW = 25;
        int energyX = GAUGE_AREA_X + 13;
        int coolantX = GAUGE_AREA_X + GAUGE_AREA_W - 13 - gaugeW;
        if (isMouseIn(energyX, gaugeY, gaugeW, gaugeH, mouseX, mouseY)) {
            g.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.crafting.energy_usage"),
                            Component.literal(NELDLibText.number(state.energyUsage()) + " AE"),
                            Component.literal(NELDLibText.percent(clampRatio(state.energyUsage(), MAX_ENERGY_USAGE)))),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(coolantX, gaugeY, gaugeW, gaugeH, mouseX, mouseY)) {
            g.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.crafting.coolant"),
                            Component.literal(
                                    NELDLibText.usedTotal(state.coolantAmount(), state.coolantCapacity()) + " mB"),
                            Component.literal(NELDLibText.percentOrNA(state.coolantAmount(), state.coolantCapacity()))),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private boolean renderModuleTooltip(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        int cols = visibleWorkerColumns(state.workerCount());
        if (cols <= 0) {
            return false;
        }
        int slotSize = 18;
        int totalW = cols * slotSize;
        int startX = MODULE_AREA_X + (MODULE_AREA_W - totalW) / 2;
        int topY = MODULE_AREA_Y + 16;
        int middleY = topY + slotSize;
        int bottomY = middleY + slotSize;
        if (isMouseIn(startX, topY, totalW, slotSize, mouseX, mouseY)) {
            int col = (mouseX - absX(startX)) / slotSize;
            g.renderTooltip(
                    font(),
                    getParallelPerCoreTooltip(tierAt(state.parallelCoreTiers(), col), state.overclocked()),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(startX, bottomY, totalW, slotSize, mouseX, mouseY)) {
            int col = (mouseX - absX(startX)) / slotSize;
            g.renderTooltip(
                    font(),
                    getParallelPerCoreTooltip(tierAt(state.parallelCoreTiers(), col + cols), state.overclocked()),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(startX, middleY, totalW, slotSize, mouseX, mouseY)) {
            int col = (mouseX - absX(startX)) / slotSize;
            if (col >= 0 && col < state.workerCraftOutputs().size()) {
                ItemStack stack = state.workerCraftOutputs().get(col);
                if (!stack.isEmpty()) {
                    List<Component> lines =
                            Screen.getTooltipFromItem(net.minecraft.client.Minecraft.getInstance(), stack);
                    g.renderTooltip(font(), lines, stack.getTooltipImage(), stack, mouseX, mouseY);
                    return true;
                }
            }
            g.renderTooltip(font(), Component.translatable("block.neoecoae.crafting_worker"), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void renderStatsTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!isMouseIn(STATS_AREA_X, STATS_AREA_Y, STATS_AREA_W, STATS_AREA_H, mouseX, mouseY)) {
            return;
        }
        NECraftingUiState state = currentState();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.neoecoae.crafting.parallel_core_tiers"));
        lines.add(Component.literal("FT4: " + countTier(state, 1) + " x " + parallelPerCore(1, state.overclocked())));
        lines.add(Component.literal("FT6: " + countTier(state, 2) + " x " + parallelPerCore(2, state.overclocked())));
        lines.add(Component.literal("FT9: " + countTier(state, 3) + " x " + parallelPerCore(3, state.overclocked())));
        lines.add(Component.translatable(
                "gui.neoecoae.crafting.effective_parallel", NELDLibText.number(state.effectiveParallel())));
        g.renderTooltip(font(), lines, Optional.empty(), mouseX, mouseY);
    }

    private void drawLine(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font(), text, absX(x), absY(y), color, false);
    }

    private void drawInlineValueLine(GuiGraphics g, String label, long value, int x, int y) {
        int cursor = NELDLibStyle.drawSegment(g, font(), label, absX(x), absY(y), NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibStyle.drawSegment(
                g, font(), NELDLibText.number(value), absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void drawCompactPairLine(GuiGraphics g, String label, long current, long max, int x, int y) {
        int cursor = NELDLibStyle.drawSegment(g, font(), label, absX(x), absY(y), NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibStyle.drawSegment(
                g, font(), NELDLibText.number(current), absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_SUCCESS);
        cursor += NELDLibStyle.drawSegment(g, font(), " / ", absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibStyle.drawSegment(
                g, font(), NELDLibText.number(max), absX(x) + cursor, absY(y), NELDLibStyle.DARK_TEXT_VALUE);
    }

    private ResourceLocation resolveParallelCoreLightTexture() {
        String titleText = title.getString().toUpperCase(Locale.ROOT);
        if (titleText.contains("F9")) {
            return MODULE_PARALLEL_CORE_LIGHT_L9;
        }
        if (titleText.contains("F6")) {
            return MODULE_PARALLEL_CORE_LIGHT_L6;
        }
        return MODULE_PARALLEL_CORE_LIGHT_L4;
    }

    private static ResourceLocation lightForTier(int tier) {
        return switch (tier) {
            case 3 -> MODULE_PARALLEL_CORE_LIGHT_L9;
            case 2 -> MODULE_PARALLEL_CORE_LIGHT_L6;
            default -> MODULE_PARALLEL_CORE_LIGHT_L4;
        };
    }

    private static int visibleWorkerColumns(int workerCount) {
        return workerCount <= 0 ? 0 : Math.min(16, workerCount);
    }

    private static int tierAt(List<Integer> tiers, int index) {
        return index >= 0 && index < tiers.size() ? tiers.get(index) : 1;
    }

    private static Component getParallelPerCoreTooltip(int tier, boolean overclocked) {
        return Component.translatable(
                "gui.neoecoae.crafting.parallel_per_core", NELDLibText.number(parallelPerCore(tier, overclocked)));
    }

    private static int countTier(NECraftingUiState state, int tier) {
        int count = 0;
        for (int value : state.parallelCoreTiers()) {
            if (value == tier) {
                count++;
            }
        }
        return count;
    }

    private static int parallelPerCore(int tier, boolean overclocked) {
        return switch (tier) {
            case 3 -> overclocked ? 384 : 256;
            case 2 -> overclocked ? 96 : 72;
            default -> overclocked ? 32 : 24;
        };
    }

    private static int ratioWidth(long current, long max, int fullWidth) {
        if (fullWidth <= 0 || max <= 0 || current <= 0) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(current, max));
        return (int) Math.max(1L, Math.min(fullWidth, clamped * fullWidth / max));
    }

    private static double clampRatio(long value, long max) {
        if (value <= 0 || max <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (double) value / (double) max));
    }

    private static int energyGaugeColor(double ratio) {
        if (ratio >= 0.9D) {
            return NELDLibStyle.DARK_TEXT_ERROR;
        }
        if (ratio >= 0.5D) {
            return NELDLibStyle.DARK_TEXT_WARNING;
        }
        return NELDLibStyle.DARK_TEXT_SUCCESS;
    }
}
