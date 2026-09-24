package cn.dancingsnow.neoecoae.grid;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Grid-owned pattern key and free-slot index for ECO buses. */
public class PatternCatalog implements IECOPatternStorageService, IGridServiceProvider {
    private final Map<IGridNode, IECOPatternStorage> patternStorages = new IdentityHashMap<>();
    private final Map<ECOCraftingPatternBusBlockEntity, BusIndex<AEItemKey>> busIndexes = new IdentityHashMap<>();
    private final Object2IntOpenHashMap<AEItemKey> patternCounts = new Object2IntOpenHashMap<>();
    private final IECOPatternStorage combinedStorage = this::tryInsertPattern;
    @Nullable private IECOPatternStorage preferredStorage;

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        IGridServiceProvider.super.addNode(gridNode, savedData);
        IECOPatternStorage storage = gridNode.getService(IECOPatternStorage.class);
        if (storage == null) return;
        patternStorages.put(gridNode, storage);
        if (storage instanceof ECOCraftingPatternBusBlockEntity bus) {
            indexBus(bus);
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        IGridServiceProvider.super.removeNode(gridNode);
        IECOPatternStorage storage = patternStorages.remove(gridNode);
        if (storage == preferredStorage) preferredStorage = null;
        if (storage instanceof ECOCraftingPatternBusBlockEntity bus) {
            BusIndex<AEItemKey> index = busIndexes.remove(bus);
            if (index != null) for (AEItemKey key : index.keys) decrement(key);
        }
    }

    /** Called synchronously by the inventory callback, before provider publication is coalesced. */
    public void onPatternSlotChanged(ECOCraftingPatternBusBlockEntity bus, int slot) {
        BusIndex<AEItemKey> index = busIndexes.get(bus);
        if (index == null) return;
        InternalInventory inventory = bus.getTerminalPatternInventory();
        if (index.keys.size() != inventory.size() || slot < 0 || slot >= index.keys.size()) {
            indexBus(bus);
            return;
        }
        AEItemKey oldKey = index.keys.get(slot);
        AEItemKey newKey = AEItemKey.of(inventory.getStackInSlot(slot));
        if (java.util.Objects.equals(oldKey, newKey)) return;
        decrement(oldKey);
        increment(newKey);
        index.set(slot, newKey);
    }

    private void indexBus(ECOCraftingPatternBusBlockEntity bus) {
        BusIndex<AEItemKey> previous = busIndexes.remove(bus);
        if (previous != null) for (AEItemKey key : previous.keys) decrement(key);
        InternalInventory inventory = bus.getTerminalPatternInventory();
        BusIndex<AEItemKey> index = new BusIndex<>(inventory.size());
        for (int slot = 0; slot < index.keys.size(); slot++) {
            AEItemKey key = AEItemKey.of(inventory.getStackInSlot(slot));
            index.set(slot, key);
            increment(key);
        }
        busIndexes.put(bus, index);
    }

    private void increment(@Nullable AEItemKey key) {
        if (key != null) patternCounts.addTo(key, 1);
    }

    private void decrement(@Nullable AEItemKey key) {
        if (key == null) return;
        int count = patternCounts.getInt(key);
        if (count <= 1) patternCounts.removeInt(key);
        else patternCounts.put(key, count - 1);
    }

    public ECOPatternInsertionResult tryInsertPattern(ItemStack patternItem) {
        if (patternItem.isEmpty()) return ECOPatternInsertionResult.INCOMPATIBLE;
        AEItemKey key = AEItemKey.of(patternItem);
        if (key != null && patternCounts.containsKey(key)) {
            for (var entry : patternStorages.entrySet()) {
                if (entry.getKey().isActive() && entry.getValue() instanceof ECOCraftingPatternBusBlockEntity bus) {
                    BusIndex<AEItemKey> index = busIndexes.get(bus);
                    if (index != null && index.contains(key)) {
                        return bus.canAcceptPattern(patternItem)
                                ? ECOPatternInsertionResult.ALREADY_PRESENT
                                : ECOPatternInsertionResult.INCOMPATIBLE;
                    }
                }
            }
        }
        boolean noSpace = false;
        boolean incompatible = false;
        IECOPatternStorage preferred = preferredStorage;
        if (preferred != null && isActiveCandidate(preferred)) {
            if (!hasRoom(preferred)) {
                ECOPatternInsertionResult result = fullBusResult(preferred, patternItem);
                if (result == ECOPatternInsertionResult.ALREADY_PRESENT) return result;
                noSpace = result == ECOPatternInsertionResult.NO_SPACE;
                incompatible = result == ECOPatternInsertionResult.INCOMPATIBLE;
            } else {
                ECOPatternInsertionResult result = preferred.insertPattern(patternItem);
                if (result == ECOPatternInsertionResult.INSERTED || result == ECOPatternInsertionResult.ALREADY_PRESENT)
                    return result;
                noSpace = result == ECOPatternInsertionResult.NO_SPACE;
                incompatible = result == ECOPatternInsertionResult.INCOMPATIBLE;
            }
        }
        for (var entry : patternStorages.entrySet()) {
            IECOPatternStorage storage = entry.getValue();
            if (!entry.getKey().isActive() || storage == preferred) continue;
            if (!hasRoom(storage)) {
                ECOPatternInsertionResult result = fullBusResult(storage, patternItem);
                if (result == ECOPatternInsertionResult.ALREADY_PRESENT) return result;
                noSpace |= result == ECOPatternInsertionResult.NO_SPACE;
                incompatible |= result == ECOPatternInsertionResult.INCOMPATIBLE;
                continue;
            }
            switch (storage.insertPattern(patternItem)) {
                case INSERTED -> {
                    preferredStorage = storage;
                    return ECOPatternInsertionResult.INSERTED;
                }
                case ALREADY_PRESENT -> { return ECOPatternInsertionResult.ALREADY_PRESENT; }
                case NO_SPACE -> noSpace = true;
                case INCOMPATIBLE -> incompatible = true;
                default -> { }
            }
        }
        return noSpace ? ECOPatternInsertionResult.NO_SPACE
                : incompatible ? ECOPatternInsertionResult.INCOMPATIBLE : ECOPatternInsertionResult.NO_TARGET;
    }

    private boolean isActiveCandidate(IECOPatternStorage storage) {
        return patternStorages.entrySet().stream().anyMatch(entry -> entry.getValue() == storage
                && entry.getKey().isActive());
    }

    private boolean hasRoom(IECOPatternStorage storage) {
        if (storage instanceof ECOCraftingPatternBusBlockEntity bus) {
            BusIndex<AEItemKey> index = busIndexes.get(bus);
            return index == null || index.emptySlots > 0;
        }
        return true;
    }

    private static ECOPatternInsertionResult fullBusResult(IECOPatternStorage storage, ItemStack patternItem) {
        if (storage instanceof ECOCraftingPatternBusBlockEntity bus) {
            if (!bus.canAcceptPattern(patternItem)) return ECOPatternInsertionResult.INCOMPATIBLE;
            if (bus.containsPatternInCluster(patternItem)) return ECOPatternInsertionResult.ALREADY_PRESENT;
            return ECOPatternInsertionResult.NO_SPACE;
        }
        return storage.insertPattern(patternItem);
    }

    @Override
    public IECOPatternStorage getPatternStorage() {
        return combinedStorage;
    }

    static final class BusIndex<K> {
        private final List<K> keys;
        private final Object2IntOpenHashMap<K> counts = new Object2IntOpenHashMap<>();
        private int emptySlots;

        BusIndex(int slots) {
            keys = new ArrayList<>(Collections.nCopies(slots, null));
            emptySlots = slots;
        }

        void set(int slot, @Nullable K key) {
            K previous = keys.get(slot);
            if (previous != null) {
                int count = counts.getInt(previous);
                if (count <= 1) counts.removeInt(previous);
                else counts.put(previous, count - 1);
            } else {
                emptySlots--;
            }
            keys.set(slot, key);
            if (key == null) emptySlots++;
            else counts.addTo(key, 1);
        }

        boolean contains(K key) {
            return counts.containsKey(key);
        }

        int emptySlots() { return emptySlots; }
    }
}
