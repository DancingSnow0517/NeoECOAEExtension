package cn.dancingsnow.neoecoae.gui.storage;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiMatrixState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.sync.NEStorageUiStateCodec;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEAe2IconButtonWidget;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NELDLibSyncedStateWidget;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** LDLib1 implementation of the 1.21 storage host surface. Server storage remains authoritative. */
public final class StorageHostUI extends NELDLibSyncedStateWidget<NEStorageUiState> {
    public static final int WIDTH = 272;
    public static final int HEIGHT = 216;
    private static final int ACTION = FIRST_CUSTOM_UPDATE_ID + 1;
    private static final int CONFIG = FIRST_CUSTOM_UPDATE_ID + 2;
    private static final ResourceLocation BACKGROUND =
            cn.dancingsnow.neoecoae.NeoECOAE.id("textures/gui/storage/estorage_infinite_controller.png");
    private final ECOStorageSystemBlockEntity storage;
    private final Player player;
    private int scroll;
    private int detailScroll;
    private int selected = -1;
    private int panel;
    private int priority;
    private int length = 1;
    private boolean mirrored;
    private double animatedRatio;
    private TextFieldWidget priorityField;
    private SlotWidget infiniteSlot;
    private SlotWidget megaUpgradeSlot;
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
        addWidget(new NEAe2IconButtonWidget(-20, 0, 18, 20, NEAe2IconButtonWidget.Ae2Icon.WRENCH, click -> {
                    if (click.isRemote) panel = panel == 1 ? 0 : 1;
                })
                .useAeTabButton());
        addWidget(new NEAe2IconButtonWidget(-20, 22, 18, 20, NEAe2IconButtonWidget.Ae2Icon.WRENCH, click -> {
                    if (click.isRemote) panel = panel == 2 ? 0 : 2;
                })
                .useAeTabButton());
        addWidget(new NEAe2IconButtonWidget(-20, 44, 18, 20, Icon.LEVEL_ENERGY, click -> {
                    if (click.isRemote) panel = panel == 4 ? 0 : 4;
                })
                .useAeTabButton());
        addWidget(new NEAe2IconButtonWidget(-20, 66, 18, 20, Icon.POWER_UNIT_AE, click -> {
                    if (click.isRemote) panel = panel == 5 ? 0 : 5;
                })
                .useAeTabButton());
        addWidget(new NEAe2IconButtonWidget(-20, 88, 18, 20, Icon.HELP, click -> {
                    if (!click.isRemote && net.minecraftforge.fml.ModList.get().isLoaded("guideme")) {
                        guideme.GuidesCommon.openGuide(
                                player,
                                appeng.core.AppEng.makeId("guide"),
                                guideme.PageAnchor.parse("neoecoae:neoecoae_intro/storage_system.md"));
                    }
                })
                .useAeTabButton());
        addWidget(new NEAe2IconButtonWidget(-20, 110, 18, 20, Icon.BACKGROUND_UPGRADE, click -> {
                    if (click.isRemote) panel = panel == 6 ? 0 : 6;
                })
                .useAeTabButton());
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
                        116,
                        82,
                        true,
                        true)
                .setBackgroundTexture(IGuiTexture.EMPTY);
        megaUpgradeSlot.setVisible(false);
        megaUpgradeSlot.setActive(false);
        addWidget(megaUpgradeSlot);
        priorityField = new TextFieldWidget(60, 55, 92, 14, () -> Integer.toString(priority), text -> {
            try {
                if (player instanceof ServerPlayer
                        && !storage.isRemoved()
                        && player.distanceToSqr(storage.getBlockPos().getCenter()) <= 64) {
                    storage.setPriority(Integer.parseInt(text));
                }
            } catch (NumberFormatException ignored) {
            }
        });
        priorityField.setNumbersOnly(Integer.MIN_VALUE, Integer.MAX_VALUE).setMaxStringLength(11);
        priorityField.setVisible(false);
        priorityField.setActive(false);
        addWidget(priorityField);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        long currentMegaFingerprint = storage.getEcoMegaConfigurationFingerprint();
        if (priority != storage.getPriority()
                || length != storage.getSelectedBuildLength()
                || megaFingerprint != currentMegaFingerprint) {
            priority = storage.getPriority();
            length = storage.getSelectedBuildLength();
            megaFingerprint = currentMegaFingerprint;
            writeUpdateInfo(CONFIG, this::writeConfig);
        }
    }

    private void writeConfig(FriendlyByteBuf buf) {
        buf.writeInt(storage.getPriority());
        buf.writeVarInt(storage.getSelectedBuildLength());
        buf.writeVarInt(storage.getEcoMegaBulkCellCount());
        buf.writeVarInt(storage.getSelectedEcoMegaBulkCell());
        buf.writeVarInt(storage.getSelectedEcoMegaPage());
        buf.writeBoolean(storage.hasEcoMegaUpgradeCard());
        for (int slot = 0; slot < 25; slot++) buf.writeItem(storage.getEcoMegaFilterStack(slot));
    }

    private void readConfig(FriendlyByteBuf buf) {
        priority = buf.readInt();
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
            case 1 -> storage.setPriority(value);
            case 2 -> {
                if (value == 1
                        || value == 10
                        || value == 100
                        || value == 1000
                        || value == -1
                        || value == -10
                        || value == -100
                        || value == -1000) storage.setPriority(StoragePriority.adjust(storage.getPriority(), value));
            }
            case 3 -> {
                if (value > 0) storage.increaseBuildLength();
                else storage.decreaseBuildLength();
            }
            case 4 -> storage.previewStructure(serverPlayer, storage.getSelectedBuildLength(), mirror);
            case 5 -> storage.autoBuild(serverPlayer, storage.getSelectedBuildLength(), mirror);
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
        NEPlayerInventoryWidgets.drawPlayerInventorySlots(g, this::absX, this::absY, 7, 129, 187);
        double target = percent(currentState().totalUsedBytes(), currentState().totalBytes());
        animatedRatio += (target - animatedRatio) * 0.15;
        g.fill(absX(72), absY(22), absX(104), absY(114), 0xFF302C42);
        int fill = (int) Math.round(animatedRatio * 90);
        g.fill(absX(73), absY(113 - fill), absX(103), absY(113), 0xFF9178C5);
        priorityField.setVisible(panel == 1);
        priorityField.setActive(panel == 1);
        infiniteSlot.setVisible(panel == 0);
        infiniteSlot.setActive(panel == 0);
        megaUpgradeSlot.setVisible(panel == 6 && megaCellCount > 0);
        megaUpgradeSlot.setActive(panel == 6 && megaCellCount > 0);
        if (panel != 0) drawPanel(g, 6, 6, 166, 116);
    }

    private List<NEStorageUiMatrixState> cells() {
        return currentState().matrixStates().stream()
                .filter(NEStorageUiMatrixState::hasMatrix)
                .toList();
    }

    private String capacity(long value) {
        return value < 0 ? "∞" : fmt(value);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int maxWidth) {
        g.pose().pushPose();
        g.pose().translate(absX(x), absY(y), 0);
        g.pose().scale(0.65F, 0.65F, 1);
        g.drawString(
                font(), font().plainSubstrByWidth(text.getString(), (int) (maxWidth / 0.65F)), 0, 0, 0xFF413F54, false);
        g.pose().popPose();
    }

    @Override
    protected void drawMachineForeground(GuiGraphics g, int mx, int my, float partial) {
        if (panel == 0) {
            small(g, title, 8, 6, 160);
            small(
                    g,
                    Component.translatable(
                            "gui.neoecoae.storage.legacy.graph.total_bytes",
                            fmt(currentState().totalUsedBytes())),
                    10,
                    51,
                    60);
            small(
                    g,
                    Component.literal(String.format(java.util.Locale.ROOT, "%.1f%%", animatedRatio * 100)),
                    108,
                    51,
                    58);
            small(
                    g,
                    Component.literal(fmt(currentState().storedEnergy()) + " / "
                            + fmt(currentState().maxEnergy()) + " AE"),
                    10,
                    32,
                    60);
            small(
                    g,
                    Component.literal(fmt(currentState().totalUsedTypes()) + " / "
                            + capacity(currentState().totalTypes())),
                    108,
                    32,
                    58);
        }
        var cells = cells();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, cells.size() - 6)));
        for (int row = 0; row < 6 && scroll + row < cells.size(); row++) {
            var cell = cells.get(scroll + row);
            int y = 22 + row * 28;
            g.fill(absX(178), absY(y), absX(261), absY(y + 26), selected == scroll + row ? 0xFFECE5F7 : 0xFFC6B8DC);
            small(g, cell.stack().getHoverName(), 180, y + 2, 78);
            small(
                    g,
                    Component.literal(capacity(cell.usedTypes()) + " / " + capacity(cell.totalTypes())),
                    180,
                    y + 10,
                    78);
            small(
                    g,
                    Component.literal(capacity(cell.usedBytes()) + " / " + capacity(cell.totalBytes()) + " B"),
                    180,
                    y + 18,
                    78);
        }
        if (panel != 0) drawFloatingPanel(g);
    }

    private void drawFloatingPanel(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        small(
                g,
                Component.translatable(
                        panel == 1
                                ? "gui.ae2.Priority"
                                : panel == 2 ? "gui.neoecoae.storage.host.build" : "gui.neoecoae.storage.host.details"),
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
        } else if (panel == 2) {
            small(g, Component.literal("−       " + length + "       +"), 20, 34, 130);
            small(g, Component.translatable("gui.neoecoae.storage.host.mirror", mirrored), 20, 54, 138);
            small(g, Component.translatable("gui.neoecoae.storage.host.preview"), 20, 78, 64);
            small(g, Component.translatable("gui.neoecoae.storage.host.build"), 94, 78, 64);
        } else if (panel == 4) {
            var entries = currentState().hugeStacks();
            detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, entries.size() - 4)));
            for (int i = 0; i < 4 && i + detailScroll < entries.size(); i++) {
                var entry = entries.get(i + detailScroll);
                small(g, entry.key().getDisplayName(), 12, 28 + i * 16, 72);
                small(g, Component.literal(entry.amount()), 86, 28 + i * 16, 80);
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
                        Component.literal(type.safeUsedAmount() + " / " + capacity(type.totalBytes()) + " B"),
                        12,
                        38 + i * 24,
                        154);
            }
            small(g, Component.literal(currentState().infiniteDomainState()), 12, 105, 154);
        } else if (panel == 6) {
            drawMegaPanel(g);
        } else if (selected >= 0 && selected < cells().size()) {
            var cell = cells().get(selected);
            small(g, cell.stack().getHoverName(), 12, 32, 154);
            small(g, Component.literal("L" + (cell.tier() == 3 ? 9 : cell.tier() == 2 ? 6 : 4)), 12, 48, 154);
            small(
                    g,
                    Component.literal(capacity(cell.usedBytes()) + " / " + capacity(cell.totalBytes()) + " B"),
                    12,
                    65,
                    154);
            small(g, Component.literal(capacity(cell.usedTypes()) + " / " + capacity(cell.totalTypes())), 12, 82, 154);
        }
        g.pose().popPose();
    }

    private void drawMegaPanel(GuiGraphics g) {
        small(g, Component.translatable("gui.neoecoae.storage.mega.title"), 12, 12, 140);
        if (megaCellCount <= 0) {
            small(g, Component.translatable("gui.neoecoae.storage.bulk_mark.result.no_bulk_cell"), 12, 34, 145);
            return;
        }
        for (int slot = 0; slot < 25; slot++) {
            int x = 12 + slot % 5 * 18;
            int y = 27 + slot / 5 * 18;
            g.fill(absX(x), absY(y), absX(x + 17), absY(y + 17), 0xFF8F83A7);
            if (slot < megaFilters.size() && !megaFilters.get(slot).isEmpty()) {
                g.renderItem(megaFilters.get(slot), absX(x), absY(y));
            }
        }
        small(g, Component.literal("< " + (megaCellIndex + 1) + "/" + megaCellCount + " >"), 108, 30, 48);
        small(g, Component.literal("< " + (megaPage + 1) + "/" + (megaUpgraded ? 2 : 1) + " >"), 108, 52, 48);
        small(g, Component.translatable("gui.neoecoae.storage.bulk_mark"), 108, 73, 48);
        small(g, Component.translatable("gui.neoecoae.storage.mega.upgrade"), 108, 101, 48);
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
            } else if (panel == 2) {
                if (isMouseIn(20, 28, 30, 18, mx, my)) send(3, -1);
                if (isMouseIn(90, 28, 40, 18, mx, my)) send(3, 1);
                if (isMouseIn(20, 48, 138, 18, mx, my)) mirrored = !mirrored;
                if (isMouseIn(20, 72, 64, 18, mx, my)) send(4, 0);
                if (isMouseIn(94, 72, 64, 18, mx, my)) send(5, 0);
            } else if (panel == 4 && isMouseIn(12, 96, 148, 24, mx, my)) {
                send(6, currentState().hugeStackPage() + (mx < absX(86) ? -1 : 1));
            } else if (panel == 6) {
                if (isMouseIn(12, 27, 90, 90, mx, my)) {
                    int slot = (my - absY(27)) / 18 * 5 + (mx - absX(12)) / 18;
                    ItemStack carried = player.containerMenu.getCarried();
                    if (button == 1) sendFilter(slot, ItemStack.EMPTY);
                    else if (button == 0 && !carried.isEmpty()) sendFilter(slot, carried);
                } else if (isMouseIn(106, 24, 52, 18, mx, my)) {
                    send(7, mx < absX(132) ? -1 : 1);
                } else if (isMouseIn(106, 46, 52, 18, mx, my) && megaUpgraded) {
                    send(8, mx < absX(132) ? -1 : 1);
                } else if (isMouseIn(106, 68, 52, 18, mx, my)) {
                    send(10, 0);
                }
            }
            return true;
        }
        if (isMouseIn(178, 22, 83, 168, mx, my)) {
            selected = scroll + (my - absY(22)) / 28;
            if (selected < cells().size()) panel = 3;
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
        if (isMouseIn(178, 22, 83, 172, (int) x, (int) y)) {
            scroll = Math.max(0, Math.min(Math.max(0, cells().size() - 6), scroll + (delta < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseWheelMove(x, y, delta);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics g, int x, int y) {
        String[] keys = {
            "gui.ae2.Priority",
            "gui.neoecoae.storage.host.build",
            "gui.neoecoae.storage.infinite_domain",
            "gui.neoecoae.storage.host.details",
            "gui.neoecoae.storage.host.guide",
            "gui.neoecoae.storage.mega.title"
        };
        for (int i = 0; i < keys.length; i++) {
            if (isMouseIn(-20, i * 22, 18, 20, x, y)) {
                g.renderComponentTooltip(font(), List.of(Component.translatable(keys[i])), x, y);
                return;
            }
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
