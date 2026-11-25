package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellReference;
import cn.dancingsnow.neoecoae.storage.persistence.StorageCatalogSavedData;
import cn.dancingsnow.neoecoae.storage.persistence.StorageTransferCoordinator;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

final class ECOInfiniteStorageModeController {
    static final int MINIMUM_MIGRATION_SOURCES = 12;
    private final ECOStorageSystemBlockEntity host;
    private Set<ECODriveBlockEntity> occupied = Set.of();
    private Set<ECODriveBlockEntity> candidates = Set.of();
    private boolean dirty = true;
    private boolean updating;
    private boolean hostAccess;
    private long generation;
    private long nextCheck;
    private MinecraftServer server;
    private UUID domain;
    private SavedDataInfiniteStorageEngine engine;
    private StorageCatalogSavedData catalog;

    ECOInfiniteStorageModeController(ECOStorageSystemBlockEntity host) { this.host = host; }

    void markMembersDirty() { dirty = true; }
    void membersChanged() { markMembersDirty(); update(); }
    void refreshHostAccess() { membersChanged(); }
    void hostModeChanged() { invalidateViews(); }
    int migrationProgressPercent() { return 0; }
    @Nullable ECOInfiniteStorageEngine.MigrationBlock migrationBlock() { return null; }
    int missingDisks() { return Math.max(0, MINIMUM_MIGRATION_SOURCES - candidates.size()); }
    boolean occupies(ECODriveBlockEntity drive) { return occupied.contains(drive); }
    @Nullable ECOInfiniteStorageEngine getEngine() { return engine; }

    void update() {
        if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || host.isRemoved() || host.isStorageServerStopping() || updating) return;
        updating = true;
        try {
            var cluster = host.getCluster();
            boolean formed = host.isFormed() && cluster != null && !cluster.isDestroyed();
            if (host.getTier() != ECOTier.L9) {
                publish(formed, false);
                return;
            }
            if (catalog == null) catalog = StorageCatalogSavedData.open(ECOStorageRepository.session(level.getServer()));
            if (!catalog.ready() && level.getGameTime() % 100L == 0L) catalog.flush();
            GlobalPos location = GlobalPos.of(level.dimension(), host.getBlockPos());
            UUID associated = catalog.domainAt(location);
            UUID saved = host.infiniteDomainId();
            if (associated != null && !associated.equals(saved)) {
                if (!persistLegacyRoster()) { publish(formed, false); return; }
                release();
                host.clearLegacyRoster();
                host.setInfiniteDomainId(associated);
                saved = associated;
            }
            if (saved != null && !saved.equals(domain)) {
                if (engine != null) release();
                openExisting(level, location, saved);
            }
            if (domain != null && !location.equals(catalog.locationOf(domain))) {
                release();
                publish(formed, false);
                return;
            }
            boolean recheck = dirty || level.getGameTime() >= nextCheck;
            if (recheck) {
                nextCheck = level.getGameTime() + 20L;
                var drives = formed ? cluster.getDrives().stream()
                    .filter(drive -> drive.isInstalledStorageAvailable() && drive.getStorageController() == host).toList()
                    : List.<ECODriveBlockEntity>of();
                if (engine != null) {
                    if (!engine.noteLegacyRoster(host.infiniteMigrationSourceIds(), host.hasMalformedLegacyRoster())) {
                        publish(formed, false);
                        return;
                    }
                    verifyLegacy(level, drives);
                    if (engine.canUseMountedStorage()) {
                        host.clearLegacyRoster();
                        for (var drive : drives) clearSettledCell(drive);
                    }
                }
                var next = new LinkedHashSet<ECODriveBlockEntity>();
                var diskIds = new HashSet<UUID>();
                for (var drive : drives) {
                    if (!drive.refreshInfiniteQualification()) continue;
                    UUID id = ECOCellReference.id(drive.getCellStack());
                    if (id == null || diskIds.add(id)) next.add(drive);
                }
                candidates = next;
                dirty = false;
            }
            boolean eligible = formed && host.hasRequiredInfiniteComponents() && !host.hasLegacyCompatibilityFailure()
                    && candidates.size() >= MINIMUM_MIGRATION_SOURCES;
            if (eligible && saved == null && catalog.ready()) {
                UUID id = UUID.randomUUID();
                var fresh = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquire(level, id);
                host.setInfiniteDomainId(id);
                hold(level, id, fresh);
                if (!fresh.flush() || !catalog.bindHost(id, location, false))
                    host.storageFaults().report("domain claim", id + ": cannot persist domain association", level.getGameTime());
            }
            if (engine != null && !engine.canUseMountedStorage() && level.getGameTime() % 100L == 0L) engine.flush();
            boolean active = eligible && engine != null && catalog.ready() && location.equals(catalog.locationOf(domain))
                    && engine.canUseMountedStorage();
            publish(formed, active);
        } catch (RuntimeException failure) {
            publish(false, false);
            dirty = true;
            host.storageFaults().report("domain", "Domain " + domain + ": " + failure, level.getGameTime(), failure);
        } finally {
            updating = false;
        }
    }

    private void openExisting(ServerLevel level, GlobalPos location, UUID id) {
        if (!catalog.ready() || !ECOInfiniteStorageDomains.exists(level.getServer(), id)
                || catalog.locationOf(id) != null && !location.equals(catalog.locationOf(id))
                || ECOInfiniteStorageDomains.mountedHosts(level.getServer(), id) != 0) {
            host.storageFaults().report("domain claim", id + ": unavailable or claimed elsewhere", level.getGameTime());
            return;
        }
        var loaded = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquireExisting(level, id);
        boolean accepted = false;
        try {
            if (catalog.locationOf(id) == null) {
                if (!Objects.equals(loaded.ownerToken(), host.infiniteBindingToken())) return;
                if (!loaded.noteLegacyRoster(host.infiniteMigrationSourceIds(), host.hasMalformedLegacyRoster())) return;
                if (!loaded.flush() || !catalog.bindHost(id, location, false)) return;
            }
            hold(level, id, loaded);
            accepted = true;
        } finally {
            if (!accepted) ECOInfiniteStorageDomains.release(level.getServer(), id);
        }
    }

    private void hold(ServerLevel level, UUID id, SavedDataInfiniteStorageEngine loaded) {
        server = level.getServer(); domain = id; engine = loaded; dirty = true;
        host.storageFaults().recovered("domain claim");
    }

    private void verifyLegacy(ServerLevel level, List<ECODriveBlockEntity> drives) {
        Set<UUID> sources = engine.legacyMigrationSources();
        Set<AEKey> restores = engine.legacyRestoreKeys();
        for (var drive : drives) {
            var stack = drive.getCellStack();
            UUID migration = ECOInfiniteStorageMember.getMigrationId(stack);
            if (migration != null && ECOInfiniteStorageMember.getMigrationDomainId(stack).map(domain::equals).orElse(false)) {
                engine.noteLegacyRoster(Set.of(migration), false);
            }
            if (drive.hasLegacyRestoreReceipts() || ECOInfiniteStorageMember.hasMalformedEvidence(stack))
                engine.noteLegacyRoster(Set.of(), true);
        }
        sources = engine.legacyMigrationSources();
        if (sources.isEmpty() && restores.isEmpty() && !engine.legacyRosterMalformed()) return;
        try {
            if (engine.legacyRosterMalformed() || engine.hasPendingTransfers() || !engine.canExitOrRestore())
                throw new IllegalStateException("Unreadable or unfinished legacy domain evidence");
            Map<UUID, ECODriveBlockEntity> byIdentity = new HashMap<>();
            Map<UUID, ECODriveBlockEntity> byMigration = new HashMap<>();
            for (var drive : drives) {
                var stack = drive.getCellStack();
                if (!ECOInfiniteStorageMember.isMemberOf(stack, domain)
                    && !ECOInfiniteStorageMember.getMigrationDomainId(stack).map(domain::equals).orElse(false)) continue;
                UUID identity = ECOInfiniteStorageMember.getIdentity(stack).orElse(null);
                UUID migration = ECOInfiniteStorageMember.getMigrationId(stack);
                if (identity != null && byIdentity.putIfAbsent(identity, drive) != null
                    || migration != null && byMigration.putIfAbsent(migration, drive) != null)
                    throw new IllegalStateException("Duplicate legacy disk evidence");
            }
            Map<AEKey, BigInteger> overlaps = new HashMap<>();
            Set<UUID> settledMembers = new HashSet<>();
            for (UUID id : sources) {
                var drive = byMigration.getOrDefault(id, byIdentity.get(id));
                if (drive == null) throw new IllegalStateException("Missing original migration source " + id);
                var cell = readableCell(drive);
                if (cell instanceof ECOStorageCell ordinary && ordinary.storageSession() != null) {
                    var journal = StorageTransferCoordinator.journal(ordinary.storageSession(), ordinary.storageData().id(), false);
                    if (journal != null && id.equals(journal.transaction()) && domain.equals(journal.domain())) {
                        if (!journal.readable() || !journal.completed() || !engine.hasMigrationReceipt(id)
                            || !ordinary.storageData().amounts().isEmpty()) throw new IllegalStateException("Unsettled migration journal " + id);
                        continue;
                    }
                }
                if (engine.hasMigrationReceipt(id)) throw new IllegalStateException("Missing whole-cell journal " + id);
                if (!ECOInfiniteStorageMember.isMigrating(drive.getCellStack()))
                    throw new IllegalStateException("Original migration receipt unavailable " + id);
                Set<AEKey> observed = new HashSet<>();
                var entries = cell.migrationEntries();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    AEKey key = entry.getKey();
                    long amount = cell.getMigrationAmountBigInteger(key).longValueExact();
                    if (amount <= 0 || !observed.add(key)) throw new IllegalStateException("Invalid legacy source quantity");
                    String encoded = ECOInfiniteStorageTransfer.encodedKey(key);
                    boolean matched = engine.hasMigrationReceipt(legacyTransactionId(domain, "to-domain", drive.getBlockPos().asLong(), encoded, amount));
                    if (!matched && engine.hasMigrationReceipt(ECOInfiniteStorageTransfer.sealedReceipt(id, encoded)))
                        throw new IllegalStateException("Missing original quantity receipt " + id);
                    if (matched) overlaps.merge(key, BigInteger.valueOf(amount), BigInteger::add);
                }
                for (AEKey key : engine.legacyLedgerKeys()) {
                    if (!observed.contains(key) && engine.hasMigrationReceipt(ECOInfiniteStorageTransfer.sealedReceipt(id, ECOInfiniteStorageTransfer.encodedKey(key))))
                        throw new IllegalStateException("Missing credited resource in original source " + id);
                }
            }
            for (AEKey key : restores) {
                UUID transaction = engine.restoreTransaction(key);
                Set<UUID> targets = engine.restoreTargetIds(key);
                var plan = engine.restorePlan(key);
                if (targets.isEmpty() || plan.isEmpty() || !byIdentity.keySet().containsAll(targets))
                    throw new IllegalStateException("Missing original restore target or proof " + transaction);
                for (UUID identity : targets) {
                    settledMembers.add(identity);
                    var drive = byIdentity.get(identity);
                    var goal = plan.get(identity);
                    BigInteger observed = readableCell(drive).getMigrationAmountBigInteger(key);
                    BigInteger receipt = drive.getRestoreReceiptExact(receiptId(transaction, identity));
                    if (goal == null) {
                        if (receipt.signum() != 0) throw new IllegalStateException("Unexpected restore receipt " + transaction);
                        continue;
                    }
                    BigInteger prior = goal.transferredExact().subtract(goal.afterExact().subtract(goal.beforeExact()));
                    if (observed.compareTo(goal.beforeExact()) < 0 || observed.compareTo(goal.afterExact()) > 0
                        || !receipt.equals(prior) && !(receipt.equals(goal.transferredExact()) && observed.equals(goal.afterExact())))
                        throw new IllegalStateException("Conflicting restore evidence " + transaction);
                    overlaps.merge(key, prior.add(observed.subtract(goal.beforeExact())), BigInteger::add);
                }
            }
            if (!engine.settleLegacy(sources, restores, overlaps, settledMembers)) throw new IllegalStateException("Legacy overlaps cannot be settled durably");
            host.storageFaults().recovered("legacy domain");
        } catch (RuntimeException failure) {
            host.storageFaults().report("legacy domain", domain + ": " + failure, level.getGameTime());
        }
    }

    private static IECOStorageMigrationCell readableCell(ECODriveBlockEntity drive) {
        if (!(drive.getCellInventory() instanceof IECOStorageMigrationCell cell) || !cell.isInfiniteStorageEligible()
            || drive.hasLegacyRestoreReceipts()) throw new IllegalStateException("Unreadable legacy disk at " + drive.getBlockPos());
        return cell;
    }

    private void clearSettledCell(ECODriveBlockEntity drive) {
        var stack = drive.getCellStack();
        if (!ECOInfiniteStorageMember.isSealed(stack) || drive.hasLegacyRestoreReceipts()
                || ECOInfiniteStorageMember.hasMalformedEvidence(stack)) return;
        UUID migration = ECOInfiniteStorageMember.getMigrationId(stack);
        if (migration != null && (!engine.legacySourceSettled(migration)
                || !ECOInfiniteStorageMember.getMigrationDomainId(stack).map(domain::equals).orElse(false))) return;
        if (migration == null && !ECOInfiniteStorageMember.isAnonymous(stack) && !ECOInfiniteStorageMember.isMemberOf(stack, domain)) return;
        if (migration == null && !ECODriveBlockEntity.isEmptyInfiniteCandidate(drive.getCellInventory())
                && !ECOInfiniteStorageMember.getIdentity(stack).map(engine::legacyMemberSettled).orElse(false)
                && !NEItemData.read(stack).contains("neoecoae_restore_receipts")) return;
        drive.clearSettledInfiniteMarkers();
    }

    private void publish(boolean formed, boolean active) {
        var next = active ? Set.copyOf(candidates) : Set.<ECODriveBlockEntity>of();
        var mode = active ? ECOStorageHostMode.FORMED_INFINITE : formed ? ECOStorageHostMode.FORMED_NORMAL : ECOStorageHostMode.UNFORMED;
        if (hostAccess == active && occupied.equals(next) && host.storageHostMode() == mode) return;
        hostAccess = false;
        invalidateViews();
        for (var drive : occupied) if (!next.contains(drive)) drive.setInfiniteOccupied(false);
        for (var drive : next) drive.setInfiniteOccupied(true);
        occupied = next;
        hostAccess = active;
        host.setStorageHostMode(mode);
        host.invalidateStorageStatistics();
        host.refreshDriveStorageProviders();
        host.setChanged(); host.markForUpdate();
    }

    private void invalidateViews() { generation++; host.storageInterfaceTransfer().invalidateInfiniteStorageView(); }
    void topologyChanging() {
        if (host.getLevel() instanceof ServerLevel && !host.isStorageServerStopping() && !host.isRemoved()) publish(false, false);
        else { hostAccess = false; invalidateViews(); }
        candidates = Set.of(); dirty = true;
    }
    void release() {
        if (!host.isStorageServerStopping()) persistLegacyRoster();
        topologyChanging();
        releaseEngine();
    }
    void releaseForUnload() {
        if (!host.isStorageServerStopping()) persistLegacyRoster();
        hostAccess = false;
        invalidateViews();
        for (var drive : occupied) drive.clearInfiniteOccupancy();
        occupied = Set.of(); candidates = Set.of(); dirty = true;
        releaseEngine();
    }
    private void releaseEngine() {
        if (server != null && domain != null) ECOInfiniteStorageDomains.release(server, domain);
        server = null; domain = null; engine = null;
    }
    private boolean persistLegacyRoster() {
        if (host.infiniteMigrationSourceIds().isEmpty() && !host.hasMalformedLegacyRoster()) return true;
        if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread() || host.isStorageServerStopping()) return false;
        UUID id = host.infiniteDomainId();
        if (id == null || !ECOInfiniteStorageDomains.exists(level.getServer(), id)) return false;
        if (engine != null && id.equals(domain)) return engine.noteLegacyRoster(host.infiniteMigrationSourceIds(), host.hasMalformedLegacyRoster());
        var saved = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquireExisting(level, id);
        try { return saved.noteLegacyRoster(host.infiniteMigrationSourceIds(), host.hasMalformedLegacyRoster()) && saved.flush(); }
        finally { ECOInfiniteStorageDomains.release(level.getServer(), id); }
    }
    MEStorage createStorageView(ECOInfiniteStorageEngine backend) {
        long issued = generation;
        return new ECOInfiniteStorage(backend, host.getBlockState().getBlock().getName(),
            () -> issued == generation && backend == engine && canInsertIntoDomain());
    }
    boolean canInsertIntoDomain() {
        return hostAccess && engine != null && host.isFormed() && !host.isRemoved() && !host.isStorageServerStopping()
                && host.hasRequiredInfiniteComponents() && catalog != null && catalog.ready() && engine.canUseMountedStorage();
    }

    @Nullable Component recoverDomain(UUID id) {
        String prefix = "command.neoecoae.storage_recover.";
        if (!(host.getLevel() instanceof ServerLevel level) || !host.isFormed() || host.isStorageServerStopping()
            || host.getTier() != ECOTier.L9) return Component.translatable(prefix + "invalid_host");
        if (catalog == null) catalog = StorageCatalogSavedData.open(ECOStorageRepository.session(level.getServer()));
        if (!catalog.ready() || host.hasMalformedLegacyRoster() || !host.infiniteMigrationSourceIds().isEmpty())
            return Component.translatable(prefix + "unhealthy");
        if (!ECOInfiniteStorageDomains.exists(level.getServer(), id)) return Component.translatable(prefix + "not_found", id.toString());
        GlobalPos location = GlobalPos.of(level.dimension(), host.getBlockPos());
        UUID previous = catalog.domainAt(location);
        if (previous == null) previous = host.infiniteDomainId();
        if (previous != null && !previous.equals(id)) {
            if (!ECOInfiniteStorageDomains.exists(level.getServer(), previous)) return Component.translatable(prefix + "previous_not_empty", previous.toString());
            var old = ECOInfiniteStorageDomains.acquireExisting(level, previous);
            try { if (!old.canUseMountedStorage() || !old.isEmpty()) return Component.translatable(prefix + "occupied"); }
            finally { ECOInfiniteStorageDomains.release(level.getServer(), previous); }
        }
        if (ECOInfiniteStorageDomains.mountedHosts(level.getServer(), id) > (id.equals(domain) ? 1 : 0))
            return Component.translatable(prefix + "mounted");
        var loaded = ECOInfiniteStorageDomains.acquireExisting(level, id);
        try {
            if (!loaded.canUseMountedStorage() || !catalog.bindHost(id, location, true)) return Component.translatable(prefix + "unhealthy");
            release(); host.setInfiniteDomainId(id);
        } finally { ECOInfiniteStorageDomains.release(level.getServer(), id); }
        membersChanged(); return null;
    }
    @Nullable Component forceRecoverDomain(UUID id, Consumer<Component> success) {
        Component refusal = recoverDomain(id);
        if (refusal == null) success.accept(Component.translatable("command.neoecoae.storage_recover.success", id.toString()));
        return refusal;
    }
    static UUID receiptId(UUID transaction, UUID identity) {
        return UUID.nameUUIDFromBytes((transaction + ":" + identity).getBytes(StandardCharsets.UTF_8));
    }
    static UUID legacyTransactionId(UUID domain, String direction, long position, String key, long amount) {
        return UUID.nameUUIDFromBytes((domain + ":" + direction + ":" + position + ":" + key + ":" + amount).getBytes(StandardCharsets.UTF_8));
    }
}
