package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOFiniteStorageDomain;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageSourceAdapterRegistry;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOTransferScheduler;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.util.NEMath;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Owns interface-transfer scheduling, finite-domain recovery leases and halted-key persistence. */
final class ECOStorageInterfaceTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOStorageSystemBlockEntity.class);
    private static final String FINITE_TRANSFER_DOMAIN_TAG = "finiteTransferDomain";
    private final java.util.Set<AEKey> haltedTransferKeys = new java.util.HashSet<>();
    private boolean unresolvedTransferHalt;
    private final net.minecraft.nbt.ListTag unresolvedHaltedKeys = new net.minecraft.nbt.ListTag();
    private final cn.dancingsnow.neoecoae.impl.storage.transfer.ECOGenericTransfer genericTransfer =
        new cn.dancingsnow.neoecoae.impl.storage.transfer.ECOGenericTransfer();
    private ECOInfiniteStorageEngine cachedStorageEngine;
    private MEStorage cachedInfiniteStorage;
    @Nullable
    private transient ECOFiniteStorageDomain finiteTransferDomain;
    @Nullable
    private transient ECOTransferScheduler finiteTransferScheduler;
    @Nullable
    private transient CompoundTag pendingFiniteTransferDomain;
    private transient boolean finiteDomainRestoreFailed;
    private transient boolean finiteDomainLeaseDurable;
    private final transient ECOStorageSourceAdapterRegistry sourceAdapterRegistry =
        new ECOStorageSourceAdapterRegistry();
    private CombinedStorage cachedCombinedStorage;

    private final ECOStorageSystemBlockEntity host;

    ECOStorageInterfaceTransfer(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    boolean blocksInfiniteMigration() {
        return finiteTransferDomain != null || pendingFiniteTransferDomain != null || finiteDomainRestoreFailed;
    }

    void invalidateInfiniteStorageView() {
        cachedStorageEngine = null;
        cachedInfiniteStorage = null;
    }

    void appendDiagnostics(net.minecraft.network.chat.MutableComponent text) {
        if (!haltedTransferKeys.isEmpty() || unresolvedTransferHalt) {
            text.append("Transfer requires review: " + haltedTransferKeys.size() + " keys\n");
        }
    }

    public boolean isFiniteTransferDomainLocked() {
        return finiteTransferDomain != null;
    }

    public boolean materializeFiniteTransferDomain() {
        if (finiteTransferDomain == null) return true;
        if (finiteDomainRestoreFailed) {
            LOGGER.error("Finite storage transfer domain at {} cannot materialize because restore failed", host.getBlockPos());
            return false;
        }
        long tick = host.getLevel() == null ? 0L : host.getLevel().getGameTime();
        if (tick < host.storageStageRetryTicks().getOrDefault("materialization", Long.MIN_VALUE)) return false;
        resetFiniteTransferScheduler();
        if (!finiteTransferDomain.materializePhaseA()) {
            LOGGER.error("Unable to materialize finite storage transfer domain at {}; drives remain locked", host.getBlockPos());
            host.storageStageRetryTicks().put("materialization", NEMath.saturatingAdd(tick, 200L));
            host.setChanged();
            return false;
        }
        // Phase A must make both the MATERIALIZING recovery snapshot and every Drive component durable before the
        // controller is allowed to relinquish recovery ownership.
        host.setChanged();
        try {
            if (host.getLevel() instanceof ServerLevel serverLevel) serverLevel.getChunkSource().save(true);
        } catch (RuntimeException e) {
            LOGGER.error("Unable to persist finite storage handoff at {}; recovery lease retained", host.getBlockPos(), e);
            host.storageStageRetryTicks().put("materialization", NEMath.saturatingAdd(tick, 200L));
            return false;
        }
        if (!finiteTransferDomain.verifyMaterialized()) {
            LOGGER.error("Unable to verify finite storage transfer domain at {}; recovery lease retained", host.getBlockPos());
            host.storageStageRetryTicks().put("materialization", NEMath.saturatingAdd(tick, 200L));
            return false;
        }
        finiteTransferDomain = null;
        pendingFiniteTransferDomain = null;
        finiteDomainRestoreFailed = false;
        finiteDomainLeaseDurable = false;
        host.storageStageRetryTicks().remove("materialization");
        host.invalidateStorageStatistics();
        host.refreshDriveStorageProviders();
        host.setChanged();
        host.markForUpdate();
        return true;
    }

    void updateFiniteTransferDomain(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (host.isInfiniteMode() || !host.isFormed()) {
            materializeFiniteTransferDomain();
            return;
        }
        boolean transferRequested = storageInterface.isStorageTransferMode();
        if (!transferRequested && pendingFiniteTransferDomain == null) {
            materializeFiniteTransferDomain();
            return;
        }
        if (host.getCluster() == null) return;
        IActionSource actionSource = IActionSource.ofMachine(storageInterface);
        if (finiteTransferDomain == null) {
            finiteTransferDomain = ECOFiniteStorageDomain.create(
                host.getCluster().getDrives().stream()
                    .filter(drive -> !host.isInfiniteMemberCell(drive.getCellStack()))
                    .toList(),
                host.getTier(), storageInterface.getStorageInterfaceMode(),
                host.getBlockState().getBlock().getName(), actionSource, pendingFiniteTransferDomain);
            long eligibleCells = host.getCluster().getDrives().stream()
                .filter(drive -> !host.isInfiniteMemberCell(drive.getCellStack()))
                .map(ECODriveBlockEntity::getCellInventory)
                .filter(java.util.Objects::nonNull)
                .filter(cell -> host.getTier().compareTo(cell.getTier()) >= 0)
                .count();
            if (finiteTransferDomain.shardCount() != eligibleCells) {
                // Optional/external cell handlers keep using their standard MEStorage path until they expose the
                // controller-domain mutation contract. Mixing both ownership models would make materialization unsafe.
                if (pendingFiniteTransferDomain != null) {
                    finiteDomainRestoreFailed = true;
                    LOGGER.error("Finite storage transfer domain at {} cannot restore because its cell handler set changed",
                        host.getBlockPos());
                    return;
                }
                materializeFiniteTransferDomain();
                return;
            }
            if (pendingFiniteTransferDomain != null) {
                try {
                    ECOFiniteStorageDomain.RestoreResult result = finiteTransferDomain.restore(
                        pendingFiniteTransferDomain, host.getLevel().registryAccess(), actionSource);
                    pendingFiniteTransferDomain = null;
                    if (result == ECOFiniteStorageDomain.RestoreResult.ALREADY_MATERIALIZED) {
                        finiteTransferDomain = null;
                        finiteDomainRestoreFailed = false;
                        host.refreshDriveStorageProviders();
                        host.setChanged();
                        return;
                    }
                    finiteDomainLeaseDurable = true;
                } catch (RuntimeException e) {
                    finiteDomainRestoreFailed = true;
                    LOGGER.error("Unable to restore finite storage transfer domain at {}; drives remain locked",
                        host.getBlockPos(), e);
                    return;
                }
            }
            host.invalidateStorageStatistics();
            host.refreshDriveStorageProviders();
            host.setChanged();
            if (pendingFiniteTransferDomain == null && !finiteDomainLeaseDurable) {
                try {
                    if (host.getLevel() instanceof ServerLevel serverLevel) serverLevel.getChunkSource().save(true);
                    finiteDomainLeaseDurable = true;
                } catch (RuntimeException e) {
                    LOGGER.error("Unable to persist finite storage ownership lease at {}; transfer remains disabled",
                        host.getBlockPos(), e);
                    return;
                }
            }
        }
        if (!finiteDomainLeaseDurable && !finiteDomainRestoreFailed) {
            try {
                host.setChanged();
                if (host.getLevel() instanceof ServerLevel serverLevel) serverLevel.getChunkSource().save(true);
                finiteDomainLeaseDurable = true;
            } catch (RuntimeException e) {
                LOGGER.error("Unable to persist finite storage ownership lease at {}; will retry", host.getBlockPos(), e);
                return;
            }
        }
        if (finiteTransferDomain.state() == ECOFiniteStorageDomain.State.MATERIALIZING) {
            materializeFiniteTransferDomain();
            return;
        }
        if (!transferRequested) {
            materializeFiniteTransferDomain();
            return;
        }
        if (finiteTransferDomain.mode() != storageInterface.getStorageInterfaceMode()) {
            finiteTransferDomain.setMode(storageInterface.getStorageInterfaceMode());
            resetFiniteTransferScheduler();
        }
    }

    void resetFiniteTransferScheduler() {
        if (finiteTransferScheduler != null) {
            haltedTransferKeys.addAll(finiteTransferScheduler.haltedKeys());
            finiteTransferScheduler.stop();
            finiteTransferScheduler = null;
        }
    }

    long transferStorageInterfaceContents(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (unresolvedTransferHalt) return 0L;
        if (!host.isFormed() || !storageInterface.isStorageTransferMode()) return 0L;
        if (!storageInterface.isTargetOnline()) return 0L;
        var grid = storageInterface.getMainNode().getGrid();
        if (grid == null) return 0L;
        MEStorage network = grid.getStorageService().getInventory();
        MEStorage hostStorage = getStorageInterfaceHostStorage();
        if (hostStorage == null) return 0L;
        if (finiteTransferDomain != null && (finiteDomainRestoreFailed || !finiteDomainLeaseDurable)) return 0L;
        IActionSource source = IActionSource.ofMachine(storageInterface);
        long moved;
        if (!host.isInfiniteMode() && finiteTransferDomain != null && !finiteDomainRestoreFailed
            && finiteDomainLeaseDurable) {
            if (finiteTransferScheduler == null) {
                finiteTransferScheduler = new ECOTransferScheduler(
                    finiteTransferDomain,
                    grid,
                    network,
                    source,
                    sourceAdapterRegistry,
                    NEConfig.storageTransferKeysPerTick,
                    NEConfig.storageTransferNanosPerTick,
                    NEConfig.storageTransferRate,
                    this::onFiniteDomainMutation
                );
                finiteTransferScheduler.start(host.getLevel().getGameTime());
                finiteTransferScheduler.restoreHalted(haltedTransferKeys);
            }
            moved = finiteTransferScheduler.tick(host.getLevel().getGameTime(), host.currentStorageBudget());
        } else {
            genericTransfer.restoreHalted(haltedTransferKeys);
            moved = storageInterface.isStorageInputMode()
                ? genericTransfer.tick(network, hostStorage, source, true, host.getLevel().getGameTime(),
                    NEConfig.storageTransferKeysPerTick, host.currentStorageBudget(),
                    NEConfig.storageTransferRate, reason -> host.storageFaults().report("generic transfer", reason, host.getLevel().getGameTime()))
                : genericTransfer.tick(hostStorage, network, source, false, host.getLevel().getGameTime(),
                    NEConfig.storageTransferKeysPerTick, host.currentStorageBudget(),
                    NEConfig.storageTransferRate, reason -> host.storageFaults().report("generic transfer", reason, host.getLevel().getGameTime()));
        }
        int previousHalted = haltedTransferKeys.size();
        haltedTransferKeys.addAll(genericTransfer.haltedKeys());
        if (finiteTransferScheduler != null) haltedTransferKeys.addAll(finiteTransferScheduler.haltedKeys());
        if (haltedTransferKeys.size() != previousHalted) host.setChanged();
        if (moved > 0L) {
            host.setChanged();
            host.markForUpdate();
        }
        return moved;
    }

    private void onFiniteDomainMutation() {
        host.setChanged();
    }

    @Nullable
    MEStorage getStorageInterfaceHostStorage() {
        if (host.storageHostMode().isTransitioning() || (host.storageHostMode().isInfiniteState() && host.isInfiniteExitRequested())) return null;
        if (host.canUseHostDomainStorage()) {
            ECOInfiniteStorageEngine engine = host.getInfiniteEngine();
            if (engine != cachedStorageEngine) {
                cachedStorageEngine = engine;
                cachedInfiniteStorage = engine == null ? null : host.createInfiniteStorageView(engine);
            }
            return cachedInfiniteStorage;
        }
        if (finiteTransferDomain != null && !finiteDomainRestoreFailed) {
            return finiteTransferDomain;
        }
        if (host.getCluster() == null) return null;

        List<MEStorage> cells = new ArrayList<>();
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            IECOStorageCell cell = drive.getCellInventory();
            if (cell != null
                && host.getTier().compareTo(cell.getTier()) >= 0
                && !host.isInfiniteMemberCell(drive.getCellStack())) {
                cells.add(cell);
            }
        }
        if (cells.isEmpty()) { cachedCombinedStorage = null; return null; }
        if (cachedCombinedStorage == null || !cachedCombinedStorage.inventories().equals(cells)) {
            cachedCombinedStorage = new CombinedStorage(cells, host.getBlockState().getBlock().getName());
        }
        return cachedCombinedStorage;
    }

    private record CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description)
        implements MEStorage {
        private CombinedStorage {
            inventories = List.copyOf(inventories);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long inserted = 0L;
            for (MEStorage inventory : inventories) {
                if (inserted >= amount) break;
                inserted += inventory.insert(key, amount - inserted, mode, source);
            }
            return inserted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = 0L;
            for (MEStorage inventory : inventories) {
                if (extracted >= amount) break;
                extracted += inventory.extract(key, amount - extracted, mode, source);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (MEStorage inventory : inventories) {
                inventory.getAvailableStacks(out);
            }
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return description;
        }
    }

    void saveHaltedTransfers(CompoundTag data, HolderLookup.Provider registries) {
        net.minecraft.nbt.ListTag halted = new net.minecraft.nbt.ListTag();
        halted.addAll(unresolvedHaltedKeys);
        for (AEKey key : haltedTransferKeys) {
            try { halted.add(key.toTagGeneric(registries)); }
            catch (RuntimeException e) { unresolvedTransferHalt = true; }
        }
        data.put("haltedStorageTransfers", halted);
        data.putBoolean("unresolvedStorageTransfer", unresolvedTransferHalt);
    }

    void saveDomain(CompoundTag data, HolderLookup.Provider registries) {
        if (finiteTransferDomain != null) {
            data.put(FINITE_TRANSFER_DOMAIN_TAG, finiteTransferDomain.save(registries));
        } else if (pendingFiniteTransferDomain != null) {
            data.put(FINITE_TRANSFER_DOMAIN_TAG, pendingFiniteTransferDomain.copy());
        }
    }

    void loadHaltedTransfers(CompoundTag data, HolderLookup.Provider registries) {
        haltedTransferKeys.clear();
        unresolvedHaltedKeys.clear();
        unresolvedTransferHalt = data.getBoolean("unresolvedStorageTransfer");
        for (var raw : data.getList("haltedStorageTransfers", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            try {
                AEKey key = AEKey.fromTagGeneric(registries, (CompoundTag) raw);
                if (key == null) { unresolvedTransferHalt = true; unresolvedHaltedKeys.add(raw.copy()); }
                else haltedTransferKeys.add(key);
            } catch (RuntimeException e) { unresolvedTransferHalt = true; unresolvedHaltedKeys.add(raw.copy()); }
        }
    }

    void loadDomain(CompoundTag data) {
        pendingFiniteTransferDomain = data.contains(FINITE_TRANSFER_DOMAIN_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)
            ? data.getCompound(FINITE_TRANSFER_DOMAIN_TAG).copy()
            : null;
        finiteDomainRestoreFailed = false;
        finiteDomainLeaseDurable = false;
    }
}
