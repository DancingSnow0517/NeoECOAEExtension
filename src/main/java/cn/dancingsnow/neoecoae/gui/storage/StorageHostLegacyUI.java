package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** The 1.12.2-style storage controller surface, backed by the current storage implementation. */
public final class StorageHostLegacyUI {
    public static final int ROOT_WIDTH = 256;
    public static final int ROOT_HEIGHT = 207;

    private static final int CHART_LEFT = 63;
    private static final int CHART_TOP = 22;
    private static final int BAR_WIDTH = 32;
    private static final int BAR_HEIGHT = 92;
    private static final int GRAPH_HEIGHT = 16;
    private static final int GRAPH_LABEL_HEIGHT = 10;
    private static final int GRAPH_TEXT_SCALE = 6;
    private static final int GRAPH_OFFSET_LEFT = -55;
    private static final int GRAPH_OFFSET_TOP = -26;
    private static final int GRAPH_FOCUSED_COLOR = 0xFFFFFFFF;
    private static final int GRAPH_UNFOCUSED_COLOR = 0x66FFFFFF;
    private static final int CELL_LIST_LEFT = 178;
    private static final int CELL_LIST_TOP = 22;
    private static final int CELL_LIST_WIDTH = 67;
    private static final int CELL_LIST_HEIGHT = 172;
    private static final int CELL_TEXT_LEFT = 10;
    private static final int CELL_TEXT_TOP = 1;
    private static final int CELL_TEXT_LINE_STEP = 13;
    private static final int INVENTORY_LEFT = 7;
    private static final int INVENTORY_TOP = 124;
    private static final int INVENTORY_WIDTH = 9 * 18;
    private static final int INVENTORY_HEIGHT = 4 * 18;

    private static final int KIND_EMPTY = 0;
    private static final int KIND_ITEM = 1;
    private static final int KIND_FLUID = 2;
    private static final int KIND_GAS = 3;

    private static final ResourceLocations TEXTURES = new ResourceLocations();

    private StorageHostLegacyUI() {
    }

    public record Config(
        Supplier<Component> title,
        LongSupplier storedEnergy,
        LongSupplier maxEnergy,
        LongSupplier energyConsumePerTick,
        Supplier<List<CellEntry>> cellEntries,
        BooleanSupplier infiniteStorage,
        BooleanSupplier migratingToInfinite
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
        public static final int KIND_EMPTY = StorageHostLegacyUI.KIND_EMPTY;
        public static final int KIND_ITEM = StorageHostLegacyUI.KIND_ITEM;
        public static final int KIND_FLUID = StorageHostLegacyUI.KIND_FLUID;
        public static final int KIND_GAS = StorageHostLegacyUI.KIND_GAS;
    }

    public static UIElement create(Config config) {
        UIElement root = new UIElement().layout(layout -> layout
            .width(ROOT_WIDTH)
            .height(ROOT_HEIGHT)
        ).style(style -> style.backgroundTexture(TEXTURES.background));

        root.addChild(new TextElement()
            .setText(config.title().get())
            .textStyle(style -> style
                .adaptiveHeight(true)
                .adaptiveWidth(true)
                .textWrap(TextWrap.HOVER_ROLL)
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
            .left(CHART_LEFT)
            .top(CHART_TOP)
            .width(BAR_WIDTH)
            .height(BAR_HEIGHT));
        root.addChild(graphBar);

        addGraphs(root, graphBar, config);

        CellListElement cellList = new CellListElement(config.cellEntries());
        cellList.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(CELL_LIST_LEFT)
            .top(CELL_LIST_TOP)
            .width(CELL_LIST_WIDTH)
            .height(CELL_LIST_HEIGHT));
        root.addChild(cellList);
        root.addChild(playerInventory());
        return root;
    }

    private static void addGraphs(UIElement root, LegacyGraphBar graphBar, Config config) {
        root.addChild(graph(
            graphBar,
            9,
            51,
            65,
            TEXTURES.itemPercent,
            () -> kindMetric(config, KIND_ITEM),
            metric -> percentText("gui.neoecoae.storage.legacy.graph.item", metric),
            true));
        root.addChild(graph(
            graphBar,
            10,
            32,
            60,
            TEXTURES.fluidPercent,
            () -> kindMetric(config, KIND_FLUID),
            metric -> percentText("gui.neoecoae.storage.legacy.graph.fluid", metric),
            true));
        boolean hasOtherType = hasKind(config, KIND_GAS);
        if (hasOtherType) {
            root.addChild(graph(
                graphBar,
                10,
                70,
                60,
                TEXTURES.gasPercent,
                () -> kindMetric(config, KIND_GAS),
                metric -> percentText("gui.neoecoae.storage.legacy.graph.gas", metric),
                true));
        }
        LegacyGraphElement totalGraph = graph(
            graphBar,
            8,
            hasOtherType ? 89 : 70,
            64,
            TEXTURES.totalPercent,
            () -> totalMetric(config),
            metric -> percentText("gui.neoecoae.storage.legacy.graph.total", metric),
            true);
        root.addChild(totalGraph);
        graphBar.focus(totalGraph);
        root.addChild(graph(
            graphBar,
            85,
            46,
            60,
            TEXTURES.fluidType,
            () -> kindTypeMetric(config, KIND_FLUID),
            metric -> typeText("gui.neoecoae.storage.legacy.graph.fluid_type", metric),
            false));
        root.addChild(graph(
            graphBar,
            82,
            29,
            59,
            TEXTURES.itemType,
            () -> kindTypeMetric(config, KIND_ITEM),
            metric -> typeText("gui.neoecoae.storage.legacy.graph.item_type", metric),
            false));
        root.addChild(graph(
            graphBar,
            83,
            hasOtherType ? 80 : 63,
            61,
            TEXTURES.energyUsage,
            () -> new Metric(config.energyConsumePerTick().getAsLong(), 0L),
            StorageHostLegacyUI::energyUsageText,
            false));
        root.addChild(graph(
            graphBar,
            83,
            hasOtherType ? 97 : 80,
            60,
            TEXTURES.energyLevel,
            () -> new Metric(config.storedEnergy().getAsLong(), config.maxEnergy().getAsLong()),
            metric -> percentText("gui.neoecoae.storage.legacy.graph.energy_stored", metric),
            false));
        if (hasOtherType) {
            root.addChild(graph(
                graphBar,
                82,
                63,
                59,
                TEXTURES.gasType,
                () -> kindTypeMetric(config, KIND_GAS),
                metric -> typeText("gui.neoecoae.storage.legacy.graph.gas_type", metric),
                false));
        }
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
        for (InventorySlots.Row row : inventory.rows) {
            row.apply(slot -> slot.transform(transform -> transform.translate(0, 1)));
        }
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
        String value = metric.total() < 0L
            ? Component.translatable("gui.neoecoae.storage.infinite_value").getString()
            : metric.total() <= 0L ? "0%" : HostText.percent(metric.used(), metric.total());
        return Component.translatable(key, value).withColor(HostText.PRIMARY);
    }

    private static Component typeText(String key, Metric metric) {
        String used = HostText.ae2Amount(metric.used());
        String total = metric.total() < 0L ? "\u221E" : HostText.ae2Amount(metric.total());
        return Component.translatable(key, used, total).withColor(HostText.PRIMARY);
    }

    private static Component energyUsageText(Metric metric) {
        return Component.translatable("gui.neoecoae.storage.legacy.graph.energy_usage",
            HostText.ae2Amount(metric.used())).withColor(HostText.PRIMARY);
    }

    private record Metric(long used, long total) {
        private static final Metric EMPTY = new Metric(0L, 0L);
    }

    private static final class LegacyGraphElement extends UIElement implements IBindable<CompoundTag> {
        private static final String NBT_USED = "u";
        private static final String NBT_TOTAL = "t";

        private final LegacyGraphBar graphBar;
        private final SpriteTexture background;
        private final Supplier<Metric> metricSupplier;
        private final Function<Metric, Component> textSupplier;
        private final boolean leftAlign;
        private Metric metric = Metric.EMPTY;
        private CompoundTag syncedTag = new CompoundTag();

        private LegacyGraphElement(
            LegacyGraphBar graphBar,
            SpriteTexture background,
            Supplier<Metric> metricSupplier,
            Function<Metric, Component> textSupplier,
            boolean leftAlign
        ) {
            this.graphBar = graphBar;
            this.background = background;
            this.metricSupplier = metricSupplier;
            this.textSupplier = textSupplier;
            this.leftAlign = leftAlign;
            bind(DataBindingBuilder.create(
                () -> metricTag(this.metricSupplier.get()),
                ignored -> {
                }
            ).syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                graphBar.focus(this);
                event.hoverTooltips = HoverTooltips.empty().append(textSupplier.apply(metric));
            });
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);
            int color = graphBar.isFocused(this) ? GRAPH_FOCUSED_COLOR : GRAPH_UNFOCUSED_COLOR;
            guiContext.drawTexture(background.copy().setColor(color),
                getPositionX(), getPositionY() + GRAPH_LABEL_HEIGHT, getSizeWidth(), getSizeHeight() - GRAPH_LABEL_HEIGHT);
            drawText(guiContext, textSupplier.apply(metric));
        }

        private void drawText(GUIContext guiContext, Component text) {
            Font font = Minecraft.getInstance().font;
            float scale = GRAPH_TEXT_SCALE / 10.0F;
            float availableWidth = getSizeWidth() / scale;
            float textX = leftAlign ? 2.0F : Math.max(0.0F, availableWidth - font.width(text) - 2.0F);
            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().translate(getPositionX(), getPositionY() + 1.0F, 0.0F);
            guiContext.graphics.pose().scale(scale, scale, 1.0F);
            guiContext.graphics.drawString(font, text, Math.round(textX), 0, HostText.PRIMARY, false);
            guiContext.graphics.pose().popPose();
        }

        private Metric metric() {
            return metric;
        }

        @Override
        public CompoundTag getValue() {
            return syncedTag.copy();
        }

        @Override
        public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
            syncedTag = value == null ? new CompoundTag() : value.copy();
            metric = readMetric(syncedTag);
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
        private Metric syncedMetric = Metric.EMPTY;
        @Nullable
        private LegacyGraphElement focused;
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
            focused = graph;
        }

        private boolean isFocused(LegacyGraphElement graph) {
            return focused == graph;
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);
            Metric metric = focused == null ? syncedMetric : focused.metric();
            boolean infinite = metric.total() < 0L;
            float ratio = infinite ? 1.0F : HostText.usageRatio(metric.used(), metric.total());
            if (ratio <= 0.0F && !infinite) {
                return;
            }
            float bottom = getPositionY() + getSizeHeight();
            float bodyHeight = Math.max(0.0F, getSizeHeight() - CAP_HEIGHT);
            float barHeight = infinite ? bodyHeight : Math.round(bodyHeight * ratio);
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
        private static final int MAX_SYNCED_CELLS = 256;
        private static final int MAX_SYNC_BYTES = 30_000;
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
                case KIND_GAS -> TEXTURES.cellGas;
                default -> TEXTURES.cellEmpty;
            };
            guiContext.drawTexture(cellBackground, x, y, CELL_LIST_WIDTH, ROW_HEIGHT);
            guiContext.drawTexture(cellKind, x, y, 8.0F, ROW_HEIGHT);

            Font font = Minecraft.getInstance().font;
            Component type = Component.translatable("gui.neoecoae.storage.legacy.cell_info",
                cellKindName(entry.kind()), tierName(entry.tier()));
            Component types = Component.translatable("gui.neoecoae.storage.legacy.cell_types",
                HostText.ae2Amount(entry.usedTypes()), formatCapacity(entry.totalTypes()));
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
                cellKindName(entry.kind()), tierName(entry.tier()),
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
            ListTag cells = new ListTag();
            int remainingBytes = MAX_SYNC_BYTES;
            if (source != null) {
                for (CellEntry entry : source) {
                    if (entry == null || cells.size() >= MAX_SYNCED_CELLS) {
                        break;
                    }
                    CompoundTag cell = new CompoundTag();
                    cell.putInt(NBT_TYPE, entry.typeId());
                    cell.putInt(NBT_TIER, entry.tier());
                    cell.putInt(NBT_KIND, entry.kind());
                    cell.putLong(NBT_USED_TYPES, Math.max(0L, entry.usedTypes()));
                    cell.putLong(NBT_TOTAL_TYPES, entry.totalTypes());
                    cell.putLong(NBT_USED_BYTES, Math.max(0L, entry.usedBytes()));
                    cell.putLong(NBT_TOTAL_BYTES, entry.totalBytes());
                    cell.putBoolean(NBT_INFINITE, entry.infinite());
                    int size = Math.toIntExact(Math.min(Integer.MAX_VALUE, cell.sizeInBytes()));
                    if (size > remainingBytes) {
                        break;
                    }
                    cells.add(cell);
                    remainingBytes -= size;
                }
            }
            result.put(NBT_CELLS, cells);
            return result;
        }

        private static List<CellEntry> readEntries(CompoundTag tag) {
            ListTag cells = tag.getList(NBT_CELLS, Tag.TAG_COMPOUND);
            List<CellEntry> result = new ArrayList<>(Math.min(cells.size(), MAX_SYNCED_CELLS));
            for (int i = 0; i < cells.size() && result.size() < MAX_SYNCED_CELLS; i++) {
                CompoundTag cell = cells.getCompound(i);
                result.add(new CellEntry(
                    cell.getInt(NBT_TYPE),
                    Math.clamp(cell.getInt(NBT_TIER), 1, 3),
                    Math.clamp(cell.getInt(NBT_KIND), KIND_EMPTY, KIND_GAS),
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

    private static Component cellKindName(int kind) {
        return switch (kind) {
            case KIND_ITEM -> Component.translatable("gui.neoecoae.storage.legacy.cell_info.item");
            case KIND_FLUID -> Component.translatable("gui.neoecoae.storage.legacy.cell_info.fluid");
            case KIND_GAS -> Component.translatable("gui.neoecoae.storage.legacy.cell_info.gas");
            default -> Component.translatable("gui.neoecoae.storage.legacy.cell_info.empty");
        };
    }

    private static final class ResourceLocations {
        private final SpriteTexture background = sprite("estorage_controller.png", 0, 0, 256, 207);
        private final SpriteTexture itemPercent = sprite("estorage_controller_elements.png", 1, 232, 65, 6);
        private final SpriteTexture fluidPercent = sprite("estorage_controller_elements.png", 2, 225, 60, 6);
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
