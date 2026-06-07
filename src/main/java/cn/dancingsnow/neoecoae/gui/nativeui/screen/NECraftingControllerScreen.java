package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingControllerMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NEAe2IconButton;
import cn.dancingsnow.neoecoae.network.NECraftingUiState;
import cn.dancingsnow.neoecoae.network.NENetwork;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Screen for the ECO Crafting Controller — machine running status only.
 * <p>
 * Building operations (preview, auto-build, length selection) have been
 * migrated to the {@link NEStructureTerminalScreen}, accessed via the
 * Structure Terminal item.
 * </p>
 */
public class NECraftingControllerScreen extends NEBaseMachineScreen<NECraftingControllerMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    // ── Dark panel colours ──
    private static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    private static final int DARK_TEXT_VALUE = 0xFF8377FF;
    private static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    private static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;
    private static final int DARK_TEXT_WARNING = 0xFFFFD65A;
    private static final int DARK_TEXT_ERROR = 0xFFFF6A75;
    private static final int DARK_TEXT_BLUE = 0xFF3FD6FF;

    private static final long MAX_ENERGY_USAGE = 563200L;

    // ── Crafting module textures ──
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

    /*
     * Keep the original three toolbar buttons at exactly the same relative
     * distance from the main panel.
     *
     * Old values:
     * main panel: (7, 24)
     * toolbar: (-22, 3)
     * relative: (-29, -21)
     */
    private static final int TOOLBAR_TO_MAIN_PANEL_X = -29;
    private static final int TOOLBAR_TO_MAIN_PANEL_Y = -21;
    private static final int TOOLBAR_X = MAIN_PANEL_X + TOOLBAR_TO_MAIN_PANEL_X;
    private static final int TOOLBAR_Y = MAIN_PANEL_Y + TOOLBAR_TO_MAIN_PANEL_Y;
    private static final int TOOLBAR_BUTTON_SIZE = 14;
    private static final int TOOLBAR_BUTTON_STRIDE = TOOLBAR_BUTTON_SIZE + 7;
    private static final int TOOLBAR_BUTTON_AREA_H = TOOLBAR_BUTTON_SIZE * 3 + 7 * 2;

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

    private static final String OVERCLOCK_KEY = "gui.neoecoae.crafting.overclock";
    private static final String OVERCLOCK_ON_KEY = "gui.neoecoae.crafting.overclock.on";
    private static final String OVERCLOCK_OFF_KEY = "gui.neoecoae.crafting.overclock.off";
    private static final String ACTIVE_COOLING_KEY = "gui.neoecoae.crafting.active_cooling";
    private static final String ACTIVE_COOLING_ON_KEY = "gui.neoecoae.crafting.active_cooling.on";
    private static final String ACTIVE_COOLING_OFF_KEY = "gui.neoecoae.crafting.active_cooling.off";
    private static final String AUTO_CLEAR_COOLANT_KEY = "gui.neoecoae.crafting.auto_clear_coolant";
    private static final String AUTO_CLEAR_COOLANT_ON_KEY = "gui.neoecoae.crafting.auto_clear_coolant.on";
    private static final String AUTO_CLEAR_COOLANT_OFF_KEY = "gui.neoecoae.crafting.auto_clear_coolant.off";

    private boolean hasCraftingState;
    private NECraftingUiState craftingState;
    private NEAe2IconButton overclockButton;
    private NEAe2IconButton activeCoolingButton;
    private NEAe2IconButton autoClearWasteButton;

    public NECraftingControllerScreen(NECraftingControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.CRAFTING_CONTROLLER);
        this.imageWidth = 372;
        this.imageHeight = 240;
        this.craftingState = NECraftingUiState.empty(menu.getMachinePos());
    }

    /**
     * Called from the network thread via
     * {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}.
     */
    public void setCraftingUiState(NECraftingUiState state) {
        this.hasCraftingState = true;
        this.craftingState = state;
    }

    @Override
    protected void init() {
        super.init();

        int x = leftPos + TOOLBAR_X;
        int y = topPos + TOOLBAR_Y;

        overclockButton = new NEAe2IconButton(
                x,
                y,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                Component.translatable(OVERCLOCK_KEY),
                btn -> sendCraftingAction(NENetwork.NECraftingUiActionPacket.Action.TOGGLE_OVERCLOCK));
        overclockButton.setIcons(Icon.LEVEL_ENERGY, Icon.POWER_UNIT_AE);
        addRenderableWidget(overclockButton);

        activeCoolingButton = new NEAe2IconButton(
                x,
                y + TOOLBAR_BUTTON_STRIDE,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                Component.translatable(ACTIVE_COOLING_KEY),
                btn -> sendCraftingAction(NENetwork.NECraftingUiActionPacket.Action.TOGGLE_ACTIVE_COOLING));
        activeCoolingButton.setIcons(Icon.FLUID_SUBSTITUTION_ENABLED, Icon.FLUID_SUBSTITUTION_DISABLED);
        addRenderableWidget(activeCoolingButton);

        autoClearWasteButton = new NEAe2IconButton(
                x,
                y + TOOLBAR_BUTTON_STRIDE * 2,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE,
                Component.translatable(AUTO_CLEAR_COOLANT_KEY),
                btn -> sendCraftingAction(NENetwork.NECraftingUiActionPacket.Action.TOGGLE_AUTO_CLEAR_COOLING_WASTE));
        autoClearWasteButton.setIcons(Icon.CONDENSER_OUTPUT_TRASH, Icon.BACKGROUND_TRASH);
        addRenderableWidget(autoClearWasteButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateToolbarButtons(resolveCraftingState());
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderGaugeTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NECraftingUiState s = resolveCraftingState();

        drawDarkInsetRect(guiGraphics, MAIN_PANEL_X, MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);

        drawModuleArea(guiGraphics, s);
        drawStatusArea(guiGraphics, s);
        drawStatsArea(guiGraphics, s);
        drawGaugeArea(guiGraphics, s);

        drawFormedStatusBar(guiGraphics, s.formed(), imageWidth, imageHeight);
    }

    private void drawModuleArea(GuiGraphics g, NECraftingUiState s) {
        drawDarkInsetRect(g, MODULE_AREA_X, MODULE_AREA_Y, MODULE_AREA_W, MODULE_AREA_H);

        drawLine(g, "结构模块预览", MODULE_AREA_X + 8, MODULE_AREA_Y + 5, DARK_TEXT_PRIMARY);
        drawRightAlignedLine(
                g,
                "FT " + fmt(s.parallelCount()) + "   FX " + fmt(s.workerCount()),
                MODULE_AREA_X + MODULE_AREA_W - 8,
                MODULE_AREA_Y + 5,
                DARK_TEXT_VALUE);

        int cols = visibleWorkerColumns(s.workerCount());
        if (cols <= 0) {
            drawCenteredLine(g, "未检测到工作核心", MODULE_AREA_X, MODULE_AREA_Y + 39, MODULE_AREA_W, DARK_TEXT_MUTED);
            return;
        }

        int slotSize = 18;
        int gap = 0;
        int rowGap = 0;
        int totalW = cols * slotSize + (cols - 1) * gap;
        int startX = MODULE_AREA_X + (MODULE_AREA_W - totalW) / 2;

        int topY = MODULE_AREA_Y + 16;
        int middleY = topY + slotSize + rowGap;
        int bottomY = middleY + slotSize + rowGap;

        ResourceLocation tierLight = resolveParallelCoreLightTexture();

        int parallelSlots = Math.max(0, s.parallelCount());
        int activeTopFtSlots = Math.min(parallelSlots, cols);
        int activeBottomFtSlots = Math.min(Math.max(0, parallelSlots - cols), cols);

        for (int col = 0; col < cols; col++) {
            int x = startX + col * (slotSize + gap);

            boolean activeTop = col < activeTopFtSlots;
            boolean activeMiddle = col < s.workerCount();
            boolean activeBottom = col < activeBottomFtSlots;

            drawTexturedModuleSlot(g, x, topY, slotSize, MODULE_PARALLEL_CORE_FRONT, tierLight, activeTop);
            drawTexturedModuleSlot(g, x, middleY, slotSize, MODULE_CORE_SIDE, null, activeMiddle);
            drawTexturedModuleSlot(g, x, bottomY, slotSize, MODULE_PARALLEL_CORE_FRONT, tierLight, activeBottom);
        }
    }

    private void drawTexturedModuleSlot(
            GuiGraphics g,
            int x,
            int y,
            int size,
            ResourceLocation baseTexture,
            ResourceLocation overlayTexture,
            boolean active) {
        drawDarkInsetRect(g, x, y, size, size);

        int innerX = x + 2;
        int innerY = y + 2;
        int innerSize = size - 4;

        g.fill(innerX, innerY, innerX + innerSize, innerY + innerSize, 0xAA17141E);

        if (baseTexture != null) {
            g.blit(baseTexture, innerX, innerY, innerSize, innerSize, 0, 0, 16, 16, 16, 16);
        }

        if (overlayTexture != null) {
            g.blit(overlayTexture, innerX, innerY, innerSize, innerSize, 0, 0, 16, 16, 16, 16);
        }

        if (!active) {
            g.fill(innerX, innerY, innerX + innerSize, innerY + innerSize, 0x99000000);
        }
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

    private static int visibleWorkerColumns(int workerCount) {
        if (workerCount <= 0) {
            return 0;
        }
        return Math.min(16, workerCount);
    }

    private void drawStatusArea(GuiGraphics g, NECraftingUiState s) {
        drawDarkInsetRect(g, STATUS_AREA_X, STATUS_AREA_Y, STATUS_AREA_W, STATUS_AREA_H);

        drawLine(g, "状态", STATUS_AREA_X + 8, STATUS_AREA_Y + 5, DARK_TEXT_PRIMARY);

        int y = STATUS_AREA_Y + 21;
        drawStatusRow(g, "超频", s.overclocked(), STATUS_AREA_X + 4, y);
        y += 15;
        drawStatusRow(g, "冷却", s.activeCooling(), STATUS_AREA_X + 4, y);
        y += 15;
        drawStatusRow(g, "清废", s.autoClearCoolingWaste(), STATUS_AREA_X + 4, y);
    }

    private void drawStatsArea(GuiGraphics g, NECraftingUiState s) {
        drawDarkInsetRect(g, STATS_AREA_X, STATS_AREA_Y, STATS_AREA_W, STATS_AREA_H);

        drawLine(g, "合成统计", STATS_AREA_X + 8, STATS_AREA_Y + 5, DARK_TEXT_PRIMARY);

        int x = STATS_AREA_X + 8;
        int y = STATS_AREA_Y + 19;

        drawCompactPairLine(g, "并行", s.runningThreadCount(), s.availableThreads(), x, y);
        drawThreadUsageBar(g, x, y + 12, STATS_AREA_W - 16, 9, s.runningThreadCount(), s.availableThreads());

        y += 25;
        drawInlineValueLine(g, "最大上限并行", s.threadCount(), x, y);

        y += 11;
        drawInlineValueLine(g, "样板", s.patternBusCount(), x, y);
        drawInlineValueLine(g, "工作", s.workerCount(), x + 76, y);
    }

    private void drawGaugeArea(GuiGraphics g, NECraftingUiState s) {
        drawDarkInsetRect(g, GAUGE_AREA_X, GAUGE_AREA_Y, GAUGE_AREA_W, GAUGE_AREA_H);

        drawLine(g, "能耗 / 冷却", GAUGE_AREA_X + 8, GAUGE_AREA_Y + 5, DARK_TEXT_PRIMARY);

        int gaugeY = GAUGE_AREA_Y + 16;
        int gaugeH = 34;
        int gaugeW = 25;
        int energyX = GAUGE_AREA_X + 13;
        int coolantX = GAUGE_AREA_X + GAUGE_AREA_W - 13 - gaugeW;

        long energyUsage = s.energyUsage();
        double energyRatio = clampRatio(energyUsage, MAX_ENERGY_USAGE);
        int energyColor = energyGaugeColor(energyRatio);

        long coolantAmount = s.coolantAmount();
        long coolantCapacity = s.coolantCapacity();
        double coolantRatio = clampRatio(coolantAmount, coolantCapacity);

        drawVerticalReserveGauge(g, energyX, gaugeY, gaugeW, gaugeH, "", energyColor, energyRatio);
        drawVerticalReserveGauge(g, coolantX, gaugeY, gaugeW, gaugeH, "", DARK_TEXT_BLUE, coolantRatio);

        drawCenteredLine(g, "能耗", energyX - 8, gaugeY + gaugeH + 1, gaugeW + 16, DARK_TEXT_MUTED);
        drawCenteredLine(g, "冷却", coolantX - 8, gaugeY + gaugeH + 1, gaugeW + 16, DARK_TEXT_MUTED);
    }

    private void drawStatusRow(GuiGraphics g, String label, boolean enabled, int x, int y) {
        drawDarkInsetRect(g, x, y - 3, 13, 13);

        int light = enabled ? DARK_TEXT_SUCCESS : DARK_TEXT_ERROR;
        g.fill(x + 4, y + 1, x + 9, y + 6, light);

        drawLine(g, label, x + 18, y, DARK_TEXT_MUTED);
        drawLine(g, enabled ? "启用" : "关闭", x + 44, y, enabled ? DARK_TEXT_SUCCESS : DARK_TEXT_ERROR);
    }

    private void drawVerticalReserveGauge(
            GuiGraphics g, int x, int y, int w, int h, String label, int accentColor, double fillRatio) {
        drawDarkInsetRect(g, x, y, w, h);

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
        drawDarkInsetRect(g, x, y, w, h);

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
            g.fill(ix, iy, ix + fillW, iy + ih, DARK_TEXT_SUCCESS);
        }
    }

    private NECraftingUiState resolveCraftingState() {
        if (hasCraftingState) {
            return this.craftingState;
        }
        ECOCraftingSystemBlockEntity be = getCraftingBE();
        return be != null ? be.createCraftingUiState() : this.craftingState;
    }

    private void updateToolbarButtons(NECraftingUiState state) {
        if (overclockButton != null) {
            overclockButton.setToggled(state.overclocked());
            overclockButton.setTooltip(
                    Tooltip.create(Component.translatable(state.overclocked() ? OVERCLOCK_ON_KEY : OVERCLOCK_OFF_KEY)));
        }
        if (activeCoolingButton != null) {
            activeCoolingButton.setToggled(state.activeCooling());
            activeCoolingButton.setTooltip(Tooltip.create(
                    Component.translatable(state.activeCooling() ? ACTIVE_COOLING_ON_KEY : ACTIVE_COOLING_OFF_KEY)));
        }
        if (autoClearWasteButton != null) {
            autoClearWasteButton.setToggled(state.autoClearCoolingWaste());
            autoClearWasteButton.setTooltip(Tooltip.create(Component.translatable(
                    state.autoClearCoolingWaste() ? AUTO_CLEAR_COOLANT_ON_KEY : AUTO_CLEAR_COOLANT_OFF_KEY)));
        }
    }

    private void sendCraftingAction(NENetwork.NECraftingUiActionPacket.Action action) {
        NENetwork.CHANNEL.sendToServer(new NENetwork.NECraftingUiActionPacket(menu.getMachinePos(), action));
    }

    @Override
    public boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (mouseX >= guiLeft + TOOLBAR_X
                && mouseX < guiLeft + TOOLBAR_X + TOOLBAR_BUTTON_SIZE
                && mouseY >= guiTop + TOOLBAR_Y
                && mouseY < guiTop + TOOLBAR_Y + TOOLBAR_BUTTON_AREA_H) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    public List<Rect2i> getJeiExtraAreas() {
        List<Rect2i> areas = new ArrayList<>();
        areas.add(new Rect2i(leftPos + TOOLBAR_X, topPos + TOOLBAR_Y, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_AREA_H));
        return areas;
    }

    private ECOCraftingSystemBlockEntity getCraftingBE() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.getMachinePos());
        if (be instanceof ECOCraftingSystemBlockEntity crafting) {
            return crafting;
        }
        return null;
    }

    // ── Shared drawing helpers ──

    private void drawDarkInsetRect(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFCBCCD4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D0D11);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF85818D);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF0D0D11);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF47434F);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFF605A66);
    }

    private void drawFormedStatusBar(GuiGraphics g, boolean formed, int imageWidth, int imageHeight) {
        int x = PANEL_MARGIN;
        int y = imageHeight - FORMED_BAR_BOTTOM_GAP - FORMED_BAR_H;
        int w = imageWidth - PANEL_MARGIN * 2;
        int h = FORMED_BAR_H;

        drawDarkInsetRect(g, x, y, w, h);

        Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component value = boolText(formed);

        int textW = font.width(label) + font.width(value);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - font.lineHeight) / 2;

        g.drawString(font, label, textX, textY, DARK_TEXT_PRIMARY, false);
        g.drawString(
                font, value, textX + font.width(label), textY, formed ? DARK_TEXT_SUCCESS : DARK_TEXT_ERROR, false);
    }

    private void drawLine(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, Component.literal(text), x, y, color, false);
    }

    private void drawCenteredLine(GuiGraphics g, String text, int x, int y, int w, int color) {
        g.drawString(font, Component.literal(text), x + (w - font.width(text)) / 2, y, color, false);
    }

    private void drawRightAlignedLine(GuiGraphics g, String text, int rightX, int y, int color) {
        g.drawString(font, Component.literal(text), rightX - font.width(text), y, color, false);
    }

    private void drawInlineValueLine(GuiGraphics g, String label, long value, int x, int y) {
        int cursor = drawSegment(g, label + ": ", x, y, DARK_TEXT_MUTED);
        drawSegment(g, fmt(value), x + cursor, y, DARK_TEXT_VALUE);
    }

    private void drawCompactPairLine(GuiGraphics g, String label, long current, long max, int x, int y) {
        int cursor = drawSegment(g, label + ": ", x, y, DARK_TEXT_MUTED);
        cursor += drawSegment(g, fmt(current), x + cursor, y, DARK_TEXT_SUCCESS);
        cursor += drawSegment(g, " / ", x + cursor, y, DARK_TEXT_MUTED);
        drawSegment(g, fmt(max), x + cursor, y, DARK_TEXT_VALUE);
    }

    private int drawSegment(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, Component.literal(text), x, y, color, false);
        return font.width(text);
    }

    private static int ratioWidth(long current, long max, int fullWidth) {
        if (fullWidth <= 0 || max <= 0 || current <= 0) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(current, max));
        return (int) Math.max(1L, Math.min(fullWidth, clamped * fullWidth / max));
    }

    private static String fmt(long value) {
        return NUMBER_FORMAT.format(value);
    }

    // ── Gauge helpers ──

    private static double clampRatio(long value, long max) {
        if (value <= 0 || max <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (double) value / (double) max));
    }

    private static int energyGaugeColor(double ratio) {
        if (ratio >= 0.9D) {
            return DARK_TEXT_ERROR;
        }
        if (ratio >= 0.5D) {
            return DARK_TEXT_WARNING;
        }
        return DARK_TEXT_SUCCESS;
    }

    private static boolean isMouseInRect(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void renderGaugeTooltips(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState s = resolveCraftingState();

        int gaugeY = GAUGE_AREA_Y + 16;
        int gaugeH = 34;
        int gaugeW = 25;
        int energyX = GAUGE_AREA_X + 13;
        int coolantX = GAUGE_AREA_X + GAUGE_AREA_W - 13 - gaugeW;

        int screenEnergyX = leftPos + energyX;
        int screenCoolantX = leftPos + coolantX;
        int screenGaugeY = topPos + gaugeY;

        if (isMouseInRect(mouseX, mouseY, screenEnergyX, screenGaugeY, gaugeW, gaugeH)) {
            String tip = "当前能耗：" + fmt(s.energyUsage());
            g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
        }

        if (isMouseInRect(mouseX, mouseY, screenCoolantX, screenGaugeY, gaugeW, gaugeH)) {
            String tip = "冷却液还剩：" + fmt(s.coolantAmount()) + " / " + fmt(s.coolantCapacity()) + " mB";
            g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
        }
    }

    public NECraftingControllerMenu getMenu() {
        return menu;
    }
}
