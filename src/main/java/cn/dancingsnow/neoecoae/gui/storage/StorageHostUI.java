package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.vfyjxf.taffy.style.FlexDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** The storage controller surface, backed by the current storage implementation. */
public final class StorageHostUI {
    public static final int ROOT_WIDTH = 272;
    public static final int ROOT_HEIGHT = 216;

    private static final int CHART_LEFT = 63;
    private static final int CHART_TOP = 22;
    private static final int GRAPH_BAR_OFFSET_LEFT = 9;
    private static final int GRAPH_BAR_OFFSET_TOP = 0;
    private static final int BAR_WIDTH = 32;
    private static final int BAR_HEIGHT = 92;
    private static final int GRAPH_HEIGHT = 16;
    private static final int GRAPH_LABEL_HEIGHT = 10;
    private static final int GRAPH_TEXT_OFFSET_TOP = 2;
    private static final int GRAPH_TEXT_SCALE = 6;
    private static final int GRAPH_OFFSET_LEFT = -55;
    private static final int GRAPH_OFFSET_TOP = -26;
    private static final int ITEM_TYPE_GRAPH_OFFSET_LEFT = 5;
    private static final int GRAPH_FOCUSED_COLOR = 0xFFFFFFFF;
    private static final int GRAPH_UNFOCUSED_COLOR = 0x66FFFFFF;
    private static final int CELL_LIST_LEFT = 178;
    private static final int CELL_LIST_TOP = 22;
    private static final int CELL_LIST_WIDTH = 83;
    private static final int CELL_LIST_HEIGHT = 172;
    private static final int CELL_TEXT_LEFT = 10;
    private static final int CELL_TEXT_TOP = 1;
    private static final int CELL_TEXT_LINE_STEP = 13;
    private static final int INFINITE_COMPONENT_SLOT_LEFT = 146;
    private static final int INFINITE_COMPONENT_SLOT_TOP = 100;
    private static final int INFINITE_COMPONENT_SLOT_SIZE = 18;
    private static final int INVENTORY_LEFT = 7;
    private static final int INVENTORY_TOP = 129;
    private static final int INVENTORY_WIDTH = 9 * 18;
    private static final int INVENTORY_HEIGHT = 3 * 18 + 4 + 18;

    private static final int KIND_EMPTY = 0;
    private static final int KIND_ITEM = 1;
    private static final int KIND_FLUID = 2;
    private static final int KIND_OTHER = 3;

    private static final ResourceLocations TEXTURES = new ResourceLocations();

    private StorageHostUI() {
    }

    public record StorageTypeLine(
        ECOCellType type,
        int registryIndex,
        Supplier<Component> displayName,
        BooleanSupplier visible,
        LongSupplier usedTypes,
        LongSupplier totalTypes,
        LongSupplier usedBytes,
        LongSupplier totalBytes,
        Supplier<String> infiniteBytesText
    ) {
        public StorageTypeLine(
            ECOCellType type,
            int registryIndex,
            LongSupplier usedTypes,
            LongSupplier totalTypes,
            LongSupplier usedBytes,
            LongSupplier totalBytes,
            Supplier<String> infiniteBytesText
        ) {
            this(type, registryIndex, type::desc, type::visible, usedTypes, totalTypes,
                usedBytes, totalBytes, infiniteBytesText);
        }
    }

    public record Config(
        Supplier<Component> title,
        LongSupplier storedEnergy,
        LongSupplier maxEnergy,
        LongSupplier energyConsumePerTick,
        Supplier<String> totalUsedBytesText,
        Supplier<List<CellEntry>> cellEntries,
        List<StorageTypeLine> storageTypes,
        BooleanSupplier infiniteStorage,
        BooleanSupplier migratingToInfinite,
        BooleanSupplier canExtractInfiniteComponents,
        IItemHandlerModifiable infiniteComponentInventory
    ) {
    }

    public record CellEntry(
        int typeId,
        int tier,
        int kind,
        long usedTypes,
        long totalTypes,
        long usedBytes,
        long totalBytes,
        boolean infinite
    ) {
        public static final int KIND_EMPTY = StorageHostUI.KIND_EMPTY;
        public static final int KIND_ITEM = StorageHostUI.KIND_ITEM;
        public static final int KIND_FLUID = StorageHostUI.KIND_FLUID;
        public static final int KIND_OTHER = StorageHostUI.KIND_OTHER;
        /** @deprecated Use {@link #KIND_OTHER}; the legacy fallback covers all non-item/non-fluid key types. */
        @Deprecated
        public static final int KIND_GAS = KIND_OTHER;
    }

    public static UIElement create(Config config) {
        UIElement root = new UIElement().layout(layout -> layout
            .width(ROOT_WIDTH)
            .height(ROOT_HEIGHT)
        );
        root.addChild(HostElements.absolute(new UIElement(), 0, 0, ROOT_WIDTH, ROOT_HEIGHT)
            .style(style -> style.backgroundTexture(TEXTURES.infiniteBackground)));

        root.addChild(new TextElement()
            .setText(config.title().get())
            .textStyle(style -> style
                .adaptiveHeight(true)
                .adaptiveWidth(true)
                .textWrap(TextWrap.NONE)
                .textColor(0x3F3D52)
                .textShadow(false))
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8)
                .top(6)
                .width(160)
                .height(12)));

        LegacyGraphBar graphBar = new LegacyGraphBar(() -> totalMetric(config));
        graphBar.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(CHART_LEFT + GRAPH_BAR_OFFSET_LEFT)
            .top(CHART_TOP + GRAPH_BAR_OFFSET_TOP)
            .width(BAR_WIDTH)
            .height(BAR_HEIGHT));
        root.addChild(graphBar);

        addGraphs(root, graphBar, config);

        root.addChild(storageTypeList(config));
        root.addChild(infiniteComponentSlot(
            config.canExtractInfiniteComponents(),
            config.infiniteComponentInventory()
        ).layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(INFINITE_COMPONENT_SLOT_LEFT)
            .top(INFINITE_COMPONENT_SLOT_TOP)
            .width(INFINITE_COMPONENT_SLOT_SIZE)
            .height(INFINITE_COMPONENT_SLOT_SIZE)));
        root.addChild(playerInventory());
        return root;
    }

    private static UIElement infiniteComponentSlot(
        BooleanSupplier canExtractInfiniteComponents,
        IItemHandlerModifiable infiniteComponentInventory
    ) {
        UIElement wrapper = new UIElement();
        ItemHandlerSlot slot = new ItemHandlerSlot(infiniteComponentInventory, 0)
            .setCanTake(player -> canTakeInfiniteComponent(player, canExtractInfiniteComponents));
        wrapper.addChild(new ItemSlot(slot));
        return wrapper;
    }

    private static boolean canTakeInfiniteComponent(
        @Nullable Player player,
        BooleanSupplier canExtractInfiniteComponents
    ) {
        if (canExtractInfiniteComponents.getAsBoolean()) {
            return true;
        }
        if (player != null) {
            player.displayClientMessage(
                Component.translatable("tooltip.neoecoae.storage.infinite_component_locked"),
                true
            );
        }
        return false;
    }

    private static void addGraphs(UIElement root, LegacyGraphBar graphBar, Config config) {
        root.addChild(graph(
            graphBar, 10, 32, 60, TEXTURES.fluidPercent,
            () -> new Metric(config.energyConsumePerTick().getAsLong(), 0L),
            StorageHostUI::energyUsageText, true));
        root.addChild(graph(
            graphBar, 87, 29, 59, TEXTURES.itemType,
            () -> new Metric(config.storedEnergy().getAsLong(), config.maxEnergy().getAsLong()),
            metric -> percentText("gui.neoecoae.storage.legacy.graph.energy_stored", metric), false));
        root.addChild(graph(
            graphBar, 10, 51, 60, TEXTURES.fluidPercent,
            () -> Metric.EMPTY,
            metric -> Component.translatable("gui.neoecoae.storage.legacy.graph.total_bytes",
                config.totalUsedBytesText().get()).withColor(HostText.PRIMARY), true));
        root.addChild(graph(
            graphBar, 87, 48, 59, TEXTURES.itemType,
            () -> totalMetric(config),
            metric -> percentText("gui.neoecoae.storage.legacy.graph.total_usage", metric), false));
    }

    private static boolean isInfinite(Config config) {
        return config.infiniteStorage().getAsBoolean() || config.migratingToInfinite().getAsBoolean();
    }

    private static UIElement storageTypeList(Config config) {
        ScrollerView list = new ScrollerView();
        HostElements.absolute(list, CELL_LIST_LEFT, CELL_LIST_TOP, CELL_LIST_WIDTH, CELL_LIST_HEIGHT);
        list.scrollerStyle(style -> style.horizontalScrollDisplay(ScrollDisplay.NEVER));
        // ScrollerView paints its default border on the viewport, not the list itself.
        list.viewPort(view -> view
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY))
            .layout(layout -> layout.paddingAll(0)));
        list.viewContainer(view -> view.layout(layout -> layout.paddingAll(2).gapAll(5)
            .flexDirection(FlexDirection.COLUMN)));
        for (StorageTypeLine line : config.storageTypes()) {
            // Both sides must register the same bindings in the same order, even before the
            // client's host mode is current. Sync visibility instead of changing the UI tree.
            UIElement block = HostElements.syncedDisplay(() -> line.visible().getAsBoolean()
                && (line.usedTypes().getAsLong() > 0
                || line.usedBytes().getAsLong() > 0 || safeEntries(config.cellEntries()).stream()
                .anyMatch(entry -> entry.typeId() == line.registryIndex())));
            block.layout(layout -> layout.widthPercent(100).gapAll(2).flexDirection(FlexDirection.COLUMN));
            block.addChild(HostElements.textSegment(line.displayName(),
                () -> HostText.storageTypeAccentColor(line.type(), line.registryIndex()))
                .textStyle(style -> style.fontSize(9).adaptiveWidth(false).textWrap(TextWrap.NONE))
                .layout(layout -> layout.widthPercent(100).height(11)));
            block.addChild(compactLabel(() -> storageTypesText(config, line), HostText.PRIMARY));
            block.addChild(storageProgressBar(line.usedTypes(), line.totalTypes(), () -> !isInfinite(config)));
            block.addChild(compactLabel(() -> storageBytesText(config, line), HostText.PRIMARY));
            block.addChild(storageProgressBar(line.usedBytes(), line.totalBytes(), () -> !isInfinite(config)));
            list.addScrollViewChild(block);
        }
        return list;
    }

    private static UIElement compactLabel(Supplier<Component> text, int color) {
        return HostElements.textSegment(text, () -> color)
            .textStyle(style -> style.fontSize(6).adaptiveWidth(false).textWrap(TextWrap.NONE))
            .layout(layout -> layout.widthPercent(100).height(8));
    }

    private static Component storageTypesText(Config config, StorageTypeLine line) {
        String used = HostText.ae2Amount(line.usedTypes().getAsLong());
        if (isInfinite(config)) {
            return usedOnlyText("gui.neoecoae.storage.legacy.cell_types", used);
        }
        return Component.translatable("gui.neoecoae.storage.legacy.cell_types", used,
            formatCapacity(line.totalTypes().getAsLong())).withColor(HostText.PRIMARY);
    }

    private static Component storageBytesText(Config config, StorageTypeLine line) {
        if (isInfinite(config)) {
            return usedOnlyText("gui.neoecoae.storage.legacy.cell_bytes", line.infiniteBytesText().get());
        }
        return Component.translatable("gui.neoecoae.storage.legacy.cell_bytes",
            HostText.ae2Amount(line.usedBytes().getAsLong()),
            formatCapacity(line.totalBytes().getAsLong())).withColor(HostText.PRIMARY);
    }

    private static Component usedOnlyText(String translationKey, String used) {
        String marker = "\\u0001";
        String rendered = Component.translatable(translationKey, used, marker).getString();
        int markerIndex = rendered.indexOf(marker);
        if (markerIndex < 0) {
            return Component.literal(rendered).withColor(HostText.PRIMARY);
        }
        String prefix = rendered.substring(0, markerIndex);
        int separator = prefix.lastIndexOf('/');
        if (separator >= 0) {
            prefix = prefix.substring(0, separator).stripTrailing();
        }
        return Component.literal(prefix).withColor(HostText.PRIMARY);
    }

    private static UIElement storageProgressBar(LongSupplier used, LongSupplier total, BooleanSupplier visible) {
        UIElement wrapper = HostElements.syncedDisplay(visible);
        wrapper.layout(layout -> layout.widthPercent(100).height(4));
        wrapper.addChild(new ProgressBar().label(label -> label.setText(""))
            .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
            // A nonpositive total denotes unbounded capacity and has no finite usage percentage.
            .bind(DataBindingBuilder.floatValS2C(() -> HostText.usageRatio(
                used.getAsLong(), total.getAsLong())).build())
            .addClass("eco-host-progress")
            .layout(layout -> layout.widthPercent(100).height(4)));
        return wrapper;
    }

    private static LegacyGraphElement graph(
        LegacyGraphBar graphBar,
        int left,
        int top,
        int width,
        SpriteTexture background,
        Supplier<Metric> metric,
        Function<Metric, Component> text,
        boolean leftAlign
    ) {
        LegacyGraphElement graph = new LegacyGraphElement(graphBar, background, metric, text, leftAlign);
        graph.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(CHART_LEFT + GRAPH_OFFSET_LEFT + left)
            .top(CHART_TOP + GRAPH_OFFSET_TOP + top)
            .width(width)
            .height(GRAPH_HEIGHT));
        return graph;
    }

    private static UIElement playerInventory() {
        InventorySlots inventory = new InventorySlots();
        inventory.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(INVENTORY_LEFT)
            .top(INVENTORY_TOP)
            .width(INVENTORY_WIDTH)
            .height(INVENTORY_HEIGHT));
        inventory.apply(slot -> slot.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.getChildren().forEach(child -> child.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.hotbar.getLayout().marginTop(4);
        return inventory;
    }

    private static Metric totalMetric(Config config) {
        if (config.infiniteStorage().getAsBoolean() || config.migratingToInfinite().getAsBoolean()) {
            return new Metric(0L, -1L);
        }
        long used = 0L;
        long total = 0L;
        for (CellEntry entry : safeEntries(config.cellEntries())) {
            if (entry.infinite()) {
                return new Metric(0L, -1L);
            }
            used = saturatingAdd(used, Math.max(0L, entry.usedBytes()));
            total = saturatingAdd(total, Math.max(0L, entry.totalBytes()));
        }
        return new Metric(used, total);
    }

    private static Metric kindMetric(Config config, int kind) {
        if (config.infiniteStorage().getAsBoolean() || config.migratingToInfinite().getAsBoolean()) {
            return new Metric(0L, -1L);
        }
        long used = 0L;
        long total = 0L;
        for (CellEntry entry : safeEntries(config.cellEntries())) {
            if (entry.kind() != kind) {
                continue;
            }
            if (entry.infinite()) {
                return new Metric(0L, -1L);
            }
            used = saturatingAdd(used, Math.max(0L, entry.usedBytes()));
            total = saturatingAdd(total, Math.max(0L, entry.totalBytes()));
        }
        return new Metric(used, total);
    }

    private static Metric kindTypeMetric(Config config, int kind) {
        if (config.infiniteStorage().getAsBoolean() || config.migratingToInfinite().getAsBoolean()) {
            return new Metric(0L, -1L);
        }
        long used = 0L;
        long total = 0L;
        for (CellEntry entry : safeEntries(config.cellEntries())) {
            if (entry.kind() != kind) {
                continue;
            }
            if (entry.infinite()) {
                return new Metric(0L, -1L);
            }
            used = saturatingAdd(used, Math.max(0L, entry.usedTypes()));
            total = saturatingAdd(total, Math.max(0L, entry.totalTypes()));
        }
        return new Metric(used, total);
    }

    private static boolean hasKind(Config config, int kind) {
        for (CellEntry entry : safeEntries(config.cellEntries())) {
            if (entry.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    private static List<CellEntry> safeEntries(Supplier<List<CellEntry>> supplier) {
        List<CellEntry> entries = supplier.get();
        return entries == null ? List.of() : entries;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static Component percentText(String key, Metric metric) {
        Object value = metric.total() < 0L
            ? Component.translatable("gui.neoecoae.storage.infinite_value")
            : metric.total() <= 0L ? "0%" : HostText.percent(metric.used(), metric.total());
        return Component.translatable(key, value).withColor(HostText.PRIMARY);
    }

    private static Component typeText(String key, Metric metric) {
        String used = compactTypeAmount(metric.used());
        String total = metric.total() < 0L ? "\u221E" : compactTypeAmount(metric.total());
        return Component.translatable(key, used, total).withColor(HostText.PRIMARY);
    }

    private static String compactTypeAmount(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        double scaled = safe;
        String[] suffixes = {"k", "M", "G", "T", "P", "E"};
        int suffixIndex = -1;
        while (scaled >= 1_000.0D && suffixIndex + 1 < suffixes.length) {
            scaled /= 1_000.0D;
            suffixIndex++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f", scaled)
            .replaceFirst("\\.0$", "") + suffixes[suffixIndex];
    }

    private static Component energyUsageText(Metric metric) {
        return Component.translatable("gui.neoecoae.storage.legacy.graph.energy_usage",
            HostText.ae2Amount(metric.used())).withColor(HostText.PRIMARY);
    }

    private record Metric(long used, long total) {
        private static final Metric EMPTY = new Metric(0L, 0L);
    }

    private static final class LegacyGraphElement extends UIElement implements IBindable<Component> {
        private final LegacyGraphBar graphBar;
        private final SpriteTexture background;
        private final boolean leftAlign;
        private boolean focused;
        private Component syncedText = Component.empty();

        private LegacyGraphElement(
            LegacyGraphBar graphBar,
            SpriteTexture background,
            Supplier<Metric> metricSupplier,
            Function<Metric, Component> textSupplier,
            boolean leftAlign
        ) {
            this.graphBar = graphBar;
            this.background = background;
            this.leftAlign = leftAlign;
            // Sync the formatted label so unbounded byte totals never pass through a long.
            bind(DataBindingBuilder.componentS2C(() -> textSupplier.apply(metricSupplier.get())).build());
            // Graph focus is selected by the layout; moving the mouse must not replace the displayed metric.
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);
            boolean hovered = isMouseOver(
                getPositionX(),
                getPositionY() + GRAPH_LABEL_HEIGHT,
                getSizeWidth(),
                getSizeHeight() - GRAPH_LABEL_HEIGHT,
                guiContext.mouseX,
                guiContext.mouseY
            );
            int color = graphBar.isFocused(this) || hovered ? GRAPH_FOCUSED_COLOR : GRAPH_UNFOCUSED_COLOR;
            guiContext.drawTexture(background.copy().setColor(color),
                getPositionX(), getPositionY() + GRAPH_LABEL_HEIGHT, getSizeWidth(), getSizeHeight() - GRAPH_LABEL_HEIGHT);
            drawText(guiContext, syncedText);
        }

        private void drawText(GUIContext guiContext, Component text) {
            Font font = Minecraft.getInstance().font;
            float scale = GRAPH_TEXT_SCALE / 10.0F;
            float availableWidth = getSizeWidth() / scale;
            float textX = leftAlign ? 2.0F : Math.max(0.0F, availableWidth - font.width(text) - 2.0F);
            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().translate(
                getPositionX(), getPositionY() + GRAPH_TEXT_OFFSET_TOP, 0.0F);
            guiContext.graphics.pose().scale(scale, scale, 1.0F);
            guiContext.graphics.drawString(font, text, Math.round(textX), 0, HostText.PRIMARY, false);
            guiContext.graphics.pose().popPose();
        }

        @Override
        public Component getValue() {
            return syncedText;
        }

        @Override
        public IDataSource<Component> setValue(@Nullable Component value) {
            syncedText = value == null ? Component.empty() : value;
            return this;
        }
    }

    private static final class LegacyGraphBar extends UIElement implements IBindable<CompoundTag> {
        private static final int TOP_U = 1;
        private static final int TOP_V = 246;
        private static final int MID_U = 34;
        private static final int MID_V = 250;
        private static final int CAP_HEIGHT = 8;
        private static final int INFINITE_COLOR = 0xD8CA6CFF;

        private final Supplier<Metric> defaultMetric;
        private final StorageHostAnimatedRatio animatedRatio = new StorageHostAnimatedRatio();
        private Metric syncedMetric = Metric.EMPTY;
        private CompoundTag syncedTag = new CompoundTag();

        private LegacyGraphBar(Supplier<Metric> defaultMetric) {
            this.defaultMetric = defaultMetric;
            bind(DataBindingBuilder.create(
                () -> metricTag(this.defaultMetric.get()),
                ignored -> {
                }
            ).syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        }

        private void focus(LegacyGraphElement graph) {
            graph.focused = true;
        }

        private boolean isFocused(LegacyGraphElement graph) {
            return graph.focused;
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);
            Metric metric = syncedMetric;
            boolean infinite = metric.total() < 0L;
            float targetRatio = infinite ? -1.0F : HostText.usageRatio(metric.used(), metric.total());
            animatedRatio.setTarget(targetRatio);
            float ratio = (float) animatedRatio.value();
            if (ratio <= 0.0F) {
                return;
            }
            float bottom = getPositionY() + getSizeHeight();
            float bodyHeight = Math.max(0.0F, getSizeHeight() - CAP_HEIGHT);
            float barHeight = Math.round(bodyHeight * ratio);
            float top = bottom - barHeight - CAP_HEIGHT;
            int color = infinite ? INFINITE_COLOR : HostText.gaugeColor(ratio);
            SpriteTexture topTexture = TEXTURES.gaugeTop.copy().setColor(color);
            SpriteTexture midTexture = TEXTURES.gaugeMiddle.copy().setColor(color);
            SpriteTexture bottomTexture = TEXTURES.gaugeBottom.copy().setColor(color);
            guiContext.drawTexture(topTexture, getPositionX(), top, getSizeWidth(), CAP_HEIGHT);
            float middleStart = top + CAP_HEIGHT / 2.0F + 1.0F;
            float middleEnd = bottom - CAP_HEIGHT / 2.0F + 1.0F;
            for (float y = middleStart; y < middleEnd; y += 1.0F) {
                guiContext.drawTexture(midTexture, getPositionX(), y, getSizeWidth(), 4.0F);
            }
            guiContext.drawTexture(bottomTexture, getPositionX(), bottom - CAP_HEIGHT, getSizeWidth(), CAP_HEIGHT);
        }

        @Override
        public CompoundTag getValue() {
            return syncedTag.copy();
        }

        @Override
        public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
            syncedTag = value == null ? new CompoundTag() : value.copy();
            syncedMetric = readMetric(syncedTag);
            return this;
        }
    }

    private static final class CellListElement extends UIElement implements IBindable<CompoundTag> {
        private static final String NBT_CELLS = "c";
        private static final String NBT_TYPE = "i";
        private static final String NBT_TIER = "r";
        private static final String NBT_KIND = "k";
        private static final String NBT_USED_TYPES = "ut";
        private static final String NBT_TOTAL_TYPES = "tt";
        private static final String NBT_USED_BYTES = "ub";
        private static final String NBT_TOTAL_BYTES = "tb";
        private static final String NBT_INFINITE = "inf";
        private static final String NBT_META = "m";
        private static final String NBT_AMOUNTS = "a";
        private static final String NBT_FLAGS = "f";
        private static final int MAX_SYNCED_CELLS = 48;
        // LowDragLib carries this binding in a small fixed-size advanced-data packet (about 2.4 KiB).
        // Keep headroom for the packet envelope so a full cell list cannot overrun the reader buffer.
        private static final int MAX_SYNC_BYTES = 2_100;
        private static final int ROW_HEIGHT = 26;
        private static final int ROW_GAP = 2;
        private static final int ROW_STRIDE = ROW_HEIGHT + ROW_GAP;
        private static final float TEXT_SCALE = 0.6F;

        private final Supplier<List<CellEntry>> entries;
        private List<CellEntry> syncedEntries = List.of();
        private CompoundTag syncedTag = new CompoundTag();
        private int scrollOffset;

        private CellListElement(Supplier<List<CellEntry>> entries) {
            this.entries = entries;
            bind(DataBindingBuilder.create(
                () -> writeEntries(this.entries.get()),
                ignored -> {
                }
            ).syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
            addEventListener(UIEvents.MOUSE_WHEEL, event -> {
                if (syncedEntries.size() <= visibleRows()) {
                    scrollOffset = 0;
                    return;
                }
                scrollOffset = Math.clamp(scrollOffset + (event.deltaY < 0 ? 1 : -1),
                    0, Math.max(0, syncedEntries.size() - visibleRows()));
                event.stopImmediatePropagation();
            });
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                CellEntry entry = entryAt(event.y);
                if (entry == null) {
                    return;
                }
                event.hoverTooltips = HoverTooltips.empty().append(cellTooltip(entry));
            });
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);
            scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, syncedEntries.size() - visibleRows()));
            if (syncedEntries.isEmpty()) {
                return;
            }
            float x = getPositionX();
            float y = getPositionY();
            guiContext.graphics.enableScissor(
                Math.round(x),
                Math.round(y),
                Math.round(x + getSizeWidth()),
                Math.round(y + getSizeHeight())
            );
            int visible = Math.min(visibleRows() + 1, syncedEntries.size() - scrollOffset);
            for (int row = 0; row < visible; row++) {
                float rowY = y + row * ROW_STRIDE - (scrollOffset * ROW_STRIDE);
                drawRow(guiContext, syncedEntries.get(scrollOffset + row), x, rowY);
            }
            guiContext.graphics.disableScissor();
        }

        private void drawRow(GUIContext guiContext, CellEntry entry, float x, float y) {
            SpriteTexture cellBackground = switch (entry.tier()) {
                case 3 -> TEXTURES.cellL9;
                case 2 -> TEXTURES.cellL6;
                default -> TEXTURES.cellL4;
            };
            SpriteTexture cellKind = switch (entry.kind()) {
                case KIND_ITEM -> TEXTURES.cellItem;
                case KIND_FLUID -> TEXTURES.cellFluid;
                case KIND_OTHER -> TEXTURES.cellGas;
                default -> TEXTURES.cellEmpty;
            };
            guiContext.drawTexture(cellBackground, x, y, CELL_LIST_WIDTH, ROW_HEIGHT);
            guiContext.drawTexture(cellKind, x, y, 8.0F, ROW_HEIGHT);

            Font font = Minecraft.getInstance().font;
            Component type = Component.translatable("gui.neoecoae.storage.legacy.cell_info",
                cellKindName(entry), tierName(entry.tier()));
            Component types = Component.translatable("gui.neoecoae.storage.legacy.cell_types",
                HostText.ae2Amount(entry.usedTypes()), entry.totalTypes() == 0L ? "\u221E" : formatCapacity(entry.totalTypes()));
            Component bytes = Component.translatable("gui.neoecoae.storage.legacy.cell_bytes",
                HostText.ae2Amount(entry.usedBytes()), formatCapacity(entry.totalBytes()));
            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().translate(x + CELL_TEXT_LEFT, y + CELL_TEXT_TOP, 0.0F);
            guiContext.graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            guiContext.graphics.drawString(font, type, 0, 0, HostText.PRIMARY, false);
            guiContext.graphics.drawString(font, types, 0, CELL_TEXT_LINE_STEP, HostText.PRIMARY, false);
            guiContext.graphics.drawString(font, bytes, 0, CELL_TEXT_LINE_STEP * 2, HostText.PRIMARY, false);
            guiContext.graphics.pose().popPose();
        }

        private Component cellTooltip(CellEntry entry) {
            return Component.translatable("gui.neoecoae.storage.legacy.cell_tooltip",
                cellKindName(entry), tierName(entry.tier()),
                HostText.ae2Amount(entry.usedBytes()), formatCapacity(entry.totalBytes()));
        }

        @Nullable
        private CellEntry entryAt(double mouseY) {
            float localY = (float)mouseY - getPositionY();
            if (localY < 0.0F || localY >= getSizeHeight()) {
                return null;
            }
            int row = (int)Math.floor((localY + scrollOffset * ROW_STRIDE) / ROW_STRIDE);
            if (row < 0 || row >= syncedEntries.size()) {
                return null;
            }
            float inRow = (localY + scrollOffset * ROW_STRIDE) % ROW_STRIDE;
            return inRow < ROW_HEIGHT ? syncedEntries.get(row) : null;
        }

        private int visibleRows() {
            return Math.max(1, getSizeHeight() == 0 ? 1 : (int)getSizeHeight() / ROW_STRIDE);
        }

        @Override
        public CompoundTag getValue() {
            return syncedTag.copy();
        }

        @Override
        public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
            syncedTag = value == null ? new CompoundTag() : value.copy();
            syncedEntries = readEntries(syncedTag);
            scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, syncedEntries.size() - visibleRows()));
            return this;
        }

        private static CompoundTag writeEntries(List<CellEntry> source) {
            CompoundTag result = new CompoundTag();
            int[] meta = new int[MAX_SYNCED_CELLS * 3];
            long[] amounts = new long[MAX_SYNCED_CELLS * 4];
            byte[] flags = new byte[MAX_SYNCED_CELLS];
            int count = 0;
            int remainingBytes = MAX_SYNC_BYTES;
            if (source != null) {
                for (CellEntry entry : source) {
                    if (entry == null || count >= MAX_SYNCED_CELLS) {
                        break;
                    }
                    int entrySize = 3 * Integer.BYTES + 4 * Long.BYTES + 1;
                    if (entrySize > remainingBytes) {
                        break;
                    }
                    int metaIndex = count * 3;
                    int amountIndex = count * 4;
                    meta[metaIndex] = entry.typeId();
                    meta[metaIndex + 1] = entry.tier();
                    meta[metaIndex + 2] = entry.kind();
                    amounts[amountIndex] = Math.max(0L, entry.usedTypes());
                    amounts[amountIndex + 1] = entry.totalTypes();
                    amounts[amountIndex + 2] = Math.max(0L, entry.usedBytes());
                    amounts[amountIndex + 3] = entry.totalBytes();
                    flags[count] = (byte) (entry.infinite() ? 1 : 0);
                    count++;
                    remainingBytes -= entrySize;
                }
            }
            result.putInt("n", count);
            result.putIntArray(NBT_META, java.util.Arrays.copyOf(meta, count * 3));
            result.putLongArray(NBT_AMOUNTS, java.util.Arrays.copyOf(amounts, count * 4));
            result.putByteArray(NBT_FLAGS, java.util.Arrays.copyOf(flags, count));
            return result;
        }

        private static List<CellEntry> readEntries(CompoundTag tag) {
            int count = Math.clamp(tag.getInt("n"), 0, MAX_SYNCED_CELLS);
            int[] meta = tag.getIntArray(NBT_META);
            long[] amounts = tag.getLongArray(NBT_AMOUNTS);
            byte[] flags = tag.getByteArray(NBT_FLAGS);
            if (meta.length >= count * 3 && amounts.length >= count * 4 && flags.length >= count) {
                List<CellEntry> result = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    result.add(new CellEntry(
                        meta[i * 3], Math.clamp(meta[i * 3 + 1], 1, 3),
                        Math.clamp(meta[i * 3 + 2], KIND_EMPTY, KIND_OTHER),
                        Math.max(0L, amounts[i * 4]), amounts[i * 4 + 1],
                        Math.max(0L, amounts[i * 4 + 2]), amounts[i * 4 + 3],
                        flags[i] != 0));
                }
                return List.copyOf(result);
            }
            // Accept the old representation while clients with an older menu are still connected.
            ListTag cells = tag.getList(NBT_CELLS, Tag.TAG_COMPOUND);
            List<CellEntry> result = new ArrayList<>(Math.min(cells.size(), MAX_SYNCED_CELLS));
            for (int i = 0; i < cells.size() && result.size() < MAX_SYNCED_CELLS; i++) {
                CompoundTag cell = cells.getCompound(i);
                result.add(new CellEntry(
                    cell.getInt(NBT_TYPE),
                    Math.clamp(cell.getInt(NBT_TIER), 1, 3),
                    Math.clamp(cell.getInt(NBT_KIND), KIND_EMPTY, KIND_OTHER),
                    Math.max(0L, cell.getLong(NBT_USED_TYPES)),
                    cell.getLong(NBT_TOTAL_TYPES),
                    Math.max(0L, cell.getLong(NBT_USED_BYTES)),
                    cell.getLong(NBT_TOTAL_BYTES),
                    cell.getBoolean(NBT_INFINITE)
                ));
            }
            return List.copyOf(result);
        }
    }

    private static Metric readMetric(CompoundTag tag) {
        return new Metric(Math.max(0L, tag.getLong("u")), tag.getLong("t"));
    }

    private static CompoundTag metricTag(Metric metric) {
        CompoundTag tag = new CompoundTag();
        if (metric != null) {
            tag.putLong("u", Math.max(0L, metric.used()));
            tag.putLong("t", metric.total());
        }
        return tag;
    }

    private static String formatCapacity(long value) {
        return value < 0L ? "\u221E" : HostText.ae2Amount(value);
    }

    private static String tierName(int tier) {
        return switch (tier) {
            case 3 -> "L9";
            case 2 -> "L6";
            default -> "L4";
        };
    }

    private static Component cellKindName(CellEntry entry) {
        ECOCellType registeredType = entry.typeId() < 0 ? null : NERegistries.CELL_TYPE.byId(entry.typeId());
        if (registeredType != null) {
            return Component.translatable("gui.neoecoae.storage.legacy.cell_info.custom", registeredType.desc());
        }
        return switch (entry.kind()) {
            case KIND_ITEM -> Component.translatable("gui.neoecoae.storage.legacy.cell_info.item");
            case KIND_FLUID -> Component.translatable("gui.neoecoae.storage.legacy.cell_info.fluid");
            case KIND_OTHER -> Component.translatable("gui.neoecoae.storage.legacy.cell_info.other");
            default -> Component.translatable("gui.neoecoae.storage.legacy.cell_info.empty");
        };
    }

    private static final class ResourceLocations {
        private final SpriteTexture infiniteBackground = sprite(
            "estorage_infinite_controller.png", 0, 0, ROOT_WIDTH, ROOT_HEIGHT);
        private final SpriteTexture itemPercent = sprite("estorage_controller_elements.png", 1, 232, 65, 6);
        private final SpriteTexture fluidPercent = sprite("estorage_controller_elements.png", 6, 225, 60, 6);
        private final SpriteTexture gasPercent = sprite("estorage_controller_elements.png", 1, 232, 65, 6);
        private final SpriteTexture totalPercent = sprite("estorage_controller_elements.png", 2, 239, 64, 6);
        private final SpriteTexture itemType = sprite("estorage_controller_elements.png", 1, 197, 59, 6);
        private final SpriteTexture fluidType = sprite("estorage_controller_elements.png", 1, 197, 60, 6);
        private final SpriteTexture gasType = sprite("estorage_controller_elements.png", 1, 197, 59, 6);
        private final SpriteTexture energyUsage = sprite("estorage_controller_elements.png", 1, 211, 61, 6);
        private final SpriteTexture energyLevel = sprite("estorage_controller_elements.png", 1, 218, 60, 6);
        private final SpriteTexture gaugeTop = sprite("estorage_controller_elements.png", 1, 246, 32, 8);
        private final SpriteTexture gaugeMiddle = sprite("estorage_controller_elements.png", 34, 250, 32, 4);
        private final SpriteTexture gaugeBottom = sprite("estorage_controller_elements.png", 1, 246, 32, 8);
        private final SpriteTexture cellL4 = sprite("estorage_controller_elements.png", 1, 1, 67, 26);
        private final SpriteTexture cellL6 = sprite("estorage_controller_elements.png", 1, 28, 67, 26);
        private final SpriteTexture cellL9 = sprite("estorage_controller_elements.png", 1, 55, 67, 26);
        private final SpriteTexture cellEmpty = sprite("estorage_controller_elements.png", 1, 82, 8, 26);
        private final SpriteTexture cellFluid = sprite("estorage_controller_elements.png", 10, 82, 8, 26);
        private final SpriteTexture cellItem = sprite("estorage_controller_elements.png", 19, 82, 8, 26);
        private final SpriteTexture cellGas = sprite("estorage_controller_elements.png", 28, 82, 8, 26);

        private static SpriteTexture sprite(String path, int u, int v, int width, int height) {
            return SpriteTexture.of(NeoECOAE.id("textures/gui/storage/" + path))
                .setSprite(u, v, width, height);
        }
    }
}
