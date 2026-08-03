package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostPerformanceText;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class StorageMetricElements {
    private static final int PROGRESS_ROW_LABEL_WIDTH = 24;
    private static final int PROGRESS_ROW_BAR_WIDTH = 36;

    private StorageMetricElements() {
    }

    static UIElement usedTotalRow(
        Supplier<Component> prefix,
        Supplier<HostText.UsedTotal> text,
        LongSupplier used,
        LongSupplier max
    ) {
        UIElement row = HostElements.horizontalRow(10, 0);
        row.addChild(HostElements.textSegment(prefix, () -> HostText.MUTED));
        row.addChild(HostElements.textSegment(() -> Component.literal(text.get().usedText()),
            () -> HostText.usedValueColor(used.getAsLong(), max.getAsLong())));
        row.addChild(HostElements.textSegment(() -> Component.literal(" / "), () -> HostText.MUTED));
        row.addChild(HostElements.textSegment(() -> Component.literal(text.get().maxText()), () -> HostText.VALUE));
        row.addChild(
            HostElements.textSegment(
                () -> Component.literal(" ").append(text.get().suffix()),
                () -> HostText.MUTED
            )
        );
        return row;
    }

    static UIElement infiniteAwareUsageRow(
        Supplier<Component> label,
        Supplier<HostText.UsedTotal> text,
        Supplier<Component> tooltip,
        Supplier<Component> infiniteTooltip,
        LongSupplier used,
        LongSupplier max,
        BooleanSupplier infiniteLoad,
        @Nullable Supplier<String> infiniteText
    ) {
        UIElement wrapper = new UIElement().layout(layout -> {
            layout.height(10);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        wrapper.addChild(
            HostElements.syncedDisplay(() -> !infiniteLoad.getAsBoolean())
                .addChild(usageProgressRow(label, text, tooltip, used, max))
        );
        wrapper.addChild(
            HostElements.syncedDisplay(infiniteLoad)
                .addChild(usedOnlyRow(label, text, infiniteTooltip, used, infiniteText))
        );
        return wrapper;
    }

    static Component usedTotalTooltip(HostText.UsedTotal text, long used, long max) {
        MutableComponent line = Component.literal(text.usedText()).withColor(HostText.usedValueColor(used, max))
            .append(Component.literal(" / ").withColor(HostText.MUTED))
            .append(Component.literal(text.maxText()).withColor(HostText.VALUE));
        if (!Component.empty().equals(text.suffix())) {
            line.append(Component.literal(" ").append(text.suffix()).withColor(HostText.MUTED));
        }
        return line;
    }

    static Component usedOnlyTooltip(HostText.UsedTotal text, long used) {
        MutableComponent line = Component.literal(text.usedText())
            .withColor(HostText.usedValueColor(used, Long.MAX_VALUE));
        if (!Component.empty().equals(text.suffix())) {
            line.append(Component.literal(" ").append(text.suffix()).withColor(HostText.MUTED));
        }
        return line;
    }

    private static UIElement usageProgressRow(
        Supplier<Component> label,
        Supplier<HostText.UsedTotal> text,
        Supplier<Component> tooltip,
        LongSupplier used,
        LongSupplier max
    ) {
        UIElement row = HostElements.horizontalRow(10, 2);
        row.addChild(HostElements.textSegment(label, () -> HostText.MUTED)
            .layout(layout -> layout.width(PROGRESS_ROW_LABEL_WIDTH)));
        row.addChild(new TooltipProgressBarElement(used, max, tooltip)
            .layout(layout -> layout.width(PROGRESS_ROW_BAR_WIDTH).height(4)));
        UIElement value = HostElements.horizontalRow(10, 0);
        value.addChild(HostElements.textSegment(() -> Component.literal(text.get().usedText()),
            () -> HostText.usedValueColor(used.getAsLong(), max.getAsLong())));
        value.addChild(HostElements.textSegment(() -> Component.literal(" / "), () -> HostText.MUTED));
        value.addChild(HostElements.textSegment(() -> Component.literal(text.get().maxText()), () -> HostText.VALUE));
        row.addChild(value);
        return row;
    }

    private static UIElement usedOnlyRow(
        Supplier<Component> label,
        Supplier<HostText.UsedTotal> text,
        Supplier<Component> tooltip,
        LongSupplier used,
        @Nullable Supplier<String> overrideText
    ) {
        UIElement row = new TooltippedElement(tooltip).layout(layout -> {
            layout.height(10);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(2);
        });
        row.addChild(
            HostElements.textSegment(
                () -> Component.literal(
                    overrideText == null ? text.get().usedText() : overrideText.get()
                ),
                () -> HostText.usedValueColor(used.getAsLong(), Long.MAX_VALUE)
            ).layout(layout -> layout.width(PROGRESS_ROW_LABEL_WIDTH + PROGRESS_ROW_BAR_WIDTH + 2))
        );
        row.addChild(HostElements.textSegment(label, () -> HostText.MUTED));
        return row;
    }

    private static HoverTooltips tooltipOf(Component... components) {
        return new HoverTooltips(List.of(components), null, null, null);
    }

    private static final class TooltipProgressBarElement extends UIElement implements IBindable<Component> {
        private Component tooltip;

        private TooltipProgressBarElement(
            LongSupplier used,
            LongSupplier max,
            Supplier<Component> tooltip
        ) {
            this.tooltip = tooltip.get();
            bind(DataBindingBuilder.componentS2C(tooltip).build());
            ProgressBar progressBar = new ProgressBar();
            progressBar.label(label -> label.setText(""))
                .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
                .bind(DataBindingBuilder.floatValS2C(
                    () -> HostText.usageRatio(used.getAsLong(), max.getAsLong())
                ).build());
            progressBar.addClass("eco-host-progress");
            addChild(progressBar.layout(layout -> layout.widthPercent(100).height(4)));
            addEventListener(
                UIEvents.HOVER_TOOLTIPS,
                event -> event.hoverTooltips = tooltipOf(this.tooltip)
            );
        }

        @Override
        public IDataSource<Component> setValue(@Nullable Component value) {
            tooltip = value == null ? Component.empty() : value;
            return this;
        }

        @Override
        public Component getValue() {
            return tooltip;
        }
    }

    private static final class TooltippedElement extends UIElement implements IBindable<Component> {
        private Component tooltip;

        private TooltippedElement(Supplier<Component> tooltip) {
            this.tooltip = tooltip.get();
            bind(DataBindingBuilder.componentS2C(tooltip).build());
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = tooltipOf(this.tooltip));
        }

        @Override
        public IDataSource<Component> setValue(@Nullable Component value) {
            tooltip = value == null ? Component.empty() : value;
            return this;
        }

        @Override
        public Component getValue() {
            return tooltip;
        }
    }

    static final class PerformanceLabelElement extends UIElement implements IBindable<Long> {
        private static final float COMPACT_FONT_SIZE = 7.0F;
        private long syncedAverageNanos;

        PerformanceLabelElement(LongSupplier performanceAverageNanos) {
            syncedAverageNanos = Math.max(0L, performanceAverageNanos.getAsLong());
            bind(DataBindingBuilder.longValS2C(
                () -> Math.max(0L, performanceAverageNanos.getAsLong())
            ).build());
            addEventListener(
                UIEvents.HOVER_TOOLTIPS,
                event -> event.hoverTooltips = tooltipOf(
                    Component.translatable("gui.neoecoae.crafting.performance"),
                    Component.literal(HostPerformanceText.formatTooltip(syncedAverageNanos))
                )
            );
        }

        @Override
        public IDataSource<Long> setValue(@Nullable Long value) {
            syncedAverageNanos = value == null ? 0L : Math.max(0L, value);
            return this;
        }

        @Override
        public Long getValue() {
            return syncedAverageNanos;
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            Font font = Minecraft.getInstance().font;
            String text = HostPerformanceText.formatCorner(syncedAverageNanos);
            float scale = COMPACT_FONT_SIZE / 9.0F;
            int x = (int) getPositionX();
            int y = (int) getPositionY();
            int width = (int) getSizeWidth();
            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().translate(x, y, 0.0F);
            guiContext.graphics.pose().scale(scale, scale, 1.0F);
            guiContext.graphics.drawString(
                font,
                text,
                Math.round(width / scale) - font.width(text),
                0,
                HostText.VALUE,
                false
            );
            guiContext.graphics.pose().popPose();
        }
    }
}
