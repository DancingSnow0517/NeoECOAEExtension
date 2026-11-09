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
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.CombinedExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.storage.SaturatingStackAccumulator;
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
    private final List<MEStorage> candidateCells = new ArrayList<>();

    ECOStorageInterfaceTransfer(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void invalidateInfiniteStorageView() {
        cachedStorageEngine = null;
        cachedInfiniteStorage = null;
        cachedCombinedStorage = null;
        candidateCells.clear();
        ioPortTransfer.reset();
    }

    long transferStorageInterfaceContents(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (!host.isFormed() || !storageInterface.isStorageTransferMode() || !storageInterface.isTargetOnline()) {
            return 0L;
        }
        var grid = storageInterface.getMainNode().getGrid();
        if (grid == null) return 0L;

        MEStorage hostStorage = getStorageInterfaceHostStorage();
        if (hostStorage == null) return 0L;

        long moved = ioPortTransfer.transfer(
            grid,
            hostStorage,
            storageInterface.isStorageInputMode(),
            storageInterface.isIgnoringCreativeStorageInput(),
            storageInterface.isExcludingStorageBusContainers(),
            IActionSource.ofMachine(storageInterface),
            storageInterface.storageResourceFilter()
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
        List<MEStorage> cells = candidateCells;
        cells.clear();
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
        if (cachedCombinedStorage == null || !cachedCombinedStorage.inventories().equals(cells)) {
            cachedCombinedStorage = new CombinedStorage(cells, host.getBlockState().getBlock().getName(),
                    () -> !host.storageHostMode().isInfiniteState() || host.canInsertIntoInfiniteDomain());
        }
        return cachedCombinedStorage;
    }

    record CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description,
            BooleanSupplier allowAccess)
        implements MEStorage, CombinedExactAmountSource, cn.dancingsnow.neoecoae.storage.StorageListingDelta,
            NetworkInventoryCache.Mounts {
        CombinedStorage {
            inventories = List.copyOf(inventories);
        }
        CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description) {
            this(inventories, description, () -> true);
        }

        @Override public long neoecoae$mountGeneration() { return allowAccess.getAsBoolean() ? 0 : 1; }

        @Override public boolean neoecoae$withMounts(java.util.function.Consumer<List<MEStorage>> action) {
            action.accept(allowAccess.getAsBoolean() ? inventories : List.of());
            return true;
        }

        @Override public long neoecoae$listingVersion() {
            if (!allowAccess.getAsBoolean()) return -1;
            return inventories.size() == 1 && inventories.get(0) instanceof cn.dancingsnow.neoecoae.storage.StorageListingVersion source
                ? source.neoecoae$listingVersion() : UNKNOWN;
        }

        @Override public Object neoecoae$listingIdentity() {
            return inventories.size() == 1 && inventories.get(0) instanceof cn.dancingsnow.neoecoae.storage.StorageListingVersion source
                ? source.neoecoae$listingIdentity() : this;
        }

        @Override public boolean neoecoae$visitListingChanges(long since, long current, java.util.function.ObjLongConsumer<AEKey> visitor) {
            return allowAccess.getAsBoolean() && since >= 0 && current >= 0 && inventories.size() == 1
                && inventories.get(0) instanceof cn.dancingsnow.neoecoae.storage.StorageListingDelta source
                && source.neoecoae$visitListingChanges(since,current,visitor);
        }

        @Override
        public long insert(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
            if (!allowAccess.getAsBoolean()) return 0L;
            long inserted = 0L;
            for (int pass = 0; pass < 2 && inserted < amount; pass++) {
                for (MEStorage inventory : inventories) {
                    if (inserted >= amount) break;
                    boolean bulk = inventory instanceof IECOStorageCell cell && cell.prioritizesMarkedInserts();
                    if (bulk != (pass == 0)) continue;
                    inserted += inventory.insert(key, amount - inserted, mode, source);
                }
            }
            return inserted;
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
