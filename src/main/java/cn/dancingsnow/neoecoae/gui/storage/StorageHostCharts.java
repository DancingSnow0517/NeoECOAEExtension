package cn.dancingsnow.neoecoae.gui.storage;

import static cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.safeEntries;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.CellEntry;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.Config;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Storage charts and their synchronized metrics; independent of the controller layout. */
final class StorageHostCharts {
    private StorageHostCharts() {}
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
    private static final ResourceLocations TEXTURES = new ResourceLocations();

    static void addTo(UIElement root, Config config) {
        LegacyGraphBar graphBar = new LegacyGraphBar(() -> totalMetric(config));
        graphBar.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(CHART_LEFT + GRAPH_BAR_OFFSET_LEFT).top(CHART_TOP + GRAPH_BAR_OFFSET_TOP)
            .width(BAR_WIDTH).height(BAR_HEIGHT));
        root.addChild(graphBar);
        addGraphs(root, graphBar, config);
    }

    private static void addGraphs(UIElement root, LegacyGraphBar graphBar, Config config) {
        root.addChild(graph(
            graphBar, 10, 32, 60, TEXTURES.fluidPercent,
            () -> new Metric(config.energyConsumePerTick().getAsLong(), 0L),
            StorageHostCharts::energyUsageText, true));
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

    private static final class ResourceLocations {
        private final SpriteTexture fluidPercent = sprite("estorage_controller_elements.png", 6, 225, 60, 6);
        private final SpriteTexture itemType = sprite("estorage_controller_elements.png", 1, 197, 59, 6);
        private final SpriteTexture gaugeTop = sprite("estorage_controller_elements.png", 1, 246, 32, 8);
        private final SpriteTexture gaugeMiddle = sprite("estorage_controller_elements.png", 34, 250, 32, 4);
        private final SpriteTexture gaugeBottom = sprite("estorage_controller_elements.png", 1, 246, 32, 8);
        private static SpriteTexture sprite(String path, int u, int v, int width, int height) {
            return SpriteTexture.of(NeoECOAE.id("textures/gui/storage/" + path))
                .setSprite(u, v, width, height);
        }
    }
}
