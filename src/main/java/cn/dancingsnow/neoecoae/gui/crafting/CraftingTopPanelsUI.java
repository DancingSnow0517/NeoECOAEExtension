package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostPerformanceText;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Package-private status, statistics, and gauge composition for the crafting host page. */
final class CraftingTopPanelsUI {
    private static final int TOP_PANEL_HEIGHT = 70;
    private static final int STATUS_WIDTH = 76;
    private static final int STATS_WIDTH = 114;
    private static final int GAUGE_WIDTH = 90;
    private static final int PERFORMANCE_WIDTH = 60;
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000L;

    private CraftingTopPanelsUI() {
    }

    static UIElement create(CraftingHostPanelUI.Config config) {
        UIElement row = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .height(TOP_PANEL_HEIGHT)
                .flexDirection(FlexDirection.ROW));
        row.addClass("eco-host-panel-row");
        row.addChildren(statusPanel(config), statsPanel(config), gaugePanel(config));
        return row;
    }

    private static UIElement statusPanel(CraftingHostPanelUI.Config config) {
        UIElement panel = HostElements.hostCard(STATUS_WIDTH, TOP_PANEL_HEIGHT);
        panel.addChild(sectionLabel("gui.neoecoae.crafting.ui.status"));
        panel.addChild(statusRow("gui.neoecoae.crafting.ui.overclock_short", config.overclocked()));
        panel.addChild(statusRow("gui.neoecoae.crafting.ui.cooling_short", config.activeCooling()));
        return panel;
    }

    private static UIElement statusRow(String key, BooleanSupplier value) {
        UIElement row = new UIElement()
                .addClass("eco-host-status-row")
                .layout(layout -> layout.widthPercent(100).height(13).flexDirection(FlexDirection.ROW)
                        .alignItems(AlignItems.CENTER));
        row.addChild(statusIndicator(value));
        row.addChild(localizedBooleanLabel(key, value).layout(layout -> layout.flex(1).height(10)));
        return row;
    }

    private static UIElement statusIndicator(BooleanSupplier value) {
        UIElement frame = new UIElement()
                .addClass("eco-host-status-light-edge")
                .layout(layout -> layout.width(13).height(13).paddingAll(1));
        UIElement border = new UIElement()
                .addClass("eco-host-status-light-border")
                .layout(layout -> layout.widthPercent(100).heightPercent(100).paddingAll(1));
        UIElement lamp = new UIElement()
                .addClass(value.getAsBoolean() ? "eco-host-status-light-on" : "eco-host-status-light-off")
                .layout(layout -> layout.widthPercent(100).heightPercent(100));
        border.addChild(lamp);
        frame.addChild(border);

        BindableValue<Boolean> syncedValue = HostElements.syncedBoolean(value);
        syncedValue.registerValueListener(enabled -> {
            lamp.removeClasses("eco-host-status-light-on", "eco-host-status-light-off");
            lamp.addClass(Boolean.TRUE.equals(enabled) ? "eco-host-status-light-on" : "eco-host-status-light-off");
        });
        frame.addChild(syncedValue);
        return frame;
    }

    private static UIElement statsPanel(CraftingHostPanelUI.Config config) {
        UIElement panel = HostElements.hostCard(STATS_WIDTH, TOP_PANEL_HEIGHT);
        UIElement titleRow = new UIElement().layout(layout -> layout
                .widthPercent(100).height(10).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER));
        titleRow.addChild(sectionLabel("gui.neoecoae.crafting.ui.stats").layout(layout -> layout.flex(1).height(10)));
        titleRow.addChild(performanceLabel(config.performanceAverageNanos()));
        panel.addChild(titleRow);
        panel.addChild(localizedProgressLabel("gui.neoecoae.crafting.ui.recipe_slots", config.occupiedCraftingSlots(),
                config.maxCraftingSlots()));
        panel.addChild(new ProgressBar()
                .label(label -> label.setText(""))
                .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
                .bind(DataBindingBuilder.floatValS2C(() -> HostText.usageRatio(
                        config.occupiedCraftingSlots().getAsInt(), config.maxCraftingSlots().getAsInt())).build())
                .addClass("eco-host-stats-progress")
                .layout(layout -> layout.widthPercent(100).height(9)));
        Label batchPerThread = localizedIntLabel("gui.neoecoae.crafting.ui.batch_per_thread", config.maxBatchPerThread(),
                value -> Tooltips.ofNumber(value).copy().withColor(CraftingHostStyles.PANEL_VALUE), CraftingHostStyles.PANEL_MUTED);
        batchPerThread.textStyle(CraftingHostStyles::inlineStats);
        addBatchPerThreadTooltip(batchPerThread, config);
        panel.addChild(batchPerThread);
        UIElement overflowRow = new UIElement().layout(layout -> layout
                .widthPercent(100).height(9).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        Label overflow = localizedIntLabel("gui.neoecoae.host.crafting.overflow", config.overflowThreads(),
                value -> Tooltips.ofNumber(value).copy().withColor(CraftingHostStyles.PANEL_OVERFLOW_VALUE), CraftingHostStyles.PANEL_MUTED);
        overflow.textStyle(CraftingHostStyles::inlineStats);
        Label timeRatio = localizedIntLabel("gui.neoecoae.crafting.ui.recipe_time_ratio", config.effectiveOverclockTimes(),
                value -> Component.literal(CraftingHostText.recipeTimeMultiplier(value)).withColor(CraftingHostStyles.PANEL_TIME_VALUE),
                CraftingHostStyles.PANEL_TIME_VALUE);
        timeRatio.textStyle(CraftingHostStyles::inlineStats);
        overflowRow.addChildren(overflow, timeRatio);
        panel.addChild(overflowRow);
        return panel;
    }

    private static Label performanceLabel(LongSupplier performanceAverageNanos) {
        Label label = boundLabel(() -> Component.literal(HostPerformanceText.formatCorner(performanceAverageNanos.getAsLong())),
                CraftingHostStyles.PANEL_VALUE);
        label.addClass("eco-host-performance");
        label.textStyle(style -> style.textAlignHorizontal(Horizontal.RIGHT));
        label.layout(layout -> layout.width(PERFORMANCE_WIDTH).height(10));
        BindableValue<Component> detail = HostElements.syncedComponent(
                () -> Component.literal(HostPerformanceText.formatTooltip(performanceAverageNanos.getAsLong())));
        label.addChild(detail);
        HostElements.tooltips(label, () -> List.of(Component.translatable("gui.neoecoae.crafting.performance"), detail.getValue()));
        return label;
    }

    private static UIElement gaugePanel(CraftingHostPanelUI.Config config) {
        UIElement panel = HostElements.hostCard(GAUGE_WIDTH, TOP_PANEL_HEIGHT);
        panel.addChild(sectionLabel("gui.neoecoae.crafting.ui.energy_cooling"));
        UIElement gauges = new UIElement().layout(layout -> layout.widthPercent(100).flex(1)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER));
        gauges.addClass("eco-host-gauge-row");
        gauges.addChildren(energyGauge(config.energyUsage()), coolantGauge(config));
        panel.addChild(gauges);
        return panel;
    }

    private static ProgressBar energyGauge(LongSupplier energyUsage) {
        DynamicEnergyProgressBar gauge = new DynamicEnergyProgressBar();
        gauge.label(label -> label.setText(""));
        gauge.progressBarStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP));
        gauge.bind(DataBindingBuilder.floatValS2C(() -> HostText.usageRatio(Math.max(0L, energyUsage.getAsLong()),
                ENERGY_GAUGE_REFERENCE)).build());
        gauge.addClass("eco-host-energy-gauge");
        gauge.layout(layout -> layout.width(20).height(32));
        BindableValue<Component> tooltip = HostElements.syncedComponent(
                () -> Tooltips.ofNumber(Math.max(0L, energyUsage.getAsLong())).append(" AE/t"));
        gauge.addChild(tooltip);
        HostElements.tooltips(gauge, () -> List.of(Component.translatable("gui.neoecoae.crafting.ui.energy_usage"), tooltip.getValue()));
        return gauge;
    }

    private static UIElement coolantGauge(CraftingHostPanelUI.Config config) {
        UIElement frame = new UIElement().addClass("eco-host-coolant-gauge").layout(layout -> layout.width(23).height(32));
        CoolantFluidSlot fluid = new CoolantFluidSlot(config);
        fluid.layout(layout -> layout.widthPercent(100).heightPercent(100));
        frame.addChild(fluid);
        return frame;
    }

    private static Label sectionLabel(String key) {
        Label label = localLabel(Component.translatable(key), CraftingHostStyles.PANEL_TEXT);
        label.addClass("eco-host-section-title");
        label.layout(layout -> layout.widthPercent(100).height(10));
        return label;
    }

    private static Label boundLabel(Supplier<Component> text, int color) {
        Label label = HostElements.textSegment(() -> text.get().copy().withColor(color), () -> color);
        label.addClass("eco-host-label");
        label.textStyle(CraftingHostStyles::compact);
        return label;
    }

    private static Label localLabel(Component text, int color) {
        Label label = new Label();
        label.setText(text.copy().withColor(color));
        label.addClass("eco-host-label");
        label.textStyle(CraftingHostStyles::compact);
        return label;
    }

    private static Label localizedBooleanLabel(String key, BooleanSupplier value) {
        Label label = localLabel(statusText(key, value.getAsBoolean()), CraftingHostStyles.PANEL_MUTED);
        BindableValue<Boolean> syncedValue = HostElements.syncedBoolean(value);
        syncedValue.registerValueListener(enabled -> label.setText(statusText(key, Boolean.TRUE.equals(enabled))));
        label.addChild(syncedValue);
        return label;
    }

    private static Component statusText(String key, boolean enabled) {
        return Component.translatable(key).withColor(CraftingHostStyles.PANEL_MUTED).append(": ")
                .append(Component.translatable(enabled ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off")
                        .withColor(enabled ? CraftingHostStyles.PANEL_SUCCESS : CraftingHostStyles.PANEL_MUTED));
    }

    private static Label localizedProgressLabel(String key, IntSupplier used, IntSupplier max) {
        BindableValue<Integer> syncedUsed = HostElements.syncedInt(used);
        BindableValue<Integer> syncedMax = HostElements.syncedInt(max);
        Label label = localLabel(progressText(key, syncedUsed.getValue(), syncedMax.getValue()), CraftingHostStyles.PANEL_MUTED);
        Runnable refresh = () -> label.setText(progressText(key, syncedUsed.getValue(), syncedMax.getValue()));
        syncedUsed.registerValueListener(value -> refresh.run());
        syncedMax.registerValueListener(value -> refresh.run());
        label.addChildren(syncedUsed, syncedMax);
        return label;
    }

    private static Component progressText(String key, Integer used, Integer max) {
        HostText.UsedTotal progress = HostText.typeProgress(used == null ? 0 : used, max == null ? 0 : max);
        return Component.translatable(key).withColor(CraftingHostStyles.PANEL_MUTED).append(": ")
                .append(progress.usedText()).append(" / ").append(progress.maxText());
    }

    private static Label localizedIntLabel(String key, IntSupplier value, IntFunction<Component> valueText, int color) {
        BindableValue<Integer> syncedValue = HostElements.syncedInt(value);
        Label label = localLabel(intText(key, syncedValue.getValue(), valueText, color), color);
        syncedValue.registerValueListener(synced -> label.setText(intText(key, synced == null ? 0 : synced, valueText, color)));
        label.addChild(syncedValue);
        return label;
    }

    private static Component intText(String key, int value, IntFunction<Component> valueText, int color) {
        return Component.translatable(key).withColor(color).append(": ").append(valueText.apply(value));
    }

    private static void addBatchPerThreadTooltip(Label label, CraftingHostPanelUI.Config config) {
        CraftingHostBatchSyncElement detail = new CraftingHostBatchSyncElement();
        detail.bind(DataBindingBuilder.<net.minecraft.nbt.CompoundTag>create(
                () -> CraftingHostBatchSyncElement.encode(config.hostBatchInfos().get().stream()
                        .map(info -> new CraftingHostBatchSyncElement.HostBatchData(info.highEnergy(), info.threadCount(),
                                info.maxBatchPerThread())).toList()),
                ignored -> { }).syncType(net.minecraft.nbt.CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        detail.setDisplay(false);
        label.addChild(detail);
        HostElements.tooltips(label, () -> {
            List<CraftingHostBatchSyncElement.HostBatchData> hosts = CraftingHostBatchSyncElement.decode(detail.getValue());
            java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.translatable("gui.neoecoae.crafting.ui.batch_per_thread.detail").withColor(CraftingHostStyles.PANEL_MUTED));
            if (!hosts.isEmpty()) {
                for (CraftingHostBatchSyncElement.HostBatchData host : hosts) {
                    lines.add(CraftingHostText.hostBatchLine(host.highEnergy(), host.threads(), host.batch()));
                }

                long totalPerTick = 0L;
                for (CraftingHostBatchSyncElement.HostBatchData host : hosts) {
                    totalPerTick = CraftingHostText.saturatingBatchTotal(totalPerTick, host.threads(), host.batch());
                }

                lines.add(Component.translatable("gui.neoecoae.crafting.ui.batch_per_thread.total",
                        CraftingHostText.infiniteAwareAmount(totalPerTick).copy().withColor(CraftingHostStyles.PANEL_VALUE))
                        .withColor(CraftingHostStyles.PANEL_MUTED));
            }
            return lines;
        });
    }

    private static final class DynamicEnergyProgressBar extends ProgressBar {
        @Override
        public ProgressBar setValue(@Nullable Float value) {
            super.setValue(value);
            float ratio = value == null ? 0.0F : value;
            if (ratio >= 0.9F) {
                removeClass("eco-host-energy-medium");
                addClass("eco-host-energy-warning");
            } else if (ratio >= 0.5F) {
                removeClass("eco-host-energy-warning");
                addClass("eco-host-energy-medium");
            } else {
                removeClasses("eco-host-energy-warning", "eco-host-energy-medium");
            }
            return this;
        }
    }

    private static final class CoolantFluidSlot extends FluidSlot {
        private int maxOverclock = -1;

        private CoolantFluidSlot(CraftingHostPanelUI.Config config) {
            setAllowClickFilled(false);
            setAllowClickDrained(false);
            amountLabel.setDisplay(false);
            slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP).showFluidTooltips(false));
            style(style -> style.backgroundTexture(new ColorRectTexture(0xFF17141E)));
            bind(DataBindingBuilder.fluidStackS2C(() -> coolantStack(config)).build());
            addSyncValue(DataBindingBuilder.intValS2C(() -> Math.max(0, config.coolantCapacity().getAsInt()))
                    .remoteSetter(this::setCapacity).build().getSyncValue());
            addSyncValue(DataBindingBuilder.intValS2C(config.coolantMaxOverclock()::getAsInt)
                    .remoteSetter(value -> maxOverclock = value).build().getSyncValue());
        }

        @Override
        public List<Component> getFullTooltipTexts() {
            return List.of(Component.translatable("gui.neoecoae.host.crafting.coolant"),
                    Component.literal(HostText.typeProgress(getFluid().getAmount(), getCapacity()).usedText() + " / "
                            + HostText.typeProgress(getFluid().getAmount(), getCapacity()).maxText() + " mB"),
                    Component.translatable("gui.neoecoae.crafting.coolant_max_overclock",
                            maxOverclock < 0 ? "-" : Tooltips.ofNumber(maxOverclock)));
        }

        private static FluidStack coolantStack(CraftingHostPanelUI.Config config) {
            FluidStack fluid = config.coolantFluid().get();
            int amount = Math.max(0, config.coolantAmount().getAsInt());
            return fluid == null || fluid.isEmpty() || amount == 0 ? FluidStack.EMPTY : fluid.copyWithAmount(amount);
        }
    }
}
