package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.api.capability.ECOFeature;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.storage.infinite.ECOStorageHostMode;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Coordinates infinite-storage mode transitions and the mounted domain lifetime.
 */
final class ECOInfiniteStorageModeController {
    private static final int MINIMUM_MIGRATION_SOURCES = 12;
    // Distinct from the "migration" stage, whose runner clears its own fault after every successful run.
    static final String MIGRATION_FAULT = "migration blocked";

    private final ECOStorageSystemBlockEntity host;
    private final ECOInfiniteStorageTransfer transfer = new ECOInfiniteStorageTransfer();
    private final Set<UUID> preparedSourceSeals = new HashSet<>();

    private boolean targetInfiniteMode;
    private boolean updating;
    private long modeCheckTick = Long.MIN_VALUE;
    private long backendGeneration;
    private boolean hostAccess;
    // Injectable for tests; the per-tick migration budget is measured with it.
    LongSupplier migrationClock = System::nanoTime;
    @Nullable
    private ECOInfiniteStorageEngine.MigrationBlock migrationBlock;
    private double migrationProgressHighWater;

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
                && host.getTier().supportsFeature(ECOFeature.INFINITE_STORAGE_HOST)
                && host.isFormed()
                && host.getCluster() != null
                && !host.isStorageInterfaceTransferMode()
                && targetInfiniteMode
                && countMigrationSources() >= MINIMUM_MIGRATION_SOURCES
                && !hasForeignMembers()
                && !hasDuplicateCandidateIdentities();
    }

    private boolean hasDuplicateCandidateIdentities() {
        if (host.getCluster() == null) return false;
        Set<UUID> identities = new HashSet<>();
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack == null || stack.isEmpty()
                    || (!ECOInfiniteStorageMember.isMember(stack) && !ECOInfiniteStorageTransfer.isEligible(cell))) {
                continue;
            }
            var identity = ECOInfiniteStorageMember.getIdentity(stack);
            if (identity.isPresent() && !identities.add(identity.get())) return true;
        }
        return false;
    }

    int migrationProgressPercent() {
        if (host.storageHostMode() == ECOStorageHostMode.FORMED_INFINITE) {
            return 100;
        }
        // Floor, so 100% is only shown once the host has actually left the migration state.
        return Math.clamp((int) Math.floor(migrationProgress() * 100.0D), 0, 100);
    }

    /**
     * Completed sources plus the processed share of every source in progress, over all sources. Never decreases
     * during one migration, even when a matrix is added or a cursor is rebuilt after a reload.
     */
    double migrationProgress() {
        if (host.storageHostMode() == ECOStorageHostMode.FORMED_INFINITE) {
            return 1.0D;
        }
        if (host.storageHostMode() != ECOStorageHostMode.MIGRATING_TO_INFINITE || host.getCluster() == null) {
            return 0.0D;
        }
        int migrated = countInfiniteMembers();
        int pending = 0;
        double partial = 0.0D;
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack != null && !stack.isEmpty() && ECOInfiniteStorageTransfer.isEligible(cell)
                    && !ECOInfiniteStorageMember.isMember(stack)) {
                pending++;
                partial += transfer.progress(ECOInfiniteStorageMember.getMigrationId(stack));
            }
        }
        migrationProgressHighWater = Math.max(migrationProgressHighWater,
                combinedMigrationProgress(migrated, partial, pending));
        return migrationProgressHighWater;
    }

    static double combinedMigrationProgress(int completed, double partial, int pending) {
        int total = completed + pending;
        if (total <= 0) return 1.0D;
        return Math.clamp((completed + Math.clamp(partial, 0.0D, pending)) / total, 0.0D, 1.0D);
    }

    /** The reason the running migration currently cannot advance, or {@code null}. */
    @Nullable
    ECOInfiniteStorageEngine.MigrationBlock migrationBlock() {
        return host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE ? migrationBlock : null;
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
        if (engine == null) {
            return;
        }
        long tick = host.getLevel().getGameTime();
        // A blocked domain keeps every source sealed and waits without a cooldown: the domain state, not a drive,
        // has to change before anything can advance.
        ECOInfiniteStorageEngine.MigrationBlock blocked = engine.migrationBlock();
        boolean domainBlocked = blocked != null;
        if (!domainBlocked) {
            sealTransferSources(serverLevel, domainId);
        }
        var budget = new ECOInfiniteStorageTransfer.Budget(migrationClock,
                Math.max(0L, NEConfig.infiniteMigrationTickBudgetMicros) * 1_000L,
                NEConfig.infiniteMigrationKeysPerTick);
        boolean hasPending = false;
        // One source at a time in a stable order; leftover budget moves on to the next source in the same tick.
        for (ECODriveBlockEntity drive : migrationOrder()) {
            String stage = "migration drive " + drive.getBlockPos();
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
                if (domainBlocked || !preparedSourceSeals.contains(ECOInfiniteStorageMember.getMigrationId(stack))
                        || !budget.hasRemaining()) {
                    continue;
                }
                ECOInfiniteStorageTransfer.StepResult result = migrateDrive(drive, cell, engine, domainId, budget);
                if (result.state() == ECOInfiniteStorageTransfer.State.BLOCKED) {
                    if (blocked == null) blocked = result.block();
                } else {
                    host.storageFaults().recovered(stage);
                }
            } catch (RuntimeException exception) {
                hasPending = true;
                host.storageStageRetryTicks().put(stage, tick + 200L);
                host.storageFaults().report(stage, exception.toString(), tick, exception);
            }
        }
        if (blocked != null) {
            reportMigrationBlock(blocked, tick);
        } else if (migrationBlock != null) {
            migrationBlock = null;
            host.storageFaults().recovered(MIGRATION_FAULT);
        }
        // As before, an unwritable domain never completes the transition; a degraded readable one may.
        if (!hasPending && host.infiniteMigrationSourceIds().isEmpty() && engine.isHealthy()) {
            host.setStorageHostMode(ECOStorageHostMode.FORMED_INFINITE);
        }
    }

    private void reportMigrationBlock(ECOInfiniteStorageEngine.MigrationBlock block, long tick) {
        migrationBlock = block;
        host.storageFaults().report(MIGRATION_FAULT, block.describe(), tick);
    }

    private List<ECODriveBlockEntity> migrationOrder() {
        List<ECODriveBlockEntity> drives = new ArrayList<>(host.getCluster().getDrives());
        drives.sort(Comparator.comparingLong(drive -> drive.getBlockPos().asLong()));
        return drives;
    }

    private ECOInfiniteStorageTransfer.StepResult migrateDrive(
            ECODriveBlockEntity drive,
            IECOStorageCell cell,
            ECOInfiniteStorageEngine engine,
            UUID domainId,
            ECOInfiniteStorageTransfer.Budget budget
    ) {
        if (!(cell instanceof IECOStorageMigrationCell migrationCell)) {
            throw new IllegalStateException("Cell handler does not support resumable migration");
        }
        UUID migration = ECOInfiniteStorageMember.beginMigration(drive.getCellStack(), domainId);
        long position = drive.getBlockPos().asLong();
        ECOInfiniteStorageTransfer.StepResult result = transfer.step(drive.getCellStack(), migrationCell, domainId,
                engine, host.getLevel().registryAccess(),
                () -> {
                    if (!preparedSourceSeals.contains(migration)) {
                        throw new IllegalStateException("Source seal has not been prepared");
                    }
                },
                () -> drive.convertCellToInfiniteMember(domainId),
                (key, keyTag, amount) -> ECOInfiniteStorageTransfer.legacyReceipt(
                        domainId, "to-domain", position, keyTag, amount),
                budget);
        if (result.state() != ECOInfiniteStorageTransfer.State.COMPLETED) {
            return result;
        }
        host.rememberInfiniteMembers();
        preparedSourceSeals.remove(migration);
        host.infiniteMigrationSourceIds().remove(migration);
        IStorageProvider.requestUpdate(drive.getMainNode());
        host.invalidateStorageStatistics();
        drive.setChanged();
        host.setChanged();
        host.markForUpdate();
        return result;
    }

    private void sealTransferSources(ServerLevel serverLevel, UUID domainId) {
        Set<UUID> prepared = new HashSet<>();
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            if (prepared.size() >= ECOInfiniteStorageTransfer.SEALS_PER_TICK) break;
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
                if (host.infiniteMigrationSourceIds().add(migration)) {
                    host.setChanged();
                }
                if (preparedSourceSeals.contains(migration)) {
                    continue;
                }
                drive.flushPendingCellWrites();
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
            // Chunks and the domain are saved together by the next vanilla world save; receipts make a replay safe.
            host.setChanged();
            preparedSourceSeals.addAll(prepared);
        }
    }

    UUID migrationTransactionId(UUID domainId, ECODriveBlockEntity drive, AEKey key, long amount, String direction) {
        ECOInfiniteStorageEngine engine = getEngine();
        UUID restore = engine == null ? null : engine.restoreTransaction(key);
        if ("from-domain".equals(direction) && restore != null) {
            UUID identity = ECOInfiniteStorageMember.identity(drive.getCellStack());
            drive.setChanged();
            return UUID.nameUUIDFromBytes((restore + ":" + identity).getBytes(StandardCharsets.UTF_8));
        }
        return ECOInfiniteStorageTransfer.legacyReceipt(domainId, direction, drive.getBlockPos().asLong(),
                String.valueOf(key.toTagGeneric(host.getLevel().registryAccess())), amount);
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
        host.infiniteMigrationSourceIds().clear();
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
            refreshHostAccess();
        }
        return mountedEngine;
    }

    void release() {
        backendGeneration++;
        hostAccess = false;
        transfer.reset();
        preparedSourceSeals.clear();
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
        refreshHostAccess();
        long generation = backendGeneration;
        return new ECOInfiniteStorage(engine, host.getBlockState().getBlock().getName(),
                () -> generation == backendGeneration && engine == mountedEngine
                        && hostAccess && !host.isStorageServerStopping() && engine.canUseMountedStorage());
    }

    void hostModeChanged() {
        backendGeneration++;
        migrationProgressHighWater = 0.0D;
        if (migrationBlock != null) {
            // Leaving the migration (for example to restore) also retires its block diagnostics.
            migrationBlock = null;
            host.storageFaults().recovered(MIGRATION_FAULT);
        }
        refreshHostAccess();
    }

    void membersChanged() {
        boolean previouslyAccessible = hostAccess;
        refreshHostAccess();
        if (previouslyAccessible != hostAccess && host.getLevel() != null
                && !host.getLevel().isClientSide && !host.isStorageServerStopping()) {
            host.refreshDriveStorageProviders();
        }
    }

    /** Called synchronously by host transitions; domain health and restore locks are engine-owned. */
    void refreshHostAccess() {
        hostAccess = host.isFormed()
                && !host.isRemoved()
                && host.storageHostMode() == ECOStorageHostMode.FORMED_INFINITE
                && host.infiniteDomainId() != null
                && !host.isInfiniteExitRequested()
                && !host.infiniteRestore().isRestoring()
                && mountedEngine != null
                && host.getMissingInfiniteMembers() == 0;
    }

    boolean canInsertIntoDomain() {
        return hostAccess && !host.isStorageServerStopping() && mountedEngine.canUseMountedStorage();
    }
}
