package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellReference;
import cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Coordinates infinite-storage mode transitions and the mounted domain lifetime.
 */
final class ECOInfiniteStorageModeController {
    static final int MINIMUM_MIGRATION_SOURCES = 12;
    private static final String MIGRATION_DOMAIN_STAGE = "migration domain";
    private static final String RECOVERY_MEMBER_STAGE = "recover domain members";
    private static final String RECOVERY_PREFIX = "command.neoecoae.storage_recover.";
    private static final String TO_DOMAIN = "to-domain";

    private final ECOStorageSystemBlockEntity host;
    private final ECOInfiniteStorageTransfer transfer = new ECOInfiniteStorageTransfer();
    private final Set<UUID> preparedSourceSeals = new HashSet<>();

    private boolean targetInfiniteMode;
    private boolean updating;
    private long modeCheckTick = Long.MIN_VALUE;
    private long backendGeneration;
    private boolean hostAccess;
    // Display-only migration state; progress never moves backwards within one migration.
    private double migrationProgress;
    @Nullable
    private ECOInfiniteStorageEngine.MigrationBlock migrationBlock;

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
        if (host.hasLegacyCompatibilityFailure()) return;
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
        if (host.storageHostMode() == ECOStorageHostMode.FORMED_INFINITE && restoringEngine != null
                && restoringEngine.canExitOrRestore() && restoringEngine.canUseMountedStorage()) {
            releaseInactiveMemberBindings();
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
            migrationProgress = 0.0D;
            migrationBlock = null;
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
        if (host.storageHostMode() != ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            return 0;
        }
        return JdkCompat.clamp((int) Math.floor(migrationProgress * 100.0D), 0, 100);
    }

    /** Why the running migration is not progressing, or {@code null}. */
    @Nullable
    ECOInfiniteStorageEngine.MigrationBlock migrationBlock() {
        return host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE ? migrationBlock : null;
    }

    /** Completed disks plus the imported share of every started disk, over all disks taking part. */
    private void updateMigrationProgress() {
        if (host.getCluster() == null) return;
        UUID domainId = host.infiniteDomainId();
        int members = 0;
        int pending = 0;
        double partial = 0.0D;
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (domainId != null && (ECOInfiniteStorageMember.isAnonymous(stack) || ECOInfiniteStorageMember.isMemberOf(stack, domainId))) {
                members++;
            } else if (stack != null && !stack.isEmpty() && !ECOInfiniteStorageMember.isMember(stack)
                    && ECOInfiniteStorageTransfer.isEligible(drive.getCellInventory())) {
                pending++;
                UUID migration = ECOInfiniteStorageMember.getMigrationId(stack);
                if (migration != null) partial += transfer.progress(migration);
            }
        }
        int total = members + pending;
        double value = total == 0 ? 1.0D : (members + partial) / total;
        migrationProgress = Math.max(migrationProgress, Math.max(0.0D, Math.min(1.0D, value)));
    }

    /** Drives in a stable order: each disk is migrated completely before the next one starts. */
    private List<ECODriveBlockEntity> orderedDrives() {
        List<ECODriveBlockEntity> drives = new ArrayList<>(host.getCluster().getDrives());
        drives.sort(java.util.Comparator.comparing(ECODriveBlockEntity::getBlockPos));
        return drives;
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
            if (ECOInfiniteStorageMember.isMember(stack) && !ECOInfiniteStorageMember.isAnonymous(stack)
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
            if (ECOInfiniteStorageMember.isAnonymous(drive.getCellStack())
                    || ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), host.infiniteDomainId())) {
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
        long now = host.getLevel().getGameTime();
        if (engine == null || !engine.isHealthy()) {
            ECOInfiniteStorageEngine.MigrationBlock block = engine == null ? null : engine.migrationBlock();
            migrationBlock = block != null ? block : new ECOInfiniteStorageEngine.MigrationBlock("not_writable", "");
            host.storageFaults().report(MIGRATION_DOMAIN_STAGE, migrationBlock.message(), now);
            return;
        }
        host.storageFaults().recovered(MIGRATION_DOMAIN_STAGE);
        List<ECODriveBlockEntity> drives = orderedDrives();
        sealTransferSources(serverLevel, domainId, drives);
        ECOInfiniteStorageTransfer.Budget budget = ECOInfiniteStorageTransfer.Budget.perTick();
        boolean hasPending = false;
        boolean attempted = false;
        ECOInfiniteStorageEngine.MigrationBlock observed = null;
        for (ECODriveBlockEntity drive : drives) {
            String stage = "migration drive " + drive.getBlockPos();
            long tick = host.getLevel().getGameTime();
            if (tick < host.storageStageRetryTicks().getOrDefault(stage, Long.MIN_VALUE)) {
                // A drive in its retry cooldown is skipped; it does not hold back the drives after it.
                hasPending = true;
                continue;
            }
            try {
                ItemStack stack = drive.getCellStack();
                if (ECOInfiniteStorageMember.isMember(stack)) {
                    if (ECOInfiniteStorageMember.isAnonymous(stack) || ECOInfiniteStorageMember.isMemberOf(stack, domainId)) {
                        continue;
                    }
                    hasPending = true;
                    host.storageFaults().report(stage, "Foreign infinite storage member", tick);
                    continue;
                }
                IECOStorageCell cell = drive.getCellInventory();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (!ECOInfiniteStorageTransfer.isEligible(cell)) {
                    UUID sealed = ECOInfiniteStorageMember.getMigrationId(stack);
                    if (sealed != null && host.infiniteMigrationSourceIds().contains(sealed)) {
                        // A sealed source that became unreadable keeps the migration open; say so.
                        hasPending = true;
                        attempted = true;
                        String failure = cell instanceof cn.dancingsnow.neoecoae.storage.ECOStorageCell ordinary
                                ? ordinary.storageData().failure() : null;
                        var block = new ECOInfiniteStorageEngine.MigrationBlock("source_unavailable", failure);
                        if (observed == null) observed = block;
                        host.storageFaults().report(stage, block.message(), tick);
                    }
                    continue;
                }
                hasPending = true;
                if (!preparedSourceSeals.contains(ECOInfiniteStorageMember.getMigrationId(stack))) {
                    continue;
                }
                if (!budget.canContinue()) {
                    break;
                }
                attempted = true;
                ECOInfiniteStorageTransfer.StepResult result = migrateDrive(drive, cell, engine, domainId, budget);
                if (result.blocked()) {
                    // Retried every tick without a cooldown; the reason stays visible until progress resumes.
                    if (observed == null) observed = result.block();
                    host.storageFaults().report(stage, result.block().message(), tick);
                    if (engine.migrationBlock() != null) {
                        // A domain-wide reason blocks every remaining disk in the same way.
                        break;
                    }
                    continue;
                }
                host.storageFaults().recovered(stage);
                if (!result.complete()) {
                    // The budget ran out inside this disk; it continues first on the next tick.
                    break;
                }
            } catch (RuntimeException exception) {
                hasPending = true;
                host.storageStageRetryTicks().put(stage, tick + 200L);
                host.storageFaults().report(stage, exception.toString(), tick, exception);
            }
        }
        if (attempted) {
            migrationBlock = observed;
        }
        updateMigrationProgress();
        if (!hasPending && host.infiniteMigrationSourceIds().isEmpty()) {
            host.setStorageHostMode(ECOStorageHostMode.FORMED_INFINITE);
        }
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
        var releaseCompatibility = cell instanceof cn.dancingsnow.neoecoae.storage.ECOStorageCell ordinary
                && engine instanceof cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine saved
            ? cn.dancingsnow.neoecoae.storage.persistence.Legacy2042Transfers.aliases(ordinary.storageData(), saved,
                domainId, host.getLevel().dimension().location().toString(), drive.getBlockPos().asLong(), drive.getCellStack())
            : java.util.Map.<AEKey, java.util.Set<UUID>>of();
        long position = drive.getBlockPos().asLong();
        ECOInfiniteStorageTransfer.StepResult result = transfer.migrate(drive.getCellStack(), migrationCell, domainId,
                engine,
                () -> {
                    if (!preparedSourceSeals.contains(migration)) {
                        throw new IllegalStateException("Source seal has not been prepared");
                    }
                },
                () -> drive.convertCellToInfiniteMember(domainId),
                (key, encodedKey, amount) -> legacyTransactionId(domainId, TO_DOMAIN, position, encodedKey, amount),
                releaseCompatibility, budget);
        if (!result.complete()) {
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

    private void sealTransferSources(ServerLevel serverLevel, UUID domainId, List<ECODriveBlockEntity> drives) {
        Set<UUID> prepared = new HashSet<>();
        for (ECODriveBlockEntity drive : drives) {
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
        return legacyTransactionId(domainId, direction, drive.getBlockPos().asLong(),
                ECOInfiniteStorageTransfer.encodedKey(key), amount);
    }

    /**
     * Persisted controller-slot receipt. The text must stay byte-identical to
     * {@code domainId + ":" + direction + ":" + pos.asLong() + ":" + key.toTagGeneric() + ":" + amount}.
     */
    static UUID legacyTransactionId(UUID domainId, String direction, long position, String encodedKey, long amount) {
        String value = domainId + ":" + direction + ":" + position + ":" + encodedKey + ":" + amount;
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
                if (ECOInfiniteStorageMember.isAnonymous(drive.getCellStack())
                        || ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), domainId)) {
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
        boolean acquired = mountedEngine == null || mountedServer != server
                || !host.infiniteDomainId().equals(mountedDomainId);
        if (acquired) {
            release();
            mountedServer = server;
            mountedDomainId = host.infiniteDomainId();
            mountedEngine = host.storageHostMode().isInfiniteState()
                ? ECOInfiniteStorageDomains.acquireExisting(serverLevel, host.infiniteDomainId())
                : ECOInfiniteStorageDomains.acquire(serverLevel, host.infiniteDomainId());
        }
        var saved = (SavedDataInfiniteStorageEngine) mountedEngine;
        if (detachIfRevoked(saved)) return null;
        if (host.getCluster() != null)
            for (ECODriveBlockEntity drive : host.getCluster().getDrives())
                if (drive.hasLegacyRestoreReceipts()) { saved.blockLegacyRestore(); break; }
        if (saved.ownerToken() == null) {
            UUID token = UUID.randomUUID();
            if (!saved.canExitOrRestore() || !saved.persistOwnerToken(token)) return null;
            host.setInfiniteBindingToken(token);
            acquired = true;
        }
        if (acquired) refreshHostAccess();
        return mountedEngine;
    }

    boolean detachIfRevoked(SavedDataInfiniteStorageEngine engine) {
        UUID token = engine.ownerToken();
        if (token == null || token.equals(host.infiniteBindingToken())) return false;
        host.storageFaults().report("infinite domain binding",
                Component.translatableWithFallback(RECOVERY_PREFIX + "revoked",
                        "This host lost its binding to domain %s after a forced recovery.", host.infiniteDomainId()).getString(),
                host.getLevel().getGameTime());
        host.detachRevokedInfiniteDomain();
        return true;
    }

    @Nullable
    Component recoverDomain(UUID domainId) {
        return recoverDomain(domainId, false, ignored -> {});
    }

    @Nullable
    Component forceRecoverDomain(UUID domainId, Consumer<Component> success) {
        return recoverDomain(domainId, true, success);
    }

    @Nullable
    private Component recoverDomain(UUID domainId, boolean force, Consumer<Component> success) {
        if (!(host.getLevel() instanceof ServerLevel level) || host.isStorageServerStopping()
                || host.hasLegacyCompatibilityFailure() || !host.isFormed() || host.getCluster() == null
                || !host.hasRequiredInfiniteComponents()
                || !force && (!host.isFormedInfiniteMode() || host.isInfiniteExitRequested()
                    || host.infiniteRestore().isRestoring())) {
            return Component.translatable(force ? "command.neoecoae.storage_force_recover.invalid_host"
                    : RECOVERY_PREFIX + "invalid_host");
        }
        if (host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE
                || !host.infiniteMigrationSourceIds().isEmpty()) {
            return Component.translatable(RECOVERY_PREFIX + "migrating");
        }
        MinecraftServer server = level.getServer();
        if (!ECOInfiniteStorageDomains.exists(server, domainId)) {
            return Component.translatable(RECOVERY_PREFIX + "not_found", domainId.toString());
        }
        UUID previousDomain = host.infiniteDomainId();
        boolean switching = previousDomain != null && !domainId.equals(previousDomain);
        var previousHosts = ECOStorageSystemBlockEntity.getLoadedStorageHosts().stream()
                .filter(other -> other != host && other.getLevel() instanceof ServerLevel otherLevel
                        && otherLevel.getServer() == server && domainId.equals(other.infiniteDomainId()))
                .toList();
        if (previousHosts.stream().anyMatch(other -> other.isMigratingToInfinite()
                || !other.infiniteMigrationSourceIds().isEmpty())) {
            return Component.translatable(RECOVERY_PREFIX + "migrating");
        }
        if (switching) {
            if (!ECOInfiniteStorageDomains.exists(server, previousDomain)) {
                return Component.translatable(RECOVERY_PREFIX + "unhealthy");
            }
            var previous = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquireExisting(level, previousDomain);
            try {
                if (!previous.canExitOrRestore()) return Component.translatable(RECOVERY_PREFIX + "unhealthy");
                if (!previous.isEmpty()) {
                    return Component.translatable(RECOVERY_PREFIX + "previous_not_empty", previousDomain.toString());
                }
                if (!previous.canUseMountedStorage()) return Component.translatable(RECOVERY_PREFIX + "unhealthy");
                if (!java.util.Objects.equals(previous.ownerToken(), host.infiniteBindingToken())) {
                    return Component.translatable(RECOVERY_PREFIX + "mounted");
                }
            } finally {
                ECOInfiniteStorageDomains.release(server, previousDomain);
            }
        }

        var recovered = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquireExisting(level, domainId);
        try {
            boolean restoring = recovered.hasPendingRestore();
            if (!force && recovered.ownerToken() != null
                    && (switching || !recovered.ownerToken().equals(host.infiniteBindingToken()))) {
                return Component.translatable(RECOVERY_PREFIX + "mounted");
            }
            if (restoring && !force) return Component.translatable(RECOVERY_PREFIX + "unhealthy");
            if (!recovered.isHealthy() || !recovered.canExitOrRestore() || recovered.hasPendingTransfers()
                    || (!restoring && !recovered.canUseMountedStorage())) {
                return Component.translatable(RECOVERY_PREFIX + "unhealthy");
            }
            if (restoring && recovered.hasBlockedRestore()) {
                return Component.translatable(RECOVERY_PREFIX + "restore_blocked");
            }

            Set<UUID> identities = new HashSet<>();
            Set<UUID> diskIds = new HashSet<>();
            Set<UUID> requiredTargets = recovered.pendingRestoreTargets();
            Set<UUID> restoreTargets = new HashSet<>();
            List<ECODriveBlockEntity> members = new ArrayList<>();
            Map<ECODriveBlockEntity, CompoundTag> changes = new LinkedHashMap<>();
            int retained = 0;
            for (ECODriveBlockEntity drive : orderedDrives()) {
                ItemStack stack = drive.getCellStack();
                if (stack == null || stack.isEmpty()) continue;
                if (ECOInfiniteStorageMember.isMigrating(stack)) {
                    return Component.translatable(RECOVERY_PREFIX + "migrating");
                }
                UUID identity = ECOInfiniteStorageMember.getIdentity(stack).orElse(null);
                UUID diskId = ECOCellReference.id(stack);
                if (drive.hasLegacyRestoreReceipts() || (identity != null && !identities.add(identity))
                        || (diskId != null && !diskIds.add(diskId))) {
                    return Component.translatable(RECOVERY_PREFIX + "invalid_members");
                }
                IECOStorageCell cell = drive.loadedCellInventory();
                if (cell == null) return Component.translatable(RECOVERY_PREFIX + "unhealthy");
                if (ECOInfiniteStorageMember.isMember(stack)) {
                    boolean anonymous = ECOInfiniteStorageMember.isAnonymous(stack);
                    boolean targetMember = ECOInfiniteStorageMember.isMemberOf(stack, domainId);
                    boolean previousMember = switching && ECOInfiniteStorageMember.isMemberOf(stack, previousDomain);
                    if (!anonymous && (identity == null || (!targetMember && !previousMember))) {
                        return Component.translatable(RECOVERY_PREFIX + "invalid_members");
                    }
                    if (targetMember && requiredTargets.contains(identity)) {
                        if (ECOInfiniteStorageTransfer.isEligible(cell)) restoreTargets.add(identity);
                    } else if (!ECODriveBlockEntity.isEmptyInfiniteCandidate(cell)) {
                        return Component.translatable(RECOVERY_PREFIX + "invalid_members");
                    } else if (!anonymous) {
                        if (!canWriteMetadata(drive, cell)) return Component.translatable(RECOVERY_PREFIX + "unhealthy");
                        changes.put(drive, snapshotTag(drive));
                    }
                    members.add(drive);
                    continue;
                }
                if (!ECOInfiniteStorageTransfer.isEligible(cell)) {
                    if (cell.getStoredItemTypes() > 0) retained++;
                    continue;
                }
                KeyCounter contents = new KeyCounter();
                ((IECOStorageMigrationCell) cell).getMigrationStacks(contents);
                if (!contents.isEmpty()) {
                    retained++;
                    continue;
                }
                if (!canWriteMetadata(drive, cell)) return Component.translatable(RECOVERY_PREFIX + "unhealthy");
                changes.put(drive, snapshotTag(drive));
                members.add(drive);
            }
            if (!restoreTargets.containsAll(requiredTargets)) {
                return Component.translatable(RECOVERY_PREFIX + "restore_targets_missing");
            }
            if (!restoring && members.size() < MINIMUM_MIGRATION_SOURCES) {
                return Component.translatable(RECOVERY_PREFIX + "not_enough_matrices", MINIMUM_MIGRATION_SOURCES, members.size());
            }

            UUID token = UUID.randomUUID();
            if (!commitRecovery(recovered, token, changes)) {
                return Component.translatable(RECOVERY_PREFIX + "unhealthy");
            }
            finishRecovery(domainId, token, members, restoring);
            if (!force) return null;
            previousHosts.forEach(other -> other.recheckInfiniteDomainOwner(recovered));
            var message = Component.translatable("command.neoecoae.storage_force_recover.success", domainId.toString());
            if (switching) message.append(Component.translatable(RECOVERY_PREFIX + "previous_binding", previousDomain.toString()));
            if (retained > 0) message.append(Component.translatable(RECOVERY_PREFIX + "retained_cells", retained));
            success.accept(message);
            return null;
        } finally {
            ECOInfiniteStorageDomains.release(server, domainId);
        }
    }

    private static boolean canWriteMetadata(ECODriveBlockEntity drive, @Nullable IECOStorageCell inventory) {
        if (ECOCellReference.id(drive.getCellStack()) == null) return true;
        return inventory instanceof ECOStorageCell cell
                && cell.storageWritable() && cell.storageData().writeFailure() == null;
    }

    private static CompoundTag snapshotTag(ECODriveBlockEntity drive) {
        CompoundTag tag = drive.getCellStack().getTag();
        return tag == null ? null : tag.copy();
    }

    private boolean commitRecovery(SavedDataInfiniteStorageEngine engine, UUID token,
                                   Map<ECODriveBlockEntity, CompoundTag> changes) {
        UUID previousToken = engine.ownerToken();
        if (!engine.persistOwnerToken(token)) return false;
        if (markAnonymous(changes)) return true;
        if (!engine.rollbackOwnerToken(previousToken)) {
            host.storageFaults().report(RECOVERY_MEMBER_STAGE, "Cannot flush the previous domain owner token", host.getLevel().getGameTime());
        }
        return false;
    }

    private boolean markAnonymous(Map<ECODriveBlockEntity, CompoundTag> changes) {
        List<ECODriveBlockEntity> attempted = new ArrayList<>();
        try {
            for (ECODriveBlockEntity drive : changes.keySet()) {
                attempted.add(drive);
                ECOInfiniteStorageMember.markAnonymous(drive.getCellStack());
            }
        } catch (IllegalStateException failure) {
            for (int index = attempted.size() - 1; index >= 0; index--) {
                ECODriveBlockEntity drive = attempted.get(index);
                ItemStack stack = drive.getCellStack();
                stack.setTag(changes.get(drive));
                try {
                    ECOStorageRepository.persistMetadata(stack, true);
                } catch (IllegalStateException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            host.storageFaults().report(RECOVERY_MEMBER_STAGE, failure.toString(), host.getLevel().getGameTime(), failure);
            return false;
        }
        for (ECODriveBlockEntity drive : attempted) {
            drive.setChanged();
            drive.markForUpdate();
        }
        host.storageFaults().recovered(RECOVERY_MEMBER_STAGE);
        return true;
    }

    private void releaseInactiveMemberBindings() {
        if (host.infiniteMemberIds().isEmpty()) return;
        Map<ECODriveBlockEntity, CompoundTag> changes = new LinkedHashMap<>();
        for (ECODriveBlockEntity drive : orderedDrives()) {
            if (!ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), host.infiniteDomainId())) continue;
            var cell = drive.loadedCellInventory();
            if (drive.hasLegacyRestoreReceipts() || !ECODriveBlockEntity.isEmptyInfiniteCandidate(cell)
                    || !canWriteMetadata(drive, cell)) return;
            changes.put(drive, snapshotTag(drive));
        }
        if (!markAnonymous(changes)) return;
        host.infiniteMemberIds().clear();
        host.notifyStorageConfigurationChanged();
    }

    private void finishRecovery(UUID domainId, UUID token, List<ECODriveBlockEntity> members, boolean restoring) {
        host.infiniteRestore().resetRestore();
        host.infiniteMemberIds().clear();
        for (ECODriveBlockEntity drive : members) {
            ECOInfiniteStorageMember.getIdentity(drive.getCellStack()).ifPresent(host.infiniteMemberIds()::add);
        }
        host.setInfiniteDomainId(domainId);
        host.setInfiniteBindingToken(token);
        targetInfiniteMode = true;
        host.setStorageHostMode(restoring ? ECOStorageHostMode.RESTORING_TO_NORMAL : ECOStorageHostMode.FORMED_INFINITE);
        if (restoring) host.requestInfiniteExit();
        else host.clearInfiniteExitRequest();
        getEngine();
        host.storageFaults().recovered("infinite domain binding");
        host.notifyStorageConfigurationChanged();
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
                        && hostAccess && ownsMountedDomain() && !host.isStorageServerStopping() && engine.canUseMountedStorage());
    }

    void hostModeChanged() {
        backendGeneration++;
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
                && ownsMountedDomain()
                && host.getMissingInfiniteMembers() == 0;
    }

    private boolean ownsMountedDomain() {
        return mountedEngine instanceof SavedDataInfiniteStorageEngine saved
                && host.infiniteBindingToken() != null && host.infiniteBindingToken().equals(saved.ownerToken());
    }

    boolean canInsertIntoDomain() {
        return hostAccess && ownsMountedDomain() && !host.isStorageServerStopping() && mountedEngine.canUseMountedStorage();
    }
}
