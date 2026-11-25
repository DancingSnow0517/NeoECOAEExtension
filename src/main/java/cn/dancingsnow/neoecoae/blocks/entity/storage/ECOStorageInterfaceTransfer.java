package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
import cn.dancingsnow.neoecoae.grid.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.grid.crafting.display.terminal.CombinedExactAmountSource;
import cn.dancingsnow.neoecoae.grid.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.grid.storage.listing.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.grid.storage.cell.ECOCreativeCell;
import cn.dancingsnow.neoecoae.grid.storage.cell.ECOStorageCell;
import cn.dancingsnow.neoecoae.grid.storage.cell.ECOInfiniteResourceCell;
import cn.dancingsnow.neoecoae.grid.storage.listing.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.grid.storage.bulk.ECOBulkStorageCell;
import cn.dancingsnow.neoecoae.grid.storage.bulk.ECOCompressionService;
import cn.dancingsnow.neoecoae.grid.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.grid.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.grid.storage.transfer.ECOIOPortTransfer;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

/** Moves storage contents in full-key batches using AE2 ME IO Port transfer semantics. */
final class ECOStorageInterfaceTransfer {
    private final ECOStorageSystemBlockEntity host;
    private final ECOIOPortTransfer ioPortTransfer = new ECOIOPortTransfer();
    private ECOInfiniteStorageEngine cachedStorageEngine;
    private MEStorage cachedInfiniteStorage;
    private NEStorageCluster cachedCluster;
    private CombinedStorage cachedCombinedStorage;
    private final Map<ECODriveBlockEntity, IECOStorageCell> installed = new IdentityHashMap<>();
    private final List<ECODriveBlockEntity> untrackedDrives = new ArrayList<>();
    private boolean hostStorageDirty = true;

    ECOStorageInterfaceTransfer(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void invalidateInfiniteStorageView() {
        cachedStorageEngine = null;
        cachedInfiniteStorage = null;
        invalidateHostStorage();
        ioPortTransfer.reset();
    }

    void invalidateHostStorage() {
        hostStorageDirty = true;
        cachedCluster = null;
        cachedCombinedStorage = null;
        installed.clear();
        untrackedDrives.clear();
    }

    void driveConfigurationChanged(ECODriveBlockEntity drive) {
        if (hostStorageDirty) return;
        IECOStorageCell inventory = eligibleCell(drive, installedCell(drive));
        if (inventory != installed.get(drive)) {
            invalidateHostStorage();
        } else if (inventory != null) {
            cachedCombinedStorage.refreshPriority(inventory);
            cachedCombinedStorage.contentsChanged(inventory);
        }
    }

    void cellContentsChanged(ECOStorageCell inventory) {
        if (cachedCombinedStorage != null) cachedCombinedStorage.contentsChanged(inventory);
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
        NEStorageCluster cluster = host.getCluster();
        if (cluster == null) return null;
        ECOInfiniteStorageEngine engine = host.storageHostMode().isInfiniteState() ? host.getInfiniteEngine() : null;
        if (engine != cachedStorageEngine) {
            cachedStorageEngine = engine;
            cachedInfiniteStorage = engine == null ? null : host.createInfiniteStorageView(engine);
            invalidateHostStorage();
        }
        if (!hostStorageDirty && cluster == cachedCluster) {
            boolean changed = false;
            for (ECODriveBlockEntity drive : untrackedDrives) {
                if (eligibleCell(drive, installedCell(drive)) != installed.get(drive)) {
                    changed = true;
                    break;
                }
            }
            if (!changed) return cachedCombinedStorage;
        }

        List<MEStorage> cells = new ArrayList<>();
        if (cachedInfiniteStorage != null) cells.add(cachedInfiniteStorage);
        installed.clear();
        untrackedDrives.clear();
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IECOStorageCell inventory = installedCell(drive);
            if (inventory != null && !hasNativeNotifications(inventory)) untrackedDrives.add(drive);
            IECOStorageCell eligible = eligibleCell(drive, inventory);
            if (eligible != null) {
                installed.put(drive, eligible);
                cells.add(eligible);
            }
        }
        cachedCombinedStorage = cells.isEmpty() ? null : new CombinedStorage(cells,
                host.getBlockState().getBlock().getName(),
                () -> !host.storageHostMode().isInfiniteState() || host.canInsertIntoInfiniteDomain());
        hostStorageDirty = false;
        cachedCluster = cluster;
        return cachedCombinedStorage;
    }

    private IECOStorageCell installedCell(ECODriveBlockEntity drive) {
        return drive.isInstalledStorageAvailable() ? drive.getCellInventory() : null;
    }

    private IECOStorageCell eligibleCell(ECODriveBlockEntity drive, @Nullable IECOStorageCell inventory) {
        return inventory != null && host.getTier().compareTo(inventory.getTier()) >= 0
            && !ECOInfiniteStorageMember.isMember(drive.getInstalledCellStack()) ? inventory : null;
    }

    private static boolean hasNativeNotifications(MEStorage inventory) {
        Class<?> type = inventory.getClass();
        return type == ECOStorageCell.class || type == ECOBulkStorageCell.class
            || type == ECOInfiniteResourceCell.class || type == ECOCreativeCell.class;
    }

    static final class CombinedStorage implements MEStorage, CombinedExactAmountSource, NetworkInventoryCache.Mounts {
        private final List<MEStorage> inventories;
        private final net.minecraft.network.chat.Component description;
        private final BooleanSupplier allowAccess;
        private final Reference2IntOpenHashMap<MEStorage> priorities = new Reference2IntOpenHashMap<>();
        private final List<MEStorage> polledPriorities;
        private final List<MEStorage> polledListings;
        private final List<MEStorage> bulkCells;
        private final boolean distinct;
        private ReferenceOpenHashSet<MEStorage> changedListings = new ReferenceOpenHashSet<>();
        private long compressionRevision = ECOCompressionService.revision();
        private List<MEStorage> orderedInserts;
        private List<MEStorage> itemInserts;
        private List<MEStorage> fluidInserts;

        CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description,
                BooleanSupplier allowAccess) {
            this.inventories = List.copyOf(inventories);
            this.description = description;
            this.allowAccess = allowAccess;
            boolean distinct = true;
            var polledPriorities = new ArrayList<MEStorage>();
            var polledListings = new ArrayList<MEStorage>();
            var bulkCells = new ArrayList<MEStorage>();
            for (MEStorage inventory : inventories) {
                distinct &= !priorities.containsKey(inventory);
                priorities.put(inventory, storagePriority(inventory));
                if (!hasNativeNotifications(inventory)) {
                    polledListings.add(inventory);
                    if (inventory instanceof IECOStorageCell) polledPriorities.add(inventory);
                }
                if (inventory instanceof ECOBulkStorageCell) bulkCells.add(inventory);
            }
            this.distinct = distinct;
            this.polledPriorities = List.copyOf(polledPriorities);
            this.polledListings = List.copyOf(polledListings);
            this.bulkCells = List.copyOf(bulkCells);
        }

        CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description) {
            this(inventories, description, () -> true);
        }

        @Override public long neoecoae$mountGeneration() { return allowAccess.getAsBoolean() ? 0 : 1; }

        @Override public boolean neoecoae$distinctMountsGuaranteed() { return distinct; }

        @Override public boolean neoecoae$withMounts(java.util.function.Consumer<List<MEStorage>> action) {
            action.accept(allowAccess.getAsBoolean() ? inventories : List.of());
            return true;
        }

        @Override
        public boolean neoecoae$visitChangedMounts(Consumer<MEStorage> action) {
            if (!allowAccess.getAsBoolean()) return true;
            long revision = ECOCompressionService.revision();
            if (revision != compressionRevision) {
                compressionRevision = revision;
                changedListings.addAll(bulkCells);
            }
            if (!changedListings.isEmpty()) {
                var pending = changedListings;
                changedListings = new ReferenceOpenHashSet<>();
                pending.forEach(action);
            }
            polledListings.forEach(action);
            return true;
        }

        void contentsChanged(MEStorage inventory) {
            if (hasNativeNotifications(inventory) && priorities.containsKey(inventory)) changedListings.add(inventory);
        }

        private void refreshPriority(MEStorage inventory) {
            int priority = storagePriority(inventory);
            if (priorities.put(inventory, priority) != priority) orderedInserts = null;
        }

        private void refreshInsertionOrder() {
            polledPriorities.forEach(this::refreshPriority);
            if (orderedInserts != null) return;
            var sorted = new ArrayList<>(inventories);
            sorted.sort((left, right) -> Integer.compare(priorities.getInt(right), priorities.getInt(left)));
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
            var accumulator = new cn.dancingsnow.neoecoae.grid.crafting.display.terminal.StorageListingAccumulator();
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
