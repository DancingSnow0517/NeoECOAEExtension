package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class StorageSystemLoadPanelUI {
    private static final float RIGHT_DETAIL_FONT_SIZE = 8.0F;
    private static final int PANEL_PADDING = 2;
    private static final int PANEL_GAP = 2;
    private static final int SCROLLBAR_HORIZONTAL_OFFSET = 2;

    private StorageSystemLoadPanelUI() {
    }

    static ScrollerView createRightPanel(StorageHostPanelUI.Config config) {
        ScrollerView panel = createEmptyPanel(StorageHostPanelUI.RIGHT_PANEL_WIDTH);
        StorageHostAnimatedRatio loadRatio = new StorageHostAnimatedRatio();
        panel.scrollerStyle(style -> style
            .verticalScrollDisplay(ScrollDisplay.NEVER)
            .horizontalScrollDisplay(ScrollDisplay.NEVER));
        panel.viewContainer(view -> {
            view.getLayout().paddingAll(0);
            view.addChild(HostElements.absolute(
                HostElements.panelTitle(() -> Component.translatable("gui.neoecoae.storage.system_load")),
                0, 2, 144, 10
            ));
            view.addChild(HostElements.absolute(
                HostElements.tinyInsetPanel(141, 169),
                2, 14, 141, 169
            ));
            view.addChild(HostElements.absolute(
                new SystemLoadTooltipElement(config),
                2, 14, 141, 169
            ));
            view.addChild(HostElements.absolute(
                StorageHostLoadGauge.bindRatio(
                    () -> loadRatio(config),
                    loadRatio,
                    config.storageTypes()
                ),
                10, 26, 32, 143
            ));
            view.addChild(HostElements.absolute(
                storageLoadLine(
                    () -> Component.translatable("gui.neoecoae.storage.current_load")
                        .append(": ")
                        .append(currentLoadPercent(config)),
                    () -> HostText.PRIMARY
                ),
                50, 31, 88, 15
            ));
            view.addChild(HostElements.absolute(
                storageLoadLine(
                    () -> Component.translatable("gui.neoecoae.storage.max_load")
                        .append(": ")
                        .append(isInfiniteDisplay(config)
                            ? "MAX"
                            : HostText.percent(
                                config.maxLoadUsedBytes().getAsLong(),
                                config.maxLoadTotalBytes().getAsLong())),
                    () -> isInfiniteDisplay(config) ? 0x22CA6CFF : HostText.WARNING
                ),
                50, 46, 88, 15
            ));
            view.addChild(HostElements.absolute(
                storageLoadLine(
                    () -> Component.translatable("gui.neoecoae.storage.status")
                        .append(": ")
                        .append(storageStatus(config)),
                    () -> config.infiniteDomainFailed().getAsBoolean()
                        ? HostText.ERROR
                        : isInfiniteDisplay(config) ? 0x22CA6CFF : storageStatusColor(config)
                ),
                50, 61, 88, 15
            ));
            view.addChild(HostElements.absolute(
                storageLoadLine(
                    () -> Component.translatable("gui.neoecoae.storage.idle_matrices")
                        .append(": ")
                        .append(Integer.toString(config.idleMatrices().getAsInt())),
                    () -> HostText.MUTED
                ),
                50, 76, 88, 15
            ));
            view.addChild(HostElements.absolute(
                StorageHostAnimatedPercentLabel.centered(
                    loadRatio,
                    () -> loadRatio.infinite()
                        ? 0xCA6CFF
                        : HostText.gaugeTextColor((float) loadRatio.value()),
                    () -> loadRatio.infinite()
                        ? Component.translatable("gui.neoecoae.storage.infinite_value")
                        : loadRatio.migrating()
                            ? Component.literal(HostText.percent(loadRatio.migrationProgress()))
                            : Component.literal(HostText.percent(loadRatio.value())),
                    0.9F
                ),
                10, 171, 32, 8
            ));
            view.addChild(HostElements.absolute(
                new StorageHostHugeStackList(config.registries(), config.hugeStacks(), 88, 82),
                50, 92, 88, 82
            ));
            view.addChild(HostElements.absolute(
                infiniteComponentSlot(
                    config.displayInfiniteStorageControls(),
                    config.canExtractInfiniteComponents(),
                    config.infiniteComponentInventory()
                ),
                120, 160, 18, 18
            ));
        });
        return panel;
    }

    static ScrollerView createEmptyPanel(int width) {
        return ECOHostWidgets.storagePanel(
            width,
            StorageHostPanelUI.PANEL_HEIGHT,
            PANEL_PADDING,
            PANEL_GAP,
            SCROLLBAR_HORIZONTAL_OFFSET
        );
    }

    private static UIElement infiniteComponentSlot(
        BooleanSupplier display,
        BooleanSupplier canExtract,
        IItemHandlerModifiable inventory
    ) {
        UIElement wrapper = HostElements.syncedDisplay(display);
        ItemHandlerSlot slot = new ItemHandlerSlot(inventory, 0)
            .setCanTake(player -> canTakeInfiniteComponent(player, canExtract));
        wrapper.addChild(new ItemSlot(slot));
        return wrapper;
    }

    private static boolean canTakeInfiniteComponent(@Nullable Player player, BooleanSupplier canExtract) {
        if (canExtract.getAsBoolean()) {
            return true;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                "tooltip.neoecoae.storage.infinite_component_locked"
            ), true);
        }
        return false;
    }

    static boolean isInfiniteLoad(StorageHostPanelUI.Config config) {
        return config.maxLoadUsedBytes().getAsLong() == Long.MAX_VALUE
            && config.maxLoadTotalBytes().getAsLong() == Long.MAX_VALUE;
    }

    private static float loadRatio(StorageHostPanelUI.Config config) {
        if (config.migratingToInfinite().getAsBoolean()) {
            return -2.0F - HostText.usageRatio(
                config.infiniteMigrationProgress().getAsInt(),
                100L
            );
        }
        if (isInfiniteLoad(config)) {
            return -1.0F;
        }
        StorageTotals totals = storageTotals(config);
        return HostText.usageRatio(totals.usedBytes(), totals.totalBytes());
    }

    private static UIElement storageLoadLine(Supplier<Component> text, IntSupplier color) {
        Label label = new Label();
        Supplier<Component> styledText = () -> text.get().copy().withColor(color.getAsInt());
        label.setText(styledText.get());
        label.bind(DataBindingBuilder.componentS2C(styledText).build());
        label.textStyle(StorageSystemLoadPanelUI::storageLoadTextStyle);
        return label;
    }

    private static void storageLoadTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(true)
            .fontSize(RIGHT_DETAIL_FONT_SIZE)
            .textWrap(TextWrap.NONE)
            .textShadow(false);
    }

    private static Component storageStatus(StorageHostPanelUI.Config config) {
        Component domainStatus = config.infiniteDomainStatus().get();
        if (!domainStatus.getString().isEmpty()) {
            return domainStatus;
        }
        if (isInfiniteDisplay(config)) {
            return Component.translatable("gui.neoecoae.storage.infinite_value");
        }
        StorageHostPanelUI.StorageTypeLine line = highestPressureLine(config);
        if (line == null) {
            return Component.translatable("gui.neoecoae.storage.status.stable");
        }
        long used = line.usedBytes().getAsLong();
        long total = line.totalBytes().getAsLong();
        float ratio = HostText.usageRatio(used, total);
        if (total > 0L && ratio >= 1.0F) {
            return Component.translatable("gui.neoecoae.storage.status.full", line.type().desc());
        }
        if (ratio >= 0.9F) {
            return Component.translatable("gui.neoecoae.storage.status.high", line.type().desc());
        }
        if (ratio >= 0.75F) {
            return Component.translatable("gui.neoecoae.storage.status.warning", line.type().desc());
        }
        return Component.translatable("gui.neoecoae.storage.status.stable");
    }

    private static int storageStatusColor(StorageHostPanelUI.Config config) {
        StorageHostPanelUI.StorageTypeLine line = highestPressureLine(config);
        return line == null
            ? HostText.MUTED
            : HostText.usedValueColor(line.usedBytes().getAsLong(), line.totalBytes().getAsLong());
    }

    private static boolean isInfiniteDisplay(StorageHostPanelUI.Config config) {
        return config.migratingToInfinite().getAsBoolean() || isInfiniteLoad(config);
    }

    private static String currentLoadPercent(StorageHostPanelUI.Config config) {
        StorageTotals totals = storageTotals(config);
        return HostText.percent(totals.usedBytes(), totals.totalBytes());
    }

    private static StorageTotals storageTotals(StorageHostPanelUI.Config config) {
        long used = 0L;
        long total = 0L;
        for (StorageHostPanelUI.StorageTypeLine line : config.storageTypes()) {
            used = saturatedAdd(used, line.usedBytes().getAsLong());
            total = saturatedAdd(total, line.totalBytes().getAsLong());
        }
        return new StorageTotals(used, total);
    }

    private static StorageHostPanelUI.StorageTypeLine highestPressureLine(StorageHostPanelUI.Config config) {
        StorageHostPanelUI.StorageTypeLine best = null;
        float bestRatio = -1.0F;
        for (StorageHostPanelUI.StorageTypeLine line : config.storageTypes()) {
            long total = line.totalBytes().getAsLong();
            if (total <= 0L) {
                continue;
            }
            float ratio = HostText.usageRatio(line.usedBytes().getAsLong(), total);
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = line;
            }
        }
        return best;
    }

    private static long saturatedAdd(long left, long right) {
        long result = left + Math.max(0L, right);
        return result < 0L ? Long.MAX_VALUE : result;
    }

    private record StorageTotals(long usedBytes, long totalBytes) {
    }

    private static Component systemLoadBytesTooltip(StorageHostPanelUI.Config config) {
        BigInteger total = BigInteger.ZERO;
        for (StorageHostPanelUI.StorageTypeLine line : config.storageTypes()) {
            total = total.add(parseHugeAmount(line.infiniteBytesExactText().get()));
        }
        return Component.literal(HostText.preciseHugeAmount(total)).withColor(HostText.USED)
            .append(Component.literal(" ").withColor(HostText.MUTED))
            .append(Component.translatable("gui.neoecoae.storage.bytes_used").withColor(HostText.MUTED));
    }

    private static Component systemLoadTypesTooltip(StorageHostPanelUI.Config config) {
        long total = 0L;
        for (StorageHostPanelUI.StorageTypeLine line : config.storageTypes()) {
            total = saturatedAdd(total, line.usedTypes().getAsLong());
        }
        return Component.translatable("gui.neoecoae.common.types").withColor(HostText.MUTED)
            .append(Component.literal(": " + HostText.fullTypeProgress(total, 0L).usedText()).withColor(HostText.MUTED));
    }

    private static Component systemLoadTypeTooltip(StorageHostPanelUI.StorageTypeLine line) {
        long usedTypes = Math.max(0L, line.usedTypes().getAsLong());
        if (usedTypes == 0L) {
            return Component.empty();
        }
        return line.type().desc().copy()
            .withColor(HostText.storageTypeAccentColor(line.type(), line.registryIndex()))
            .append(Component.literal(" ").withColor(HostText.MUTED))
            .append(Component.translatable("gui.neoecoae.common.types").withColor(HostText.MUTED))
            .append(Component.literal(": " + HostText.fullTypeProgress(usedTypes, 0L).usedText()).withColor(HostText.MUTED));
    }

    private static BigInteger parseHugeAmount(String value) {
        try {
            return new BigInteger(value == null || value.isBlank() ? "0" : value);
        } catch (NumberFormatException ignored) {
            return BigInteger.ZERO;
        }
    }

    private static final class SystemLoadTooltipElement extends UIElement {
        private final List<TooltipValueElement> values = new ArrayList<>();

        private SystemLoadTooltipElement(StorageHostPanelUI.Config config) {
            addValue(() -> Component.translatable("gui.neoecoae.storage.system_load")
                .withColor(0x55FFFF));
            addValue(() -> systemLoadBytesTooltip(config));
            addValue(() -> systemLoadTypesTooltip(config));
            for (StorageHostPanelUI.StorageTypeLine line : config.storageTypes()) {
                addValue(() -> systemLoadTypeTooltip(line));
            }
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                List<Component> lines = values.stream()
                    .map(TooltipValueElement::getValue)
                    .filter(value -> !Component.empty().equals(value))
                    .toList();
                event.hoverTooltips = new HoverTooltips(lines, null, null, null);
            });
        }

        private void addValue(Supplier<Component> supplier) {
            TooltipValueElement value = new TooltipValueElement(supplier);
            values.add(value);
            addChild(value);
        }
    }

    private static final class TooltipValueElement extends UIElement implements IBindable<Component> {
        private Component value;

        private TooltipValueElement(Supplier<Component> supplier) {
            value = supplier.get();
            bind(DataBindingBuilder.componentS2C(supplier).build());
            layout(layout -> layout.width(0).height(0));
        }

        @Override
        public IDataSource<Component> setValue(@Nullable Component value) {
            this.value = value == null ? Component.empty() : value;
            return this;
        }

        @Override
        public Component getValue() {
            return value;
        }
    }
}
