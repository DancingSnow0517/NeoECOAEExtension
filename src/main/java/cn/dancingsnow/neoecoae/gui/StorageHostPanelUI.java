package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostChannelScrollerView;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class StorageHostPanelUI {
    public static final int LEFT_PANEL_WIDTH = 172;
    public static final int RIGHT_PANEL_WIDTH = 156;
    public static final int PANEL_HEIGHT = 200;
    private static final int PANEL_PADDING = 2;
    private static final int TEXT_MAX_WIDTH = LEFT_PANEL_WIDTH - 16;
    private static final int RIGHT_INSET_X = 8;
    private static final int RIGHT_INSET_Y = 19;
    private static final int RIGHT_INSET_WIDTH = RIGHT_PANEL_WIDTH - 20;
    private static final int RIGHT_INSET_HEIGHT = 168;
    private static final int SCROLLBAR_THUMB_WIDTH = 12;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;
    private static final int SCROLLBAR_HORIZONTAL_OFFSET = 2;
    private static final int PROGRESS_ROW_LABEL_WIDTH = 24;
    private static final int PROGRESS_ROW_BAR_WIDTH = 36;

    private static final int DARK_TEXT_PRIMARY = 0xD6D0E0;
    private static final int DARK_TEXT_VALUE = 0x8377FF;
    private static final int DARK_TEXT_USED = 0x00FC00;
    private static final int DARK_TEXT_MUTED = 0xAAA4B2;
    private static final int DARK_TEXT_WARNING = 0xFFD65A;
    private static final int DARK_TEXT_ORANGE = 0xFF9A3D;
    private static final int DARK_TEXT_ERROR = 0xFF6A75;
    private static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;
    private static final int DARK_PANEL_OUTER = 0xFF17141E;
    private static final int DARK_PANEL_LOAD_INNER = 0xFF201E27;
    private static final int BYTES_IN_K = 1024;
    private static final long BYTES_IN_M = BYTES_IN_K * 1024L;
    private static final long BYTES_IN_G = BYTES_IN_M * 1024L;
    private static final long BYTES_IN_T = BYTES_IN_G * 1024L;
    private static final long BYTES_IN_P = BYTES_IN_T * 1024L;
    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
        ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));
    private static final ThreadLocal<DecimalFormat> COMPACT_DECIMAL =
        ThreadLocal.withInitial(() -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));

    private StorageHostPanelUI() {
    }

    public record StorageTypeLine(
        ECOCellType type,
        int registryIndex,
        LongSupplier usedTypes,
        LongSupplier totalTypes,
        LongSupplier usedBytes,
        LongSupplier totalBytes
    ) {
    }

    public record Config(
        LongSupplier storedEnergy,
        LongSupplier maxEnergy,
        List<StorageTypeLine> storageTypes
    ) {
    }

    public static ScrollerView createLeftPanel(Config config) {
        ScrollerView panel = createEmptyPanel(LEFT_PANEL_WIDTH);
        panel.addScrollViewChild(sectionLabel(() -> Component.translatable("gui.neoecoae.storage.energy"), () -> DARK_TEXT_PRIMARY));
        panel.addScrollViewChild(usedTotalRow(
            () -> Component.translatable("gui.neoecoae.storage.energy_storage").append(": "),
            () -> energyUsageText(config.storedEnergy().getAsLong(), config.maxEnergy().getAsLong()),
            config.storedEnergy(),
            config.maxEnergy()
        ));
        config.storageTypes().forEach(line -> panel.addScrollViewChild(storageTypeBlock(line)));
        return panel;
    }

    public static ScrollerView createRightPanel() {
        ScrollerView panel = createEmptyPanel(RIGHT_PANEL_WIDTH);
        panel.scrollerStyle(style -> style.verticalScrollDisplay(ScrollDisplay.NEVER));
        panel.viewContainer(view -> {
            view.getLayout().paddingAll(0);
            view.addChild(panelTitleLabel(() -> Component.translatable("gui.neoecoae.storage.system_load"))
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(0);
                    layout.top(8);
                    layout.width(RIGHT_PANEL_WIDTH - SCROLLBAR_THUMB_WIDTH);
                    layout.height(10);
                }));
            view.addChild(tinyInsetPanel(DARK_PANEL_LOAD_INNER).layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(RIGHT_INSET_X);
                layout.top(RIGHT_INSET_Y);
                layout.width(RIGHT_INSET_WIDTH);
                layout.height(RIGHT_INSET_HEIGHT);
            }));
        });
        return panel;
    }

    private static UIElement tinyInsetPanel(int innerColor) {
        UIElement panel = new UIElement();
        panel.addChild(new UIElement()
            .style(style -> style.backgroundTexture(new ColorRectTexture(DARK_PANEL_LIGHT_EDGE)))
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(0);
                layout.width(RIGHT_INSET_WIDTH);
                layout.height(RIGHT_INSET_HEIGHT);
            }));
        panel.addChild(new UIElement()
            .style(style -> style.backgroundTexture(new ColorRectTexture(DARK_PANEL_OUTER)))
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(1);
                layout.top(1);
                layout.width(RIGHT_INSET_WIDTH - 2);
                layout.height(RIGHT_INSET_HEIGHT - 2);
            }));
        panel.addChild(new UIElement()
            .style(style -> style.backgroundTexture(new ColorRectTexture(innerColor)))
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(2);
                layout.top(2);
                layout.width(RIGHT_INSET_WIDTH - 4);
                layout.height(RIGHT_INSET_HEIGHT - 4);
            }));
        return panel;
    }

    public static ScrollerView createEmptyPanel(int width) {
        ScrollerView panel = new ECOHostChannelScrollerView()
            .viewContainer(view -> {
                view.getLayout().gapAll(2);
                view.getLayout().paddingAll(PANEL_PADDING);
            })
            .verticalScroller(StorageHostPanelUI::styleStorageScrollbar);
        panel.layout(layout -> layout.height(PANEL_HEIGHT).width(width));
        return panel;
    }

    private static void styleStorageScrollbar(Scroller scroller) {
        scroller.layout(layout -> {
            layout.marginLeft(SCROLLBAR_HORIZONTAL_OFFSET);
            layout.marginRight(-SCROLLBAR_HORIZONTAL_OFFSET);
            layout.width(SCROLLBAR_THUMB_WIDTH);
        });
        scroller.headButton(button -> button.setDisplay(false));
        scroller.tailButton(button -> button.setDisplay(false));
        scroller.scrollContainer(container -> {
            container.layout(layout -> {
                layout.marginLeft(3);
                layout.width(6);
            });
            container.style(style -> style.backgroundTexture(NETextures.AE_SCROLLBAR_TRACK));
        });
        scroller.scrollBar(button -> button
            .noText()
            .buttonStyle(style -> style
                .baseTexture(IGuiTexture.EMPTY)
                .hoverTexture(IGuiTexture.EMPTY)
                .pressedTexture(IGuiTexture.EMPTY))
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY))
            .layout(layout -> {
                layout.marginLeft(-3);
                layout.width(SCROLLBAR_THUMB_WIDTH);
            })
            .addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> dragStorageScrollbar(scroller, event)));
    }

    private static void dragStorageScrollbar(Scroller scroller, UIEvent event) {
        if (event.dragHandler == null || !(event.dragHandler.draggingObject instanceof Float initialValue)) {
            return;
        }
        float trackHeight = scroller.scrollContainer.getContentHeight();
        float remainingSpace = Math.max(1, trackHeight - SCROLLBAR_THUMB_HEIGHT);
        float deltaY = scroller.getLocalMouse(event.x, event.y).y - scroller.getLocalMouse(event.dragStartX, event.dragStartY).y;
        float valueRange = scroller.getMaxValue() - scroller.getMinValue();
        scroller.setValue(initialValue + deltaY / remainingSpace * valueRange);
        event.stopImmediatePropagation();
    }

    private static UIElement storageTypeBlock(StorageTypeLine line) {
        SyncedDisplayElement block = new SyncedDisplayElement(shouldShowStorageType(line));
        block.bind(DataBindingBuilder.boolS2C(() -> shouldShowStorageType(line)).build());
        block.layout(layout -> {
            layout.gapAll(2);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        block.addChild(sectionLabel(() -> line.type().desc(), () -> storageTypeAccentColor(line.type(), line.registryIndex())));
        block.addChild(usageProgressRow(
            () -> Component.translatable("gui.neoecoae.host.metric.types"),
            () -> typeProgressText(line.usedTypes().getAsLong(), line.totalTypes().getAsLong()),
            line.usedTypes(),
            line.totalTypes()
        ));
        block.addChild(usageProgressRow(
            () -> Component.translatable("gui.neoecoae.host.metric.bytes"),
            () -> byteProgressText(line.usedBytes().getAsLong(), line.totalBytes().getAsLong()),
            line.usedBytes(),
            line.totalBytes()
        ));
        return block;
    }

    private static Label sectionLabel(Supplier<Component> text, IntSupplier color) {
        Label label = textSegment(text, color);
        label.textStyle(StorageHostPanelUI::sectionTextStyle);
        return label;
    }

    private static Label panelTitleLabel(Supplier<Component> text) {
        Label label = new Label();
        label.bind(DataBindingBuilder.componentS2C(() -> text.get().copy().withColor(DARK_TEXT_PRIMARY)).build());
        label.textStyle(StorageHostPanelUI::panelTitleTextStyle);
        return label;
    }

    private static UIElement usedTotalRow(
        Supplier<Component> prefix,
        Supplier<UsedTotalText> text,
        LongSupplier used,
        LongSupplier max
    ) {
        UIElement row = new UIElement().layout(layout -> {
            layout.height(10);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(0);
        });
        row.addChild(textSegment(prefix, () -> DARK_TEXT_MUTED));
        row.addChild(textSegment(() -> Component.literal(text.get().usedText()), () -> usedValueColor(used.getAsLong(), max.getAsLong())));
        row.addChild(textSegment(() -> Component.literal(" / "), () -> DARK_TEXT_MUTED));
        row.addChild(textSegment(() -> Component.literal(text.get().maxText()), () -> DARK_TEXT_VALUE));
        row.addChild(textSegment(() -> Component.literal(" ").append(text.get().suffix()), () -> DARK_TEXT_MUTED));
        return row;
    }

    private static UIElement usageProgressRow(
        Supplier<Component> label,
        Supplier<UsedTotalText> text,
        LongSupplier used,
        LongSupplier max
    ) {
        UIElement row = new UIElement().layout(layout -> {
            layout.height(10);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(2);
        });
        row.addChild(textSegment(label, () -> DARK_TEXT_MUTED).layout(layout -> layout.width(PROGRESS_ROW_LABEL_WIDTH)));
        row.addChild(new ProgressBar()
            .label(progressLabel -> progressLabel.setText(""))
            .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
            .bind(DataBindingBuilder.floatValS2C(() -> usageRatio(used.getAsLong(), max.getAsLong())).build())
            .layout(layout -> layout.width(PROGRESS_ROW_BAR_WIDTH).height(4))
            .addClass("eco-host-progress"));

        UIElement value = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(0);
        });
        value.addChild(textSegment(() -> Component.literal(text.get().usedText()), () -> usedValueColor(used.getAsLong(), max.getAsLong())));
        value.addChild(textSegment(() -> Component.literal(" / "), () -> DARK_TEXT_MUTED));
        value.addChild(textSegment(() -> Component.literal(text.get().maxText()), () -> DARK_TEXT_VALUE));
        row.addChild(value);
        return row;
    }

    private static UIElement usedTotalRow(
        Supplier<Component> prefix,
        Supplier<String> usedText,
        Supplier<String> maxText,
        LongSupplier used,
        LongSupplier max,
        Supplier<Component> suffix
    ) {
        return usedTotalRow(prefix, () -> new UsedTotalText(usedText.get(), maxText.get(), suffix.get()), used, max);
    }

    private static Label textSegment(Supplier<Component> text, IntSupplier color) {
        Label label = new Label();
        label.bind(DataBindingBuilder.componentS2C(() -> text.get().copy().withColor(color.getAsInt())).build());
        label.textStyle(StorageHostPanelUI::lineTextStyle);
        return label;
    }

    private static boolean shouldShowStorageType(StorageTypeLine line) {
        return line.totalBytes().getAsLong() > 0 || line.totalTypes().getAsLong() > 0;
    }

    private static int usedValueColor(long used, long max) {
        if (used <= 0 || max <= 0) {
            return DARK_TEXT_USED;
        }
        double pct = (double) used / (double) max;
        if (pct >= 1.0D) {
            return DARK_TEXT_ERROR;
        }
        if (pct >= 0.9D) {
            return DARK_TEXT_ORANGE;
        }
        if (pct >= 0.75D) {
            return DARK_TEXT_WARNING;
        }
        return DARK_TEXT_USED;
    }

    private static float usageRatio(long used, long max) {
        if (max <= 0) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, (double) used / (double) max));
    }

    private static String number(long value) {
        return NUMBER_FORMAT.get().format(value);
    }

    private static UsedTotalText energyUsageText(long used, long max) {
        String prefix = "Energy Storage: ";
        String usedText = number(used);
        String maxText = number(max);
        Component suffix = Component.literal("AE");
        if (usedTotalWidth(prefix, usedText, maxText, "AE") > TEXT_MAX_WIDTH) {
            usedText = compactTaskAmount(used);
            maxText = compactTaskAmount(max);
        }
        return new UsedTotalText(usedText, maxText, suffix);
    }

    private static UsedTotalText typeUsageText(long used, long max) {
        String usedText = number(used);
        String maxText = number(max);
        Component suffix = Component.translatable("gui.neoecoae.common.types");
        if (usedTotalWidth("", usedText, maxText, "types") > TEXT_MAX_WIDTH) {
            usedText = compactTaskAmount(used);
            maxText = compactTaskAmount(max);
        }
        return new UsedTotalText(usedText, maxText, suffix);
    }

    private static UsedTotalText typeProgressText(long used, long max) {
        return new UsedTotalText(compactTaskAmount(used), compactTaskAmount(max), Component.empty());
    }

    private static UsedTotalText byteUsageText(long used, long max) {
        String usedText = storageBytes(used);
        String maxText = storageBytes(max);
        Component suffix = Component.translatable("gui.neoecoae.storage.bytes_used");
        if (usedTotalWidth("", usedText, maxText, "bytes used") > TEXT_MAX_WIDTH) {
            suffix = Component.translatable("gui.neoecoae.storage.used_short");
        }
        if (usedTotalWidth("", usedText, maxText, "Used") > TEXT_MAX_WIDTH) {
            usedText = storageBytesCompact(used);
            maxText = storageBytesCompact(max);
        }
        return new UsedTotalText(usedText, maxText, suffix);
    }

    private static UsedTotalText byteProgressText(long used, long max) {
        return new UsedTotalText(storageBytesWhole(used), storageBytesWhole(max), Component.empty());
    }

    private static int usedTotalWidth(String prefix, String usedText, String maxText, String suffix) {
        int width = estimatedTextWidth(prefix + usedText + " / " + maxText);
        return suffix.isEmpty() ? width : width + estimatedTextWidth(" " + suffix);
    }

    private static int estimatedTextWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                width += 4;
            } else if (c < 128) {
                width += 6;
            } else {
                width += 8;
            }
        }
        return width;
    }

    private static String storageBytes(long value) {
        long safe = Math.max(0L, value);
        if (safe < BYTES_IN_G) {
            return number(safe);
        }

        long unit = BYTES_IN_G;
        String suffix = "G";
        if (safe >= BYTES_IN_P) {
            unit = BYTES_IN_P;
            suffix = "P";
        } else if (safe >= BYTES_IN_T) {
            unit = BYTES_IN_T;
            suffix = "T";
        }
        return COMPACT_DECIMAL.get().format((double) safe / (double) unit) + suffix;
    }

    private static String storageBytesCompact(long value) {
        long safe = Math.max(0L, value);
        if (safe < BYTES_IN_K) {
            return Long.toString(safe);
        }

        long unit = BYTES_IN_K;
        String suffix = "K";
        if (safe >= BYTES_IN_P) {
            unit = BYTES_IN_P;
            suffix = "P";
        } else if (safe >= BYTES_IN_T) {
            unit = BYTES_IN_T;
            suffix = "T";
        } else if (safe >= BYTES_IN_G) {
            unit = BYTES_IN_G;
            suffix = "G";
        } else if (safe >= BYTES_IN_M) {
            unit = BYTES_IN_M;
            suffix = "M";
        }
        return COMPACT_DECIMAL.get().format((double) safe / (double) unit) + suffix;
    }

    private static String storageBytesWhole(long value) {
        long safe = Math.max(0L, value);
        if (safe < BYTES_IN_K) {
            return Long.toString(safe);
        }

        long unit = BYTES_IN_K;
        String suffix = "K";
        if (safe >= BYTES_IN_P) {
            unit = BYTES_IN_P;
            suffix = "P";
        } else if (safe >= BYTES_IN_T) {
            unit = BYTES_IN_T;
            suffix = "T";
        } else if (safe >= BYTES_IN_G) {
            unit = BYTES_IN_G;
            suffix = "G";
        } else if (safe >= BYTES_IN_M) {
            unit = BYTES_IN_M;
            suffix = "M";
        }
        return Math.max(1L, Math.round((double) safe / (double) unit)) + suffix;
    }

    private static String compactTaskAmount(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        if (safe < 1_000_000L) {
            return compactDecimal(safe, 1_000L, "K");
        }
        if (safe < 1_000_000_000L) {
            return compactDecimal(safe, 1_000_000L, "M");
        }
        if (safe < 1_000_000_000_000L) {
            return compactDecimal(safe, 1_000_000_000L, "G");
        }
        return compactDecimal(safe, 1_000_000_000_000L, "T");
    }

    private static String compactDecimal(long value, long unit, String suffix) {
        double scaled = (double) Math.max(0L, value) / (double) unit;
        if (scaled >= 100.0D || Math.abs(scaled - Math.rint(scaled)) < 0.05D) {
            return String.format(Locale.US, "%.0f%s", scaled, suffix);
        }
        return String.format(Locale.US, "%.1f%s", scaled, suffix);
    }

    private static int storageTypeAccentColor(ECOCellType cellType, int index) {
        String name = cellType.desc().getString().toLowerCase(Locale.ROOT);
        if (containsAny(name, "item", "items", "物品")) {
            return 0x43B678;
        }
        if (containsAny(name, "fluid", "fluids", "流体", "流體")) {
            return 0x3A8FD6;
        }
        if (containsAny(name, "chemical", "chem", "gas", "infuse", "infusion", "pigment", "slurry", "化学", "化學")) {
            return 0x9A6AE8;
        }
        if (containsAny(name, "flux", "fe", "energy", "能量")) {
            return 0xE8A84A;
        }
        if (containsAny(name, "mana", "魔力")) {
            return 0x33B6D8;
        }
        if (containsAny(name, "source", "源质", "源質")) {
            return 0xB66AE8;
        }
        int[] palette = {0xE06C75, 0x61AFEF, 0x98C379, 0xD19A66, 0xC678DD};
        return palette[Math.floorMod(index, palette.length)];
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static void lineTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textShadow(false);
    }

    private static void sectionTextStyle(TextElement.TextStyle style) {
        lineTextStyle(style);
    }

    private static void panelTitleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(false)
            .textAlignHorizontal(Horizontal.CENTER)
            .textWrap(TextWrap.HOVER_ROLL)
            .textShadow(false);
    }

    private record UsedTotalText(String usedText, String maxText, Component suffix) {
    }

    private static final class SyncedDisplayElement extends UIElement implements IBindable<Boolean> {
        private boolean display;

        private SyncedDisplayElement(boolean display) {
            setValue(display);
        }

        @Override
        public Boolean getValue() {
            return display;
        }

        @Override
        public IDataSource<Boolean> setValue(@Nullable Boolean value) {
            display = Boolean.TRUE.equals(value);
            setDisplay(display);
            return this;
        }
    }
}
