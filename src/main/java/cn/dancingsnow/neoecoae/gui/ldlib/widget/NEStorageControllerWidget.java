package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.GuiText;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageHugeStackState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibValueText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStorageMetricsModel.Metric;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStorageMetricsModel.StorageMetrics;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public class NEStorageControllerWidget extends NELDLibSyncedStateWidget<NEStorageUiState> {
    public static final int UI_WIDTH = 344;
    public static final int UI_HEIGHT = 232;
    private static final ResourceLocation STORAGE_ELEMENTS =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/storage/estorage_controller_elements.png");
    private static final int LEFT_PANEL_X = 8;
    private static final int LEFT_PANEL_Y = 24;
    private static final int LEFT_PANEL_W = 162;
    private static final int LEFT_PANEL_H = 200;
    private static final int LEFT_PANEL_H_INFINITE = 117;
    private static final int TEXT_START_X = LEFT_PANEL_X + 8;
    private static final int TEXT_START_Y = LEFT_PANEL_Y + 8;
    private static final int TEXT_LINE_STEP = 13;
    private static final int TEXT_MAX_W = LEFT_PANEL_W - 16;
    private static final int USAGE_PANEL_X = 174;
    private static final int USAGE_PANEL_Y = 24;
    private static final int USAGE_PANEL_W = 166;
    private static final int USAGE_PANEL_H = 200;
    private static final int STORAGE_GAUGE_X = USAGE_PANEL_X + 18;
    private static final int STORAGE_GAUGE_Y = USAGE_PANEL_Y + 23;
    private static final int STORAGE_GAUGE_W = 32;
    private static final int STORAGE_GAUGE_H = 160;
    private static final int USAGE_DETAIL_X = STORAGE_GAUGE_X + STORAGE_GAUGE_W + 10;
    private static final int USAGE_DETAIL_Y = STORAGE_GAUGE_Y + 5;
    private static final int USAGE_DETAIL_W = USAGE_PANEL_X + USAGE_PANEL_W - 10 - USAGE_DETAIL_X;
    private static final int USAGE_DETAIL_LINE_H = 12;
    private static final float USAGE_DETAIL_TEXT_SCALE = 0.66F;
    private static final int USAGE_DARK_X = USAGE_PANEL_X + 8;
    private static final int USAGE_DARK_Y = STORAGE_GAUGE_Y - 4;
    private static final int USAGE_DARK_W = USAGE_PANEL_W - 16;
    private static final int USAGE_DARK_H = STORAGE_GAUGE_H + 8;
    private static final int STORAGE_GAUGE_CAP_H = 8;
    private static final int STORAGE_GAUGE_TOP_U = 1;
    private static final int STORAGE_GAUGE_TOP_V = 246;
    private static final int STORAGE_GAUGE_MID_U = 34;
    private static final int STORAGE_GAUGE_MID_V = 250;
    private static final int STORAGE_GAUGE_MID_H = 4;
    private static final int STORAGE_GAUGE_BOTTOM_U = 1;
    private static final int STORAGE_GAUGE_BOTTOM_V = 246;
    private static final int STORAGE_ELEMENTS_SIZE = 256;
    private static final int PRIORITY_BUTTON_X = UI_WIDTH - 22;
    private static final int PRIORITY_BUTTON_Y = 0;
    private static final int PRIORITY_BUTTON_W = 22;
    private static final int PRIORITY_BUTTON_H = 22;
    static final int SLOT_SIZE = 18;
    private static final int PLAYER_INV_X = LEFT_PANEL_X;
    private static final int PLAYER_INV_Y = LEFT_PANEL_Y + LEFT_PANEL_H_INFINITE + 7;
    private static final int PLAYER_HOTBAR_Y = USAGE_PANEL_Y + USAGE_PANEL_H - SLOT_SIZE;
    private static final int INFINITE_SLOT_X = USAGE_DARK_X + USAGE_DARK_W - 7 - SLOT_SIZE;
    private static final int INFINITE_SLOT_Y = USAGE_DARK_Y + USAGE_DARK_H - 7 - SLOT_SIZE;
    private static final int HUGE_STACK_PANEL_X = USAGE_DETAIL_X - 4;
    private static final int HUGE_STACK_PANEL_Y = USAGE_DETAIL_Y + USAGE_DETAIL_LINE_H * 3 - 5;
    private static final int HUGE_STACK_PANEL_W = USAGE_DETAIL_W;
    private static final int HUGE_STACK_PANEL_H = INFINITE_SLOT_Y - HUGE_STACK_PANEL_Y - 8;
    private static final int HUGE_STACK_ROW_H = 18;
    private static final int HEADER_STATUS_RIGHT = PRIORITY_BUTTON_X - 6;
    private static final double LEFT_SCROLL_SPEED = 13.0D;
    private static final double HUGE_STACK_SCROLL_SPEED = 20.0D;
    private static final long USAGE_ANIMATION_MS = 500L;
    private static final double USAGE_ANIMATION_EPSILON = 0.0001D;

    private static final Map<ScrollKey, ScrollSnapshot> SCROLL_MEMORY = new java.util.HashMap<>();

    private final ECOStorageSystemBlockEntity storage;
    private final Player player;

    private double leftScrollPixels;
    private double hugeStackScrollPixels;
    private double usageAnimationStart;
    private double usageAnimationTarget = -1.0D;
    private long usageAnimationStartMs;

    public NEStorageControllerWidget(ECOStorageSystemBlockEntity storage, Player player) {
        super(
                storage.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                initialStorageState(storage),
                storage::createStorageUiState,
                NELDLibStateCodecs::writeStorage,
                NELDLibStateCodecs::readStorage,
                20);
        this.storage = storage;
        this.player = player;
        restoreScrollState();
    }

    private static NEStorageUiState initialStorageState(ECOStorageSystemBlockEntity storage) {
        NEStorageUiState state = storage.createStorageUiState();
        return state == null ? NEStorageUiState.empty(storage.getBlockPos()) : state;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new NEAe2IconButtonWidget(
                        PRIORITY_BUTTON_X,
                        PRIORITY_BUTTON_Y,
                        PRIORITY_BUTTON_W,
                        PRIORITY_BUTTON_H,
                        NEAe2IconButtonWidget.Ae2Icon.WRENCH,
                        click -> {
                            if (!click.isRemote && player instanceof ServerPlayer serverPlayer && storage.isFormed()) {
                                MenuOpener.open(PriorityMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(storage));
                            }
                        })
                .useAeTabButton());
        if (hasInfiniteLayout()) {
            NEPlayerInventoryWidgets.addPlayerInventorySlots(
                    this, player.getInventory(), PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
            addWidget(new SlotWidget(
                            new NEForgeItemTransfer(
                                    storage.getInfiniteComponentItemHandler(), storage::onInfiniteComponentSlotChanged),
                            0,
                            INFINITE_SLOT_X,
                            INFINITE_SLOT_Y,
                            true,
                            true)
                    .setBackgroundTexture(IGuiTexture.EMPTY));
        }
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        restoreScrollState();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int ox = getPositionX();
        int oy = getPositionY();
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + LEFT_PANEL_X, oy + LEFT_PANEL_Y, LEFT_PANEL_W, leftPanelHeight());
        if (hasInfiniteLayout()) {
            NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                    graphics, this::absX, this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
        }
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + USAGE_PANEL_X, oy + USAGE_PANEL_Y, USAGE_PANEL_W, USAGE_PANEL_H);
        drawUsagePanelBackground(graphics, currentState());
        if (hasInfiniteLayout()) {
            NELDLibAe2StyleRenderer.drawAeSlot(graphics, ox + INFINITE_SLOT_X, oy + INFINITE_SLOT_Y);
        }
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        StorageMetrics metrics = NEStorageMetricsModel.from(currentState());
        drawLocalString(graphics, title, NELDLibUiTitleX(), NELDLibUiTitleY(), TEXT_PRIMARY);
        drawStorageTextLines(graphics, metrics);
        drawLeftScrollbar(graphics, metrics);
        drawUsagePanelText(graphics, currentState(), metrics);
        drawHugeStackPanel(graphics, currentState());
        drawInfiniteSlotOverlay(graphics);
        drawFormedStatus(graphics, currentState());
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(PRIORITY_BUTTON_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_W, PRIORITY_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font(), List.of(GuiText.Priority.text()), mouseX, mouseY);
            return;
        }
        if (renderHugeStackTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (renderLeftPanelTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        renderUsageTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (hasHugeStackPanel(currentState())
                && Widget.isMouseOver(
                        absX(HUGE_STACK_PANEL_X),
                        absY(HUGE_STACK_PANEL_Y),
                        HUGE_STACK_PANEL_W,
                        HUGE_STACK_PANEL_H,
                        mouseX,
                        mouseY)) {
            double maxScroll = maxHugeStackScrollPixels();
            double previous = hugeStackScrollPixels;
            hugeStackScrollPixels =
                    Mth.clamp(hugeStackScrollPixels - wheelDelta * HUGE_STACK_SCROLL_SPEED, 0.0D, maxScroll);
            rememberScrollState();
            return hugeStackScrollPixels != previous || maxScroll > 0.0D;
        }
        if (Widget.isMouseOver(
                absX(LEFT_PANEL_X), absY(LEFT_PANEL_Y), LEFT_PANEL_W, leftPanelHeight(), mouseX, mouseY)) {
            double maxScroll = maxLeftScrollPixels();
            double previous = leftScrollPixels;
            leftScrollPixels = Mth.clamp(leftScrollPixels - wheelDelta * LEFT_SCROLL_SPEED, 0.0D, maxScroll);
            rememberScrollState();
            return leftScrollPixels != previous || maxScroll > 0.0D;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    private void drawStorageTextLines(GuiGraphics g, StorageMetrics metrics) {
        leftScrollPixels = Mth.clamp(leftScrollPixels, 0.0D, maxLeftScrollPixels(metrics));
        List<Metric> activeMetrics = activeTypeMetrics(metrics);
        int x = absX(TEXT_START_X);
        int y = absY(TEXT_START_Y - (int) Math.round(leftScrollPixels));
        g.enableScissor(
                absX(LEFT_PANEL_X + 4),
                absY(LEFT_PANEL_Y + 4),
                absX(LEFT_PANEL_X + LEFT_PANEL_W - 4),
                absY(LEFT_PANEL_Y + leftPanelHeight() - 4));

        drawPlainLine(g, Component.translatable("gui.neoecoae.storage.energy"), x, y, NELDLibStyle.DARK_TEXT_PRIMARY);
        y += TEXT_LINE_STEP;
        drawEnergyUsedTotalLine(g, metrics.energy(), x, y);
        y += TEXT_LINE_STEP;

        if (currentState().infiniteMode()) {
            drawInfiniteDomainBlock(g, x, y);
        } else {
            for (Metric metric : activeMetrics) {
                y = drawStorageTypeBlock(g, metric, x, y);
            }
        }
        g.disableScissor();
    }

    private double maxLeftScrollPixels() {
        return maxLeftScrollPixels(NEStorageMetricsModel.from(currentState()));
    }

    private double maxLeftScrollPixels(StorageMetrics metrics) {
        if (currentState().infiniteMode()) {
            return 0.0D;
        }
        int typeCount = activeTypeMetrics(metrics).size();
        int lineCount = 2 + typeCount * 3;
        int contentHeight = (lineCount - 1) * TEXT_LINE_STEP + font().lineHeight;
        int viewportHeight = leftPanelHeight() - 16;
        return Math.max(0, contentHeight - viewportHeight);
    }

    private void drawLeftScrollbar(GuiGraphics g, StorageMetrics metrics) {
        double maxScroll = maxLeftScrollPixels(metrics);
        if (maxScroll <= 0.0D) {
            leftScrollPixels = 0.0D;
            return;
        }
        leftScrollPixels = Mth.clamp(leftScrollPixels, 0.0D, maxScroll);
        int trackX = absX(LEFT_PANEL_X + LEFT_PANEL_W - 5);
        int trackY = absY(LEFT_PANEL_Y + 5);
        int trackH = leftPanelHeight() - 10;
        int contentH = trackH + (int) Math.ceil(maxScroll);
        int thumbH = Math.max(12, trackH * trackH / contentH);
        int thumbY = trackY + (int) Math.round((trackH - thumbH) * leftScrollPixels / maxScroll);
        g.fill(trackX, trackY, trackX + 2, trackY + trackH, 0xAA17141E);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF8B83A0);
    }

    private static String totalInfiniteAmount(NEStorageUiState state) {
        BigInteger total = BigInteger.ZERO;
        for (NEStorageUiTypeState type : state.typeStates()) {
            total = total.add(parseAmount(type.safeUsedAmount()));
        }
        return total.toString();
    }

    private static BigInteger parseAmount(String value) {
        try {
            return value == null || value.isBlank() || !value.chars().allMatch(Character::isDigit)
                    ? BigInteger.ZERO
                    : new BigInteger(value);
        } catch (RuntimeException ignored) {
            return BigInteger.ZERO;
        }
    }

    private int drawStorageTypeBlock(GuiGraphics g, Metric metric, int x, int y) {
        drawPlainLine(g, metric.label(), x, y, metric.accentColor());
        y += TEXT_LINE_STEP;
        drawTypeUsedTotalLine(g, metric, x, y);
        y += TEXT_LINE_STEP;
        drawByteUsedTotalLine(g, metric.used(), metric.max(), x, y);
        return y + TEXT_LINE_STEP;
    }

    private void drawInfiniteDomainBlock(GuiGraphics g, int x, int y) {
        NEStorageUiState state = currentState();
        drawPlainLine(g, Component.translatable("gui.neoecoae.storage.infinite_domain"), x, y, 0xFF9ED47D);
        y += TEXT_LINE_STEP;
        drawInfiniteUsedLine(
                g,
                NELDLibText.number(state.totalUsedTypes()),
                Component.translatable("gui.neoecoae.common.types").getString(),
                x,
                y);
        y += TEXT_LINE_STEP;
        drawInfiniteUsedLine(
                g,
                NELDLibText.hugeAmount(totalInfiniteAmount(state)),
                Component.translatable("gui.neoecoae.storage.bytes_used").getString(),
                x,
                y);
    }

    private void drawInfiniteUsedLine(GuiGraphics g, String usedText, String suffix, int x, int y) {
        int cursor = NELDLibClientStyle.drawSegment(g, font(), usedText, x, y, NELDLibStyle.DARK_TEXT_USED);
        if (!suffix.isEmpty()) {
            NELDLibClientStyle.drawSegment(g, font(), " " + suffix, x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        }
    }

    private void drawEnergyUsedTotalLine(GuiGraphics g, Metric energy, int x, int y) {
        String prefix =
                Component.translatable("gui.neoecoae.storage.energy_storage").getString() + ": ";
        String usedText = NELDLibText.number(energy.used());
        String maxText = NELDLibText.number(energy.max());
        String suffix = "AE";
        if (usedTotalWidth(prefix, usedText, maxText, suffix) > TEXT_MAX_W) {
            usedText = NELDLibText.compactTaskAmount(energy.used());
            maxText = NELDLibText.compactTaskAmount(energy.max());
        }
        NELDLibValueText.drawUsedTotal(g, font(), prefix, usedText, maxText, energy.used(), energy.max(), suffix, x, y);
    }

    private void drawTypeUsedTotalLine(GuiGraphics g, Metric metric, int x, int y) {
        String usedText = NELDLibText.number(metric.usedTypes());
        String maxText =
                metric.totalTypes() == Long.MAX_VALUE ? infiniteText() : NELDLibText.number(metric.totalTypes());
        String suffix = Component.translatable("gui.neoecoae.common.types").getString();
        if (usedTotalWidth("", usedText, maxText, suffix) > TEXT_MAX_W) {
            usedText = NELDLibText.compactTaskAmount(metric.usedTypes());
            maxText = NELDLibText.compactTaskAmount(metric.totalTypes());
        }
        NELDLibValueText.drawUsedTotal(
                g,
                font(),
                "",
                usedText,
                maxText,
                metric.usedTypes(),
                finiteColorMax(metric.totalTypes()),
                suffix,
                x,
                y);
    }

    private void drawByteUsedTotalLine(GuiGraphics g, long used, long max, int x, int y) {
        String usedText = NELDLibText.storageBytes(used);
        String maxText = max == Long.MAX_VALUE ? infiniteText() : NELDLibText.storageBytes(max);
        String suffix =
                Component.translatable("gui.neoecoae.storage.bytes_used").getString();
        if (usedTotalWidth("", usedText, maxText, suffix) > TEXT_MAX_W) {
            suffix = Component.translatable("gui.neoecoae.storage.used_short").getString();
        }
        if (usedTotalWidth("", usedText, maxText, suffix) > TEXT_MAX_W) {
            usedText = NELDLibText.storageBytesCompact(used);
            maxText = NELDLibText.storageBytesCompact(max);
        }
        NELDLibValueText.drawUsedTotal(g, font(), "", usedText, maxText, used, finiteColorMax(max), suffix, x, y);
    }

    private String infiniteText() {
        return Component.translatable("gui.neoecoae.storage.infinite_value").getString();
    }

    private int usedTotalWidth(String prefix, String usedText, String maxText, String suffix) {
        int width = font().width(prefix + usedText + " / " + maxText);
        return suffix.isEmpty() ? width : width + font().width(" " + suffix);
    }

    private void drawFormedStatus(GuiGraphics g, NEStorageUiState state) {
        Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component value = boolText(state.formed());
        int textW = font().width(label) + font().width(value);
        int textX = absX(HEADER_STATUS_RIGHT - textW);
        int textY = absY(NELDLibUiTitleY());
        g.drawString(font(), label, textX, textY, 0xFF4A4A4A, false);
        int color = state.infiniteMode() ? 0xFFCA6CFF : state.formed() ? 0xFF1F9D55 : 0xFFD13F3F;
        g.drawString(font(), value, textX + font().width(label), textY, color, false);
    }

    private void drawUsagePanelBackground(GuiGraphics g, NEStorageUiState state) {
        double usage = animatedUsagePercent(totalUsagePercent(state));
        NELDLibClientStyle.drawTinyInsetRect(
                g, absX(USAGE_DARK_X), absY(USAGE_DARK_Y), USAGE_DARK_W, USAGE_DARK_H, 0xFF201E27);
        int x = absX(STORAGE_GAUGE_X);
        int y = absY(STORAGE_GAUGE_Y);
        if (state.infiniteMode()) {
            drawInfiniteStorageGauge(g, x, y, state, NEStorageMetricsModel.from(state));
        } else {
            drawStorageGauge(g, x, y, usage, storageGaugeColor(usage));
        }
    }

    private void drawUsagePanelText(GuiGraphics g, NEStorageUiState state, StorageMetrics metrics) {
        double usage = animatedUsagePercent(totalUsagePercent(state));
        NELDLibClientStyle.drawCentered(
                g,
                font(),
                Component.translatable("gui.neoecoae.storage.system_load"),
                absX(USAGE_PANEL_X),
                absY(USAGE_PANEL_Y + 8),
                USAGE_PANEL_W,
                NELDLibStyle.DARK_TEXT_PRIMARY);

        int y = USAGE_DETAIL_Y;
        drawUsageDetailLine(
                g,
                Component.translatable("gui.neoecoae.storage.current_load")
                        .append(": ")
                        .append(Component.literal(
                                state.infiniteMode()
                                        ? "N/A"
                                        : NELDLibText.percentOrNA(state.totalUsedBytes(), state.totalBytes()))),
                y,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        y += USAGE_DETAIL_LINE_H;
        drawUsageDetailLine(
                g,
                Component.translatable("gui.neoecoae.storage.max_load")
                        .append(": ")
                        .append(Component.literal(
                                state.infiniteMode()
                                        ? infiniteText()
                                        : NELDLibText.percentOrNA(maxMatrixUsed(state), maxMatrixTotal(state)))),
                y,
                NELDLibStyle.DARK_TEXT_WARNING);
        y += USAGE_DETAIL_LINE_H;
        Metric highestType = state.infiniteMode() ? null : highestPressureMetric(metrics);
        drawUsageDetailLine(
                g,
                Component.translatable("gui.neoecoae.storage.status")
                        .append(": ")
                        .append(storageStatus(highestType)),
                y,
                statusColor(highestType));
        y += USAGE_DETAIL_LINE_H;
        if (!state.infiniteMode()) {
            drawUsageDetailLine(
                    g,
                    Component.translatable("gui.neoecoae.storage.idle_matrices")
                            .append(": ")
                            .append(Component.literal(NELDLibText.number(idleMatrixCount(state)))),
                    y,
                    NELDLibStyle.DARK_TEXT_MUTED);
        }

        NELDLibClientStyle.drawCenteredScaledString(
                g,
                font(),
                state.infiniteMode() ? infiniteText() : state.totalBytes() <= 0L ? "N/A" : NELDLibText.percent(usage),
                absX(STORAGE_GAUGE_X),
                absY(USAGE_PANEL_Y + USAGE_PANEL_H - 12),
                STORAGE_GAUGE_W,
                8,
                state.infiniteMode()
                        ? 0xFFCA6CFF
                        : state.totalBytes() <= 0L
                                ? NELDLibStyle.DARK_TEXT_MUTED
                                : NELDLibStyle.usedValueColor(
                                        Math.round(usage * state.totalBytes()), state.totalBytes()),
                0.9F);
    }

    private void drawHugeStackPanel(GuiGraphics g, NEStorageUiState state) {
        if (!hasHugeStackPanel(state)) {
            return;
        }
        List<NEStorageHugeStackState> hugeStacks = state.hugeStacks();
        hugeStackScrollPixels = Mth.clamp(hugeStackScrollPixels, 0.0D, maxHugeStackScrollPixels(hugeStacks));
        int x = absX(HUGE_STACK_PANEL_X + 4);
        int y = absY(HUGE_STACK_PANEL_Y + 4 - (int) Math.round(hugeStackScrollPixels));
        int clipX = absX(HUGE_STACK_PANEL_X + 2);
        int clipY = absY(HUGE_STACK_PANEL_Y + 2);
        int clipW = HUGE_STACK_PANEL_W - 4;
        int clipH = HUGE_STACK_PANEL_H - 4;
        g.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);
        for (int i = 0; i < hugeStacks.size(); i++) {
            drawHugeStackRow(g, hugeStacks.get(i), x, y + i * HUGE_STACK_ROW_H);
        }
        g.disableScissor();
        drawHugeStackScrollbar(g, hugeStacks);
    }

    private void drawHugeStackRow(GuiGraphics g, NEStorageHugeStackState entry, int x, int y) {
        if (entry.key() instanceof AEFluidKey fluidKey) {
            FluidStack stack = fluidKey.toStack(1);
            NELDLibAe2StyleRenderer.drawFluidIcon(g, x, y + 1, 16, stack);
        } else {
            ItemStack displayStack = entry.key().wrapForDisplayOrFilter();
            if (displayStack.isEmpty()) {
                displayStack = GenericStack.wrapInItemStack(entry.key(), 1L);
            }
            g.renderItem(displayStack, x, y + 1);
        }
        int textX = x + 20;
        int textY = y + 4;
        g.pose().pushPose();
        g.pose().translate(textX, textY, 0.0F);
        g.pose().scale(USAGE_DETAIL_TEXT_SCALE, USAGE_DETAIL_TEXT_SCALE, 1.0F);
        g.drawString(font(), NELDLibText.hugeAmount(entry.amount()), 0, 0, NELDLibStyle.DARK_TEXT_SUCCESS, false);
        g.pose().popPose();
    }

    private void drawHugeStackScrollbar(GuiGraphics g, List<NEStorageHugeStackState> hugeStacks) {
        double maxScroll = maxHugeStackScrollPixels(hugeStacks);
        if (maxScroll <= 0.0D) {
            hugeStackScrollPixels = 0.0D;
            return;
        }
        int trackX = absX(HUGE_STACK_PANEL_X + HUGE_STACK_PANEL_W - 4);
        int trackY = absY(HUGE_STACK_PANEL_Y + 4);
        int trackH = HUGE_STACK_PANEL_H - 8;
        int contentH = hugeStacks.size() * HUGE_STACK_ROW_H;
        int thumbH = Math.max(10, trackH * trackH / Math.max(trackH, contentH));
        int thumbY = trackY + (int) Math.round((trackH - thumbH) * hugeStackScrollPixels / maxScroll);
        g.fill(trackX, trackY, trackX + 2, trackY + trackH, 0xAA071D34);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF73D7FF);
    }

    private boolean renderHugeStackTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!hasHugeStackPanel(currentState())
                || !isMouseIn(
                        HUGE_STACK_PANEL_X,
                        HUGE_STACK_PANEL_Y,
                        HUGE_STACK_PANEL_W,
                        HUGE_STACK_PANEL_H,
                        mouseX,
                        mouseY)) {
            return false;
        }
        NEStorageHugeStackState entry = hugeStackAt(mouseX, mouseY);
        if (entry == null) {
            return false;
        }
        graphics.renderComponentTooltip(
                font(),
                List.of(
                        entry.key().getDisplayName().copy().withStyle(ChatFormatting.AQUA),
                        Component.literal(NELDLibText.hugeAmount(entry.amount()))
                                .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_SUCCESS))),
                mouseX,
                mouseY);
        return true;
    }

    @Nullable private NEStorageHugeStackState hugeStackAt(int mouseX, int mouseY) {
        if (!isMouseIn(
                HUGE_STACK_PANEL_X, HUGE_STACK_PANEL_Y, HUGE_STACK_PANEL_W, HUGE_STACK_PANEL_H, mouseX, mouseY)) {
            return null;
        }
        int localY = mouseY - absY(HUGE_STACK_PANEL_Y + 4) + (int) Math.round(hugeStackScrollPixels);
        int index = localY / HUGE_STACK_ROW_H;
        List<NEStorageHugeStackState> hugeStacks = currentState().hugeStacks();
        return index >= 0 && index < hugeStacks.size() ? hugeStacks.get(index) : null;
    }

    private double maxHugeStackScrollPixels() {
        return maxHugeStackScrollPixels(currentState().hugeStacks());
    }

    private double maxHugeStackScrollPixels(List<NEStorageHugeStackState> hugeStacks) {
        int contentHeight = hugeStacks.size() * HUGE_STACK_ROW_H;
        int viewportHeight = HUGE_STACK_PANEL_H - 8;
        return Math.max(0, contentHeight - viewportHeight);
    }

    private static boolean hasHugeStackPanel(NEStorageUiState state) {
        return state.infiniteMode() && !state.hugeStacks().isEmpty();
    }

    private void drawUsageDetailLine(GuiGraphics g, Component text, int localY, int color) {
        int x = absX(USAGE_DETAIL_X);
        int y = absY(localY);
        int maxW = Math.max(1, USAGE_DETAIL_W - 2);
        g.enableScissor(x, y - 1, x + maxW, y + USAGE_DETAIL_LINE_H);
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(USAGE_DETAIL_TEXT_SCALE, USAGE_DETAIL_TEXT_SCALE, 1.0F);
        g.drawString(font(), text, 0, 0, color, false);
        g.pose().popPose();
        g.disableScissor();
    }

    private List<Metric> activeTypeMetrics(StorageMetrics metrics) {
        List<Metric> active = new java.util.ArrayList<>();
        for (Metric metric : metrics.types()) {
            if (metric.max() > 0 || metric.totalTypes() > 0) {
                active.add(metric);
            }
        }
        return active;
    }

    private Metric highestPressureMetric(StorageMetrics metrics) {
        Metric highest = null;
        double highestPercent = -1.0D;
        for (Metric metric : activeTypeMetrics(metrics)) {
            double percent = metric.percent();
            if (percent > highestPercent) {
                highestPercent = percent;
                highest = metric;
            }
        }
        return highest;
    }

    private Component storageStatus(Metric highestType) {
        if (highestType == null) {
            return Component.translatable("gui.neoecoae.storage.status.ok");
        }
        if (highestType.percent() >= 0.999D) {
            return Component.translatable("gui.neoecoae.storage.status.capacity_full", highestType.label());
        }
        return Component.translatable("gui.neoecoae.storage.status.ok");
    }

    private int statusColor(Metric highestType) {
        if (highestType != null && highestType.percent() >= 0.999D) {
            return NELDLibStyle.DARK_TEXT_WARNING;
        }
        return NELDLibStyle.DARK_TEXT_MUTED;
    }

    private boolean renderLeftPanelTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        NEStorageUiState state = currentState();
        if (!state.infiniteMode()
                || !isMouseIn(LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_W, leftPanelHeight(), mouseX, mouseY)) {
            return false;
        }
        graphics.renderComponentTooltip(
                font(),
                List.of(
                        Component.translatable("gui.neoecoae.storage.infinite_domain")
                                .withStyle(ChatFormatting.AQUA),
                        Component.literal(Component.translatable("gui.neoecoae.common.types")
                                                .getString()
                                        + ": "
                                        + NELDLibText.number(state.totalUsedTypes()))
                                .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_USED))),
                mouseX,
                mouseY);
        return true;
    }

    private void renderUsageTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseIn(USAGE_DARK_X, USAGE_DARK_Y, USAGE_DARK_W, USAGE_DARK_H, mouseX, mouseY)) {
            return;
        }
        NEStorageUiState state = currentState();
        if (hasHugeStackPanel(state)
                && isMouseIn(
                        HUGE_STACK_PANEL_X,
                        HUGE_STACK_PANEL_Y,
                        HUGE_STACK_PANEL_W,
                        HUGE_STACK_PANEL_H,
                        mouseX,
                        mouseY)) {
            return;
        }
        if (state.infiniteMode()) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.storage.system_load")
                                    .withStyle(ChatFormatting.AQUA),
                            Component.literal(NELDLibText.hugeAmount(totalInfiniteAmount(state)))
                                    .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_USED))
                                    .append(Component.literal(" "
                                                    + Component.translatable("gui.neoecoae.storage.bytes_used")
                                                            .getString())
                                            .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED))),
                            Component.literal(Component.translatable("gui.neoecoae.common.types")
                                                    .getString()
                                            + ": "
                                            + NELDLibText.number(state.totalUsedTypes()))
                                    .withStyle(ChatFormatting.GRAY)),
                    mouseX,
                    mouseY);
            return;
        }
        graphics.renderComponentTooltip(
                font(),
                List.of(
                        Component.translatable("gui.neoecoae.storage.system_load")
                                .withStyle(ChatFormatting.AQUA),
                        NELDLibValueText.usedTotalComponent(
                                "",
                                NELDLibText.storageBytes(state.totalUsedBytes()),
                                state.infiniteMode() || state.totalBytes() == Long.MAX_VALUE
                                        ? infiniteText()
                                        : NELDLibText.storageBytes(state.totalBytes()),
                                state.totalUsedBytes(),
                                finiteColorMax(state.totalBytes()),
                                Component.translatable("gui.neoecoae.storage.bytes_used")
                                        .getString()),
                        Component.translatable(
                                "gui.neoecoae.machine.types_value",
                                NELDLibText.number(state.totalUsedTypes()),
                                state.totalTypes() == Long.MAX_VALUE
                                        ? infiniteText()
                                        : NELDLibText.number(state.totalTypes()))),
                mouseX,
                mouseY);
    }

    private void drawInfiniteSlotOverlay(GuiGraphics g) {
        NEStorageUiState state = currentState();
        if (!hasInfiniteLayout() || state.canTakeInfiniteComponent()) {
            return;
        }
        int x = absX(INFINITE_SLOT_X);
        int y = absY(INFINITE_SLOT_Y);
        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x88404040);
    }

    private boolean hasInfiniteLayout() {
        return currentState().infiniteSlotVisible();
    }

    private int leftPanelHeight() {
        return hasInfiniteLayout() ? LEFT_PANEL_H_INFINITE : LEFT_PANEL_H;
    }

    private static long finiteColorMax(long max) {
        return max == Long.MAX_VALUE ? 0L : max;
    }

    private void drawStorageGauge(GuiGraphics g, int x, int y, double pct, int color) {
        double clamped = Mth.clamp(pct, 0.0D, 1.0D);
        if (clamped <= 0.0D) {
            return;
        }
        int bodyHeight = STORAGE_GAUGE_H - STORAGE_GAUGE_CAP_H;
        int barHeight = (int) Math.round(bodyHeight * clamped);
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(red, green, blue, alpha);
        g.blit(
                STORAGE_ELEMENTS,
                x,
                y + STORAGE_GAUGE_H - barHeight - STORAGE_GAUGE_CAP_H,
                STORAGE_GAUGE_W,
                STORAGE_GAUGE_CAP_H,
                STORAGE_GAUGE_TOP_U,
                STORAGE_GAUGE_TOP_V,
                STORAGE_GAUGE_W,
                STORAGE_GAUGE_CAP_H,
                STORAGE_ELEMENTS_SIZE,
                STORAGE_ELEMENTS_SIZE);
        int midStart = y + STORAGE_GAUGE_H - barHeight - STORAGE_GAUGE_CAP_H / 2 + 1;
        int midEnd = y + STORAGE_GAUGE_H - STORAGE_GAUGE_CAP_H + STORAGE_GAUGE_CAP_H / 2 + 1;
        for (int drawY = midStart; drawY < midEnd; drawY++) {
            g.blit(
                    STORAGE_ELEMENTS,
                    x,
                    drawY,
                    STORAGE_GAUGE_W,
                    STORAGE_GAUGE_MID_H,
                    STORAGE_GAUGE_MID_U,
                    STORAGE_GAUGE_MID_V,
                    STORAGE_GAUGE_W,
                    STORAGE_GAUGE_MID_H,
                    STORAGE_ELEMENTS_SIZE,
                    STORAGE_ELEMENTS_SIZE);
        }
        g.blit(
                STORAGE_ELEMENTS,
                x,
                y + STORAGE_GAUGE_H - STORAGE_GAUGE_CAP_H,
                STORAGE_GAUGE_W,
                STORAGE_GAUGE_CAP_H,
                STORAGE_GAUGE_BOTTOM_U,
                STORAGE_GAUGE_BOTTOM_V,
                STORAGE_GAUGE_W,
                STORAGE_GAUGE_CAP_H,
                STORAGE_ELEMENTS_SIZE,
                STORAGE_ELEMENTS_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawInfiniteStorageGauge(GuiGraphics g, int x, int y, NEStorageUiState state, StorageMetrics metrics) {
        List<Metric> segments = new java.util.ArrayList<>();
        BigInteger total = BigInteger.ZERO;
        for (Metric metric : metrics.types()) {
            BigInteger amount = parseAmount(metric.usedAmount());
            if (amount.signum() <= 0) {
                continue;
            }
            segments.add(metric);
            total = total.add(amount);
        }
        if (segments.isEmpty() || total.signum() <= 0 || state.infiniteDomainEmpty()) {
            drawInfiniteStandbyGauge(g, x, y);
            return;
        }

        int bottom = y + STORAGE_GAUGE_H;
        double consumed = 0.0D;
        for (int i = 0; i < segments.size(); i++) {
            Metric segment = segments.get(i);
            int top;
            if (i == segments.size() - 1) {
                top = y;
            } else {
                consumed += amountRatio(parseAmount(segment.usedAmount()), total) * STORAGE_GAUGE_H;
                top = y + STORAGE_GAUGE_H - (int) Math.round(consumed);
            }
            if (bottom > top) {
                g.enableScissor(x, top, x + STORAGE_GAUGE_W, bottom);
                drawStorageGauge(g, x, y, 1.0D, infiniteGaugeColor(segment.accentColor()));
                g.disableScissor();
            }
            bottom = top;
        }
    }

    private void drawInfiniteStandbyGauge(GuiGraphics g, int x, int y) {
        drawStorageGauge(g, x, y, 1.0D, 0x22CA6CFF);
    }

    private static double amountRatio(BigInteger amount, BigInteger total) {
        if (amount.signum() <= 0 || total.signum() <= 0) {
            return 0.0D;
        }
        return new BigDecimal(amount)
                .divide(new BigDecimal(total), 8, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static int infiniteGaugeColor(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        int max = Math.max(red, Math.max(green, blue));
        if (max > 0) {
            red = Math.min(255, red * 255 / max);
            green = Math.min(255, green * 255 / max);
            blue = Math.min(255, blue * 255 / max);
        }
        int saturated = 0xD8000000 | (red << 16) | (green << 8) | blue;
        return NELDLibStyle.lerpColor(saturated, 0xD8FFFFFF, 0.08D);
    }

    private static double totalUsagePercent(NEStorageUiState state) {
        return NELDLibMachineWidget.percent(state.totalUsedBytes(), state.totalBytes());
    }

    private static int gaugeColor(NEStorageUiState state) {
        return storageGaugeColor(totalUsagePercent(state));
    }

    private static int storageGaugeColor(double pct) {
        double amount = Mth.clamp(pct, 0.0D, 1.0D);
        if (amount < 0.5D) {
            return NELDLibStyle.lerpColor(0xBF00FF00, 0xBFFFFF00, amount / 0.5D);
        }
        return NELDLibStyle.lerpColor(0xBFFFFF00, 0xBFFF0000, (amount - 0.5D) / 0.5D);
    }

    private double animatedUsagePercent(double target) {
        long now = Util.getMillis();
        if (usageAnimationTarget < 0.0D) {
            usageAnimationStart = 0.0D;
            usageAnimationTarget = target;
            usageAnimationStartMs = now;
        } else if (Math.abs(usageAnimationTarget - target) > USAGE_ANIMATION_EPSILON) {
            usageAnimationStart = currentAnimatedUsagePercent(now);
            usageAnimationTarget = target;
            usageAnimationStartMs = now;
        }
        return currentAnimatedUsagePercent(now);
    }

    private double currentAnimatedUsagePercent(long now) {
        double elapsed = Mth.clamp((double) (now - usageAnimationStartMs) / (double) USAGE_ANIMATION_MS, 0.0D, 1.0D);
        double eased = cubicBezierEase(elapsed);
        return usageAnimationStart + (usageAnimationTarget - usageAnimationStart) * eased;
    }

    private static double cubicBezierEase(double progress) {
        double t = Mth.clamp(progress, 0.0D, 1.0D);
        for (int i = 0; i < 5; i++) {
            double x = cubicBezier(t, 0.25D, 0.25D);
            double slope = cubicBezierSlope(t, 0.25D, 0.25D);
            if (slope == 0.0D) {
                break;
            }
            t = Mth.clamp(t - (x - progress) / slope, 0.0D, 1.0D);
        }
        return cubicBezier(t, 0.1D, 1.0D);
    }

    private static double cubicBezier(double t, double p1, double p2) {
        double inverse = 1.0D - t;
        return 3.0D * inverse * inverse * t * p1 + 3.0D * inverse * t * t * p2 + t * t * t;
    }

    private static double cubicBezierSlope(double t, double p1, double p2) {
        double inverse = 1.0D - t;
        return 3.0D * inverse * inverse * p1 + 6.0D * inverse * t * (p2 - p1) + 3.0D * t * t * (1.0D - p2);
    }

    private static long maxMatrixUsed(NEStorageUiState state) {
        long used = 0L;
        long total = 0L;
        double maxPct = -1.0D;
        for (var matrix : state.matrixStates()) {
            if (!matrix.hasMatrix() || matrix.totalBytes() <= 0L) {
                continue;
            }
            double pct = NELDLibMachineWidget.percent(matrix.usedBytes(), matrix.totalBytes());
            if (pct > maxPct) {
                maxPct = pct;
                used = matrix.usedBytes();
                total = matrix.totalBytes();
            }
        }
        return total <= 0L ? 0L : used;
    }

    private static long maxMatrixTotal(NEStorageUiState state) {
        long total = 0L;
        double maxPct = -1.0D;
        for (var matrix : state.matrixStates()) {
            if (!matrix.hasMatrix() || matrix.totalBytes() <= 0L) {
                continue;
            }
            double pct = NELDLibMachineWidget.percent(matrix.usedBytes(), matrix.totalBytes());
            if (pct > maxPct) {
                maxPct = pct;
                total = matrix.totalBytes();
            }
        }
        return total;
    }

    private static int idleMatrixCount(NEStorageUiState state) {
        int count = 0;
        for (var matrix : state.matrixStates()) {
            if (matrix.hasMatrix() && matrix.usedBytes() <= 0L && matrix.usedTypes() <= 0L) {
                count++;
            }
        }
        return count;
    }

    private void drawPlainLine(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    private int NELDLibUiTitleX() {
        return 8;
    }

    private int NELDLibUiTitleY() {
        return 8;
    }

    private void restoreScrollState() {
        scrollKey().map(SCROLL_MEMORY::get).ifPresent(snapshot -> {
            leftScrollPixels = snapshot.leftScrollPixels();
            hugeStackScrollPixels = snapshot.hugeStackScrollPixels();
        });
    }

    private void rememberScrollState() {
        scrollKey()
                .ifPresent(key -> SCROLL_MEMORY.put(key, new ScrollSnapshot(leftScrollPixels, hugeStackScrollPixels)));
    }

    private Optional<ScrollKey> scrollKey() {
        if (storage.getLevel() == null) {
            return Optional.empty();
        }
        return Optional.of(new ScrollKey(
                player.getUUID(),
                storage.getLevel().dimension().location(),
                storage.getBlockPos().immutable()));
    }

    private record ScrollKey(UUID playerId, ResourceLocation dimension, BlockPos pos) {}

    private record ScrollSnapshot(double leftScrollPixels, double hugeStackScrollPixels) {}
}
