package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;

import appeng.api.client.AEKeyRendering;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.api.stacks.AEKey;
import appeng.api.util.AEColor;
import appeng.client.gui.widgets.AE2Button;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostProgressAnimation;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.Nullable;

/**
 * The 427x240 ECO crafting status page. A: CPU list, B: crafting status in AE2 order, C: running tasks in
 * start order, D: search that moves matches to B's first cell, E: AE2's suspend/cancel controls.
 */
final class ComputationCpuStatusPanel extends BindableValue<CompoundTag> {
    private final HostProgressAnimation opening = new HostProgressAnimation();
    static final int WIDTH = 427, HEIGHT = 240, ROWS = 7;
    private static final int ROW_WIDTH = 67, ROW_HEIGHT = 22, ROW_STRIDE = 23, LIST_Y = 27;
    private static final int TRACK_Y = 26, TRACK_HEIGHT = 162;
    private static final int CPU_X = 11, CPU_TRACK_X = 89;
    private static final int ITEM_X = 105, ITEM_WIDTH = ROW_WIDTH, ITEM_HEIGHT = ROW_HEIGHT, ITEM_COLUMN_STRIDE = 68;
    private static final int ITEM_COLS = 3, ITEM_ROWS = ROWS, ITEM_STRIDE = ROW_STRIDE, ITEM_PAGE = ITEM_COLS * ITEM_ROWS;
    private static final int ITEM_TRACK_X = 317;
    private static final int TASK_X = 333, TASK_TRACK_X = 411;
    private static final int SEARCH_X = 333, SEARCH_Y = 9, SEARCH_WIDTH = 67, SEARCH_HEIGHT = 10;
    private static final int TIER_X = 134, TIER_Y = 5, TIER_WIDTH = 13, TIER_HEIGHT = 18;
    private static final int FOOTER_X = 97, FOOTER_Y = 214, FOOTER_WIDTH = 233;
    private static final int TEXT = 0x413F54;
    private static final int BATCH_ICON_SIZE = 16, BATCH_ICON_LEFT = -1;
    private static final ResourceLocation BATCH_ICON =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/computation/eco_batching.png");
    private static final ResourceLocation ATLAS =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/guis/eco_crafting_processing.png");
    private static final int ATLAS_WIDTH = 432, ATLAS_HEIGHT = 288;
    // The two row states sit in the lower-left notch of the 427x240 panel, so the panel is drawn in two slices.
    private static final IGuiTexture BACKGROUND_UPPER = sprite(0, 0, WIDTH, 214);
    private static final IGuiTexture BACKGROUND_LOWER = sprite(95, 214, 237, 26);
    private static final int ROW_NORMAL_U = 11, ROW_NORMAL_V = 215, ROW_SELECTED_U = 11, ROW_SELECTED_V = 237;
    private static final IGuiTexture ROW_NORMAL = sprite(ROW_NORMAL_U, ROW_NORMAL_V, ROW_WIDTH, ROW_HEIGHT);
    private static final IGuiTexture ROW_SELECTED = sprite(ROW_SELECTED_U, ROW_SELECTED_V, ROW_WIDTH, ROW_HEIGHT);
    private static final IGuiTexture TIER_COVER = sprite(132, TIER_Y, 1, TIER_HEIGHT);

    /** The server-side capabilities the page needs; the controller and the AE2 takeover supply their own. */
    record Source(@Nullable Player player, LongSupplier gameTime, BooleanSupplier canInteract,
            Supplier<List<? extends ICraftingCPU>> cpus, Consumer<ICraftingCPU> toggleCpuSuspended,
            Consumer<ICraftingCPU> cancelCpu, @Nullable Runnable serverBack) {
        HolderLookup.Provider registries() {
            return player == null ? RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                : player.registryAccess();
        }

        static Source of(ComputationControllerUI.Config config) {
            return new Source(config.player(), config.gameTime(), config.canInteract(), () -> config.cpus().get(),
                cpu -> { if (cpu instanceof ECOCraftingCPU eco) config.toggleCpuSuspended().accept(eco); },
                cpu -> { if (cpu instanceof ECOCraftingCPU eco) config.cancelCpu().accept(eco); }, null);
        }
    }

    private final Source source;
    private final BindableValue<Integer> selectionRequest = new BindableValue<>(-1);
    private final BindableValue<CompoundTag> cpuScrollRequest = new BindableValue<>(new CompoundTag());
    private final BindableValue<CompoundTag> itemScrollRequest = new BindableValue<>(new CompoundTag());
    private final BindableValue<CompoundTag> taskScrollRequest = new BindableValue<>(new CompoundTag());
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
    private final ComputationScrollbar taskScrollbar;
    private final ComputationScrollPage<ComputationCpuEntry> cpuPage = new ComputationScrollPage<>(ROWS, 1);
    private final ComputationScrollPage<ComputationCpuItemEntry> itemPage = new ComputationScrollPage<>(ITEM_ROWS, ITEM_COLS);
    private final ComputationScrollPage<ComputationCpuItemEntry> taskPage = new ComputationScrollPage<>(ROWS, 1);
    private ComputationCpuEntry selected;
    private int selectedSerial = -1, total, cpuOffset, itemTotal, itemOffset, taskTotal, taskOffset;
    private int tier;
    private boolean tierBuiltin = true;
    private Component tierName = Component.empty();
    private boolean cpuScrollPending, itemScrollPending, taskScrollPending;
    private long cpuScrollSequence, itemScrollSequence, taskScrollSequence;
    private Runnable backAction = () -> {};

    ComputationCpuStatusPanel(ComputationControllerUI.Config config, CpuSelectionState.Identities<ICraftingCPU> identities) {
        this(Source.of(config), identities);
    }

    ComputationCpuStatusPanel(Source source, CpuSelectionState.Identities<ICraftingCPU> identities) {
        super(new CompoundTag());
        this.source = source;
        layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0));
        setOverflowVisible(true);
        ServerState server = new ServerState(source, identities);
        var cpuAction = addRPCEvent(RPCEventBuilder.simple(Integer.class, Integer.class, server::action));
        var itemAction = addRPCEvent(RPCEventBuilder.simple(Integer.class, CompoundTag.class, server::itemAction));
        itemActionsPopup = new ComputationItemActionsPopup(source.registries(), request -> itemAction.send(selectedSerial, request));
        bind(DataBindingBuilder.create(server::snapshot, ignored -> {})
            .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        selectionRequest.bind(DataBindingBuilder.intValC2S(server::select).build());
        cpuScrollRequest.bind(DataBindingBuilder.<CompoundTag>create(CompoundTag::new, server::scrollCpu)
            .syncType(CompoundTag.class).s2cStrategy(SyncStrategy.NONE).build());
        itemScrollRequest.bind(DataBindingBuilder.<CompoundTag>create(CompoundTag::new, server::scrollItems)
            .syncType(CompoundTag.class).s2cStrategy(SyncStrategy.NONE).build());
        taskScrollRequest.bind(DataBindingBuilder.<CompoundTag>create(CompoundTag::new, server::scrollTasks)
            .syncType(CompoundTag.class).s2cStrategy(SyncStrategy.NONE).build());
        itemActionResponse.bind(DataBindingBuilder.create(() -> server.itemActionResponse, ignored -> {})
            .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        selectionRequest.setDisplay(false);
        cpuScrollRequest.setDisplay(false);
        itemScrollRequest.setDisplay(false);
        taskScrollRequest.setDisplay(false);
        itemActionResponse.setDisplay(false);
        addChildren(selectionRequest, cpuScrollRequest, itemScrollRequest, taskScrollRequest, itemActionResponse);
        addChildren(cpuHitbox(), itemHitbox(), taskHitbox(), searchField(server));
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (itemActionsPopup.isDisplayed() && !itemActionsPopup.isMouseOverElement(event.x, event.y)) {
                itemActionsPopup.close();
                if (event.button != 1) event.stopImmediatePropagation();
            }
        }, true);
        cpuScrollbar = ComputationScrollbar.thin(this, CPU_TRACK_X, TRACK_Y, TRACK_HEIGHT, this::scrollCpuTo);
        itemScrollbar = ComputationScrollbar.thin(this, ITEM_TRACK_X, TRACK_Y, TRACK_HEIGHT,
            row -> scrollItemsTo(row * ITEM_COLS));
        taskScrollbar = ComputationScrollbar.thin(this, TASK_TRACK_X, TRACK_Y, TRACK_HEIGHT, this::scrollTasksTo);
        addChildren(cpuScrollbar, itemScrollbar, taskScrollbar);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (itemActionsPopup.isDisplayed()) {
                if (!itemActionsPopup.isMouseOverElement(event.x, event.y)) event.stopImmediatePropagation();
                return;
            }
            float localX = event.x - getPositionX(), localY = event.y - getPositionY();
            if (localY < LIST_Y || localY >= LIST_Y + ROWS * ROW_STRIDE) return;
            int direction = event.deltaY < 0 ? 1 : -1;
            if (localX >= CPU_X && localX < CPU_TRACK_X + ComputationScrollbar.THIN_WIDTH) {
                scrollCpuTo(cpuOffset + direction * (ROWS / 3));
            } else if (localX >= ITEM_X && localX < ITEM_TRACK_X + ComputationScrollbar.THIN_WIDTH) {
                scrollItemsTo(itemOffset + direction * ITEM_COLS);
            } else if (localX >= TASK_X && localX < TASK_TRACK_X + ComputationScrollbar.THIN_WIDTH) {
                scrollTasksTo(taskOffset + direction);
            } else {
                return;
            }
            event.stopImmediatePropagation();
        }, true);

        boolean takeover = source.serverBack() != null;
        Button back = new BackButton().noText()
            .setOnClick(event -> {
                itemActionsPopup.close();
                if (!takeover) backAction.run();
            });
        if (takeover) back.setOnServerClick(event -> source.serverBack().run());
        back.setId("computation-status-back");
        back.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(WIDTH - 22).top(-5).width(20).height(20).paddingAll(0));
        back.setOverflowVisible(true);
        HostElements.tooltips(back,
            Component.translatable("gui.neoecoae.computation.settings.back"));
        pauseButton.setId("computation-status-pause");
        cancelButton.setId("computation-status-cancel");
        pauseButton.setText(GuiText.Suspend.text())
            .setOnClick(event -> { if (event.button == 0) cpuAction.send(selectedSerial, 1); });
        placeButton(pauseButton, FOOTER_X + FOOTER_WIDTH - 106, FOOTER_Y + 1);
        cancelButton.setText(GuiText.Cancel.text())
            .setOnClick(event -> { if (event.button == 0) cpuAction.send(selectedSerial, 2); });
        placeButton(cancelButton, FOOTER_X + FOOTER_WIDTH - 54, FOOTER_Y + 1);
        pauseButton.setActive(false);
        cancelButton.setActive(false);
        addChildren(back, pauseButton, cancelButton, itemActionsPopup);
    }

    void setBackAction(Runnable action) { backAction = action; }

    @Override public boolean isIntersectWithPoint(double x, double y) {
        return super.isIntersectWithPoint(x, y) || itemActionsPopup != null && itemActionsPopup.isDisplayed();
    }

    void open(int serial) {
        itemScrollPending = false;
        taskScrollPending = false;
        itemActionsPopup.close();
        selectedSerial = serial;
        selectionRequest.setValue(serial);
    }

    private TextField searchField(ServerState server) {
        TextField search = new TextField();
        search.setId("computation-status-search");
        search.setAnyString();
        search.textFieldStyle(style -> style.textColor(0xF2F2F2).textShadow(false).fontSize(8)
            .placeholder(GuiText.SearchPlaceholder.text().withStyle(ChatFormatting.GRAY)));
        search.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        search.bind(DataBindingBuilder.stringC2S(server::search).build());
        search.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(SEARCH_X + 1).top(SEARCH_Y)
            .width(SEARCH_WIDTH - 2).height(SEARCH_HEIGHT).paddingAll(0));
        search.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            List.of(GuiText.SearchTooltip.text(), GuiText.SearchTooltipModId.text().withStyle(ChatFormatting.GRAY),
                GuiText.SearchTooltipItemId.text().withStyle(ChatFormatting.GRAY)), null, null, null));
        return search;
    }

    @Override
    public ComputationCpuStatusPanel setValue(CompoundTag value, boolean notify) {
        super.setValue(value, notify);
        if (value == null || !value.contains("total")) return this;
        total = value.getInt("total");
        if (selectedSerial != value.getInt("selectedSerial")) itemActionsPopup.close();
        selectedSerial = value.getInt("selectedSerial");
        itemTotal = value.getInt("itemTotal");
        taskTotal = value.getInt("taskTotal");
        tier = value.getInt("tier");
        tierBuiltin = value.getBoolean("tierBuiltin");
        tierName = Component.literal(value.getString("tierName"));
        cpuOffset = Math.clamp(cpuOffset, 0, Math.max(0, total - ROWS));
        itemOffset = Math.clamp(itemOffset / ITEM_COLS, 0, maxItemRows(itemTotal)) * ITEM_COLS;
        taskOffset = Math.clamp(taskOffset, 0, Math.max(0, taskTotal - ROWS));
        var registries = source.registries();
        if (!cpuScrollPending || value.getLong("cpuScrollSequence") == cpuScrollSequence) {
            cpuOffset = value.getInt("cpuOffset");
            cpuPage.accept(cpuOffset, value.getInt("cpuPageStart"), value.getList("cpus", Tag.TAG_COMPOUND).stream()
                .map(tag -> ComputationCpuEntry.read((CompoundTag) tag, registries)).toList());
            cpuScrollPending = false;
            cpuScrollRequest.setValue(scrollTag(cpuScrollSequence, cpuOffset), false);
        }
        if (!itemScrollPending || value.getLong("itemScrollSequence") == itemScrollSequence) {
            itemOffset = value.getInt("itemOffset");
            itemPage.accept(itemOffset, value.getInt("itemPageStart"), items(value.getList("items", Tag.TAG_COMPOUND)));
            itemScrollPending = false;
            itemScrollRequest.setValue(scrollTag(itemScrollSequence, itemOffset), false);
        }
        if (!taskScrollPending || value.getLong("taskScrollSequence") == taskScrollSequence) {
            taskOffset = value.getInt("taskOffset");
            taskPage.accept(taskOffset, value.getInt("taskPageStart"), items(value.getList("tasks", Tag.TAG_COMPOUND)));
            taskScrollPending = false;
            taskScrollRequest.setValue(scrollTag(taskScrollSequence, taskOffset), false);
        }
        selected = value.contains("selected")
            ? ComputationCpuEntry.read(value.getCompound("selected"), registries) : null;
        selectionRequest.setValue(selectedSerial, false);
        cpuScrollbar.update(cpuOffset, total - ROWS, ROWS / 3);
        itemScrollbar.update(itemOffset / ITEM_COLS, maxItemRows(itemTotal), 1);
        taskScrollbar.update(taskOffset, taskTotal - ROWS, 1);
        boolean busy = selected != null && !selected.status().equals("idle");
        pauseButton.setActive(busy && !selected.status().equals("error") && !selected.status().equals("returning"));
        cancelButton.setActive(busy);
        pauseButton.setText(selected != null && selected.status().equals("suspended") ? GuiText.Resume.text() : GuiText.Suspend.text());
        return this;
    }

    private List<ComputationCpuItemEntry> items(ListTag tags) {
        var registries = source.registries();
        return tags.stream().map(tag -> ComputationCpuItemEntry.read((CompoundTag) tag, registries)).toList();
    }

    private UIElement cpuHitbox() {
        UIElement hitbox = hitbox(CPU_X, LIST_Y, ROW_WIDTH, ROWS * ROW_STRIDE - 1);
        hitbox.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button != 0) return;
            ComputationCpuEntry entry = cpuPage.hit(event.y - getPositionY() - LIST_Y, 0, ROW_STRIDE, ROW_HEIGHT);
            if (entry == null) return;
            open(entry.serial());
            event.stopImmediatePropagation();
        });
        hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            // LDLib2's tooltip collection event has no mouse coordinates.
            var ui = getModularUI();
            if (ui == null) return;
            ComputationCpuEntry entry = cpuPage.hit(ui.getLastMouseY() - getPositionY() - LIST_Y, 0, ROW_STRIDE, ROW_HEIGHT);
            if (entry != null) {
                event.hoverTooltips = CraftingListCard.hover(ComputationCards.cpu(entry));
            }
        });
        return hitbox;
    }

    private UIElement itemHitbox() {
        UIElement hitbox = hitbox(ITEM_X, LIST_Y, ITEM_COLS * ITEM_COLUMN_STRIDE - 1, ITEM_ROWS * ITEM_STRIDE - 1);
        itemEvents(hitbox, this::itemAt);
        return hitbox;
    }

    private UIElement taskHitbox() {
        UIElement hitbox = hitbox(TASK_X, LIST_Y, ROW_WIDTH, ROWS * ROW_STRIDE - 1);
        itemEvents(hitbox, (mouseX, mouseY) -> taskPage.hit(mouseY - getPositionY() - LIST_Y, 0, ROW_STRIDE, ROW_HEIGHT));
        return hitbox;
    }

    private void itemEvents(UIElement hitbox, ItemLookup lookup) {
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 1) return;
            var item = lookup.at(event.x, event.y);
            if (item == null) return;
            itemActionsPopup.open(item, event.x - getPositionX() + 2, event.y - getPositionY() + 2);
            event.stopImmediatePropagation();
        });
        hitbox.addEventListener(UIEvents.MOUSE_UP, event -> { if (event.button == 1) event.stopImmediatePropagation(); });
        hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            var ui = getModularUI();
            if (ui == null) return;
            ComputationCpuItemEntry item = lookup.at(ui.getLastMouseX(), ui.getLastMouseY());
            if (item == null) return;
            event.hoverTooltips = CraftingListCard.hover(
                ComputationCards.item(AEKeyRendering.getTooltip(item.key()), item));
        });
    }

    @FunctionalInterface
    private interface ItemLookup {
        @Nullable ComputationCpuItemEntry at(float mouseX, float mouseY);
    }

    private ComputationCpuItemEntry itemAt(float mouseX, float mouseY) {
        float localX = mouseX - getPositionX() - ITEM_X;
        float localY = mouseY - getPositionY() - LIST_Y;
        if (localX < 0 || localX >= ITEM_COLS * ITEM_COLUMN_STRIDE - 1 || localY < 0 || localY >= ITEM_ROWS * ITEM_STRIDE - 1) return null;
        int column = (int) (localX / ITEM_COLUMN_STRIDE);
        if (localX - column * ITEM_COLUMN_STRIDE >= ITEM_WIDTH) return null;
        return itemPage.hit(localY, column, ITEM_STRIDE, ITEM_HEIGHT);
    }

    private void scrollCpuTo(int value) {
        int next = Math.clamp(value, 0, Math.max(0, total - ROWS));
        if (next == cpuOffset) return;
        cpuOffset = next;
        cpuScrollPending = true;
        cpuScrollbar.update(cpuOffset, total - ROWS, ROWS / 3);
        cpuScrollRequest.setValue(scrollTag(++cpuScrollSequence, cpuOffset));
    }

    private void scrollItemsTo(int value) {
        int next = Math.clamp(value / ITEM_COLS, 0, maxItemRows(itemTotal)) * ITEM_COLS;
        if (next == itemOffset) return;
        itemOffset = next;
        itemScrollPending = true;
        itemScrollbar.update(itemOffset / ITEM_COLS, maxItemRows(itemTotal), 1);
        itemScrollRequest.setValue(scrollTag(++itemScrollSequence, itemOffset));
    }

    private void scrollTasksTo(int value) {
        int next = Math.clamp(value, 0, Math.max(0, taskTotal - ROWS));
        if (next == taskOffset) return;
        taskOffset = next;
        taskScrollPending = true;
        taskScrollbar.update(taskOffset, taskTotal - ROWS, 1);
        taskScrollRequest.setValue(scrollTag(++taskScrollSequence, taskOffset));
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
        context.drawTexture(BACKGROUND_UPPER, x, y, WIDTH, 214);
        context.drawTexture(BACKGROUND_LOWER, x + 95, y + 214, 237, 26);
        drawTier(context, font, x, y);
        context.graphics.flush();
        ComputationCpuSelector.fittedText(context, font, GuiText.CraftingCPUs.text().getString(),
            x + CPU_X, y + 12, CPU_TRACK_X - CPU_X - 2, 0.75F, TEXT);
        Component status = title(selected, itemTotal > 0);
        float statusScale = Math.min(0.75F, (FOOTER_WIDTH - 114) / (float) Math.max(1, font.width(status)));
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x + FOOTER_X + 4, y + FOOTER_Y + 11 - font.lineHeight * statusScale / 2, 0);
        context.graphics.pose().scale(statusScale, statusScale, 1);
        context.graphics.drawString(font, status, 0, 0, 0xFF000000 | TEXT, false);
        context.graphics.pose().popPose();
        // Switch complete acknowledged pages together with the rows used for hit testing.
        cpuPage.advance();
        itemPage.advance();
        taskPage.advance();
        context.enableScissor(x + CPU_X, y + LIST_Y, ROW_WIDTH, ROWS * ROW_STRIDE - 1);
        try {
            for (int index = 0; index < cpuPage.size(); index++) {
                double row = cpuPage.row(index);
                if (row > -1 && row < ROWS) drawCpu(context, font, cpuPage.get(index),
                    x + CPU_X, y + LIST_Y + (float) row * ROW_STRIDE);
            }
        } finally {
            context.graphics.flush();
            context.disableScissor();
        }
        context.enableScissor(x + ITEM_X, y + LIST_Y, ITEM_COLS * ITEM_COLUMN_STRIDE - 1, ITEM_ROWS * ITEM_STRIDE - 1);
        try {
            for (int index = 0; index < itemPage.size(); index++) {
                double row = itemPage.row(index);
                if (row > -1 && row < ITEM_ROWS) drawItem(context, font, itemPage.get(index),
                    x + ITEM_X + itemPage.column(index) * ITEM_COLUMN_STRIDE,
                    y + LIST_Y + (float) row * ITEM_STRIDE, AEConfig.instance().isUseColoredCraftingStatus());
            }
        } finally {
            context.graphics.flush();
            context.disableScissor();
        }
        context.enableScissor(x + TASK_X, y + LIST_Y, ROW_WIDTH, ROWS * ROW_STRIDE - 1);
        try {
            for (int index = 0; index < taskPage.size(); index++) {
                double row = taskPage.row(index);
                if (row > -1 && row < ROWS) drawTask(context, font, taskPage.get(index),
                    x + TASK_X, y + LIST_Y + (float) row * ROW_STRIDE);
            }
        } finally {
            context.graphics.flush();
            context.disableScissor();
        }
    }

    /** Replaces the artwork's "O" with the selected host tier glyph, as the controller pages do. */
    private void drawTier(GUIContext context, Font font, float x, float y) {
        if (tierBuiltin && tier >= 1 && tier <= 3) {
            context.drawTexture(TIER_COVER, x + TIER_X, y + TIER_Y, TIER_WIDTH, TIER_HEIGHT);
            context.drawTexture(ComputationControllerTextures.tier(tier), x + TIER_X, y + TIER_Y, TIER_WIDTH, TIER_HEIGHT);
            return;
        }
        if (tierBuiltin || tierName.getString().isEmpty()) return;
        context.drawTexture(TIER_COVER, x + TIER_X, y + TIER_Y, TIER_WIDTH, TIER_HEIGHT);
        context.graphics.flush();
        float scale = Math.min(1, Math.min(TIER_WIDTH / (float) Math.max(1, font.width(tierName)),
            TIER_HEIGHT / (float) font.lineHeight));
        var pose = context.graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + TIER_X + (TIER_WIDTH - font.width(tierName) * scale) / 2,
                y + TIER_Y + (TIER_HEIGHT - font.lineHeight * scale) / 2, 0);
            pose.scale(scale, scale, 1);
            context.graphics.drawString(font, tierName, 0, 0, 0xFF3F3D52, false);
        } finally {
            pose.popPose();
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
        taskPage.reset();
    }

    private void drawCpu(GUIContext context, Font font, ComputationCpuEntry entry, float x, float y) {
        ComputationCpuSelector.drawCpuRow(context, font, entry, x, y, entry.serial() == selectedSerial,
            ROW_NORMAL, ROW_SELECTED, opening.value(entry.progress()));
    }

    /** A running task card: the whole frame fills from the normal row state to the selected row state. */
    private void drawTask(GUIContext context, Font font, ComputationCpuItemEntry entry, float x, float y) {
        int left = Math.round(x), top = Math.round(y);
        context.drawTexture(ROW_NORMAL, left, top, ROW_WIDTH, ROW_HEIGHT);
        context.graphics.flush();
        var processing = entry.processing();
        float progress = processing.hasProgress() ? opening.value(Math.clamp(processing.progress(), 0, 1)) : 0;
        int filled = Math.round(ROW_WIDTH * progress);
        if (filled > 0) {
            context.graphics.blit(ATLAS, left, top, ROW_SELECTED_U, ROW_SELECTED_V, filled, ROW_HEIGHT,
                ATLAS_WIDTH, ATLAS_HEIGHT);
        }
        key(context, entry.key(), x + ROW_WIDTH - 19, y + 3, 1.0F);
        ComputationCpuSelector.fittedText(context, font, entry.key().getDisplayName().getString(),
            x + 3, y + 3, ROW_WIDTH - 23, 0.5F, TEXT);
        ComputationCpuSelector.fittedText(context, font, GuiText.Crafting.text(entry.amount(entry.active(), false)).getString(),
            x + 3, y + 9, ROW_WIDTH - 23, 0.5F, TEXT);
        Component state = processing.hasProgress()
            ? Component.literal(Math.round(processing.progress() * 100) + "%")
            : Component.translatable("gui.neoecoae.processing.state." + processing.state().name().toLowerCase(Locale.ROOT));
        ComputationCpuSelector.fittedText(context, font, state.getString(), x + 3, y + 15, ROW_WIDTH - 23, 0.5F,
            processing.state() == ECOProcessingProgress.State.WAITING ? 0x8A5A12 : TEXT);
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
        if (entry.active().signum() <= 0 || !entry.processing().hasProgress()) return;
        int left = Math.round(x + 2), right = Math.round(x + ITEM_WIDTH - 2);
        int top = Math.round(y + ITEM_HEIGHT - 3);
        context.graphics.fill(left, top, right, top + 2, 0xFF5B566B);
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

    private static IGuiTexture sprite(int x, int y, int width, int height) {
        return SpriteTexture.of(ATLAS).setSprite(x, y, width, height);
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
            AE2Button.renderButtonText(context.graphics, Minecraft.getInstance().font, label,
                x + 2, y, x + Math.round(getSizeWidth()) - 2, y + Math.round(getSizeHeight()), offset, 0xFF000000 | color);
        }
    }

    /** AE2's crafting status order: crafting + scheduled amount first, then stored amount, both descending. */
    static final Comparator<ComputationCpuItemEntry> AE2_ORDER = Comparator
        .comparing((ComputationCpuItemEntry item) -> item.active().add(item.pending()))
        .thenComparing(ComputationCpuItemEntry::stored)
        .reversed()
        .thenComparing(item -> item.key().getDisplayName().getString())
        .thenComparing(item -> item.key().getId().toString());

    /** AE2-style terminal search: plain text matches names, {@code @} the mod id and {@code *} the resource id. */
    static boolean matches(AEKey key, String query) {
        if (query.isEmpty()) return false;
        if (query.startsWith("@")) return key.getModId().toLowerCase(Locale.ROOT).contains(query.substring(1));
        if (query.startsWith("*")) return key.getId().toString().toLowerCase(Locale.ROOT).contains(query.substring(1));
        return key.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private static final class ServerState {
        private final Source source;
        private final CpuSelectionState<ICraftingCPU> selection;
        private CompoundTag cached;
        private long sampledTick = Long.MIN_VALUE;
        private int itemOffset, taskOffset;
        private long cpuScrollSequence, itemScrollSequence, taskScrollSequence;
        private CompoundTag itemActionResponse = new CompoundTag();
        private String query = "";
        private boolean focusPending;
        // Running tasks keep the order in which they were first observed; earlier tasks stay on top.
        private final LinkedHashSet<AEKey> taskOrder = new LinkedHashSet<>();
        private int taskSerial = -1;
        private UUID taskJob;

        private ServerState(Source source, CpuSelectionState.Identities<ICraftingCPU> identities) {
            this.source = source;
            selection = new CpuSelectionState<>(ROWS, identities);
        }

        private void refresh() {
            selection.update(source.cpus().get(), ICraftingCPU::isBusy,
                cpu -> cpu.getName() == null ? null : cpu.getName().getString());
        }

        private void select(int serial) {
            if (!source.canInteract().getAsBoolean()) return;
            refresh();
            if (serial != selection.selectedSerial()) {
                itemOffset = 0;
                taskOffset = 0;
                focusPending = !query.isEmpty();
            }
            selection.select(serial);
            cached = null;
        }

        private void scrollCpu(CompoundTag request) {
            if (!source.canInteract().getAsBoolean()) return;
            refresh();
            selection.scroll(request.getInt("offset"));
            cpuScrollSequence = request.getLong("sequence");
            cached = null;
        }

        private void scrollItems(CompoundTag request) {
            if (!source.canInteract().getAsBoolean()) return;
            refresh();
            int total = selectedItems(selection.selected()).size();
            itemOffset = Math.clamp(request.getInt("offset") / ITEM_COLS, 0, maxItemRows(total)) * ITEM_COLS;
            itemScrollSequence = request.getLong("sequence");
            cached = null;
        }

        private void scrollTasks(CompoundTag request) {
            if (!source.canInteract().getAsBoolean()) return;
            refresh();
            int total = tasks(selection.selected(), selectedItems(selection.selected())).size();
            taskOffset = Math.clamp(request.getInt("offset"), 0, Math.max(0, total - ROWS));
            taskScrollSequence = request.getLong("sequence");
            cached = null;
        }

        private void search(String text) {
            if (!source.canInteract().getAsBoolean()) return;
            String next = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            if (next.equals(query)) return;
            query = next;
            // Matches move to B's first cell, so the item view returns to its top.
            itemOffset = 0;
            focusPending = !query.isEmpty();
            cached = null;
        }

        private void action(int serial, int action) {
            if (!source.canInteract().getAsBoolean()) return;
            refresh();
            // Carry the target in this request; selection synchronization may arrive on a later tick.
            if (!selection.select(serial)) return;
            ICraftingCPU cpu = selection.selected();
            if (action == 1) source.toggleCpuSuspended().accept(cpu);
            if (action == 2) source.cancelCpu().accept(cpu);
            cached = null;
        }

        private void itemAction(int serial, CompoundTag request) {
            if (!source.canInteract().getAsBoolean()) return;
            var response = new CompoundTag();
            response.putInt("serial", serial);
            response.putLong("sequence", request.getLong("sequence"));
            response.put("key", request.getCompound("key").copy());
            itemActionResponse = response;
            refresh();
            var cpu = selection.selected();
            AEKey key = AEKey.fromTagGeneric(source.registries(), request.getCompound("key"));
            if (cpu == null || serial != selection.selectedSerial() || key == null) {
                response.putString("error", "gui.neoecoae.processing.task_changed");
                return;
            }
            var job = jobId(cpu);
            String action = request.getString("action");
            if ((!action.equals("inspect") && !action.equals("open")) || job == null
                || (action.equals("open") && (!request.hasUUID("job") || !job.equals(request.getUUID("job"))))) {
                response.putString("error", "gui.neoecoae.processing.task_changed");
                return;
            }
            response.putUUID("job", job);
            var grid = cpu instanceof ECOCraftingCPU eco ? eco.getGrid()
                : cpu instanceof CraftingCPUCluster cluster ? cluster.getGrid() : null;
            var sources = grid != null && grid.getCraftingService() instanceof appeng.me.service.CraftingService crafting
                ? cn.dancingsnow.neoecoae.compat.ae2.ECOProviderActions.find(patterns(cpu, key, crafting), crafting)
                : List.<cn.dancingsnow.neoecoae.compat.ae2.ECOProviderActions.Source>of();
            var list = new ListTag();
            for (var source : sources) list.add(source.describe(this.source.registries()));
            response.put("sources", list);
            if (action.equals("open")) {
                var target = sources.stream().filter(source -> source.id().equals(request.getString("source"))).findFirst();
                try {
                    if (!(this.source.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        || target.isEmpty() || !target.get().open(player)) response.putString("error", "gui.neoecoae.processing.open_unavailable");
                } catch (RuntimeException | LinkageError unsupported) {
                    response.putString("error", "gui.neoecoae.processing.open_unavailable");
                }
            }
        }

        private CompoundTag snapshot() {
            long tick = source.gameTime().getAsLong();
            if (cached != null && tick >= sampledTick && tick - sampledTick < 3) return cached;
            sampledTick = tick;
            refresh();
            CompoundTag tag = new CompoundTag();
            tag.putInt("total", selection.size());
            tag.putInt("cpuOffset", selection.offset());
            tag.putLong("cpuScrollSequence", cpuScrollSequence);
            tag.putLong("itemScrollSequence", itemScrollSequence);
            tag.putLong("taskScrollSequence", taskScrollSequence);
            tag.putInt("selectedSerial", selection.selectedSerial());
            var registries = source.registries();
            ListTag cpuPage = new ListTag();
            tag.putInt("cpuPageStart", Math.max(0, selection.offset() - ROWS));
            for (ICraftingCPU cpu : selection.bufferedPage()) cpuPage.add(ComputationCpuEntry.sample(cpu, selection.serial(cpu)).write(registries));
            tag.put("cpus", cpuPage);
            ICraftingCPU selectedCpu = selection.selected();
            writeTier(tag, selectedCpu instanceof ECOCraftingCPU eco ? eco.getTier() : null);
            List<ComputationCpuItemEntry> all = selectedItems(selectedCpu);
            List<ComputationCpuItemEntry> running = tasks(selectedCpu, all);
            if (focusPending) {
                focusPending = false;
                for (int index = 0; index < running.size(); index++) {
                    if (matches(running.get(index).key(), query)) {
                        taskOffset = index;
                        break;
                    }
                }
            }
            if (selectedCpu != null) tag.put("selected", ComputationCpuEntry.sample(selectedCpu, selection.selectedSerial()).write(registries));
            Map<AEKey, ECOProcessingProgress> progress = new HashMap<>();
            tag.putInt("itemTotal", all.size());
            itemOffset = Math.clamp(itemOffset / ITEM_COLS, 0, maxItemRows(all.size())) * ITEM_COLS;
            tag.putInt("itemOffset", itemOffset);
            int itemPageStart = Math.max(0, itemOffset - ITEM_PAGE);
            tag.putInt("itemPageStart", itemPageStart);
            tag.put("items", page(selectedCpu, all, itemPageStart, Math.min(all.size(), itemOffset + ITEM_PAGE * 2), progress));
            tag.putInt("taskTotal", running.size());
            taskOffset = Math.clamp(taskOffset, 0, Math.max(0, running.size() - ROWS));
            tag.putInt("taskOffset", taskOffset);
            int taskPageStart = Math.max(0, taskOffset - ROWS);
            tag.putInt("taskPageStart", taskPageStart);
            tag.put("tasks", page(selectedCpu, running, taskPageStart, Math.min(running.size(), taskOffset + ROWS * 2), progress));
            return cached = tag;
        }

        private static void writeTier(CompoundTag tag, @Nullable IECOTier tier) {
            tag.putInt("tier", tier == null ? 0 : tier.getTier());
            tag.putBoolean("tierBuiltin", tier == null || tier instanceof ECOTier);
            tag.putString("tierName", tier == null || tier instanceof ECOTier ? "" : tier.getPanelTierName().getString());
        }

        private ListTag page(@Nullable ICraftingCPU cpu, List<ComputationCpuItemEntry> entries, int from, int to,
                Map<AEKey, ECOProcessingProgress> progress) {
            ListTag page = new ListTag();
            if (cpu == null) return page;
            var registries = source.registries();
            for (ComputationCpuItemEntry item : entries.subList(Math.min(from, to), to)) {
                // Machine progress is only tracked for ECO jobs; AE2 tasks show their state without a fill.
                var sampled = !(cpu instanceof ECOCraftingCPU eco) || item.active().signum() <= 0 ? ECOProcessingProgress.EMPTY
                    : progress.computeIfAbsent(item.key(), key -> {
                        var value = eco.getLogic().getProcessingProgress(key);
                        return value == null ? ECOProcessingProgress.EMPTY : value.page(0);
                    });
                page.add(item.withProcessing(sampled).write(registries));
            }
            return page;
        }

        /** Running entries in first-observed order; the order restarts when the CPU or its job changes. */
        private List<ComputationCpuItemEntry> tasks(@Nullable ICraftingCPU cpu, List<ComputationCpuItemEntry> all) {
            UUID job = jobId(cpu);
            if (selection.selectedSerial() != taskSerial || !Objects.equals(job, taskJob)) {
                taskOrder.clear();
                taskSerial = selection.selectedSerial();
                taskJob = job;
            }
            Map<AEKey, ComputationCpuItemEntry> active = new HashMap<>();
            List<AEKey> naturalOrder = new ArrayList<>();
            for (ComputationCpuItemEntry item : all) {
                if (item.active().signum() <= 0) continue;
                active.put(item.key(), item);
                naturalOrder.add(item.key());
            }
            taskOrder.retainAll(active.keySet());
            // Tasks first observed together keep AE2's order, so the initial list is deterministic.
            naturalOrder.sort(Comparator.comparing(active::get, AE2_ORDER));
            taskOrder.addAll(naturalOrder);
            List<ComputationCpuItemEntry> result = new ArrayList<>(taskOrder.size());
            for (AEKey key : taskOrder) result.add(active.get(key));
            return result;
        }

        /** ECO jobs know their own patterns; for AE2 jobs every network pattern producing the key is a candidate. */
        private static List<appeng.api.crafting.IPatternDetails> patterns(ICraftingCPU cpu, AEKey key,
                appeng.me.service.CraftingService crafting) {
            if (cpu instanceof ECOCraftingCPU eco) return eco.getLogic().getProcessingPatterns(key);
            return List.copyOf(crafting.getCraftingFor(key));
        }

        /** ECO jobs carry their own id; AE2 clusters expose the link of the job they are running. */
        @Nullable
        private static UUID jobId(@Nullable ICraftingCPU cpu) {
            if (cpu instanceof ECOCraftingCPU eco) return eco.getLogic().getProcessingJobId();
            if (cpu instanceof CraftingCPUCluster cluster && cluster.craftingLogic.hasJob()) {
                var link = cluster.craftingLogic.getLastLink();
                return link == null ? null : link.getCraftingID();
            }
            return null;
        }

        private Comparator<ComputationCpuItemEntry> order() {
            return query.isEmpty() ? AE2_ORDER
                : Comparator.<ComputationCpuItemEntry, Boolean>comparing(item -> !matches(item.key(), query)).thenComparing(AE2_ORDER);
        }

        private List<ComputationCpuItemEntry> selectedItems(@Nullable ICraftingCPU cpu) {
            if (cpu instanceof ECOCraftingCPU eco) return selectedItems(eco);
            // Native AE2 CPUs: the same per-key amounts AE2's CraftingStatus packet reports.
            if (!(cpu instanceof CraftingCPUCluster cluster)) return List.of();
            var logic = cluster.craftingLogic;
            Set<AEKey> keys = new HashSet<>();
            appeng.api.stacks.KeyCounter all = new appeng.api.stacks.KeyCounter();
            logic.getAllItems(all);
            all.forEach(entry -> keys.add(entry.getKey()));
            logic.getAllWaitingFor(keys);
            return keys.stream().map(key -> new ComputationCpuItemEntry(key,
                    BigInteger.valueOf(Math.max(0L, logic.getStored(key))),
                    BigInteger.valueOf(Math.max(0L, logic.getWaitingFor(key))),
                    BigInteger.valueOf(Math.max(0L, logic.getPendingOutputs(key))), false))
                .filter(item -> item.stored().signum() != 0 || item.active().signum() != 0 || item.pending().signum() != 0)
                .sorted(order())
                .toList();
        }

        private List<ComputationCpuItemEntry> selectedItems(ECOCraftingCPU cpu) {
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
                .sorted(order())
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
                logic.isBatchedOutput(key));
        }
    }
}
