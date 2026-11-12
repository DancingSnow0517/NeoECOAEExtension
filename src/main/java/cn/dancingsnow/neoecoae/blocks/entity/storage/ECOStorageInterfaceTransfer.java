package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.CombinedExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.transfer.ECOIOPortTransfer;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;

/** Moves storage contents in full-key batches using AE2 ME IO Port transfer semantics. */
final class ECOStorageInterfaceTransfer {
    private final ECOStorageSystemBlockEntity host;
    private final ECOIOPortTransfer ioPortTransfer = new ECOIOPortTransfer();
    private ECOInfiniteStorageEngine cachedStorageEngine;
    private MEStorage cachedInfiniteStorage;
    private CombinedStorage cachedCombinedStorage;

    ECOStorageInterfaceTransfer(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void invalidateInfiniteStorageView() {
        cachedStorageEngine = null;
        cachedInfiniteStorage = null;
        ioPortTransfer.reset();
    }

    long transferStorageInterfaceContents(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (!host.isFormed() || !storageInterface.isStorageTransferMode() || !storageInterface.isTargetOnline()) {
            ioPortTransfer.reset();
            return 0L;
        }
        var grid = storageInterface.getMainNode().getGrid();
        if (grid == null) { ioPortTransfer.reset(); return 0L; }

        MEStorage hostStorage = getStorageInterfaceHostStorage();
        if (hostStorage == null) { ioPortTransfer.reset(); return 0L; }

        long moved = ioPortTransfer.transfer(
            grid,
            hostStorage,
            storageInterface.isStorageInputMode(),
            storageInterface.isIgnoringCreativeStorageInput(),
            storageInterface.isExcludingStorageBusContainers(),
            IActionSource.ofMachine(storageInterface),
            storageInterface.storageResourceFilter(),
            host.getLevel() == null ? 0L : host.getLevel().getGameTime(),
            cn.dancingsnow.neoecoae.config.NEConfig.storageOutputTransferIntervalTicks,
            cn.dancingsnow.neoecoae.config.NEConfig.storageOutputFlushThreshold
        );
        if (moved > 0L) {
            host.setChanged();
            host.markForUpdate();
        }
        return moved;
    }

    @Nullable
    MEStorage getStorageInterfaceHostStorage() {
        if (host.storageHostMode().isTransitioning()
            || host.storageHostMode().isInfiniteState()
                && (host.isInfiniteExitRequested() || !host.canUseHostDomainStorage())) {
            return null;
        }
        List<MEStorage> cells = new ArrayList<>();
        if (host.canUseHostDomainStorage()) {
            ECOInfiniteStorageEngine engine = host.getInfiniteEngine();
            if (engine != cachedStorageEngine) {
                cachedStorageEngine = engine;
                cachedInfiniteStorage = engine == null ? null : host.createInfiniteStorageView(engine);
            }
            if (cachedInfiniteStorage != null) cells.add(cachedInfiniteStorage);
        }
        if (host.getCluster() == null) return null;

        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            IECOStorageCell cell = drive.getCellInventory();
            if (cell != null
                && host.getTier().compareTo(cell.getTier()) >= 0
                && !host.isInfiniteMemberCell(drive.getCellStack())) {
                cells.add(cell);
            }
        }
        if (cells.isEmpty()) {
            cachedCombinedStorage = null;
            return null;
        }
        if (cachedCombinedStorage == null || !cachedCombinedStorage.hasInventories(cells)) {
            cachedCombinedStorage = new CombinedStorage(cells, host.getBlockState().getBlock().getName(),
                    () -> !host.storageHostMode().isInfiniteState() || host.canInsertIntoInfiniteDomain());
        }
        return cachedCombinedStorage;
    }

    static final class CombinedStorage implements MEStorage, CombinedExactAmountSource, NetworkInventoryCache.Mounts {
        private final List<MEStorage> inventories;
        private final net.minecraft.network.chat.Component description;
        private final BooleanSupplier allowAccess;
        private final int[] priorities;
        private List<MEStorage> orderedInserts;
        private List<MEStorage> itemInserts;
        private List<MEStorage> fluidInserts;

        CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description,
                BooleanSupplier allowAccess) {
            this.inventories = List.copyOf(inventories);
            this.description = description;
            this.allowAccess = allowAccess;
            priorities = new int[inventories.size()];
        }

        CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description) {
            this(inventories, description, () -> true);
        }

        boolean hasInventories(List<MEStorage> current) {
            if (current.size() != inventories.size()) return false;
            for (int i = 0; i < current.size(); i++) if (current.get(i) != inventories.get(i)) return false;
            return true;
        }

        @Override public long neoecoae$mountGeneration() { return allowAccess.getAsBoolean() ? 0 : 1; }

        @Override public boolean neoecoae$withMounts(java.util.function.Consumer<List<MEStorage>> action) {
            action.accept(allowAccess.getAsBoolean() ? inventories : List.of());
            return true;
        }

        private void refreshInsertionOrder() {
            boolean changed = orderedInserts == null;
            for (int i = 0; i < inventories.size(); i++) {
                int priority = storagePriority(inventories.get(i));
                if (priority != priorities[i]) { priorities[i] = priority; changed = true; }
            }
            if (!changed) return;
            // 1.21.1 has arbitrary, mutable priorities. Preserve stable tie order and observe every change.
            int[] order = new int[inventories.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            it.unimi.dsi.fastutil.ints.IntArrays.stableSort(order,
                (left, right) -> Integer.compare(priorities[right], priorities[left]));
            var sorted = new ArrayList<MEStorage>(order.length);
            for (int index : order) sorted.add(inventories.get(index));
            orderedInserts = List.copyOf(sorted);
            itemInserts = insertCandidates(AEKeyType.items());
            fluidInserts = insertCandidates(AEKeyType.fluids());
        }

        private List<MEStorage> insertCandidates(AEKeyType requested) {
            var result = new ArrayList<MEStorage>();
            for (MEStorage inventory : orderedInserts) {
                // Subclasses and optional multi-channel cells retain their own acceptance rules.
                if (inventory.getClass() == ECOStorageCell.class) {
                    AEKeyType type = ((ECOStorageCell) inventory).getKeyType();
                    if ((type == AEKeyType.items() || type == AEKeyType.fluids()) && type != requested) continue;
                }
                result.add(inventory);
            }
            return result.size() == orderedInserts.size() ? orderedInserts : List.copyOf(result);
        }

        @Override
        public long insert(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
            if (!allowAccess.getAsBoolean()) return 0L;
            refreshInsertionOrder();
            long inserted = 0L;
            var candidates = key instanceof AEItemKey ? itemInserts
                : key instanceof AEFluidKey ? fluidInserts : orderedInserts;
            for (MEStorage inventory : candidates) {
                if (inserted >= amount) break;
                inserted += inventory.insert(key, amount - inserted, mode, source);
            }
            return inserted;
        }

        private static int storagePriority(MEStorage inventory) {
            if (!(inventory instanceof IECOStorageCell cell)) return 0;
            int priority = cell.getStoragePriority();
            // Keep third-party/mock cells that only implement the legacy marked-insert flag
            // ahead of ordinary storage while the new local priority API is absent.
            return priority == 0 && cell.prioritizesMarkedInserts() ? 1 : priority;
        }

        @Override
        public long extract(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
            if (!allowAccess.getAsBoolean()) return 0L;
            long extracted = 0L;
            for (MEStorage inventory : inventories) {
                if (extracted >= amount) break;
                extracted += inventory.extract(key, amount - extracted, mode, source);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (!allowAccess.getAsBoolean()) return;
            var accumulator = new cn.dancingsnow.neoecoae.crafting.display.terminal.StorageListingAccumulator();
            for (MEStorage inventory : inventories) {
                accumulator.append(inventory, out, inventory::getAvailableStacks);
            }
        }

        @Override
        public boolean neoecoae$hasSaturatingListing() { return true; }

        @Override
        public void neoecoae$listWithExactAmounts(KeyCounter out, BiConsumer<AEKey, ExactAmount> visitor) {
            if (!allowAccess.getAsBoolean()) return;
            Map<AEKey, ExactAmount> totals = new HashMap<>();
            for (MEStorage inventory : inventories) {
                KeyCounter contribution = new KeyCounter();
                if (inventory instanceof CombinedExactAmountSource source) {
                    source.neoecoae$listWithExactAmounts(contribution,
                        (key, amount) -> totals.merge(key, amount, ExactAmount::add));
                } else {
                    inventory.getAvailableStacks(contribution);
                    ExactAmountCollector.visitAmounts(inventory, contribution,
                        (key, amount) -> totals.merge(key, amount, ExactAmount::add));
                }
                SaturatingStackAccumulator.addAll(out, contribution);
            }
            totals.forEach(visitor);
        }

        @Override
        public void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
            neoecoae$listWithExactAmounts(new KeyCounter(), visitor);
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return description;
        }
    }
}
