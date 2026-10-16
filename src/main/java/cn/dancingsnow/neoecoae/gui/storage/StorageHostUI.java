package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import appeng.api.storage.cells.CellState;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import java.util.List;
import java.util.UUID;
import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/** The existing JSON loader owns the host's geometry and element tree. */
public final class StorageHostUI {
    public static final int ROOT_WIDTH = 253;
    public static final int ROOT_HEIGHT = 231;
    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/storage/storage_controller");

    private StorageHostUI() {}

    public record StorageTypeLine(ECOCellType type, int registryIndex, Supplier<Component> displayName,
        BooleanSupplier visible, LongSupplier usedTypes, LongSupplier totalTypes,
        LongSupplier usedBytes, LongSupplier totalBytes, Supplier<String> infiniteBytesText,
        @Nullable ResourceLocation resourceId, Supplier<BigInteger> exactUsedBytes) {
        public StorageTypeLine(ECOCellType type, int registryIndex, Supplier<Component> displayName,
            BooleanSupplier visible, LongSupplier usedTypes, LongSupplier totalTypes,
            LongSupplier usedBytes, LongSupplier totalBytes, Supplier<String> infiniteBytesText) {
            this(type, registryIndex, displayName, visible, usedTypes, totalTypes, usedBytes, totalBytes,
                infiniteBytesText, null, () -> BigInteger.valueOf(Math.max(0, usedBytes.getAsLong())));
        }
        public StorageTypeLine(ECOCellType type, int registryIndex, LongSupplier usedTypes,
            LongSupplier totalTypes, LongSupplier usedBytes, LongSupplier totalBytes, Supplier<String> infiniteBytesText) {
            this(type, registryIndex, type::desc, type::visible, usedTypes, totalTypes, usedBytes, totalBytes, infiniteBytesText);
        }
    }

    public record Config(Supplier<Component> title, IntSupplier tier, BooleanSupplier formed,
        LongSupplier storedEnergy, LongSupplier maxEnergy, LongSupplier energyConsumePerTick,
        Supplier<String> totalUsedBytesText, Supplier<List<CellEntry>> cellEntries, List<StorageTypeLine> storageTypes,
        BooleanSupplier infiniteStorage, BooleanSupplier migratingToInfinite,
        BooleanSupplier canExtractInfiniteComponents, Supplier<String> domainId, IntSupplier missingMembers,
        IItemHandlerModifiable infiniteComponentInventory, @Nullable Player player, CellExtractor cellExtractor,
        CellInserter cellInserter, Supplier<ItemStack> hostItem) {
        public Config(Supplier<Component> title, IntSupplier tier, BooleanSupplier formed,
            LongSupplier storedEnergy, LongSupplier maxEnergy, LongSupplier energyConsumePerTick,
            Supplier<String> totalUsedBytesText, Supplier<List<CellEntry>> cellEntries, List<StorageTypeLine> storageTypes,
            BooleanSupplier infiniteStorage, BooleanSupplier migratingToInfinite,
            BooleanSupplier canExtractInfiniteComponents, Supplier<String> domainId, IntSupplier missingMembers,
            IItemHandlerModifiable infiniteComponentInventory, @Nullable Player player, CellExtractor cellExtractor,
            CellInserter cellInserter) {
            this(title, tier, formed, storedEnergy, maxEnergy, energyConsumePerTick, totalUsedBytesText, cellEntries,
                storageTypes, infiniteStorage, migratingToInfinite, canExtractInfiniteComponents, domainId,
                missingMembers, infiniteComponentInventory, player, cellExtractor, cellInserter, () -> ItemStack.EMPTY);
        }

        public Config(Supplier<Component> title, IntSupplier tier, BooleanSupplier formed,
            LongSupplier storedEnergy, LongSupplier maxEnergy, LongSupplier energyConsumePerTick,
            Supplier<String> totalUsedBytesText, Supplier<List<CellEntry>> cellEntries, List<StorageTypeLine> storageTypes,
            BooleanSupplier infiniteStorage, BooleanSupplier migratingToInfinite,
            BooleanSupplier canExtractInfiniteComponents, Supplier<String> domainId, IntSupplier missingMembers,
            IItemHandlerModifiable infiniteComponentInventory, @Nullable Player player, CellExtractor cellExtractor) {
            this(title, tier, formed, storedEnergy, maxEnergy, energyConsumePerTick, totalUsedBytesText, cellEntries,
                storageTypes, infiniteStorage, migratingToInfinite, canExtractInfiniteComponents, domainId,
                missingMembers, infiniteComponentInventory, player, cellExtractor, (position, expected) -> false);
        }

        public Config(Supplier<Component> title, IntSupplier tier, BooleanSupplier formed,
            LongSupplier storedEnergy, LongSupplier maxEnergy, LongSupplier energyConsumePerTick,
            Supplier<String> totalUsedBytesText, Supplier<List<CellEntry>> cellEntries, List<StorageTypeLine> storageTypes,
            BooleanSupplier infiniteStorage, BooleanSupplier migratingToInfinite,
            BooleanSupplier canExtractInfiniteComponents, Supplier<String> domainId, IntSupplier missingMembers,
            IItemHandlerModifiable infiniteComponentInventory) {
            this(title, tier, formed, storedEnergy, maxEnergy, energyConsumePerTick, totalUsedBytesText, cellEntries,
                storageTypes, infiniteStorage, migratingToInfinite, canExtractInfiniteComponents, domainId,
                missingMembers, infiniteComponentInventory, null, (position, expected, shift) -> false);
        }

        HolderLookup.Provider registries() {
            return player == null ? RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                : player.registryAccess();
        }
    }

    @FunctionalInterface
    public interface CellExtractor {
        boolean extract(BlockPos position, UUID expected, boolean toInventory);
    }

    @FunctionalInterface
    public interface CellInserter {
        boolean insert(BlockPos position, ItemStack expected);
    }

    public record CellEntry(int typeId, int tier, int kind, long usedTypes, long totalTypes,
        long usedBytes, long totalBytes, boolean infinite, CellState state, boolean online,
        @Nullable BlockPos position, ItemStack stack, @Nullable Component extractionBlockReason,
        @Nullable UUID interactionId, @Nullable Component panelTierName) {
        public static final CellEntry EMPTY = new CellEntry(-1, 0, 0, 0, 0, 0, 0, false, CellState.ABSENT, false);

        public CellEntry(int typeId, int tier, int kind, long usedTypes, long totalTypes,
            long usedBytes, long totalBytes, boolean infinite, CellState state, boolean online,
            @Nullable BlockPos position, ItemStack stack, @Nullable Component extractionBlockReason,
            @Nullable UUID interactionId) {
            this(typeId, tier, kind, usedTypes, totalTypes, usedBytes, totalBytes, infinite, state, online,
                position, stack, extractionBlockReason, interactionId, null);
        }

        public CellEntry(int typeId, int tier, int kind, long usedTypes, long totalTypes,
            long usedBytes, long totalBytes, boolean infinite, CellState state, boolean online) {
            this(typeId, tier, kind, usedTypes, totalTypes, usedBytes, totalBytes, infinite, state, online,
                null, ItemStack.EMPTY, null, null);
        }

        public CellEntry atDrive(BlockPos position, ItemStack stack, @Nullable Component reason) {
            return atDrive(position, stack, reason, null);
        }

        public CellEntry atDrive(BlockPos position, ItemStack stack, @Nullable Component reason, @Nullable UUID interactionId) {
            return new CellEntry(typeId, tier, kind, usedTypes, totalTypes, usedBytes, totalBytes, infinite,
                state, online, position, stack.copy(), reason, interactionId, panelTierName);
        }

        public CellEntry withPanelTier(IECOTier tier) {
            return new CellEntry(typeId, this.tier, kind, usedTypes, totalTypes, usedBytes, totalBytes, infinite,
                state, online, position, stack, extractionBlockReason, interactionId, tier.getPanelTierName());
        }

        public CellEntry(int typeId, int tier, int kind, long usedTypes, long totalTypes,
            long usedBytes, long totalBytes, boolean infinite) {
            this(typeId, tier, kind, usedTypes, totalTypes, usedBytes, totalBytes, infinite,
                usedBytes == 0 ? CellState.EMPTY : totalBytes > 0 && usedBytes >= totalBytes ? CellState.FULL
                    : totalTypes > 0 && usedTypes >= totalTypes ? CellState.TYPES_FULL : CellState.NOT_EMPTY, true);
        }

        public boolean installed() { return tier > 0 || !stack.isEmpty(); }
        public static final int KIND_EMPTY = 0;
        public static final int KIND_ITEM = 1;
        public static final int KIND_FLUID = 2;
        public static final int KIND_OTHER = 3;
        /** @deprecated The fallback includes all non-item/non-fluid key types. */
        @Deprecated
        public static final int KIND_GAS = KIND_OTHER;
    }

    public static UIElement create(Config config) {
        return create(config, () -> null);
    }

    public static UIElement create(Config config, Supplier<IECOTier> panelTier) {
        return NeoUiJsonLoader.load(JSON_LAYOUT, new JsonContext(config, panelTier));
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final Config config;
        private final Supplier<IECOTier> panelTier;
        private StoragePanelSync sync;
        private StorageHostCharts charts;
        private int columns;

        private JsonContext(Config config, Supplier<IECOTier> panelTier) {
            this.config = config;
            this.panelTier = panelTier;
        }

        @Override
        public UIElement createCustom(JsonObject definition) {
            String custom = definition.get("custom").getAsString();
            return switch (custom) {
                case "storage_root" -> {
                    UIElement root = new UIElement();
                    columns = definition.get("cell_columns").getAsInt();
                    if (columns < 1 || columns > 32) throw new IllegalArgumentException("Invalid storage cell columns");
                    sync = new StoragePanelSync(root, () -> config, columns * 3);
                    charts = new StorageHostCharts(sync, columns * 3, config.hostItem().get(), panelTier);
                    yield root;
                }
                case "storage_cell_grid" -> new StorageCellGrid(sync, columns, config);
                case "infinite_component_slot" -> infiniteComponentSlot(config);
                case "player_inventory" -> playerInventory();
                default -> charts.create(custom, definition);
            };
        }
    }

    private static UIElement infiniteComponentSlot(Config config) {
        ItemHandlerSlot slot = new ItemHandlerSlot(config.infiniteComponentInventory(), 0)
            .setCanTake(player -> canTakeInfiniteComponent(player, config.canExtractInfiniteComponents()));
        return new ItemSlot(slot).style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
    }

    private static boolean canTakeInfiniteComponent(@Nullable Player player, BooleanSupplier canExtract) {
        if (canExtract.getAsBoolean()) return true;
        if (player != null) {
            player.displayClientMessage(Component.translatable("tooltip.neoecoae.storage.infinite_component_locked"), true);
        }
        return false;
    }

    static InventorySlots playerInventory() {
        InventorySlots inventory = new InventorySlots();
        inventory.apply(slot -> slot.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.getChildren().forEach(child -> child.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.hotbar.getLayout().marginTop(4);
        return inventory;
    }

    static List<CellEntry> safeEntries(Supplier<List<CellEntry>> supplier) {
        List<CellEntry> entries = supplier.get();
        return entries == null ? List.of() : entries;
    }
}
