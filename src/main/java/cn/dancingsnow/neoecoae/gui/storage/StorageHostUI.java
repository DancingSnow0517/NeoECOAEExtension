package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/** The storage controller surface, backed by the current storage implementation. */
public final class StorageHostUI {
    public static final int ROOT_WIDTH = 272;
    public static final int ROOT_HEIGHT = 216;

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

    private static final SpriteTexture BACKGROUND = SpriteTexture.of(
        NeoECOAE.id("textures/guis/estorage_infinite_controller.png"))
        .setSprite(0, 0, ROOT_WIDTH, ROOT_HEIGHT);
    private static final ResourceLocation JSON_LAYOUT =
        NeoECOAE.id("ui/storage/storage_controller");

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
        Supplier<String> domainId,
        java.util.function.IntSupplier missingMembers,
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
        return NeoUiJsonLoader.load(JSON_LAYOUT, new JsonContext(config));
    }

    /** Static storage-controller geometry lives in ui/storage/storage_controller.json. */
    private static final class JsonContext implements NeoUiJsonContext {
        private final Config config;

        private JsonContext(Config config) {
            this.config = config;
        }

        @Override
        public UIElement createCustom(com.google.gson.JsonObject definition) {
            return switch (definition.get("custom").getAsString()) {
                case "storage_title" -> new TextElement()
                    .setText(config.title().get())
                    .textStyle(style -> style.adaptiveHeight(true).adaptiveWidth(true)
                        .textWrap(TextWrap.NONE).textColor(0x3F3D52).textShadow(false));
                case "storage_domain_tooltip" -> new TitleDomainTooltip(config.domainId());
                case "storage_charts" -> {
                    UIElement charts = new UIElement();
                    StorageHostCharts.addTo(charts, config);
                    yield charts;
                }
                case "storage_type_list" -> storageTypeList(config, false);
                case "infinite_component_slot" -> infiniteComponentSlot(
                    config.canExtractInfiniteComponents(), config.infiniteComponentInventory());
                case "player_inventory" -> playerInventory(false);
                default -> throw new IllegalArgumentException("Unknown storage UI custom element: "
                    + definition.get("custom").getAsString());
            };
        }
    }

    /** Tooltip-only overlay over the title; the UUID remains synchronized without a visible label. */
    private static final class TitleDomainTooltip extends UIElement implements IBindable<Component> {
        private Component domainText = Component.empty();

        private TitleDomainTooltip(Supplier<String> domainId) {
            bind(DataBindingBuilder.componentS2C(() -> {
                String id = domainId.get();
                return id.isEmpty() ? Component.empty()
                    : Component.literal("UUID: " + id).withColor(0x55FFFF);
            }).build());
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                if (!domainText.getString().isEmpty()) {
                    event.hoverTooltips = HoverTooltips.empty().append(domainText);
                }
            });
        }

        @Override
        public Component getValue() {
            return domainText;
        }

        @Override
        public IDataSource<Component> setValue(@Nullable Component value) {
            domainText = value == null ? Component.empty() : value;
            return this;
        }
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

    private static boolean isInfinite(Config config) {
        return config.infiniteStorage().getAsBoolean() || config.migratingToInfinite().getAsBoolean();
    }

    private static UIElement storageTypeList(Config config) {
        return storageTypeList(config, true);
    }

    private static UIElement storageTypeList(Config config, boolean withLayout) {
        ScrollerView list = new ScrollerView();
        if (withLayout) {
            HostElements.absolute(list, CELL_LIST_LEFT, CELL_LIST_TOP, CELL_LIST_WIDTH, CELL_LIST_HEIGHT);
        }
        list.scrollerStyle(style -> style.horizontalScrollDisplay(ScrollDisplay.NEVER));
        // ScrollerView paints its default border on the viewport, not the list itself.
        list.viewPort(view -> view
            .style(style -> style.backgroundTexture(IGuiTexture.EMPTY))
            .layout(layout -> layout.paddingAll(0)));
        list.viewContainer(view -> view.layout(layout -> layout.paddingAll(2).gapAll(5)
            .flexDirection(FlexDirection.COLUMN)));
        UIElement warning = HostElements.syncedDisplay(() -> config.missingMembers().getAsInt() > 0);
        warning.layout(layout -> layout.widthPercent(100).gapAll(2).flexDirection(FlexDirection.COLUMN));
        warning.addChild(compactLabel(() -> Component.translatable("gui.neoecoae.storage.members_missing",
            config.missingMembers().getAsInt()), 0xFFAAAA));
        warning.addChild(compactLabel(() -> Component.translatable("gui.neoecoae.storage.members_return"), 0xFFAAAA));
        list.addScrollViewChild(warning);
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
            return Component.translatable("gui.neoecoae.storage.legacy.cell_types.used", used)
                .withColor(HostText.PRIMARY);
        }
        return Component.translatable("gui.neoecoae.storage.legacy.cell_types", used,
            formatCapacity(line.totalTypes().getAsLong())).withColor(HostText.PRIMARY);
    }

    private static Component storageBytesText(Config config, StorageTypeLine line) {
        if (isInfinite(config)) {
            return Component.translatable("gui.neoecoae.storage.legacy.cell_bytes.used", line.infiniteBytesText().get())
                .withColor(HostText.PRIMARY);
        }
        return Component.translatable("gui.neoecoae.storage.legacy.cell_bytes",
            HostText.ae2Amount(line.usedBytes().getAsLong()),
            formatCapacity(line.totalBytes().getAsLong())).withColor(HostText.PRIMARY);
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

    private static UIElement playerInventory() {
        return playerInventory(true);
    }

    private static UIElement playerInventory(boolean withLayout) {
        InventorySlots inventory = new InventorySlots();
        if (withLayout) {
            inventory.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(INVENTORY_LEFT)
                .top(INVENTORY_TOP)
                .width(INVENTORY_WIDTH)
                .height(INVENTORY_HEIGHT));
        }
        inventory.apply(slot -> slot.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.getChildren().forEach(child -> child.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.hotbar.getLayout().marginTop(4);
        return inventory;
    }

    static List<CellEntry> safeEntries(Supplier<List<CellEntry>> supplier) {
        List<CellEntry> entries = supplier.get();
        return entries == null ? List.of() : entries;
    }

    private static String formatCapacity(long value) {
        return value < 0L ? "\u221E" : HostText.ae2Amount(value);
    }

}
