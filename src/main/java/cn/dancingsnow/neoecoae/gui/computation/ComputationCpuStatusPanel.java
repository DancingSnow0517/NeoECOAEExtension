package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import appeng.api.client.AEKeyRendering;
import appeng.api.util.AEColor;
import cn.dancingsnow.neoecoae.client.gui.widget.NEButtonText;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostProgressAnimation;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.SyncStrategy;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEventBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.time.DurationFormatUtils;

/** The 326x254 ECO crafting CPU page shown when a CPU row is clicked. */
final class ComputationCpuStatusPanel extends BindableValue<CompoundTag> {
    private final HostProgressAnimation opening = new HostProgressAnimation();
    static final int WIDTH = 326, HEIGHT = 254, ROWS = 9;
    private static final int CPU_X = 8, CPU_Y = 19, CPU_WIDTH = 67, CPU_HEIGHT = 22, CPU_STRIDE = 23;
    private static final int CPU_TRACK_X = 77, TRACK_Y = 19, TRACK_HEIGHT = 206;
    private static final int ITEM_X = 97, ITEM_Y = 19, ITEM_WIDTH = 67, ITEM_HEIGHT = 22, ITEM_COLUMN_STRIDE = 68;
    private static final int ITEM_COLS = 3, ITEM_ROWS = 9, ITEM_STRIDE = 23, ITEM_PAGE = ITEM_COLS * ITEM_ROWS;
    private static final int ITEM_TRACK_X = 306;
    private static final int TEXT = 0x413F54;
    private static final int BATCH_ICON_SIZE = 16, BATCH_ICON_LEFT = -1;
    private static final ResourceLocation BATCH_ICON =
        new ResourceLocation("neoecoae", "textures/gui/computation/eco_batching.png");
    // The source sheet is 384x384, but only the 326x254 top slice is the page background.
    // The two 67x22 CPU row states live at y=260 in the same sheet.
    private static final IGuiTexture BACKGROUND = SpriteTexture.of(
        new ResourceLocation("neoecoae", "textures/guis/eco_craftingcpu.png")).setSprite(0, 0, WIDTH, HEIGHT);
    private static final IGuiTexture CPU_NORMAL = SpriteTexture.of(
        new ResourceLocation("neoecoae", "textures/guis/eco_craftingcpu.png")).setSprite(0, 260, CPU_WIDTH, CPU_HEIGHT);
    private static final IGuiTexture CPU_SELECTED = SpriteTexture.of(
        new ResourceLocation("neoecoae", "textures/guis/eco_craftingcpu.png")).setSprite(69, 260, CPU_WIDTH, CPU_HEIGHT);

    private final ComputationControllerUI.Config config;
    private final BindableValue<CompoundTag> cpuScrollRequest = new BindableValue<>(new CompoundTag());
    private final BindableValue<CompoundTag> itemScrollRequest = new BindableValue<>(new CompoundTag());
    private final Map<AEKey, ECOProcessingProgress> processingByKey = new HashMap<>();
    private final BindableValue<CompoundTag> processingOverview = new BindableValue<>(new CompoundTag()) {
        @Override public BindableValue<CompoundTag> setValue(CompoundTag value, boolean notify) {
            super.setValue(value, notify);
            if (value == null || value.getInt("serial") != selectedSerial) return this;
            processingByKey.clear();
            for (var raw : value.getList("items", Tag.TAG_COMPOUND)) {
                var tag = (CompoundTag) raw;
                AEKey key = AEKey.fromTagGeneric(tag.getCompound("key"));
                if (key != null) processingByKey.put(key, ECOProcessingProgress.read(tag.getCompound("progress")));
            }
            return this;
        }
    };
    private ComputationItemActionsPopup itemActionsPopup;
    private final BindableValue<CompoundTag> itemActionResponse = new BindableValue<>(new CompoundTag()) {
        @Override public BindableValue<CompoundTag> setValue(CompoundTag value, boolean notify) {
            super.setValue(value, notify);
            if (itemActionsPopup != null && value != null && value.getInt("serial") == selectedSerial) itemActionsPopup.accept(value);
            return this;
        }
    };
    private final StatusButton pauseButton = new StatusButton();
    private final StatusButton cancelButton = new StatusButton();
    private final ComputationScrollbar cpuScrollbar;
    private final ComputationScrollbar itemScrollbar;
    private List<ComputationCpuEntry> cpus = List.of();
    private List<ComputationCpuItemEntry> items = List.of();
    private final ComputationScrollPage<ComputationCpuEntry> cpuPage = new ComputationScrollPage<>(ROWS, 1);
    private final ComputationScrollPage<ComputationCpuItemEntry> itemPage = new ComputationScrollPage<>(ITEM_ROWS, ITEM_COLS);
    private ComputationCpuEntry selected;
    private int selectedSerial = -1, total, cpuOffset, cpuPageOffset, itemTotal, itemOffset, itemPageOffset;
    private boolean cpuScrollPending, itemScrollPending;
    private long cpuScrollSequence, itemScrollSequence;
    private Runnable backAction = () -> {};
    private java.util.function.IntConsumer selectionRequest = serial -> {};
    private java.util.function.BiConsumer<AEKey, Integer> processingDetailsRequest = (key, page) -> {};
    private Runnable stopProcessingRequest = () -> {};
    private AEKey hoveredProcessingKey, requestedProcessingKey;
    private int hoveredProcessingPage, requestedProcessingPage;
    private long nextProcessingRequest;

    ComputationCpuStatusPanel(ComputationControllerUI.Config config, CpuSelectionState.Identities<ECOCraftingCPU> identities) {
        super(new CompoundTag());
        this.config = config;
        layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0));
        style(style -> style.backgroundTexture(BACKGROUND));
        setOverflowVisible(true);
        ServerState server = new ServerState(config, identities);
        var selectCpu = addRPCEvent(RPCEventBuilder.simple(Integer.class, server::select));
        selectionRequest = serial -> selectCpu.send(serial);
        var cpuAction = addRPCEvent(RPCEventBuilder.simple(Integer.class, Integer.class, server::action));
        var itemAction = addRPCEvent(RPCEventBuilder.simple(Integer.class, CompoundTag.class, server::itemAction));
        itemActionsPopup = new ComputationItemActionsPopup(request -> itemAction.send(selectedSerial, request));
        var processingDetails = addRPCEvent(RPCEventBuilder.simple(Integer.class, CompoundTag.class, server::processingDetails));
        processingDetailsRequest = (key, page) -> {
            var request = new CompoundTag();
            request.put("key", key.toTagGeneric());
            request.putInt("page", page);
            processingDetails.send(selectedSerial, request);
        };
        stopProcessingRequest = () -> processingDetails.send(-1, new CompoundTag());
        bind(DataBindingBuilder.create(server::snapshot, ignored -> {})
            .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        cpuScrollRequest.bind(DataBindingBuilder.<CompoundTag>create(null, server::scrollCpu)
            .syncType(CompoundTag.class).s2cStrategy(SyncStrategy.NONE).build());
        itemScrollRequest.bind(DataBindingBuilder.<CompoundTag>create(null, server::scrollItems)
            .syncType(CompoundTag.class).s2cStrategy(SyncStrategy.NONE).build());
        processingOverview.bind(DataBindingBuilder.create(server::processingSnapshot, ignored -> {})
            .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        itemActionResponse.bind(DataBindingBuilder.create(() -> server.itemActionResponse, ignored -> {})
            .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        cpuScrollRequest.setDisplay(false);
        itemScrollRequest.setDisplay(false);
        processingOverview.setDisplay(false);
        itemActionResponse.setDisplay(false);
        addChildren(cpuScrollRequest, itemScrollRequest, processingOverview, itemActionResponse, itemActionsPopup);
        addChildren(cpuHitbox(), itemHitbox());
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (itemActionsPopup.isDisplayed() && !itemActionsPopup.isMouseOverElement(event.x, event.y)) {
                itemActionsPopup.close();
                if (event.button != 1) event.stopImmediatePropagation();
            }
        }, true);
        cpuScrollbar = new ComputationScrollbar(this, CPU_TRACK_X, TRACK_Y, TRACK_HEIGHT, this::scrollCpuTo);
        itemScrollbar = new ComputationScrollbar(this, ITEM_TRACK_X, TRACK_Y, TRACK_HEIGHT,
            row -> scrollItemsTo(row * ITEM_COLS));
        addChildren(cpuScrollbar, itemScrollbar);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (itemActionsPopup.isDisplayed()) {
                if (!itemActionsPopup.isMouseOverElement(event.x, event.y)) event.stopImmediatePropagation();
                return;
            }
            var hovered = itemAt(event.x, event.y);
            if (event.isShiftDown() && event.deltaY != 0 && hovered != null && hovered.processing().pages() > 1) {
                requestProcessingDetails(hovered, event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
                return;
            }
            if (event.x >= getPositionX() + CPU_X && event.x < getPositionX() + CPU_TRACK_X + ComputationScrollbar.WIDTH
                && event.y >= getPositionY() + CPU_Y && event.y < getPositionY() + CPU_Y + ROWS * CPU_STRIDE) {
                scrollCpuTo(cpuOffset + (event.deltaY < 0 ? ROWS / 3 : -ROWS / 3));
                event.stopImmediatePropagation();
            } else if (event.x >= getPositionX() + ITEM_X && event.x < getPositionX() + ITEM_TRACK_X + ComputationScrollbar.WIDTH
                && event.y >= getPositionY() + ITEM_Y && event.y < getPositionY() + ITEM_Y + ITEM_ROWS * ITEM_STRIDE) {
                scrollItemsTo(itemOffset + (event.deltaY < 0 ? ITEM_COLS : -ITEM_COLS));
                event.stopImmediatePropagation();
            }
        }, true);

        Button back = new BackButton().noText()
            .setOnClick(event -> { stopProcessingRequest.run(); backAction.run(); });
        back.setId("computation-status-back");
        back.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(302).top(-5).width(20).height(20).paddingAll(0));
        back.setOverflowVisible(true);
        HostElements.tooltips(back,
            Component.translatable("gui.neoecoae.computation.settings.back"));
        pauseButton.setId("computation-status-pause");
        cancelButton.setId("computation-status-cancel");
        pauseButton.setText(Component.translatable("gui.neoecoae.computation.pause"))
            .setOnClick(event -> { if (event.button == 0) cpuAction.send(selectedSerial, 1); });
        placeButton(pauseButton, 191, HEIGHT - 25);
        cancelButton.setText(GuiText.Cancel.text())
            .setOnClick(event -> { if (event.button == 0) cpuAction.send(selectedSerial, 2); });
        placeButton(cancelButton, 251, HEIGHT - 25);
        pauseButton.setActive(false);
        cancelButton.setActive(false);
        addChildren(back, pauseButton, cancelButton);
    }

    void setBackAction(Runnable action) { backAction = action; }

    @Override public UIElement hitTest(double x, double y) {
        var target = super.hitTest(x, y);
        if (target == null && isDisplayed() && isVisible() && itemActionsPopup != null && itemActionsPopup.isDisplayed()) return this;
        return target;
    }

    void open(int serial) {
        itemScrollPending = false;
        hoveredProcessingKey = requestedProcessingKey = null;
        if (selectedSerial != serial) processingByKey.clear();
        itemActionsPopup.close();
        selectedSerial = serial;
        // Opening an already-selected CPU must still enable the server's progress subscription.
        selectionRequest.accept(serial);
    }

    @Override
    public ComputationCpuStatusPanel setValue(CompoundTag value, boolean notify) {
        super.setValue(value, notify);
        if (value == null || !value.contains("total")) return this;
        total = value.getInt("total");
        int receivedSerial = value.getInt("selectedSerial");
        if (receivedSerial != selectedSerial) processingByKey.clear();
        selectedSerial = receivedSerial;
        itemTotal = value.getInt("itemTotal");
        cpuOffset = JdkCompat.clamp(cpuOffset, 0, Math.max(0, total - ROWS));
        itemOffset = JdkCompat.clamp(itemOffset / ITEM_COLS, 0, maxItemRows(itemTotal)) * ITEM_COLS;
        int receivedCpuOffset = value.getInt("cpuOffset");
        if (!cpuScrollPending || value.getLong("cpuScrollSequence") == cpuScrollSequence) {
            cpuOffset = cpuPageOffset = receivedCpuOffset;
            cpus = value.getList("cpus", Tag.TAG_COMPOUND).stream()
                .map(tag -> ComputationCpuEntry.read((CompoundTag) tag, config.registries())).toList();
            cpuPage.accept(cpuOffset, value.contains("cpuPageStart") ? value.getInt("cpuPageStart") : cpuOffset,
                cpus);
            cpuScrollPending = false;
            cpuScrollRequest.setValue(scrollTag(cpuScrollSequence, cpuOffset), false);
        }
        int receivedItemOffset = value.getInt("itemOffset");
        if (!itemScrollPending || value.getLong("itemScrollSequence") == itemScrollSequence) {
            itemOffset = itemPageOffset = receivedItemOffset;
            items = value.getList("items", Tag.TAG_COMPOUND).stream()
                .map(tag -> ComputationCpuItemEntry.read((CompoundTag) tag, config.registries())).toList();
            itemPage.accept(itemOffset, value.contains("itemPageStart") ? value.getInt("itemPageStart") : itemOffset,
                items);
            itemScrollPending = false;
            itemScrollRequest.setValue(scrollTag(itemScrollSequence, itemOffset), false);
        }
        selected = value.contains("selected")
            ? ComputationCpuEntry.read(value.getCompound("selected"), config.registries()) : null;
        cpuScrollbar.update(cpuOffset, total - ROWS, ROWS / 3);
        itemScrollbar.update(itemOffset / ITEM_COLS, maxItemRows(itemTotal), 1);
        boolean busy = selected != null && !selected.status().equals("idle");
        pauseButton.setActive(busy && !selected.status().equals("error") && !selected.status().equals("returning"));
        cancelButton.setActive(busy);
        pauseButton.setText(selected != null && selected.status().equals("suspended") ? Component.translatable("gui.neoecoae.computation.resume") : Component.translatable("gui.neoecoae.computation.pause"));
        return this;
    }

    private UIElement cpuHitbox() {
        UIElement hitbox = hitbox(CPU_X, CPU_Y, CPU_WIDTH, ROWS * CPU_STRIDE - 1);
        hitbox.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button != 0) return;
            ComputationCpuEntry entry = cpuPage.hit(event.y - getPositionY() - CPU_Y, 0, CPU_STRIDE, CPU_HEIGHT);
            if (entry == null) return;
            open(entry.serial());
            event.stopImmediatePropagation();
        });
        hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            ComputationCpuEntry entry = cpuPage.hit(event.y - getPositionY() - CPU_Y, 0, CPU_STRIDE, CPU_HEIGHT);
            if (entry != null) {
                event.hoverTooltips = HoverTooltips.empty().append(
                    ComputationCpuSelector.tooltip(entry).toArray(Component[]::new));
            }
        });
        return hitbox;
    }

    private UIElement itemHitbox() {
        UIElement hitbox = hitbox(ITEM_X, ITEM_Y, ITEM_COLS * ITEM_COLUMN_STRIDE - 1, ITEM_ROWS * ITEM_STRIDE - 1);
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 1) return;
            var item = itemAt(event.x, event.y);
            if (item == null) return;
            itemActionsPopup.open(item, event.x - getPositionX() + 2, event.y - getPositionY() + 2);
            event.stopImmediatePropagation();
        });
        hitbox.addEventListener(UIEvents.MOUSE_UP, event -> { if (event.button == 1) event.stopImmediatePropagation(); });
        hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            ComputationCpuItemEntry item = itemAt(event.x, event.y);
            if (item == null) return;
            if (item.processing().machineCount() > 0 || item.processing().refreshing()) requestProcessingDetails(item, 0);
            List<Component> lines = new ArrayList<>(AEKeyRendering.getTooltip(item.key()));
            lines.addAll(itemDescription(item, true));
            if (item.stepStopped()) lines.add(Component.translatable("gui.neoecoae.processing.step_stopped").withStyle(ChatFormatting.GOLD));
            if (item.batched()) {
                lines.add(Component.translatable("gui.neoecoae.crafting.smart_doubling").withStyle(neColor -> neColor.withColor(0xC7A6FF)));
            }
            event.hoverTooltips = ComputationProcessingTooltips.create(lines, item);
        });
        return hitbox;
    }

    private ComputationCpuItemEntry itemAt(float mouseX, float mouseY) {
        float localX = mouseX - getPositionX() - ITEM_X;
        float localY = mouseY - getPositionY() - ITEM_Y;
        if (localX < 0 || localX >= ITEM_COLS * ITEM_COLUMN_STRIDE - 1 || localY < 0 || localY >= ITEM_ROWS * ITEM_STRIDE - 1) return null;
        int column = (int) (localX / ITEM_COLUMN_STRIDE);
        if (localX - column * ITEM_COLUMN_STRIDE >= ITEM_WIDTH) return null;
        var item = itemPage.hit(localY, column, ITEM_STRIDE, ITEM_HEIGHT);
        return item == null ? null : withProcessing(item);
    }

    private ComputationCpuItemEntry withProcessing(ComputationCpuItemEntry item) {
        if (item.active().signum() <= 0) return item;
        var progress = processingByKey.get(item.key());
        return progress == null ? item : item.withProcessing(progress);
    }

    private void requestProcessingDetails(ComputationCpuItemEntry item, int pageDelta) {
        if (!item.key().equals(hoveredProcessingKey)) {
            hoveredProcessingKey = item.key();
            hoveredProcessingPage = Math.max(0, item.processing().detailPage());
        }
        hoveredProcessingPage = JdkCompat.clamp(hoveredProcessingPage + pageDelta, 0, item.processing().pages() - 1);
        long now = net.minecraft.Util.getMillis();
        boolean changed = !item.key().equals(requestedProcessingKey) || hoveredProcessingPage != requestedProcessingPage;
        if (now < nextProcessingRequest || (!changed && item.processing().detailPage() == hoveredProcessingPage)) return;
        processingDetailsRequest.accept(item.key(), hoveredProcessingPage);
        requestedProcessingKey = item.key();
        requestedProcessingPage = hoveredProcessingPage;
        nextProcessingRequest = now + 250;
    }

    private void scrollCpuTo(int value) {
        int next = JdkCompat.clamp(value, 0, Math.max(0, total - ROWS));
        if (next == cpuOffset) return;
        cpuOffset = next;
        cpuScrollPending = true;
        cpuScrollbar.update(cpuOffset, total - ROWS, ROWS / 3);
        cpuScrollRequest.setValue(scrollTag(++cpuScrollSequence, cpuOffset));
    }

    private void scrollItemsTo(int value) {
        int next = JdkCompat.clamp(value / ITEM_COLS, 0, maxItemRows(itemTotal)) * ITEM_COLS;
        if (next == itemOffset) return;
        itemOffset = next;
        itemScrollPending = true;
        itemScrollbar.update(itemOffset / ITEM_COLS, maxItemRows(itemTotal), 1);
        itemScrollRequest.setValue(scrollTag(++itemScrollSequence, itemOffset));
    }

    private static CompoundTag scrollTag(long sequence, int offset) {
        var tag = new CompoundTag();
        tag.putLong("sequence", sequence);
        tag.putInt("offset", offset);
        return tag;
    }

    private static int maxItemRows(int total) {
        return Math.max(0, (total + ITEM_COLS - 1) / ITEM_COLS - ITEM_ROWS);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        Font font = Minecraft.getInstance().font;
        float x = getPositionX(), y = getPositionY();
        context.graphics.flush();
        context.graphics.drawString(font, GuiText.CraftingCPUs.text(), Math.round(x + CPU_X), Math.round(y + 7), 0xFF000000 | TEXT, false);
        context.graphics.drawString(font, title(selected, itemTotal > 0), Math.round(x + ITEM_X - 1), Math.round(y + 7), 0xFF000000 | TEXT, false);
        // Wait for acknowledged data, then move both viewports independently through their buffered rows.
        cpuPage.advance();
        itemPage.advance();
        context.enableScissor(x + CPU_X, y + CPU_Y, CPU_WIDTH, ROWS * CPU_STRIDE - 1);
        try {
            for (int index = 0; index < cpuPage.size(); index++) {
                double row = cpuPage.row(index);
                if (row > -1 && row < ROWS) drawCpu(context, font, cpuPage.get(index),
                    x + CPU_X, y + CPU_Y + (float) row * CPU_STRIDE);
            }
        } finally {
            context.graphics.flush();
            context.disableScissor();
        }
        context.enableScissor(x + ITEM_X, y + ITEM_Y, ITEM_COLS * ITEM_COLUMN_STRIDE - 1, ITEM_ROWS * ITEM_STRIDE - 1);
        try {
            for (int index = 0; index < itemPage.size(); index++) {
                double row = itemPage.row(index);
                if (row > -1 && row < ITEM_ROWS) drawItem(context, font, withProcessing(itemPage.get(index)),
                    x + ITEM_X + itemPage.column(index) * ITEM_COLUMN_STRIDE,
                    y + ITEM_Y + (float) row * ITEM_STRIDE, AEConfig.instance().isUseColoredCraftingStatus());
            }
        } finally {
            context.graphics.flush();
            context.disableScissor();
        }
    }

    static Component title(ComputationCpuEntry cpu, boolean hasItems) {
        var title = GuiText.CraftingStatus.text();
        if (cpu == null) return title;
        BigInteger completed = cpu.requested().subtract(cpu.remaining()).max(BigInteger.ONE);
        BigInteger eta = BigInteger.valueOf(cpu.elapsed()).multiply(cpu.remaining()).divide(completed);
        if (hasItems && eta.signum() > 0) {
            long nanos = eta.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            title.append(" - " + DurationFormatUtils.formatDuration(
                TimeUnit.NANOSECONDS.toMillis(nanos), GuiText.ETAFormat.getLocal()));
        }
        if (cpu.status().equals("returning")) {
            title.append(" - ").append(GuiText.CantStoreItems.text().withStyle(ChatFormatting.RED));
        }
        return title;
    }

    @Override
    protected void onRemoved() {
        super.onRemoved();
        opening.reset();
        cpuPage.reset();
        itemPage.reset();
    }

    private void drawCpu(GUIContext context, Font font, ComputationCpuEntry entry, float x, float y) {
        ComputationCpuSelector.drawCpuRow(context, font, entry, x, y, entry.serial() == selectedSerial,
            CPU_NORMAL, CPU_SELECTED, opening.value(entry.progress()));
    }

    static void drawItem(GUIContext context, Font font, ComputationCpuItemEntry entry, float x, float y,
            boolean colored) {
        if (colored) {
            int color = entry.active().signum() > 0
                ? (entry.batched() ? AEColor.PURPLE : AEColor.GREEN).blackVariant
                : entry.pending().signum() > 0 ? AEColor.YELLOW.blackVariant : 0;
            if (color != 0) context.graphics.fill(Math.round(x), Math.round(y), Math.round(x + ITEM_WIDTH),
                Math.round(y + ITEM_HEIGHT), color | 0x5A000000);
        }
        if (entry.batched()) {
            context.graphics.blit(BATCH_ICON, Math.round(x + BATCH_ICON_LEFT),
                Math.round(y + (ITEM_HEIGHT - BATCH_ICON_SIZE) / 2F), 0, 0,
                BATCH_ICON_SIZE, BATCH_ICON_SIZE, BATCH_ICON_SIZE, BATCH_ICON_SIZE);
        }
        key(context, entry.key(), x + ITEM_WIDTH - 19, y + 3, 1.0F);
        drawItemDescription(context, font, itemDescription(entry, false), x, y);
        drawProcessingProgress(context, entry, x, y);
    }

    static void drawProcessingProgress(GUIContext context, ComputationCpuItemEntry entry, float x, float y) {
        if (entry.active().signum() <= 0) return;
        int left = Math.round(x + 2), right = Math.round(x + ITEM_WIDTH - 2);
        int top = Math.round(y + ITEM_HEIGHT - 3);
        // Once inputs have been dispatched, keep the track visible while machine progress is unavailable.
        context.graphics.fill(left, top, right, top + 2, 0xFF5B566B);
        if (!entry.processing().hasProgress()) return;
        int color = switch (entry.processing().state()) {
            case WAITING -> 0xFFE2AA4F;
            case PAUSED -> 0xFF90909F;
            default -> 0xFFC7A6FF;
        };
        int filled = Math.round((right - left) * entry.processing().progress());
        if (filled > 0) context.graphics.fill(left, top, left + filled, top + 2, color);
    }

    static void drawItemDescription(GUIContext context, Font font, List<Component> lines, float x, float y) {
        float scale = 0.5F;
        float height = lines.size() * font.lineHeight * scale + Math.max(0, lines.size() - 1);
        float top = Math.round(y + (ITEM_HEIGHT - height) / 2);
        for (int line = 0; line < lines.size(); line++) {
            Component value = lines.get(line);
            context.graphics.pose().pushPose();
            context.graphics.pose().translate(x + ITEM_WIDTH - 21 - font.width(value) * scale,
                top + line * (font.lineHeight * scale + 1), 0);
            context.graphics.pose().scale(scale, scale, 1);
            context.graphics.drawString(font, value, 0, 0, 0xFF000000 | TEXT, false);
            context.graphics.pose().popPose();
        }
    }

    private static List<Component> itemDescription(ComputationCpuItemEntry item, boolean full) {
        List<Component> lines = new ArrayList<>();
        if (item.stored().signum() > 0) lines.add(GuiText.FromStorage.text(item.amount(item.stored(), full)));
        if (item.active().signum() > 0) lines.add(GuiText.Crafting.text(item.amount(item.active(), full)));
        if (item.pending().signum() > 0) lines.add(GuiText.Scheduled.text(item.amount(item.pending(), full)));
        return lines;
    }

    private static void key(GUIContext context, appeng.api.stacks.AEKey key, float x, float y, float scale) {
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y, 0);
        context.graphics.pose().scale(scale, scale, 1);
        AEKeyRendering.drawInGui(Minecraft.getInstance(), context.graphics, 0, 0, key);
        context.graphics.pose().popPose();
    }

    private static UIElement hitbox(int x, int y, int width, int height) {
        return new UIElement().layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }

    private static void placeButton(Button button, int x, int y) {
        button.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(50).height(20).paddingAll(0));
    }

    private static final class BackButton extends Button {
        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            // AE2's box tab changes only with focus and draws both sprites at native size.
            ECOIcon background = isFocused() ? ECOIcon.TAB_BUTTON_BACKGROUND_FOCUS : ECOIcon.TAB_BUTTON_BACKGROUND;
            context.drawTexture(AETextures.icon(background), getPositionX(), getPositionY(), background.width, background.height);
            context.drawTexture(AETextures.icon(ECOIcon.BACK), getPositionX() + 2, getPositionY() + 1, 16, 16);
        }
    }

    private static final class StatusButton extends Button {
        private Component label = Component.empty();

        private StatusButton() { noText(); }

        @Override
        public Button setText(Component label) {
            this.label = label;
            return this;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            IGuiTexture texture = !isActive() ? NETextures.AE2_BUTTON_DISABLED
                : getState() == State.DEFAULT ? NETextures.AE2_BUTTON : NETextures.AE2_BUTTON_HIGHLIGHTED;
            context.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }

        @Override
        public void drawContents(GUIContext context) {
            super.drawContents(context);
            int x = Math.round(getPositionX()), y = Math.round(getPositionY());
            int offset = !isActive() ? -1 : getState() == State.DEFAULT ? 1 : 0;
            int color = !isActive() ? TEXT : getState() == State.DEFAULT ? 0xF2F2F2 : 0x517497;
            context.graphics.flush();
            NEButtonText.renderButtonText(context.graphics, Minecraft.getInstance().font, label,
                x + 2, y, x + Math.round(getSizeWidth()) - 2, y + Math.round(getSizeHeight()), offset, 0xFF000000 | color);
        }
    }

    private static final class ServerState {
        private final ComputationControllerUI.Config config;
        private final CpuSelectionState<ECOCraftingCPU> selection;
        private CompoundTag cached;
        private long sampledTick = Long.MIN_VALUE;
        private int itemOffset;
        private long cpuScrollSequence, itemScrollSequence;
        private boolean viewing;
        private AEKey processingDetailKey;
        private int processingDetailPage;
        private List<ComputationCpuItemEntry> processingItems = List.of();
        private CompoundTag processingCached;
        private long processingSyncTick = Long.MIN_VALUE;
        private CompoundTag itemActionResponse = new CompoundTag();

        private ServerState(ComputationControllerUI.Config config, CpuSelectionState.Identities<ECOCraftingCPU> identities) {
            this.config = config;
            selection = new CpuSelectionState<>(ROWS, identities);
        }

        private void refresh() {
            selection.update(config.cpus().get(), ECOCraftingCPU::isBusy,
                cpu -> cpu.getName() == null ? null : cpu.getName().getString());
        }

        private void select(int serial) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            if (!selection.select(serial)) return;
            viewing = true;
            processingDetailKey = null;
            processingItems = List.of();
            processingCached = null;
            itemOffset = 0;
            cached = null;
        }

        private void scrollCpu(CompoundTag request) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            selection.scroll(request.getInt("offset"));
            cpuScrollSequence = request.getLong("sequence");
            cached = null;
        }

        private void scrollItems(CompoundTag request) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            int total = selectedItems(selection.selected()).size();
            itemOffset = JdkCompat.clamp(request.getInt("offset") / ITEM_COLS, 0, maxItemRows(total)) * ITEM_COLS;
            itemScrollSequence = request.getLong("sequence");
            cached = null;
            processingCached = null;
        }

        private void action(int serial, int action) {
            if (!config.canInteract().getAsBoolean()) return;
            refresh();
            // Carry the target in this request; selection synchronization may arrive on a later tick.
            if (!selection.select(serial)) return;
            ECOCraftingCPU cpu = selection.selected();
            if (action == 1) config.toggleCpuSuspended().accept(cpu);
            if (action == 2) config.cancelCpu().accept(cpu);
            cached = null;
        }

        private void processingDetails(int serial, CompoundTag request) {
            if (!config.canInteract().getAsBoolean()) return;
            if (serial < 0) {
                viewing = false;
                processingDetailKey = null;
                processingCached = null;
                return;
            }
            if (serial != selection.selectedSerial() || selection.selected() == null) return;
            AEKey key = AEKey.fromTagGeneric(request.getCompound("key"));
            if (key == null || selection.selected().getLogic().getWaitingFor(key) <= 0) return;
            viewing = true;
            processingDetailKey = key;
            processingDetailPage = Math.max(0, request.getInt("page"));
            processingCached = null;
        }

        private void itemAction(int serial, CompoundTag request) {
            if (!config.canInteract().getAsBoolean()) return;
            var response = new CompoundTag();
            response.putInt("serial", serial);
            response.putLong("sequence", request.getLong("sequence"));
            response.put("key", request.getCompound("key").copy());
            itemActionResponse = response;
            var cpu = selection.selected();
            AEKey key = AEKey.fromTagGeneric(request.getCompound("key"));
            if (cpu == null || serial != selection.selectedSerial() || key == null) {
                response.putString("error", "gui.neoecoae.processing.task_changed");
                return;
            }
            var logic = cpu.getLogic();
            var job = logic.getProcessingJobId();
            String action = request.getString("action");
            if (job == null || (!action.equals("inspect") && (!request.hasUUID("job") || !job.equals(request.getUUID("job"))))) {
                response.putString("error", "gui.neoecoae.processing.task_changed");
                return;
            }
            response.putUUID("job", job);
            var grid = cpu.getGrid();
            if (action.equals("stop") || action.equals("resume")) {
                if (logic.setProcessingStepStopped(key, action.equals("stop"))) {
                    response.putBoolean("close", true);
                    cached = null;
                } else response.putString("error", "gui.neoecoae.processing.already_dispatched");
            }
            java.util.List<cn.dancingsnow.neoecoae.compat.ae2.ECOProviderActions.Source> sources = java.util.List.of();
            if (grid != null && grid.getCraftingService() instanceof appeng.me.service.CraftingService crafting) {
                sources = cn.dancingsnow.neoecoae.compat.ae2.ECOProviderActions.find(logic.getProcessingPatterns(key), crafting);
            }
            var list = new ListTag();
            for (var source : sources) list.add(source.describe());
            response.put("sources", list);
            logic.getProcessingProgress(key);
            var machines = cn.dancingsnow.neoecoae.compat.ae2.ECOProviderActions.findMachines(
                logic.getProcessingMonitor().machineTargets(key), cpu.getLevel());
            var machineList = new ListTag();
            for (var machine : machines) machineList.add(machine.describe());
            response.put("machines", machineList);
            response.putBoolean("stopped", logic.isProcessingStepStopped(key));
            response.putBoolean("canStop", logic.canControlProcessingStep(key));
            if (action.equals("open")) {
                var target = sources.stream().filter(source -> source.id().equals(request.getString("source"))).findFirst();
                try {
                    if (!(config.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        || target.isEmpty() || !target.get().open(player)) response.putString("error", "gui.neoecoae.processing.open_unavailable");
                } catch (RuntimeException | LinkageError unsupported) {
                    response.putString("error", "gui.neoecoae.processing.open_unavailable");
                }
            }
            if (action.equals("open_machine")) {
                var target = machines.stream().filter(machine -> machine.id().equals(request.getString("machine"))).findFirst();
                try {
                    if (!(config.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        || target.isEmpty() || !target.get().openMachine(player)) {
                        response.putString("error", "gui.neoecoae.processing.machine_open_unavailable");
                    } else response.putBoolean("close", true);
                } catch (RuntimeException | LinkageError unsupported) {
                    response.putString("error", "gui.neoecoae.processing.machine_open_unavailable");
                }
            }
        }

        private CompoundTag processingSnapshot() {
            long tick = config.gameTime().getAsLong();
            var cpu = selection.selected();
            boolean active = viewing && config.canInteract().getAsBoolean() && cpu != null;
            if (active) cpu.getLogic().refreshProcessingProgress();
            if (processingCached != null && tick >= processingSyncTick && tick - processingSyncTick < 10) return processingCached;
            processingSyncTick = tick;
            var tag = new CompoundTag();
            tag.putInt("serial", selection.selectedSerial());
            var entries = new ListTag();
            if (active) {
                for (var item : processingItems) {
                    if (item.active().signum() <= 0) continue;
                    var progress = cpu.getLogic().getProcessingProgress(item.key());
                    if (progress == null || (progress.machineCount() == 0 && !progress.refreshing())) continue;
                    var entry = new CompoundTag();
                    entry.put("key", item.key().toTagGeneric());
                    entry.put("progress", progress.page(item.key().equals(processingDetailKey) ? processingDetailPage : -1).write());
                    entries.add(entry);
                }
            }
            tag.put("items", entries);
            return processingCached = tag;
        }

        private CompoundTag snapshot() {
            long tick = config.gameTime().getAsLong();
            if (cached != null && tick >= sampledTick && tick - sampledTick < 3) return cached;
            sampledTick = tick;
            refresh();
            CompoundTag tag = new CompoundTag();
            tag.putInt("total", selection.size());
            tag.putInt("cpuOffset", selection.offset());
            tag.putLong("cpuScrollSequence", cpuScrollSequence);
            tag.putLong("itemScrollSequence", itemScrollSequence);
            tag.putInt("selectedSerial", selection.selectedSerial());
            var registries = config.registries();
            ListTag cpuPage = new ListTag();
            tag.putInt("cpuPageStart", Math.max(0, selection.offset() - ROWS));
            for (ECOCraftingCPU cpu : selection.bufferedPage()) cpuPage.add(ComputationCpuEntry.sample(cpu, selection.serial(cpu)).write(registries));
            tag.put("cpus", cpuPage);
            ECOCraftingCPU selectedCpu = selection.selected();
            if (selectedCpu != null) {
                ComputationCpuEntry selectedEntry = ComputationCpuEntry.sample(selectedCpu, selection.selectedSerial());
                tag.put("selected", selectedEntry.write(registries));
                List<ComputationCpuItemEntry> all = selectedItems(selectedCpu);
                tag.putInt("itemTotal", all.size());
                itemOffset = JdkCompat.clamp(itemOffset / ITEM_COLS, 0, maxItemRows(all.size())) * ITEM_COLS;
                tag.putInt("itemOffset", itemOffset);
                int itemPageStart = Math.max(0, itemOffset - ITEM_PAGE);
                tag.putInt("itemPageStart", itemPageStart);
                ListTag itemPage = new ListTag();
                processingItems = all.subList(itemPageStart, Math.min(all.size(), itemOffset + ITEM_PAGE * 2));
                for (ComputationCpuItemEntry item : processingItems) {
                    itemPage.add(item.write(registries));
                }
                tag.put("items", itemPage);
            } else {
                processingItems = List.of();
                tag.putInt("itemTotal", 0);
                tag.putInt("itemOffset", 0);
                tag.put("items", new ListTag());
            }
            return cached = tag;
        }

        private List<ComputationCpuItemEntry> selectedItems(ECOCraftingCPU cpu) {
            if (cpu == null) return List.of();
            var logic = cpu.getLogic();
            Map<appeng.api.stacks.AEKey, BigInteger> stored = new HashMap<>(logic.getExactStoredPreview());
            Map<appeng.api.stacks.AEKey, BigInteger> active = new HashMap<>(logic.getExactActivePreview());
            Map<appeng.api.stacks.AEKey, BigInteger> pending = new HashMap<>(logic.getExactPendingPreview());
            Set<appeng.api.stacks.AEKey> keys = new HashSet<>();
            appeng.api.stacks.KeyCounter all = new appeng.api.stacks.KeyCounter();
            logic.getAllItems(all);
            all.forEach(entry -> keys.add(entry.getKey()));
            keys.addAll(stored.keySet());
            keys.addAll(active.keySet());
            keys.addAll(pending.keySet());
            return keys.stream().map(key -> itemForKey(logic, key, stored, active, pending))
                .filter(item -> item.stored().signum() != 0 || item.active().signum() != 0 || item.pending().signum() != 0)
                .sorted(Comparator.comparing(item -> item.key().getDisplayName().getString()))
                .toList();
        }

        private ComputationCpuItemEntry itemForKey(cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic logic,
                appeng.api.stacks.AEKey key, Map<appeng.api.stacks.AEKey, BigInteger> stored,
                Map<appeng.api.stacks.AEKey, BigInteger> active, Map<appeng.api.stacks.AEKey, BigInteger> pending) {
            BigInteger storedAmount = stored.containsKey(key) ? stored.get(key)
                : BigInteger.valueOf(Math.max(0L, logic.getStored(key)));
            BigInteger activeAmount = active.containsKey(key) ? active.get(key)
                : BigInteger.valueOf(Math.max(0L, logic.getWaitingFor(key)));
            BigInteger pendingAmount = pending.containsKey(key) ? pending.get(key)
                : BigInteger.valueOf(Math.max(0L, logic.getPendingOutputs(key)));
            return new ComputationCpuItemEntry(key, storedAmount, activeAmount, pendingAmount,
                logic.isBatchedOutput(key), ECOProcessingProgress.EMPTY, logic.isProcessingStepStopped(key));
        }
    }
}
