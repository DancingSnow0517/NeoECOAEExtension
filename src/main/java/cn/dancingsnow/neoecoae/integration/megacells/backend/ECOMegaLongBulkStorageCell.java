package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import gripe._90.megacells.definition.MEGAItems;
import gripe._90.megacells.misc.CompressionChain;
import gripe._90.megacells.misc.CompressionService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A finite long-range variant of MEGA Cells' bulk cell.
 *
 * <p>The normal insert/extract path uses long values. BigInteger is only used when the MEGA API
 * expands a stored amount into its compressed display variants.</p>
 */
public final class ECOMegaLongBulkStorageCell extends ECOStorageCell {
    private static final String DATA_TAG = "neoecoae_mega_long_bulk";
    private static final String ENTRIES_TAG = "entries";
    private static final String ITEM_TAG = "item";
    private static final String COMPONENTS_TAG = "components";
    private static final String UNITS_TAG = "units";
    private static final long MAX_UNITS = Long.MAX_VALUE;

    private final ItemStack stack;
    @Nullable
    private final ISaveProvider container;
    private final List<AEItemKey> filters;
    private final boolean compressionEnabled;
    private final Map<AEItemKey, Long> storedUnits = new LinkedHashMap<>();
    private final Map<AEItemKey, CompressionChain> chains = new HashMap<>();
    private boolean persisted = true;

    public ECOMegaLongBulkStorageCell(ItemStack stack, @Nullable ISaveProvider container) {
        super(stack, container);
        this.stack = stack;
        this.container = container;
        this.compressionEnabled = getUpgradesInventory().isInstalled(MEGAItems.COMPRESSION_CARD);
        this.filters = readFilters();
        loadStoredUnits();
    }

    @Override
    public boolean isInfiniteStorageEligible() {
        return false;
    }

    @Override
    public CellState getStatus() {
        if (storedUnits.isEmpty()) {
            return CellState.EMPTY;
        }
        return getRemainingItemCount() > 0 ? CellState.NOT_EMPTY : CellState.FULL;
    }

    @Override
    public long getStoredItemCount() {
        long total = 0L;
        for (Map.Entry<AEItemKey, Long> entry : storedUnits.entrySet()) {
            long factor = unitFactor(entry.getKey(), entry.getKey());
            total = saturatedAdd(total, entry.getValue() / factor);
        }
        return total;
    }

    @Override
    public long getRemainingItemCount() {
        for (long units : storedUnits.values()) {
            if (units < MAX_UNITS) {
                return MAX_UNITS;
            }
        }
        return storedUnits.size() < filters.size() ? MAX_UNITS : 0L;
    }

    @Override
    public long getFreeBytes() {
        return Math.max(0L, getTotalBytes() - getUsedBytes());
    }

    @Override
    public int getUnusedItemCount() {
        return 0;
    }

    @Override
    public long getUsedBytes() {
        long typeBytes = saturatedMultiply(getStoredItemTypes(), getBytesPerType());
        long amountBytes = getStoredItemCount() / Math.max(1, getKeyType().getAmountPerByte());
        return saturatedAdd(typeBytes, amountBytes);
    }

    @Override
    public long getStoredItemTypes() {
        return storedUnits.size();
    }

    @Override
    public long getRemainingItemTypes() {
        return Math.max(0L, getTotalItemTypes() - getStoredItemTypes());
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return insertInternal(what, amount, mode);
    }

    @Override
    public long insertForMigration(AEKey what, long amount, Actionable mode) {
        return insertInternal(what, amount, mode);
    }

    private long insertInternal(AEKey what, long amount, Actionable mode) {
        if (amount <= 0L || !(what instanceof AEItemKey item)) {
            return 0L;
        }

        AEItemKey slot = findSlot(item, true);
        if (slot == null) {
            return 0L;
        }

        long factor = unitFactor(slot, item);
        long current = storedUnits.getOrDefault(slot, 0L);
        long remaining = MAX_UNITS - current;
        long accepted = Math.min(amount, divideSaturated(remaining, factor));
        if (accepted <= 0L) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            storedUnits.put(slot, current + saturatedMultiply(accepted, factor));
            saveChanges();
        }
        return accepted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0L || !(what instanceof AEItemKey item)) {
            return 0L;
        }

        AEItemKey slot = findSlot(item, false);
        if (slot == null) {
            return 0L;
        }

        long factor = unitFactor(slot, item);
        long available = storedUnits.getOrDefault(slot, 0L);
        long requestedUnits = saturatedMultiply(amount, factor);
        long extracted = Math.min(requestedUnits, available) / factor;
        if (extracted <= 0L) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            long remaining = available - saturatedMultiply(extracted, factor);
            if (remaining == 0L) {
                storedUnits.remove(slot);
            } else {
                storedUnits.put(slot, remaining);
            }
            saveChanges();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (Map.Entry<AEItemKey, Long> entry : storedUnits.entrySet()) {
            long units = entry.getValue();
            if (units <= 0L) {
                continue;
            }

            AEItemKey storedKey = entry.getKey();
            CompressionChain chain = chainFor(storedKey);
            if (!chain.isEmpty()) {
                if (compressionEnabled) {
                    // MEGA's public expansion API uses BigInteger; this is an output boundary, not the storage hot path.
                    chain.initStacks(BigInteger.valueOf(units), chain.size() - 1, storedKey)
                        .forEach(out::add);
                } else {
                    out.add(storedKey, units / unitFactor(storedKey, storedKey));
                }
            } else {
                out.add(storedKey, units);
            }
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return what instanceof AEItemKey item && (findSlot(item, false) != null || findSlot(item, true) != null);
    }

    @Override
    public boolean canFitInsideCell() {
        return storedUnits.isEmpty();
    }

    @Override
    public void clearAllStoredStacks() {
        storedUnits.clear();
        saveChanges();
    }

    @Override
    public void persist() {
        if (persisted) {
            return;
        }

        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (storedUnits.isEmpty()) {
            custom.remove(DATA_TAG);
        } else {
            ListTag entries = new ListTag();
            for (Map.Entry<AEItemKey, Long> entry : storedUnits.entrySet()) {
                CompoundTag value = new CompoundTag();
                value.putString(ITEM_TAG, entry.getKey().getId().toString());
                value.put(COMPONENTS_TAG, writeComponents(entry.getKey()));
                value.putLong(UNITS_TAG, entry.getValue());
                entries.add(value);
            }
            CompoundTag data = new CompoundTag();
            data.put(ENTRIES_TAG, entries);
            custom.put(DATA_TAG, data);
        }

        if (custom.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        }
        persisted = true;
    }

    private List<AEItemKey> readFilters() {
        List<AEItemKey> result = new ArrayList<>();
        var config = getConfigInventory();
        for (int i = 0; i < config.size(); i++) {
            if (config.getKey(i) instanceof AEItemKey item && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private void loadStoredUnits() {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag data = custom.getCompound(DATA_TAG);
        ListTag entries = data.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            AEItemKey key = readKey(entry);
            long units = entry.getLong(UNITS_TAG);
            if (key != null && units > 0L) {
                storedUnits.merge(key, units, ECOMegaLongBulkStorageCell::saturatedAdd);
            }
        }
    }

    @Nullable
    private static AEItemKey readKey(CompoundTag entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.getString(ITEM_TAG));
        if (id == null) {
            return null;
        }
        var item = BuiltInRegistries.ITEM.getHolder(id).orElse(null);
        if (item == null) {
            return null;
        }
        var patch = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, entry.getCompound(COMPONENTS_TAG))
            .result().orElse(DataComponentPatch.EMPTY);
        return AEItemKey.of(new ItemStack(item, 1, patch));
    }

    private static CompoundTag writeComponents(AEItemKey key) {
        return (CompoundTag) DataComponentPatch.CODEC.encodeStart(
            NbtOps.INSTANCE, key.getReadOnlyStack().getComponentsPatch()).result().orElseGet(CompoundTag::new);
    }

    @Nullable
    private AEItemKey findSlot(AEItemKey item, boolean allowEmpty) {
        for (AEItemKey stored : storedUnits.keySet()) {
            if (matches(stored, item)) {
                return stored;
            }
        }
        for (AEItemKey filter : filters) {
            if (matches(filter, item) && (allowEmpty || storedUnits.containsKey(filter))) {
                return filter;
            }
        }
        return null;
    }

    private boolean matches(AEItemKey configured, AEItemKey item) {
        return configured.equals(item) || compressionEnabled && chainFor(configured).containsVariant(item);
    }

    private CompressionChain chainFor(AEItemKey key) {
        return chains.computeIfAbsent(key, CompressionService::getChain);
    }

    private long unitFactor(AEItemKey configured, AEItemKey item) {
        return CompressionChain.clamp(chainFor(configured).unitFactor(item), MAX_UNITS);
    }

    private static long divideSaturated(long value, long divisor) {
        if (divisor <= 1L) {
            return value;
        }
        return value / divisor;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > MAX_UNITS / right ? MAX_UNITS : left * right;
    }

    private static long saturatedAdd(long left, long right) {
        return left > MAX_UNITS - right ? MAX_UNITS : left + right;
    }

    @Override
    protected void saveChanges() {
        persisted = false;
        persist();
        if (container != null) {
            container.saveChanges();
        }
    }
}
