package cn.dancingsnow.neoecoae.gui.storage;

import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostSideButtonRenderer;
import cn.dancingsnow.neoecoae.client.gui.ldlib.storage.NEStorageGaugeRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiMatrixState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.sync.NEStorageUiStateCodec;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEAe2IconButtonWidget;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NELDLibSyncedStateWidget;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * LDLib1 implementation of the 1.21 storage host surface. Server storage remains authoritative.
 */
public final class StorageHostUI extends NELDLibSyncedStateWidget<NEStorageUiState> {
    public static final int WIDTH = 272;
    public static final int HEIGHT = 216;
    private static final int ACTION = FIRST_CUSTOM_UPDATE_ID + 1;
    private static final int CONFIG = FIRST_CUSTOM_UPDATE_ID + 2;
    private static final ResourceLocation BACKGROUND =
            cn.dancingsnow.neoecoae.NeoECOAE.id("textures/gui/storage/estorage_infinite_controller.png");
    private static final ResourceLocation ELEMENTS =
            cn.dancingsnow.neoecoae.NeoECOAE.id("textures/gui/storage/estorage_controller_elements.png");
    private static final int ELEMENTS_SIZE = 256;
    private static final int CELL_LIST_LEFT = 178;
    private static final int CELL_LIST_TOP = 22;
    private static final int CELL_LIST_WIDTH = 83;
    private static final int CELL_LIST_HEIGHT = 172;
    private static final int TYPE_LIST_PADDING = 2;
    private static final int TYPE_BLOCK_STRIDE = 48;
    private static final int INFINITE_TYPE_BLOCK_STRIDE = 36;
    private static final ResourceLocation MEGA_BACKGROUND =
            cn.dancingsnow.neoecoae.NeoECOAE.id("textures/gui/storage/eco_mega_storage.png");
    private static final int MEGA_PANEL_WIDTH = 103;
    private static final int MEGA_PANEL_HEIGHT = 130;
    private static final int MEGA_PANEL_LEFT = -MEGA_PANEL_WIDTH + 3;
    private static final int MEGA_PANEL_TOP = HEIGHT - MEGA_PANEL_HEIGHT - 2;
    private static final int MEGA_SLOT_SIZE = 18;
    private static final int MEGA_GRID_LEFT = MEGA_PANEL_LEFT + 7;
    private static final int MEGA_GRID_TOP = MEGA_PANEL_TOP + 31;
    private static final int MEGA_CONTROLS_LEFT = MEGA_PANEL_LEFT + 31;
    private static final int MEGA_CELL_CONTROLS_TOP = MEGA_PANEL_TOP + 3;
    private static final int MEGA_PAGE_CONTROLS_TOP = MEGA_PANEL_TOP + 16;
    private static final int MEGA_ACTION_LEFT = MEGA_PANEL_LEFT + 82;
    private static final int MEGA_ACTION_TOP = MEGA_PANEL_TOP + 4;
    private static final int MEGA_TEXT_COLOR = 0xFF3F3D52;
    private final ECOStorageSystemBlockEntity storage;
    private final Player player;
    private int scroll;
    private int detailScroll;
    private int panel;
    private int length = 1;
    private boolean mirrored;
    private double animatedRatio;
    private SlotWidget infiniteSlot;
    private SlotWidget megaUpgradeSlot;
    private NEAe2IconButtonWidget megaPreviousCellButton;
    private NEAe2IconButtonWidget megaNextCellButton;
    private NEAe2IconButtonWidget megaPreviousPageButton;
    private NEAe2IconButtonWidget megaNextPageButton;
    private NEAe2IconButtonWidget megaBulkMarkingButton;
    private int megaCellCount;
    private int megaCellIndex;
    private int megaPage;
    private boolean megaUpgraded;
    private long megaFingerprint = Long.MIN_VALUE;
    private final List<ItemStack> megaFilters = new ArrayList<>();
    private final PageSession pageSession;

    private static final class PageSession {
        int page;
    }

    public StorageHostUI(ECOStorageSystemBlockEntity storage, Player player) {
        this(storage, player, new PageSession());
    }

    private StorageHostUI(ECOStorageSystemBlockEntity storage, Player player, PageSession pages) {
        super(
                storage.getBlockState().getBlock().getName(),
                WIDTH,
                HEIGHT,
                NEStorageUiState.empty(storage.getBlockPos()),
                () -> storage.createStorageUiState(pages.page),
                NEStorageUiStateCodec::write,
                NEStorageUiStateCodec::read,
                20);
        this.storage = storage;
        this.player = player;
        this.pageSession = pages;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected boolean shouldDrawBasePanel() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new NEAe2IconButtonWidget(-17, 25, 16, 16, NEAe2IconButtonWidget.Ae2Icon.WRENCH, click -> {
                    if (!click.isRemote && player instanceof ServerPlayer serverPlayer) {
                        MenuOpener.open(PriorityMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(storage));
                    }
                })
                .useEcoButton());
        addWidget(new NEAe2IconButtonWidget(-17, 3, 16, 16, Icon.HELP, click -> {
                    if (!click.isRemote && net.minecraftforge.fml.ModList.get().isLoaded("guideme")) {
                        guideme.GuidesCommon.openGuide(
                                player,
                                appeng.core.AppEng.makeId("guide"),
                                guideme.PageAnchor.parse("neoecoae:neoecoae_intro/storage_system.md"));
                    }
                })
                .useEcoButton());
        megaPreviousCellButton = megaArrowButton(MEGA_CONTROLS_LEFT, MEGA_CELL_CONTROLS_TOP, -1);
        megaNextCellButton = megaArrowButton(MEGA_CONTROLS_LEFT + 30, MEGA_CELL_CONTROLS_TOP, 1);
        megaPreviousPageButton = megaArrowButton(MEGA_CONTROLS_LEFT, MEGA_PAGE_CONTROLS_TOP, -2);
        megaNextPageButton = megaArrowButton(MEGA_CONTROLS_LEFT + 30, MEGA_PAGE_CONTROLS_TOP, 2);
        megaBulkMarkingButton = new NEAe2IconButtonWidget(
                        MEGA_ACTION_LEFT,
                        MEGA_ACTION_TOP,
                        16,
                        16,
                        NEAe2IconButtonWidget.Ae2Icon.TYPE_FILTER_ALL,
                        click -> {
                            if (click.isRemote) send(10, 0);
                        })
                .useEcoButton();
        addWidget(megaPreviousCellButton);
        addWidget(megaNextCellButton);
        addWidget(megaPreviousPageButton);
        addWidget(megaNextPageButton);
        addWidget(megaBulkMarkingButton);
        NEPlayerInventoryWidgets.addPlayerInventorySlots(this, player.getInventory(), 7, 129, 187);
        infiniteSlot = new SlotWidget(
                new NEForgeItemTransfer(
                        storage.getInfiniteComponentItemHandler(), storage::onInfiniteComponentSlotChanged),
                0,
                146,
                100,
                true,
                true) {
            @Override
            public boolean canTakeStack(Player actor) {
                return super.canTakeStack(actor)
                        && (actor.level().isClientSide
                                ? currentState().canTakeInfiniteComponent()
                                : storage.canTakeInfiniteStorageComponent());
            }
        }.setBackgroundTexture(IGuiTexture.EMPTY);
        addWidget(infiniteSlot);
        megaUpgradeSlot = new SlotWidget(
                        new NEForgeItemTransfer(
                                storage.getEcoMegaUpgradeItemHandler(), storage::notifyStorageConfigurationChanged),
                        0,
                        MEGA_PANEL_LEFT + 7,
                        MEGA_PANEL_TOP + 6,
                        true,
                        true)
                .setBackgroundTexture(IGuiTexture.EMPTY);
        megaUpgradeSlot.setVisible(false);
        megaUpgradeSlot.setActive(false);
        addWidget(megaUpgradeSlot);
    }

    private NEAe2IconButtonWidget megaArrowButton(int x, int y, int direction) {
        Icon icon = direction < 0 ? Icon.ARROW_LEFT : Icon.ARROW_RIGHT;
        return new NEAe2IconButtonWidget(x, y, 10, 10, icon, click -> {
                    if (!click.isRemote) return;
                    if (Math.abs(direction) == 1) send(7, direction);
                    else send(8, direction);
                })
                .useEcoButton();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        long currentMegaFingerprint = storage.getEcoMegaConfigurationFingerprint();
        if (length != storage.getSelectedBuildLength() || megaFingerprint != currentMegaFingerprint) {
            length = storage.getSelectedBuildLength();
            megaFingerprint = currentMegaFingerprint;
            writeUpdateInfo(CONFIG, this::writeConfig);
        }
    }

    private void writeConfig(FriendlyByteBuf buf) {
        buf.writeVarInt(storage.getSelectedBuildLength());
        buf.writeVarInt(storage.getEcoMegaBulkCellCount());
        buf.writeVarInt(storage.getSelectedEcoMegaBulkCell());
        buf.writeVarInt(storage.getSelectedEcoMegaPage());
        buf.writeBoolean(storage.hasEcoMegaUpgradeCard());
        for (int slot = 0; slot < 25; slot++) buf.writeItem(storage.getEcoMegaFilterStack(slot));
    }

    private void readConfig(FriendlyByteBuf buf) {
        length = buf.readVarInt();
        megaCellCount = buf.readVarInt();
        megaCellIndex = buf.readVarInt();
        megaPage = buf.readVarInt();
        megaUpgraded = buf.readBoolean();
        megaFilters.clear();
        for (int slot = 0; slot < 25; slot++) megaFilters.add(buf.readItem());
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buf) {
        super.writeInitialData(buf);
        writeConfig(buf);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buf) {
        super.readInitialData(buf);
        readConfig(buf);
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buf) {
        if (id == CONFIG) readConfig(buf);
        else super.readUpdateInfo(id, buf);
    }

    private void send(int action, int value) {
        writeClientAction(ACTION, buf -> {
            buf.writeVarInt(action);
            buf.writeInt(value);
            buf.writeBoolean(mirrored);
        });
    }

    private void sendFilter(int slot, ItemStack stack) {
        writeClientAction(ACTION, buf -> {
            buf.writeVarInt(9);
            buf.writeInt(slot);
            buf.writeBoolean(false);
            buf.writeItem(stack == null ? ItemStack.EMPTY : stack.copy());
        });
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buf) {
        if (id != ACTION) {
            super.handleClientAction(id, buf);
            return;
        }
        int action = buf.readVarInt();
        int value = buf.readInt();
        boolean mirror = buf.readBoolean();
        ItemStack filter = action == 9 ? buf.readItem() : ItemStack.EMPTY;
        if (!(player instanceof ServerPlayer serverPlayer)
                || storage.isRemoved()
                || player.distanceToSqr(storage.getBlockPos().getCenter()) > 64) return;
        switch (action) {
            case 6 -> {
                if (value >= 0 && value < currentState().hugeStackPageCount()) pageSession.page = value;
            }
            case 7 -> storage.changeSelectedEcoMegaBulkCell(value < 0 ? -1 : 1);
            case 8 -> storage.changeSelectedEcoMegaPage(value < 0 ? -1 : 1);
            case 9 -> storage.setEcoMegaFilter(value, filter);
            case 10 -> {
                var result = storage.autoMarkEcoMegaBulkCells();
                String key =
                        switch (result.status()) {
                            case SUCCESS -> "gui.neoecoae.storage.bulk_mark.result.success";
                            case NO_BULK_CELL -> "gui.neoecoae.storage.bulk_mark.result.no_bulk_cell";
                            case BUSY -> "gui.neoecoae.storage.bulk_mark.result.busy";
                            case INVALID_THRESHOLD -> "gui.neoecoae.storage.bulk_mark.result.invalid_threshold";
                            case UNAVAILABLE -> "gui.neoecoae.storage.bulk_mark.result.unavailable";
                        };
                Component message = result.status()
                                == cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration.Status.SUCCESS
                        ? Component.translatable(
                                key, result.added(), result.alreadyMarked(), result.noSpace(), result.transferred())
                        : Component.translatable(key);
                serverPlayer.displayClientMessage(message, true);
            }
            default -> {
                return;
            }
        }
        syncStateNow();
        writeUpdateInfo(CONFIG, this::writeConfig);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics g, int mx, int my, float partial) {
        g.blit(BACKGROUND, absX(0), absY(0), 0, 0, WIDTH, HEIGHT, 288, 256);
        NEHostSideButtonRenderer.drawLeft(g, absX(0), absY(0), 2, mx, my);
        double target = percent(currentState().totalUsedBytes(), currentState().totalBytes());
        animatedRatio += (target - animatedRatio) * 0.15;
        drawGraphLines(g, mx, my);
        if (currentState().infiniteMode() || currentState().migratingToInfinite()) {
            drawGauge(g, 72, 22, 1.0D, 0xD8CA6CFF);
        } else {
            drawGauge(g, 72, 22, animatedRatio, NEStorageGaugeRenderer.colorForPercent(animatedRatio));
        }
        infiniteSlot.setVisible(panel == 0);
        infiniteSlot.setActive(panel == 0);
        boolean showMega = megaCellCount > 0;
        megaUpgradeSlot.setVisible(showMega);
        megaUpgradeSlot.setActive(showMega);
        megaPreviousCellButton.setVisible(showMega);
        megaPreviousCellButton.setActive(showMega);
        megaNextCellButton.setVisible(showMega);
        megaNextCellButton.setActive(showMega);
        megaPreviousPageButton.setVisible(showMega && megaUpgraded);
        megaPreviousPageButton.setActive(showMega && megaUpgraded);
        megaNextPageButton.setVisible(showMega && megaUpgraded);
        megaNextPageButton.setActive(showMega && megaUpgraded);
        megaBulkMarkingButton.setVisible(showMega);
        megaBulkMarkingButton.setActive(showMega);
        if (showMega) {
            g.blit(
                    MEGA_BACKGROUND,
                    absX(MEGA_PANEL_LEFT),
                    absY(MEGA_PANEL_TOP),
                    0,
                    0,
                    MEGA_PANEL_WIDTH,
                    MEGA_PANEL_HEIGHT,
                    MEGA_PANEL_WIDTH,
                    MEGA_PANEL_HEIGHT);
        }
        if (panel != 0) drawPanel(g, 6, 6, 166, 116);
    }

    private List<NEStorageUiMatrixState> cells() {
        return currentState().matrixStates().stream()
                .filter(NEStorageUiMatrixState::hasMatrix)
                .toList();
    }

    private String capacity(long value) {
        return value < 0 ? "∞" : NELDLibText.ae2Amount(value);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int maxWidth) {
        small(g, text, x, y, maxWidth, 0.65F, false, 0xFFD6D0E0);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int maxWidth, float scale, boolean rightAlign) {
        small(g, text, x, y, maxWidth, scale, rightAlign, 0xFFD6D0E0);
    }

    private void small(
            GuiGraphics g, Component text, int x, int y, int maxWidth, float scale, boolean rightAlign, int color) {
        g.pose().pushPose();
        g.pose().translate(absX(x), absY(y), 0);
        g.pose().scale(scale, scale, 1);
        String clipped = font().plainSubstrByWidth(text.getString(), (int) (maxWidth / scale));
        int textX = rightAlign ? Math.max(0, Math.round(maxWidth / scale) - font().width(clipped) - 2) : 0;
        g.drawString(font(), clipped, textX, 0, color, false);
        g.pose().popPose();
    }

    private void drawGraphLines(GuiGraphics g, int mouseX, int mouseY) {
        drawGraphLine(g, 18, 38, 60, 6, 6, 225, mouseX, mouseY);
        drawGraphLine(g, 95, 35, 59, 6, 1, 197, mouseX, mouseY);
        drawGraphLine(g, 18, 57, 60, 6, 6, 225, mouseX, mouseY);
        drawGraphLine(g, 95, 54, 59, 6, 1, 197, mouseX, mouseY);
    }

    private void drawGraphLine(
            GuiGraphics g, int x, int y, int width, int height, int u, int v, int mouseX, int mouseY) {
        boolean hovered = isMouseIn(x, y, width, height, mouseX, mouseY);
        float tint = hovered ? 1.0F : 0.4F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, tint);
        g.blit(ELEMENTS, absX(x), absY(y), width, height, u, v, width, height, ELEMENTS_SIZE, ELEMENTS_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawGauge(GuiGraphics g, int x, int y, double ratio, int color) {
        double clamped = Math.max(0.0D, Math.min(1.0D, ratio));
        if (clamped <= 0.0D) return;
        int height = 92;
        int capHeight = 8;
        int bottom = absY(y + height);
        int barHeight = (int) Math.round((height - capHeight) * clamped);
        int top = bottom - barHeight - capHeight;
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(red, green, blue, alpha);
        g.blit(ELEMENTS, absX(x), top, 32, capHeight, 1, 246, 32, capHeight, ELEMENTS_SIZE, ELEMENTS_SIZE);
        for (int drawY = top + capHeight / 2 + 1; drawY < bottom - capHeight / 2 + 1; drawY++) {
            g.blit(ELEMENTS, absX(x), drawY, 32, 4, 34, 250, 32, 4, ELEMENTS_SIZE, ELEMENTS_SIZE);
        }
        g.blit(
                ELEMENTS,
                absX(x),
                bottom - capHeight,
                32,
                capHeight,
                1,
                246,
                32,
                capHeight,
                ELEMENTS_SIZE,
                ELEMENTS_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawStorageTypeList(GuiGraphics g) {
        var types = currentState().typeStates();
        boolean infinite = currentState().infiniteMode() || currentState().migratingToInfinite();
        int stride = infinite ? INFINITE_TYPE_BLOCK_STRIDE : TYPE_BLOCK_STRIDE;
        int visibleBlocks = Math.max(1, CELL_LIST_HEIGHT / stride);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, types.size() - visibleBlocks)));
        g.enableScissor(
                absX(CELL_LIST_LEFT),
                absY(CELL_LIST_TOP),
                absX(CELL_LIST_LEFT + CELL_LIST_WIDTH),
                absY(CELL_LIST_TOP + CELL_LIST_HEIGHT));
        for (int row = 0; scroll + row < types.size(); row++) {
            int y = CELL_LIST_TOP + TYPE_LIST_PADDING + row * stride;
            if (y >= CELL_LIST_TOP + CELL_LIST_HEIGHT) break;
            var type = types.get(scroll + row);
            small(
                    g,
                    type.displayComponent(),
                    CELL_LIST_LEFT + 2,
                    y,
                    CELL_LIST_WIDTH - 4,
                    0.9F,
                    false,
                    storageTypeAccentColor(row));
            small(
                    g,
                    infinite
                            ? usedOnly("gui.neoecoae.storage.legacy.cell_types", capacity(type.usedTypes()))
                            : Component.translatable(
                                    "gui.neoecoae.storage.legacy.cell_types",
                                    capacity(type.usedTypes()),
                                    capacity(type.totalTypes())),
                    CELL_LIST_LEFT + 2,
                    y + 12,
                    CELL_LIST_WIDTH - 4,
                    0.6F,
                    false);
            if (!infinite) {
                drawTypeProgress(
                        g, CELL_LIST_LEFT + 2, y + 21, CELL_LIST_WIDTH - 4, type.usedTypes(), type.totalTypes());
            }
            small(
                    g,
                    infinite
                            ? usedOnly(
                                    "gui.neoecoae.storage.legacy.cell_bytes",
                                    NELDLibText.hugeAmount(type.safeUsedAmount()))
                            : Component.translatable(
                                    "gui.neoecoae.storage.legacy.cell_bytes",
                                    capacity(type.usedBytes()),
                                    capacity(type.totalBytes())),
                    CELL_LIST_LEFT + 2,
                    y + (infinite ? 21 : 27),
                    CELL_LIST_WIDTH - 4,
                    0.6F,
                    false);
            if (!infinite) {
                drawTypeProgress(
                        g, CELL_LIST_LEFT + 2, y + 36, CELL_LIST_WIDTH - 4, type.usedBytes(), type.totalBytes());
            }
        }
        g.disableScissor();
    }

    private Component usedOnly(String translationKey, String used) {
        String marker = "\u0001";
        String rendered = Component.translatable(translationKey, used, marker).getString();
        int markerIndex = rendered.indexOf(marker);
        if (markerIndex < 0) return Component.literal(rendered);
        String prefix = rendered.substring(0, markerIndex);
        int separator = prefix.lastIndexOf('/');
        return Component.literal(separator >= 0 ? prefix.substring(0, separator).stripTrailing() : prefix);
    }

    private int storageTypeAccentColor(int visibleRow) {
        int[] palette = {0xFFE06C75, 0xFF61AFEF, 0xFF98C379, 0xFFD19A66, 0xFFC678DD};
        return palette[Math.floorMod(scroll + visibleRow, palette.length)];
    }

    private void drawTypeProgress(GuiGraphics g, int x, int y, int width, long used, long total) {
        g.fill(absX(x), absY(y), absX(x + width), absY(y + 4), 0x281F2F34);
        int fill = (int) Math.round(width * percent(used, total));
        if (fill > 0) g.fill(absX(x), absY(y), absX(x + fill), absY(y + 4), 0xFF26A6BD);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics g, int mx, int my, float partial) {
        if (panel == 0) {
            small(g, title, 8, 6, 160, 0.65F, false, 0xFF3F3D52);
            small(
                    g,
                    Component.literal(NELDLibText.ae2Amount(currentState().energyUsage()) + " AE/t"),
                    18,
                    30,
                    60,
                    0.6F,
                    false);
            small(
                    g,
                    Component.translatable(
                            "gui.neoecoae.storage.legacy.graph.energy_stored",
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.1f%%",
                                    percent(
                                                    currentState().storedEnergy(),
                                                    currentState().maxEnergy())
                                            * 100.0D)),
                    95,
                    27,
                    59,
                    0.6F,
                    true);
            small(
                    g,
                    Component.translatable(
                            "gui.neoecoae.storage.legacy.graph.total_bytes",
                            NELDLibText.ae2Amount(currentState().totalUsedBytes())),
                    18,
                    49,
                    60,
                    0.6F,
                    false);
            small(
                    g,
                    Component.translatable(
                            "gui.neoecoae.storage.legacy.graph.total_usage",
                            String.format(java.util.Locale.ROOT, "%.1f%%", animatedRatio * 100.0D)),
                    95,
                    46,
                    59,
                    0.6F,
                    true);
        }
        drawStorageTypeList(g);
        if (megaCellCount > 0) drawMegaPanel(g, mx, my);
        if (panel != 0) drawFloatingPanel(g);
    }

    private void drawFloatingPanel(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        small(
                g,
                Component.translatable(panel == 1 ? "gui.ae2.Priority" : "gui.neoecoae.storage.host.details"),
                12,
                12,
                140);
        small(g, Component.literal("×"), 157, 12, 10);
        if (panel == 1) {
            int[] steps = {1, 10, 100, 1000};
            for (int i = 0; i < steps.length; i++) {
                small(g, Component.literal("+" + steps[i]), 14 + 38 * i, 34, 36);
                small(g, Component.literal("-" + steps[i]), 14 + 38 * i, 76, 36);
            }
            small(g, Component.translatable("gui.ae2.PriorityInsertionHint"), 12, 99, 154);
            small(g, Component.translatable("gui.ae2.PriorityExtractionHint"), 12, 110, 154);
        } else if (panel == 4) {
            var entries = currentState().hugeStacks();
            detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, entries.size() - 4)));
            for (int i = 0; i < 4 && i + detailScroll < entries.size(); i++) {
                var entry = entries.get(i + detailScroll);
                small(g, entry.key().getDisplayName(), 12, 28 + i * 16, 72);
                small(g, Component.literal(NELDLibText.hugeAmount(entry.amount())), 86, 28 + i * 16, 80);
            }
            small(
                    g,
                    Component.literal("<    " + (currentState().hugeStackPage() + 1) + " / "
                            + currentState().hugeStackPageCount() + "    >"),
                    20,
                    102,
                    132);
        } else if (panel == 5) {
            var types = currentState().typeStates();
            detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, types.size() - 3)));
            for (int i = 0; i < 3 && i + detailScroll < types.size(); i++) {
                var type = types.get(i + detailScroll);
                small(g, type.displayComponent(), 12, 28 + i * 24, 154);
                small(
                        g,
                        Component.literal(NELDLibText.hugeAmount(type.safeUsedAmount()) + " / "
                                + capacity(type.totalBytes()) + " B"),
                        12,
                        38 + i * 24,
                        154);
            }
            small(g, Component.literal(currentState().infiniteDomainState()), 12, 105, 154);
        }
        g.pose().popPose();
    }

    private void drawMegaPanel(GuiGraphics g, int mouseX, int mouseY) {
        for (int slot = 0; slot < 25; slot++) {
            int x = MEGA_GRID_LEFT + slot % 5 * MEGA_SLOT_SIZE;
            int y = MEGA_GRID_TOP + slot / 5 * MEGA_SLOT_SIZE;
            if (slot < megaFilters.size() && !megaFilters.get(slot).isEmpty()) {
                g.renderItem(megaFilters.get(slot), absX(x), absY(y));
            }
            if (isMouseIn(x, y, MEGA_SLOT_SIZE, MEGA_SLOT_SIZE, mouseX, mouseY)) {
                g.fill(absX(x + 1), absY(y + 1), absX(x + 17), absY(y + 17), 0x66FFFFFF);
            }
        }
        drawMegaControlLabel(g, (megaCellIndex + 1) + "/" + megaCellCount, MEGA_CELL_CONTROLS_TOP);
        if (megaUpgraded) drawMegaControlLabel(g, (megaPage + 1) + "/2", MEGA_PAGE_CONTROLS_TOP);
    }

    private void drawMegaControlLabel(GuiGraphics g, String value, int top) {
        float scale = 2.0F / 3.0F;
        int textWidth = font().width(value);
        g.pose().pushPose();
        g.pose().translate(absX(MEGA_CONTROLS_LEFT + 10), absY(top + 2), 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(
                font(),
                Component.literal(value),
                Math.round((20 / scale - textWidth) / 2.0F),
                0,
                MEGA_TEXT_COLOR,
                false);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int mx = (int) x, my = (int) y;
        if (panel != 0 && isMouseIn(6, 6, 166, 116, mx, my)) {
            if (isMouseIn(150, 6, 22, 20, mx, my)) {
                panel = 0;
                return true;
            }
            if (panel == 1) {
                int[] steps = {1, 10, 100, 1000};
                for (int i = 0; i < 4; i++) {
                    if (isMouseIn(14 + i * 38, 28, 36, 18, mx, my)) {
                        send(2, steps[i]);
                        return true;
                    }
                    if (isMouseIn(14 + i * 38, 70, 36, 18, mx, my)) {
                        send(2, -steps[i]);
                        return true;
                    }
                }
                if (isMouseIn(60, 55, 92, 14, mx, my)) return super.mouseClicked(x, y, button);
            } else if (panel == 4 && isMouseIn(12, 96, 148, 24, mx, my)) {
                send(6, currentState().hugeStackPage() + (mx < absX(86) ? -1 : 1));
            }
            return true;
        }
        if (megaCellCount > 0 && isMouseIn(MEGA_GRID_LEFT, MEGA_GRID_TOP, 90, 90, mx, my)) {
            int slot = (my - absY(MEGA_GRID_TOP)) / MEGA_SLOT_SIZE * 5 + (mx - absX(MEGA_GRID_LEFT)) / MEGA_SLOT_SIZE;
            ItemStack carried = player.containerMenu.getCarried();
            if (button == 1) sendFilter(slot, ItemStack.EMPTY);
            else if (button == 0 && !carried.isEmpty()) sendFilter(slot, carried);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseWheelMove(double x, double y, double delta) {
        if ((panel == 4 || panel == 5) && isMouseIn(6, 6, 166, 116, (int) x, (int) y)) {
            int maximum = panel == 4
                    ? currentState().hugeStacks().size() - 4
                    : currentState().typeStates().size() - 3;
            detailScroll = Math.max(0, Math.min(Math.max(0, maximum), detailScroll + (delta < 0 ? 1 : -1)));
            return true;
        }
        if (isMouseIn(CELL_LIST_LEFT, CELL_LIST_TOP, CELL_LIST_WIDTH, CELL_LIST_HEIGHT, (int) x, (int) y)) {
            int stride = currentState().infiniteMode() || currentState().migratingToInfinite()
                    ? INFINITE_TYPE_BLOCK_STRIDE
                    : TYPE_BLOCK_STRIDE;
            int visibleBlocks = Math.max(1, CELL_LIST_HEIGHT / stride);
            scroll = Math.max(
                    0,
                    Math.min(
                            Math.max(0, currentState().typeStates().size() - visibleBlocks),
                            scroll + (delta < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseWheelMove(x, y, delta);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics g, int x, int y) {
        if (isMouseIn(-17, 3, 16, 16, x, y)) {
            g.renderComponentTooltip(
                    font(),
                    List.of(
                            ButtonToolTips.OpenGuide.text().withStyle(style -> style.withColor(0xFFFFFF)),
                            ButtonToolTips.OpenGuideDetail.text().withStyle(net.minecraft.ChatFormatting.GRAY)),
                    x,
                    y);
            return;
        }
        if (isMouseIn(-17, 25, 16, 16, x, y)) {
            g.renderComponentTooltip(
                    font(), List.of(Component.translatable("gui.neoecoae.storage_priority.open")), x, y);
            return;
        }
        if (megaCellCount > 0 && isMouseIn(MEGA_ACTION_LEFT, MEGA_ACTION_TOP, 16, 16, x, y)) {
            g.renderComponentTooltip(font(), List.of(Component.translatable("gui.neoecoae.storage.bulk_mark")), x, y);
            return;
        }
        if (panel == 4 && isMouseIn(12, 28, 154, 64, x, y)) {
            int index = detailScroll + (y - absY(28)) / 16;
            if (index < currentState().hugeStacks().size()) {
                var entry = currentState().hugeStacks().get(index);
                g.renderComponentTooltip(
                        font(), List.of(entry.key().getDisplayName(), Component.literal(entry.amount())), x, y);
            }
        }
        if (panel == 0 && isMouseIn(146, 100, 18, 18, x, y) && !currentState().canTakeInfiniteComponent()) {
            g.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("tooltip.neoecoae.storage.infinite_component_locked")),
                    x,
                    y);
        }
    }
}
