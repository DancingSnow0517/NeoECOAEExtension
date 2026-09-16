package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Coordinates infinite-storage mode transitions and the mounted domain lifetime. */
final class ECOInfiniteStorageModeController {
    private static final int MINIMUM_MIGRATION_SOURCES = 12;

    private final ECOStorageSystemBlockEntity host;
    private final ECOInfiniteStorageTransfer transfer = new ECOInfiniteStorageTransfer();
    private final Set<UUID> durableSourceSeals = new HashSet<>();

    private boolean targetInfiniteMode;
    private boolean updating;
    private long modeCheckTick = Long.MIN_VALUE;
    private long backendGeneration;
    private int migrationDriveCursor;

    @Nullable
    private MinecraftServer mountedServer;
    @Nullable
    private UUID mountedDomainId;
    @Nullable
    private ECOInfiniteStorageEngine mountedEngine;

    ECOInfiniteStorageModeController(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void update() {
        if (host.getLevel() == null || host.getLevel().isClientSide || host.isStorageServerStopping()
            || updating || modeCheckTick == host.getLevel().getGameTime()) {
            return;
        }
        modeCheckTick = host.getLevel().getGameTime();
        updating = true;
        ECOStorageHostMode previous = host.storageHostMode();
        try {
            if (host.consumeInfiniteComponentsDirty()) {
                targetInfiniteMode = host.hasRequiredInfiniteComponents();
                // Keep a completed exit latched until the components have actually been removed.
                if (!targetInfiniteMode && !host.storageHostMode().isInfiniteState()) {
                    host.clearInfiniteExitRequest();
                }
            }
            processMode();
        } finally {
            updating = false;
            syncModeChanges(previous);
        }
    }

    private void processMode() {
        host.rememberInfiniteMembers();
        if (!host.isFormed() || host.getCluster() == null) {
            if (!host.storageHostMode().isInfiniteState()) {
                host.setStorageHostMode(ECOStorageHostMode.UNFORMED);
            }
            return;
        }
        if (host.storageHostMode() == ECOStorageHostMode.UNFORMED) {
            host.setStorageHostMode(ECOStorageHostMode.FORMED_NORMAL);
        }
        if (host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            // Finish the sealed migration before considering an exit, including after reload.
            runMigrationStep();
            return;
        }
        ECOInfiniteStorageEngine restoringEngine = getEngine();
        if (host.storageHostMode() == ECOStorageHostMode.RESTORING_TO_NORMAL
            || host.infiniteRestore().isRestoring()
            || restoringEngine != null && restoringEngine.hasPendingRestore()
            || host.isInfiniteExitRequested() && host.storageHostMode().isInfiniteState()) {
            host.setStorageHostMode(ECOStorageHostMode.RESTORING_TO_NORMAL);
            host.infiniteRestore().restoreInfiniteDomainToNormalStorageIfPossible();
            return;
        }
        if (host.storageHostMode().isInfiniteState() && !targetInfiniteMode) {
            host.setStorageHostMode(ECOStorageHostMode.RESTORING_TO_NORMAL);
            host.infiniteRestore().restoreInfiniteDomainToNormalStorageIfPossible();
            return;
        }
        if (host.storageHostMode() == ECOStorageHostMode.FORMED_NORMAL && canStartMigration()) {
            ensureDomainId();
            // Opening the destination must succeed before changing ownership of any source.
            ECOInfiniteStorageEngine engine = getEngine();
            if (engine == null || !engine.isHealthy()) {
                return;
            }
            host.setStorageHostMode(ECOStorageHostMode.MIGRATING_TO_INFINITE);
        }
        if (host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            runMigrationStep();
        }
    }

    private void syncModeChanges(ECOStorageHostMode previous) {
        if (previous != host.storageHostMode()) {
            backendGeneration++;
            host.storageInterfaceTransfer().invalidateInfiniteStorageView();
            host.infiniteRestore().invalidateExtractionCheck();
            host.invalidateStorageStatistics();
            host.refreshDriveStorageProviders();
            host.setChanged();
            host.markForUpdate();
        }
    }

    private boolean canStartMigration() {
        return !host.isInfiniteExitRequested()
            && host.getTier() == ECOTier.L9
            && host.isFormed()
            && host.getCluster() != null
            && !host.storageInterfaceTransfer().blocksInfiniteMigration()
            && !host.isStorageInterfaceTransferMode()
            && targetInfiniteMode
            && countMigrationSources() >= MINIMUM_MIGRATION_SOURCES
            && !hasForeignMembers();
    }

    int migrationProgressPercent() {
        if (host.storageHostMode() == ECOStorageHostMode.FORMED_INFINITE) {
            return 100;
        }
        if (host.storageHostMode() != ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            return 0;
        }
        int migrated = countInfiniteMembers();
        int totalTargets = migrated + countPendingMigrationTargets();
        return totalTargets == 0
            ? 100
            : Math.clamp(Math.round(migrated * 100.0F / totalTargets), 0, 100);
    }

    private int countMigrationSources() {
        return countInfiniteMembers() + countPendingMigrationTargets();
    }

    private boolean hasForeignMembers() {
        if (host.getCluster() == null) {
            return false;
        }
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (ECOInfiniteStorageMember.isMember(stack)
                && (host.infiniteDomainId() == null
                    || !ECOInfiniteStorageMember.isMemberOf(stack, host.infiniteDomainId()))) {
                return true;
            }
        }
        return false;
    }

    private int countPendingMigrationTargets() {
        if (host.getCluster() == null) {
            return 0;
        }
        int count = 0;
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack != null && !stack.isEmpty()
                && ECOInfiniteStorageTransfer.isEligible(cell)
                && !ECOInfiniteStorageMember.isMember(stack)) {
                count++;
            }
        }
        return count;
    }

    private int countInfiniteMembers() {
        if (host.getCluster() == null || host.infiniteDomainId() == null) {
            return 0;
        }
        int count = 0;
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), host.infiniteDomainId())) {
                count++;
            }
        }
        return count;
    }

    private void runMigrationStep() {
        if (!(host.getLevel() instanceof ServerLevel serverLevel) || host.getCluster() == null) {
            return;
        }
        UUID domainId = ensureDomainId();
        ECOInfiniteStorageEngine engine = getEngine();
        if (engine == null || !engine.isHealthy()) {
            return;
        }
        sealTransferSources(serverLevel, domainId);
        boolean hasPending = false;
        List<ECODriveBlockEntity> drives = new ArrayList<>(host.getCluster().getDrives());
        for (int visited = 0; visited < drives.size(); visited++) {
            ECODriveBlockEntity drive = drives.get(Math.floorMod(migrationDriveCursor++, drives.size()));
            String stage = "migration drive " + drive.getBlockPos();
            long tick = host.getLevel().getGameTime();
            if (tick < host.storageStageRetryTicks().getOrDefault(stage, Long.MIN_VALUE)) {
                hasPending = true;
                continue;
            }
            try {
                ItemStack stack = drive.getCellStack();
                if (ECOInfiniteStorageMember.isMember(stack)) {
                    if (ECOInfiniteStorageMember.isMemberOf(stack, domainId)) {
                        continue;
                    }
                    hasPending = true;
                    host.storageFaults().report(stage, "Foreign infinite storage member", tick);
                    continue;
                }
                IECOStorageCell cell = drive.getCellInventory();
                if (stack == null || stack.isEmpty() || !ECOInfiniteStorageTransfer.isEligible(cell)) {
                    continue;
                }
                hasPending = true;
                if (!durableSourceSeals.contains(ECOInfiniteStorageMember.getMigrationId(stack))) {
                    continue;
                }
                migrateDrive(drive, cell, engine, domainId);
                host.storageFaults().recovered(stage);
                break;
            } catch (RuntimeException exception) {
                hasPending = true;
                host.storageStageRetryTicks().put(stage, tick + 200L);
                host.storageFaults().report(stage, exception.toString(), tick, exception);
            }
        }
        if (!hasPending) {
            host.setStorageHostMode(ECOStorageHostMode.FORMED_INFINITE);
        }
    }

    private void migrateDrive(
        ECODriveBlockEntity drive,
        IECOStorageCell cell,
        ECOInfiniteStorageEngine engine,
        UUID domainId
    ) {
        if (!(cell instanceof IECOStorageMigrationCell migrationCell)) {
            throw new IllegalStateException("Cell handler does not support resumable migration");
        }
        UUID migration = ECOInfiniteStorageMember.beginMigration(drive.getCellStack(), domainId);
        boolean finished = transfer.step(drive.getCellStack(), migrationCell, domainId, engine,
            host.getLevel().registryAccess(),
            () -> {
                if (!durableSourceSeals.contains(migration)) {
                    throw new IllegalStateException("Source seal is not durable");
                }
            },
            () -> drive.convertCellToInfiniteMember(domainId),
            (key, amount) -> migrationTransactionId(domainId, drive, key, amount, "to-domain"),
            NEConfig.storageTransferKeysPerTick,
            host.currentStorageBudget());
        if (!finished) {
            return;
        }
        host.rememberInfiniteMembers();
        durableSourceSeals.remove(migration);
        IStorageProvider.requestUpdate(drive.getMainNode());
        host.invalidateStorageStatistics();
        host.setChanged();
        host.markForUpdate();
    }

    private void sealTransferSources(ServerLevel serverLevel, UUID domainId) {
        Set<UUID> prepared = new HashSet<>();
        long started = System.nanoTime();
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            if (prepared.size() >= NEConfig.storageTransferKeysPerTick
                || !prepared.isEmpty() && System.nanoTime() - started >= host.currentStorageBudget()) {
                break;
            }
            String stage = "migration drive " + drive.getBlockPos();
            if (host.getLevel().getGameTime() < host.storageStageRetryTicks().getOrDefault(stage, Long.MIN_VALUE)) {
                continue;
            }
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack == null || stack.isEmpty() || ECOInfiniteStorageMember.isMember(stack)
                || !ECOInfiniteStorageTransfer.isEligible(cell)) {
                continue;
            }
            try {
                UUID migration = ECOInfiniteStorageMember.beginMigration(stack, domainId);
                if (durableSourceSeals.contains(migration)) {
                    continue;
                }
                ((IECOStorageMigrationCell) cell).persistMigrationContents(serverLevel);
                drive.setChanged();
                IStorageProvider.requestUpdate(drive.getMainNode());
                prepared.add(migration);
            } catch (RuntimeException exception) {
                host.storageStageRetryTicks().put(stage, host.getLevel().getGameTime() + 200L);
                host.storageFaults().report(stage, exception.toString(), host.getLevel().getGameTime(), exception);
            }
        }
        if (!prepared.isEmpty()) {
            // Save a batch of seals together, rather than saving the dimension separately for every source disk.
            serverLevel.getChunkSource().save(true);
            durableSourceSeals.addAll(prepared);
        }
    }

    UUID migrationTransactionId( UUID domainId, ECODriveBlockEntity drive, AEKey key, long amount, String direction) {
        ECOInfiniteStorageEngine engine = getEngine();
        UUID restore = engine == null ? null : engine.restoreTransaction(key);
        if ("from-domain".equals(direction) && restore != null) {
            UUID identity = ECOInfiniteStorageMember.identity(drive.getCellStack());
            drive.setChanged();
            return UUID.nameUUIDFromBytes((restore + ":" + identity).getBytes(StandardCharsets.UTF_8));
        }
        String value = domainId + ":" + direction + ":" + drive.getBlockPos().asLong() + ":"
            + key.toTagGeneric(host.getLevel().registryAccess()) + ":" + amount;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    void exitIfSafe() {
        if (host.getMissingInfiniteMembers() > 0) {
            return;
        }
        ECOInfiniteStorageEngine engine = getEngine();
        if (engine == null || !engine.canExitOrRestore() || !engine.isEmpty() || !engine.commit().successful()) {
            return;
        }
        UUID domainId = host.infiniteDomainId();
        if (host.getCluster() != null && domainId != null) {
            for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
                if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), domainId)) {
                    drive.convertInfiniteMemberToNormalStorage(domainId);
                    IStorageProvider.requestUpdate(drive.getMainNode());
                }
            }
        }
        host.setStorageHostMode(host.isFormed()
            ? ECOStorageHostMode.FORMED_NORMAL : ECOStorageHostMode.UNFORMED);
        if (host.getLevel() instanceof ServerLevel && domainId != null) {
            // Retain transfer receipts: an old source chunk must not import the same inventory again.
            release();
        }
        host.setInfiniteDomainId(null);
        host.infiniteMemberIds().clear();
        host.refreshDriveStorageProviders();
        host.setChanged();
        host.markForUpdate();
    }

    @Nullable
    ECOInfiniteStorageEngine getEngine() {
        if (!(host.getLevel() instanceof ServerLevel serverLevel) || host.infiniteDomainId() == null) {
            release();
            return null;
        }
        MinecraftServer server = serverLevel.getServer();
        if (mountedEngine == null || mountedServer != server
            || !host.infiniteDomainId().equals(mountedDomainId)) {
            release();
            mountedServer = server;
            mountedDomainId = host.infiniteDomainId();
            mountedEngine = ECOInfiniteStorageDomains.acquire(serverLevel, host.infiniteDomainId());
        }
        return mountedEngine;
    }

    void release() {
        transfer.reset();
        durableSourceSeals.clear();
        if (mountedServer != null && mountedDomainId != null) {
            ECOInfiniteStorageDomains.release(mountedServer, mountedDomainId);
        }
        mountedServer = null;
        mountedDomainId = null;
        mountedEngine = null;
        host.storageInterfaceTransfer().invalidateInfiniteStorageView();
    }

    UUID ensureDomainId() {
        UUID domainId = host.infiniteDomainId();
        if (domainId == null) {
            domainId = UUID.randomUUID();
            host.setInfiniteDomainId(domainId);
            host.setChanged();
        }
        return domainId;
    }

    MEStorage createStorageView(ECOInfiniteStorageEngine engine) {
        long generation = backendGeneration;
        return new ECOInfiniteStorage(engine, host.getBlockState().getBlock().getName(),
            () -> generation == backendGeneration && engine == mountedEngine
                && host.canInsertIntoInfiniteDomain());
    }

    boolean canInsertIntoDomain() {
        return host.isFormed()
            && !host.isRemoved()
            && !host.isStorageServerStopping()
            && host.storageHostMode() == ECOStorageHostMode.FORMED_INFINITE
            && host.infiniteDomainId() != null
            && !host.isInfiniteExitRequested()
            && !host.infiniteRestore().isRestoring()
            && mountedEngine != null
            && mountedEngine.isHealthy()
            && !mountedEngine.hasPendingRestore();
    }
}
