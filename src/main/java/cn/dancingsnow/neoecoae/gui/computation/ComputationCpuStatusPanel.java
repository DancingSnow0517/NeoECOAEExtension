package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingMachineActions;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;

import appeng.api.client.AEKeyRendering;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.api.stacks.AEKey;
import appeng.api.util.AEColor;
import appeng.api.config.TerminalStyle;
import appeng.core.localization.ButtonToolTips;
import java.util.function.IntConsumer;
import java.util.LinkedHashMap;
import net.minecraft.network.chat.MutableComponent;
import cn.dancingsnow.neoecoae.platform.JdkCompat;
import cn.dancingsnow.neoecoae.client.gui.widget.NEButtonText;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
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
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextField;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
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
 * The 427x226 ECO crafting status page (stretches downwards with AE2's terminal style). A: CPU list, B: crafting status in AE2 order, C: running tasks in
 * start order, D: search that moves matches to B's first cell, E: AE2's suspend/cancel controls.
 */
final class ComputationCpuStatusPanel extends BindableValue<CompoundTag> {
    private final HostProgressAnimation opening = new HostProgressAnimation();
    static final int WIDTH = 427, HEIGHT = 226, ROWS = 7;
    private static final int ROW_WIDTH = 67, ROW_HEIGHT = 22, ROW_STRIDE = 23, LIST_Y = 27;
    private static final int TRACK_Y = 26, TRACK_HEIGHT = 162;
    private static final int TRACK_WIDTH = 6, SCROLLER_INSET = (ComputationScrollbar.WIDTH - TRACK_WIDTH) / 2;
    private static final int CPU_X = 11, CPU_TRACK_X = 89;
    private static final int ITEM_X = 105, ITEM_WIDTH = ROW_WIDTH, ITEM_HEIGHT = ROW_HEIGHT, ITEM_COLUMN_STRIDE = 68;
    private static final int ITEM_COLS = 3, ITEM_ROWS = ROWS, ITEM_STRIDE = ROW_STRIDE, ITEM_PAGE = ITEM_COLS * ITEM_ROWS;
    private static final int ITEM_TRACK_X = 317;
    private static final int TASK_X = 333, TASK_TRACK_X = 411;
    private static final int SEARCH_X = 333, SEARCH_Y = 9, SEARCH_WIDTH = 67, SEARCH_HEIGHT = 10;
    private static final int TIER_X = 134, TIER_Y = 5, TIER_WIDTH = 13, TIER_HEIGHT = 18;
    // Suspend/cancel sit higher than the lower extension's floor, between the lists and the panel bottom.
    private static final int FOOTER_X = 97, FOOTER_WIDTH = 233, BUTTON_Y = 196;
    private static final int TEXT = 0x413F54;
    // Header text right of the ECO logo, above the line's low segment (which steps up near x=255).
    private static final int TIME_X = 152, TIME_Y = 12, TIME_WIDTH = 100;
    // 1.7.10's light text: the list cells are dark, so dark text loses contrast there.
    private static final int TEXT_LIGHT = 0xF2F3F7;
    private static final ResourceLocation ATLAS =
        new ResourceLocation("neoecoae", "textures/guis/eco_crafting_processing.png");
    // The panel body ends at y=200 with a 237x26 lower extension; the row states sit in the notch beside it.
    private static final int BODY_BOTTOM = 200, LOWER_X = 95, LOWER_WIDTH = 237, LOWER_HEIGHT = 26;
    private static final int STRIP_V = LIST_Y + ROW_STRIDE, TAIL_V = LIST_Y + (ROWS - 1) * ROW_STRIDE, MAX_ROWS = 48;
    private static final IGuiTexture BACKGROUND_TOP = sprite(0, 0, WIDTH, STRIP_V);
    private static final IGuiTexture BACKGROUND_STRIP = sprite(0, STRIP_V, WIDTH, ROW_STRIDE);
    private static final IGuiTexture BACKGROUND_TAIL = sprite(0, TAIL_V, WIDTH, BODY_BOTTOM - TAIL_V);
    private static final IGuiTexture BACKGROUND_LOWER = sprite(LOWER_X, BODY_BOTTOM, LOWER_WIDTH, LOWER_HEIGHT);
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
                : player.level().registryAccess();
        }

        static Source of(ComputationControllerUI.Config config) {
            return new Source(config.player(), config.gameTime(), config.canInteract(), () -> config.cpus().get(),
                cpu -> { if (cpu instanceof ECOCraftingCPU eco) config.toggleCpuSuspended().accept(eco); },
                cpu -> { if (cpu instanceof ECOCraftingCPU eco) config.cancelCpu().accept(eco); }, null);
        }
    }

    private final Source source;
    private final BindableValue<CompoundTag> cpuScrollRequest = new BindableValue<>(new CompoundTag());
    private final BindableValue<CompoundTag> itemScrollRequest = new BindableValue<>(new CompoundTag());
    private final BindableValue<CompoundTag> taskScrollRequest = new BindableValue<>(new CompoundTag());
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
    private java.util.function.IntConsumer selectionRequest = serial -> {};
    private java.util.function.BiConsumer<AEKey, Integer> processingDetailsRequest = (key, page) -> {};
    private Runnable stopProcessingRequest = () -> {};
    private AEKey hoveredProcessingKey, requestedProcessingKey;
    private int hoveredProcessingPage, requestedProcessingPage;
    private long nextProcessingRequest;
    // Visible list rows; AE2's terminal style stretches the page downwards like the 1.7.10 status page.
    private int rows = ROWS;
    private final BindableValue<Integer> rowsRequest = new BindableValue<>(ROWS);
    private UIElement cpuHit, itemHit, taskHit;
    // AE2's terminal-style toggle in the shared left rail, like AE2's terminal toolbar.
    private final Button styleButton = HostSideButtonBar.createButton();
    private final UIElement styleIcon = new UIElement();
    private UIElement styleBar;
    private TerminalStyle shownStyle;
    private IntConsumer heightListener = height -> {};

    ComputationCpuStatusPanel(ComputationControllerUI.Config config, CpuSelectionState.Identities<ICraftingCPU> identities) {
        this(Source.of(config), identities);
    }

    ComputationCpuStatusPanel(Source source, CpuSelectionState.Identities<ICraftingCPU> identities) {
        super(new CompoundTag());
        this.source = source;
        layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0));
        setOverflowVisible(true);
        ServerState server = new ServerState(source, identities);
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
        taskScrollRequest.bind(DataBindingBuilder.<CompoundTag>create(null, server::scrollTasks)
            .syncType(CompoundTag.class).s2cStrategy(SyncStrategy.NONE).build());
        itemActionResponse.bind(DataBindingBuilder.create(() -> server.itemActionResponse, ignored -> {})
            .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        cpuScrollRequest.setDisplay(false);
        itemScrollRequest.setDisplay(false);
        processingOverview.setDisplay(false);
        taskScrollRequest.setDisplay(false);
        itemActionResponse.setDisplay(false);
        addChildren(cpuScrollRequest, itemScrollRequest, taskScrollRequest, processingOverview, itemActionResponse);
        rowsRequest.bind(DataBindingBuilder.intValC2S(server::rows).build());
        rowsRequest.setDisplay(false);
        addChild(rowsRequest);
        addChildren(cpuHit = cpuHitbox(), itemHit = itemHitbox(), taskHit = taskHitbox(), searchField(server));
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (itemActionsPopup.isDisplayed() && !itemActionsPopup.isMouseOverElement(event.x, event.y)) {
                itemActionsPopup.close();
                if (event.button != 1) event.stopImmediatePropagation();
            }
        }, true);
        cpuScrollbar = scrollbar(CPU_TRACK_X, this::scrollCpuTo);
        itemScrollbar = scrollbar(ITEM_TRACK_X,
            row -> scrollItemsTo(row * ITEM_COLS));
        taskScrollbar = scrollbar(TASK_TRACK_X, this::scrollTasksTo);
        addChildren(cpuScrollbar, itemScrollbar, taskScrollbar);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (itemActionsPopup.isDisplayed()) {
                if (!itemActionsPopup.isMouseOverElement(event.x, event.y)) event.stopImmediatePropagation();
                return;
            }
            var hovered = itemAt(event.x, event.y);
            if (hovered == null) hovered = taskAt(event.x, event.y);
            if (event.isShiftDown() && event.deltaY != 0 && hovered != null && hovered.processing().pages() > 1) {
                requestProcessingDetails(hovered, event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
                return;
            }
            float localX = event.x - getPositionX(), localY = event.y - getPositionY();
            if (localY < LIST_Y || localY >= LIST_Y + rows * ROW_STRIDE) return;
            int direction = event.deltaY < 0 ? 1 : -1;
            if (localX >= CPU_X && localX < CPU_TRACK_X + TRACK_WIDTH + SCROLLER_INSET) {
                scrollCpuTo(cpuOffset + direction * (rows / 3));
            } else if (localX >= ITEM_X && localX < ITEM_TRACK_X + TRACK_WIDTH + SCROLLER_INSET) {
                scrollItemsTo(itemOffset + direction * ITEM_COLS);
            } else if (localX >= TASK_X && localX < TASK_TRACK_X + TRACK_WIDTH + SCROLLER_INSET) {
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
                stopProcessingRequest.run();
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
        pauseButton.setText(Component.translatable("gui.neoecoae.computation.pause"))
            .setOnClick(event -> { if (event.button == 0) cpuAction.send(selectedSerial, 1); });
        placeButton(pauseButton, FOOTER_X + FOOTER_WIDTH - 106, BUTTON_Y);
        cancelButton.setText(GuiText.Cancel.text())
            .setOnClick(event -> { if (event.button == 0) cpuAction.send(selectedSerial, 2); });
        placeButton(cancelButton, FOOTER_X + FOOTER_WIDTH - 54, BUTTON_Y);
        pauseButton.setActive(false);
        cancelButton.setActive(false);
        styleButton.setId("computation-status-terminal-style");
        styleIcon.layout(layout -> layout.heightPercent(100).setAspectRatio(1f))
            .addClasses("__icon__", "__button_post-icon__");
        styleButton.noText().addChild(styleIcon);
        updateStyleIcon();
        styleButton.setOnClick(event -> {
            var styles = TerminalStyle.values();
            var current = AEConfig.instance().getTerminalStyle();
            int step = event.button == 1 ? -1 : 1;
            AEConfig.instance().setTerminalStyle(styles[Math.floorMod(current.ordinal() + step, styles.length)]);
            updateStyleIcon();
        });
        styleButton.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            List.<Component>of(ButtonToolTips.TerminalStyle.text(), styleTooltip(AEConfig.instance().getTerminalStyle())
                .withStyle(ChatFormatting.GRAY)), null, null, null));
        styleBar = HostSideButtonBar.grayLeft(styleButton);
        addChildren(back, pauseButton, cancelButton, styleBar, itemActionsPopup);
    }

    /** AE2's big scroller, as on the previous status page, centred on the artwork's 6-pixel track. */
    private ComputationScrollbar scrollbar(int trackX, IntConsumer onScroll) {
        return new ComputationScrollbar(this, trackX - SCROLLER_INSET, TRACK_Y, TRACK_HEIGHT, onScroll);
    }

    void setBackAction(Runnable action) { backAction = action; }

    /** Receives the page height whenever the stretched row count changes. */
    void setHeightListener(IntConsumer listener) { heightListener = listener; }

    int currentHeight() { return HEIGHT + extra(); }

    private int extra() { return (rows - ROWS) * ROW_STRIDE; }

    /** AE2's terminal rule: the style takes a share of the rows that fit between the screen margins. */
    private int desiredRows() {
        // Without a window or AE2 config (headless rendering in tests) the page keeps its current height.
        var minecraft = Minecraft.getInstance();
        var config = AEConfig.instance();
        if (minecraft == null || minecraft.getWindow() == null || config == null) return rows;
        int available = minecraft.getWindow().getGuiScaledHeight() - 2 * config.getTerminalMargin();
        int max = ROWS + Math.max(0, (available - HEIGHT) / ROW_STRIDE);
        return cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(config.getTerminalStyle().getRows(max), ROWS, MAX_ROWS);
    }

    private void applyRows(int next) {
        if (next == rows) return;
        rows = next;
        int extra = extra();
        layout(layout -> layout.height(HEIGHT + extra));
        for (UIElement hitbox : new UIElement[] {cpuHit, itemHit, taskHit}) {
            hitbox.layout(layout -> layout.height(rows * ROW_STRIDE - 1));
        }
        cpuPage.rows(rows);
        itemPage.rows(rows);
        taskPage.rows(rows);
        cpuScrollbar.resize(TRACK_HEIGHT + extra);
        itemScrollbar.resize(TRACK_HEIGHT + extra);
        taskScrollbar.resize(TRACK_HEIGHT + extra);
        placeButton(pauseButton, FOOTER_X + FOOTER_WIDTH - 106, BUTTON_Y + extra);
        placeButton(cancelButton, FOOTER_X + FOOTER_WIDTH - 54, BUTTON_Y + extra);
        cpuScrollbar.update(cpuOffset, total - rows, rows / 3);
        itemScrollbar.update(itemOffset / ITEM_COLS, maxItemRows(itemTotal, rows), 1);
        taskScrollbar.update(taskOffset, taskTotal - rows, 1);
        rowsRequest.setValue(rows);
        heightListener.accept(HEIGHT + extra);
        var minecraft = Minecraft.getInstance();
        minecraft.tell(() -> {
            // Re-centre through a deferred resize, as the controller page does when it changes size.
            var screen = minecraft.screen;
            if (screen != null) screen.resize(minecraft, screen.width, screen.height);
        });
    }

    private void updateStyleIcon() {
        // The terminal style is AE2 client config; the server copy of this tree never renders it.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.CLIENT) return;
        var style = AEConfig.instance().getTerminalStyle();
        if (style == shownStyle) return;
        shownStyle = style;
        ECOIcon icon = switch (style) {
            case SMALL -> ECOIcon.TERMINAL_STYLE_SMALL;
            case MEDIUM -> ECOIcon.TERMINAL_STYLE_MEDIUM;
            case TALL -> ECOIcon.TERMINAL_STYLE_TALL;
            case FULL -> ECOIcon.TERMINAL_STYLE_FULL;
        };
        styleIcon.style(css -> css.backgroundTexture(AETextures.icon(icon)));
    }

    private static MutableComponent styleTooltip(TerminalStyle style) {
        return switch (style) {
            case SMALL -> ButtonToolTips.TerminalStyle_Small.text();
            case MEDIUM -> ButtonToolTips.TerminalStyle_Medium.text();
            case TALL -> ButtonToolTips.TerminalStyle_Tall.text();
            case FULL -> ButtonToolTips.TerminalStyle_Full.text();
        };
    }

    @Override public UIElement hitTest(double x, double y) {
        var target = super.hitTest(x, y);
        // The popup and the terminal-style button hang outside the panel but still take clicks.
        if (target == null && isDisplayed() && isVisible() && itemActionsPopup != null && itemActionsPopup.isDisplayed()) return this;
        if (target == null && styleBar != null && styleBar.isDisplayed()) return styleBar.hitTest(x, y);
        return target;
    }

    void open(int serial) {
        itemScrollPending = false;
        hoveredProcessingKey = requestedProcessingKey = null;
        if (selectedSerial != serial) processingByKey.clear();
        taskScrollPending = false;
        itemActionsPopup.close();
        selectedSerial = serial;
        // Opening an already-selected CPU must still enable the server's progress subscription.
        selectionRequest.accept(serial);
    }

    private TextField searchField(ServerState server) {
        TextField search = new SearchField();
        search.setId("computation-status-search");
        // No LDLib frame or rounded focus overlay: the artwork already draws the box.
        search.textFieldStyle(style -> style.textColor(0xF2F2F2).textShadow(false)
            .focusOverlay(IGuiTexture.EMPTY)
            .placeholder(GuiText.SearchPlaceholder.text().withStyle(ChatFormatting.GRAY)));
        search.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        // Typing stays local and is reported to the server; the server never pushes text back.
        search.bind(DataBindingBuilder.string(() -> "", server::search).s2cStrategy(SyncStrategy.NONE).build());
        search.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(SEARCH_X + 1).top(SEARCH_Y)
            .width(SEARCH_WIDTH - 2).height(SEARCH_HEIGHT).paddingAll(0));
        search.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            List.<Component>of(GuiText.SearchTooltip.text(), GuiText.SearchTooltipModId.text().withStyle(ChatFormatting.GRAY),
                GuiText.SearchTooltipItemId.text().withStyle(ChatFormatting.GRAY)), null, null, null));
        return search;
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
        taskTotal = value.getInt("taskTotal");
        tier = value.getInt("tier");
        tierBuiltin = value.getBoolean("tierBuiltin");
        tierName = Component.literal(value.getString("tierName"));
        cpuOffset = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(cpuOffset, 0, Math.max(0, total - rows));
        itemOffset = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(itemOffset / ITEM_COLS, 0, maxItemRows(itemTotal, rows)) * ITEM_COLS;
        taskOffset = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(taskOffset, 0, Math.max(0, taskTotal - rows));
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
        cpuScrollbar.update(cpuOffset, total - rows, rows / 3);
        itemScrollbar.update(itemOffset / ITEM_COLS, maxItemRows(itemTotal, rows), 1);
        taskScrollbar.update(taskOffset, taskTotal - rows, 1);
        boolean busy = selected != null && !selected.status().equals("idle");
        pauseButton.setActive(busy && !selected.status().equals("error") && !selected.status().equals("returning"));
        cancelButton.setActive(busy);
        pauseButton.setText(selected != null && selected.status().equals("suspended") ? Component.translatable("gui.neoecoae.computation.resume") : Component.translatable("gui.neoecoae.computation.pause"));
        return this;
    }

    private List<ComputationCpuItemEntry> items(ListTag tags) {
        var registries = source.registries();
        return tags.stream().map(tag -> ComputationCpuItemEntry.read((CompoundTag) tag, registries)).toList();
    }

    private UIElement cpuHitbox() {
        UIElement hitbox = hitbox(CPU_X, LIST_Y, ROW_WIDTH, rows * ROW_STRIDE - 1);
        hitbox.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button != 0) return;
            ComputationCpuEntry entry = cpuPage.hit(event.y - getPositionY() - LIST_Y, 0, ROW_STRIDE, ROW_HEIGHT);
            if (entry == null) return;
            open(entry.serial());
            event.stopImmediatePropagation();
        });
        hitbox.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            ComputationCpuEntry entry = cpuPage.hit(event.y - getPositionY() - LIST_Y, 0, ROW_STRIDE, ROW_HEIGHT);
            if (entry != null) {
                event.hoverTooltips = new HoverTooltips(List.of(), ComputationCards.cpu(entry), null, null);
            }
        });
        return hitbox;
    }

    private UIElement itemHitbox() {
        UIElement hitbox = hitbox(ITEM_X, LIST_Y, ITEM_COLS * ITEM_COLUMN_STRIDE - 1, rows * ITEM_STRIDE - 1);
        itemEvents(hitbox, this::itemAt);
        return hitbox;
    }

    private UIElement taskHitbox() {
        UIElement hitbox = hitbox(TASK_X, LIST_Y, ROW_WIDTH, rows * ROW_STRIDE - 1);
        itemEvents(hitbox, this::taskAt);
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
            ComputationCpuItemEntry item = lookup.at(event.x, event.y);
            if (item == null) return;
            if (item.processing().machineCount() > 0 || item.processing().refreshing()) requestProcessingDetails(item, 0);
            event.hoverTooltips = new HoverTooltips(List.of(),
                ComputationCards.item(AEKeyRendering.getTooltip(item.key()), item), null, null);
        });
    }

    @FunctionalInterface
    private interface ItemLookup {
        @Nullable ComputationCpuItemEntry at(float mouseX, float mouseY);
    }

    private ComputationCpuItemEntry itemAt(float mouseX, float mouseY) {
        float localX = mouseX - getPositionX() - ITEM_X;
        float localY = mouseY - getPositionY() - LIST_Y;
        if (localX < 0 || localX >= ITEM_COLS * ITEM_COLUMN_STRIDE - 1 || localY < 0 || localY >= rows * ITEM_STRIDE - 1) return null;
        int column = (int) (localX / ITEM_COLUMN_STRIDE);
        if (localX - column * ITEM_COLUMN_STRIDE >= ITEM_WIDTH) return null;
        var item = itemPage.hit(localY, column, ITEM_STRIDE, ITEM_HEIGHT);
        return item == null ? null : withProcessing(item);
    }

    private ComputationCpuItemEntry taskAt(float mouseX, float mouseY) {
        float localX = mouseX - getPositionX() - TASK_X;
        float localY = mouseY - getPositionY() - LIST_Y;
        if (localX < 0 || localX >= ROW_WIDTH || localY < 0 || localY >= rows * ROW_STRIDE - 1) return null;
        var item = taskPage.hit(localY, 0, ROW_STRIDE, ROW_HEIGHT);
        return item == null ? null : withProcessing(item);
    }

    private ComputationCpuItemEntry withProcessing(ComputationCpuItemEntry item) {
        if (item.active().signum() <= 0 && item.pending().signum() <= 0) return item;
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
        int next = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(value, 0, Math.max(0, total - rows));
        if (next == cpuOffset) return;
        cpuOffset = next;
        cpuScrollPending = true;
        cpuScrollbar.update(cpuOffset, total - rows, rows / 3);
        cpuScrollRequest.setValue(scrollTag(++cpuScrollSequence, cpuOffset));
    }

    private void scrollItemsTo(int value) {
        int next = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(value / ITEM_COLS, 0, maxItemRows(itemTotal, rows)) * ITEM_COLS;
        if (next == itemOffset) return;
        itemOffset = next;
        itemScrollPending = true;
        itemScrollbar.update(itemOffset / ITEM_COLS, maxItemRows(itemTotal, rows), 1);
        itemScrollRequest.setValue(scrollTag(++itemScrollSequence, itemOffset));
    }

    private void scrollTasksTo(int value) {
        int next = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(value, 0, Math.max(0, taskTotal - rows));
        if (next == taskOffset) return;
        taskOffset = next;
        taskScrollPending = true;
        taskScrollbar.update(taskOffset, taskTotal - rows, 1);
        taskScrollRequest.setValue(scrollTag(++taskScrollSequence, taskOffset));
    }

    private static CompoundTag scrollTag(long sequence, int offset) {
        var tag = new CompoundTag();
        tag.putLong("sequence", sequence);
        tag.putInt("offset", offset);
        return tag;
    }

    private static int maxItemRows(int total, int rows) {
        return Math.max(0, (total + ITEM_COLS - 1) / ITEM_COLS - rows);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        Font font = Minecraft.getInstance().font;
        float x = getPositionX(), y = getPositionY();
        updateStyleIcon();
        int wanted = desiredRows();
        if (wanted != rows) Minecraft.getInstance().tell(() -> applyRows(desiredRows()));
        // Header and first row, one row strip per further row, then the last row, footer and lower extension.
        context.drawTexture(BACKGROUND_TOP, x, y, WIDTH, STRIP_V);
        float stripY = y + STRIP_V;
        for (int row = 0; row < rows - 2; row++, stripY += ROW_STRIDE) {
            context.drawTexture(BACKGROUND_STRIP, x, stripY, WIDTH, ROW_STRIDE);
        }
        context.drawTexture(BACKGROUND_TAIL, x, stripY, WIDTH, BODY_BOTTOM - TAIL_V);
        context.drawTexture(BACKGROUND_LOWER, x + LOWER_X, stripY + BODY_BOTTOM - TAIL_V, LOWER_WIDTH, LOWER_HEIGHT);
        drawTier(context, font, x, y);
        context.graphics.flush();
        // Elapsed time of the selected job, in the header space between the logo and the line's raised step.
        String elapsed = selected == null || selected.status().equals("idle") || selected.elapsed() <= 0 ? "--"
            : DurationFormatUtils.formatDuration(TimeUnit.NANOSECONDS.toMillis(selected.elapsed()), GuiText.ETAFormat.getLocal());
        ComputationCpuSelector.fittedText(context, font,
            Component.translatable("gui.neoecoae.computation.status.time", elapsed).getString(),
            x + TIME_X, y + TIME_Y, TIME_WIDTH, 0.75F, TEXT);
        // Switch complete acknowledged pages together with the rows used for hit testing.
        cpuPage.advance();
        itemPage.advance();
        taskPage.advance();
        context.enableScissor(x + CPU_X, y + LIST_Y, ROW_WIDTH, rows * ROW_STRIDE - 1);
        try {
            for (int index = 0; index < cpuPage.size(); index++) {
                double row = cpuPage.row(index);
                if (row > -1 && row < rows) drawCpu(context, font, cpuPage.get(index),
                    x + CPU_X, y + LIST_Y + (float) row * ROW_STRIDE);
            }
        } finally {
            context.graphics.flush();
            context.disableScissor();
        }
        context.enableScissor(x + ITEM_X, y + LIST_Y, ITEM_COLS * ITEM_COLUMN_STRIDE - 1, rows * ITEM_STRIDE - 1);
        try {
            for (int index = 0; index < itemPage.size(); index++) {
                double row = itemPage.row(index);
                if (row > -1 && row < rows) drawItem(context, font, withProcessing(itemPage.get(index)),
                    x + ITEM_X + itemPage.column(index) * ITEM_COLUMN_STRIDE,
                    y + LIST_Y + (float) row * ITEM_STRIDE, AEConfig.instance().isUseColoredCraftingStatus());
            }
        } finally {
            context.graphics.flush();
            context.disableScissor();
        }
        context.enableScissor(x + TASK_X, y + LIST_Y, ROW_WIDTH, rows * ROW_STRIDE - 1);
        try {
            for (int index = 0; index < taskPage.size(); index++) {
                double row = taskPage.row(index);
                if (row > -1 && row < rows) drawTask(context, font, withProcessing(taskPage.get(index)),
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
            context.drawTexture(NETextures.Crafting.widget((tier - 1) * TIER_WIDTH, 0, TIER_WIDTH, TIER_HEIGHT),
                x + TIER_X, y + TIER_Y, TIER_WIDTH, TIER_HEIGHT);
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

    /**
     * A running task drawn straight on the list's own row, without a card. As on 1.7.10, the whole row is the
     * progress bar: the state colour sweeps left to right as the machines progress, and the rest keeps a faint tint
     * of the same colour so the row still reads as running.
     */
    private void drawTask(GUIContext context, Font font, ComputationCpuItemEntry entry, float x, float y) {
        var processing = entry.processing();
        int rgb = switch (processing.state()) {
            case WAITING -> 0xE2AA4F;
            case PAUSED -> 0x90909F;
            default -> ComputationCards.PROGRESS_FILL & 0xFFFFFF;
        };
        int left = Math.round(x), top = Math.round(y), right = left + ROW_WIDTH, bottom = top + ROW_HEIGHT;
        float progress = processing.hasProgress() ? opening.value(cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(processing.progress(), 0, 1)) : 0;
        int filled = Math.round(ROW_WIDTH * progress);
        if (filled < ROW_WIDTH) context.graphics.fill(left + filled, top, right, bottom, rgb | 0x30000000);
        if (filled > 0) context.graphics.fill(left, top, left + filled, bottom, rgb | 0x90000000);
        key(context, entry.key(), x + ROW_WIDTH - 19, y + 3, 1.0F);
        context.graphics.flush();
        ComputationCpuSelector.fittedText(context, font, entry.key().getDisplayName().getString(),
            x + 3, y + 3, ROW_WIDTH - 23, 0.5F, TEXT_LIGHT);
        Component amount = entry.active().signum() > 0
            ? GuiText.Crafting.text(entry.amount(entry.active(), false))
            : GuiText.Scheduled.text(entry.amount(entry.pending(), false));
        ComputationCpuSelector.fittedText(context, font, amount.getString(),
            x + 3, y + 9, ROW_WIDTH - 23, 0.5F, TEXT_LIGHT);
        Component state = processing.hasProgress() && processing.state() != ECOProcessingProgress.State.WAITING
            ? Component.literal(Math.round(processing.progress() * 100) + "%")
            : Component.translatable("gui.neoecoae.processing.state." + processing.state().name().toLowerCase(Locale.ROOT));
        ComputationCpuSelector.fittedText(context, font, state.getString(), x + 3, y + 15, ROW_WIDTH - 23, 0.5F, TEXT_LIGHT);
    }
    /** AE2's status cell: green while crafting, yellow while scheduled, amounts beside the stack, no extra marks. */
    static void drawItem(GUIContext context, Font font, ComputationCpuItemEntry entry, float x, float y,
            boolean colored) {
        if (colored) {
            int color = entry.active().signum() > 0 ? AEColor.GREEN.blackVariant
                : entry.pending().signum() > 0 ? AEColor.YELLOW.blackVariant : 0;
            if (color != 0) context.graphics.fill(Math.round(x), Math.round(y), Math.round(x + ITEM_WIDTH),
                Math.round(y + ITEM_HEIGHT), color | 0x5A000000);
        }
        key(context, entry.key(), x + ITEM_WIDTH - 19, y + 3, 1.0F);
        drawItemDescription(context, font, itemDescription(entry, false), x, y);
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
            context.graphics.drawString(font, value, 0, 0, 0xFF000000 | TEXT_LIGHT, false);
            context.graphics.pose().popPose();
        }
    }

    static List<Component> itemDescription(ComputationCpuItemEntry item, boolean full) {
        List<Component> lines = new ArrayList<>();
        if (item.stored().signum() > 0) lines.add(GuiText.FromStorage.text(item.amount(item.stored(), full)));
        if (item.active().signum() > 0) lines.add(GuiText.Crafting.text(item.amount(item.active(), full)));
        if (item.pending().signum() > 0) lines.add(GuiText.Scheduled.text(item.amount(item.pending(), full)));
        if (item.waitingForMachine()) lines.add(Component.translatable("gui.neoecoae.processing.state.waiting"));
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

    /** Text only: the artwork's box is the frame, so LDLib's rounded background and focus overlay are never drawn. */
    private static final class SearchField extends TextField {
        @Override
        public void drawBackgroundTexture(GUIContext context) {}

        @Override
        public void drawBackgroundOverlay(GUIContext context) {}
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
        private int itemOffset;
        private long cpuScrollSequence, itemScrollSequence;
        private boolean viewing;
        private AEKey processingDetailKey;
        private int processingDetailPage;
        private List<ComputationCpuItemEntry> processingItems = List.of();
        private CompoundTag processingCached;
        private long processingSyncTick = Long.MIN_VALUE;
        private int taskOffset;
        private long taskScrollSequence;
        private CompoundTag itemActionResponse = new CompoundTag();
        private String query = "";
        private boolean focusPending;
        // Running tasks keep the order in which they were first observed; earlier tasks stay on top.
        private final LinkedHashSet<AEKey> taskOrder = new LinkedHashSet<>();
        private int taskSerial = -1;
        private UUID taskJob;
        private int rows = ROWS;

        private ServerState(Source source, CpuSelectionState.Identities<ICraftingCPU> identities) {
            this.source = source;
            selection = new CpuSelectionState<>(ROWS, identities);
        }

        private void refresh() {
            selection.update(source.cpus().get(), ICraftingCPU::isBusy,
                cpu -> cpu.getName() == null ? null : cpu.getName().getString());
        }

        /** The client reports its stretched row count so the buffered pages cover what it shows. */
        private void rows(int value) {
            int next = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(value, ROWS, MAX_ROWS);
            if (next == rows) return;
            rows = next;
            selection.rows(rows);
            cached = null;
        }

        private void select(int serial) {
            if (!source.canInteract().getAsBoolean()) return;
            refresh();
            if (serial != selection.selectedSerial()) {
                itemOffset = 0;
                taskOffset = 0;
                focusPending = !query.isEmpty();
            }
            if (!selection.select(serial)) return;
            viewing = true;
            processingDetailKey = null;
            processingItems = List.of();
            processingCached = null;
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
            itemOffset = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(request.getInt("offset") / ITEM_COLS, 0, maxItemRows(total, rows)) * ITEM_COLS;
            itemScrollSequence = request.getLong("sequence");
            cached = null;
            processingCached = null;
        }

        private void scrollTasks(CompoundTag request) {
            if (!source.canInteract().getAsBoolean()) return;
            refresh();
            int total = tasks(selection.selected(), selectedItems(selection.selected())).size();
            taskOffset = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(request.getInt("offset"), 0, Math.max(0, total - rows));
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

        private void processingDetails(int serial, CompoundTag request) {
            if (!source.canInteract().getAsBoolean()) return;
            if (serial < 0) {
                viewing = false;
                processingDetailKey = null;
                processingCached = null;
                return;
            }
            if (serial != selection.selectedSerial() || selection.selected() == null) return;
            AEKey key = AEKey.fromTagGeneric(request.getCompound("key"));
            if (key == null || !(selection.selected() instanceof ECOCraftingCPU eco)
                || (eco.getLogic().getWaitingFor(key) <= 0 && eco.getLogic().getPendingOutputs(key) <= 0)) return;
            viewing = true;
            processingDetailKey = key;
            processingDetailPage = Math.max(0, request.getInt("page"));
            processingCached = null;
        }

        private void itemAction(int serial, CompoundTag request) {
            if (!source.canInteract().getAsBoolean()) return;
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
            var job = jobId(cpu);
            String action = request.getString("action");
            boolean opening = action.equals("open") || action.equals("open_machine") || action.equals("stop") || action.equals("resume");
            if ((!action.equals("inspect") && !opening) || job == null
                || (opening && (!request.hasUUID("job") || !job.equals(request.getUUID("job"))))) {
                response.putString("error", "gui.neoecoae.processing.task_changed");
                return;
            }
            response.putUUID("job", job);
            var eco = cpu instanceof ECOCraftingCPU value ? value : null;
            if (action.equals("stop") || action.equals("resume")) {
                if (eco != null && eco.getLogic().setProcessingStepStopped(key, action.equals("stop"))) {
                    response.putBoolean("close", true);
                    cached = null;
                } else response.putString("error", "gui.neoecoae.processing.already_dispatched");
            }
            var grid = eco != null ? eco.getGrid()
                : cpu instanceof CraftingCPUCluster cluster ? cluster.getGrid() : null;
            var sources = grid != null && grid.getCraftingService() instanceof appeng.me.service.CraftingService crafting
                ? cn.dancingsnow.neoecoae.compat.ae2.ECOProviderActions.find(patterns(cpu, key, crafting), crafting)
                : List.<cn.dancingsnow.neoecoae.compat.ae2.ECOProviderActions.Source>of();
            var list = new ListTag();
            for (var source : sources) list.add(source.describe());
            response.put("sources", list);
            // Machines come from the processing monitor's scan of this job's destinations; AE2 jobs have none.
            if (eco != null) eco.getLogic().getProcessingProgress(key);
            var targets = eco == null ? List.<cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target>of()
                : eco.getLogic().getProcessingMonitor().machineTargets(key);
            // GT machines open their own LDLib UI; every other machine (and FX cores, as their host) uses its block.
            var machines = cn.dancingsnow.neoecoae.compat.ae2.ECOProviderActions.findMachines(targets, cpu instanceof ECOCraftingCPU value ? value.getLevel() : null);
            var blockMachines = new ArrayList<ECOProcessingMachineActions.Machine>();
            if (this.source.player() != null) {
                for (var machine : ECOProcessingMachineActions.find(this.source.player().getServer(), targets)) {
                    if (machines.stream().noneMatch(gt -> gt.blockEntity().getLevel() == machine.level()
                            && gt.blockEntity().getBlockPos().equals(machine.pos()))) blockMachines.add(machine);
                }
            }
            var machineList = new ListTag();
            for (var machine : machines) machineList.add(machine.describe());
            for (var machine : blockMachines) machineList.add(machine.describe());
            response.put("machines", machineList);
            response.putBoolean("stopped", eco != null && eco.getLogic().isProcessingStepStopped(key));
            response.putBoolean("canStop", eco != null && eco.getLogic().canControlProcessingStep(key));
            if (action.equals("open_machine")) {
                var block = blockMachines.stream().filter(machine -> machine.id().equals(request.getString("machine"))).findFirst();
                if (block.isPresent()) {
                    String error = this.source.player() instanceof net.minecraft.server.level.ServerPlayer player
                        ? block.get().open(player) : ECOProcessingMachineActions.OPEN_UNAVAILABLE;
                    if (error != null) response.putString("error", error);
                    else response.putBoolean("close", true);
                    return;
                }
            }
            if (action.equals("open")) {
                var target = sources.stream().filter(source -> source.id().equals(request.getString("source"))).findFirst();
                try {
                    if (!(this.source.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        || target.isEmpty() || !target.get().open(player)) response.putString("error", "gui.neoecoae.processing.open_unavailable");
                } catch (RuntimeException | LinkageError unsupported) {
                    response.putString("error", "gui.neoecoae.processing.open_unavailable");
                }
            }
            if (action.equals("open_machine")) {
                var target = machines.stream().filter(machine -> machine.id().equals(request.getString("machine"))).findFirst();
                try {
                    if (!(this.source.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        || target.isEmpty() || !target.get().openMachine(player)) {
                        response.putString("error", "gui.neoecoae.processing.machine_open_unavailable");
                    } else response.putBoolean("close", true);
                } catch (RuntimeException | LinkageError unsupported) {
                    response.putString("error", "gui.neoecoae.processing.machine_open_unavailable");
                }
            }
        }

        private CompoundTag processingSnapshot() {
            long tick = source.gameTime().getAsLong();
            // Machine progress is only tracked for ECO jobs; AE2 tasks show their state without a fill.
            var cpu = selection.selected() instanceof ECOCraftingCPU eco ? eco : null;
            boolean active = viewing && source.canInteract().getAsBoolean() && cpu != null;
            if (active) cpu.getLogic().refreshProcessingProgress();
            if (processingCached != null && tick >= processingSyncTick && tick - processingSyncTick < 10) return processingCached;
            processingSyncTick = tick;
            var tag = new CompoundTag();
            tag.putInt("serial", selection.selectedSerial());
            var entries = new ListTag();
            if (active) {
                for (var item : processingItems) {
                    if (item.active().signum() <= 0 && item.pending().signum() <= 0) continue;
                    var progress = cpu.getLogic().getProcessingProgress(item.key());
                    if (progress == null || (progress.machineCount() == 0 && !progress.refreshing())) continue;
                    var entry = new CompoundTag();
                    entry.put("key", item.key().toTagGeneric());
                    entry.put("progress", progress.forStatusSync().page(item.key().equals(processingDetailKey) ? processingDetailPage : -1).write());
                    entries.add(entry);
                }
            }
            tag.put("items", entries);
            return processingCached = tag;
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
            tag.putInt("cpuPageStart", Math.max(0, selection.offset() - rows));
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
            // Processing progress is sampled separately, only for rows the client shows (see processingSnapshot).
            Map<AEKey, ComputationCpuItemEntry> visible = new LinkedHashMap<>();
            tag.putInt("itemTotal", all.size());
            itemOffset = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(itemOffset / ITEM_COLS, 0, maxItemRows(all.size(), rows)) * ITEM_COLS;
            tag.putInt("itemOffset", itemOffset);
            int itemPageStart = Math.max(0, itemOffset - (ITEM_COLS * rows));
            tag.putInt("itemPageStart", itemPageStart);
            tag.put("items", page(selectedCpu, all, itemPageStart, Math.min(all.size(), itemOffset + (ITEM_COLS * rows) * 2), visible));
            tag.putInt("taskTotal", running.size());
            taskOffset = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(taskOffset, 0, Math.max(0, running.size() - rows));
            tag.putInt("taskOffset", taskOffset);
            int taskPageStart = Math.max(0, taskOffset - rows);
            tag.putInt("taskPageStart", taskPageStart);
            tag.put("tasks", page(selectedCpu, running, taskPageStart, Math.min(running.size(), taskOffset + rows * 2), visible));
            processingItems = List.copyOf(visible.values());
            return cached = tag;
        }

        private static void writeTier(CompoundTag tag, @Nullable IECOTier tier) {
            tag.putInt("tier", tier == null ? 0 : tier.getTier());
            tag.putBoolean("tierBuiltin", tier == null || tier instanceof ECOTier);
            tag.putString("tierName", tier == null || tier instanceof ECOTier ? "" : tier.getPanelTierName().getString());
        }

        private ListTag page(@Nullable ICraftingCPU cpu, List<ComputationCpuItemEntry> entries, int from, int to,
                Map<AEKey, ComputationCpuItemEntry> visible) {
            ListTag page = new ListTag();
            if (cpu == null) return page;
            var registries = source.registries();
            for (ComputationCpuItemEntry item : entries.subList(Math.min(from, to), to)) {
                visible.putIfAbsent(item.key(), item);
                page.add(item.write(registries));
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
                // Admission-blocked pending work (for example an overheated workstation) is listed as a task too.
                if (item.active().signum() <= 0 && !blockedByMachine(cpu, item)) continue;
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

        private static boolean blockedByMachine(@Nullable ICraftingCPU cpu, ComputationCpuItemEntry item) {
            if (!(cpu instanceof ECOCraftingCPU eco) || item.pending().signum() <= 0) return false;
            var progress = eco.getLogic().getProcessingProgress(item.key());
            return progress != null && progress.state() == ECOProcessingProgress.State.WAITING;
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
                logic.isBatchedOutput(key), ECOProcessingProgress.EMPTY, logic.isProcessingStepStopped(key));
        }
    }
}
