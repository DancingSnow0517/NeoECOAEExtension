package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.capability.ECOFeature;
import cn.dancingsnow.neoecoae.grid.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.grid.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.grid.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.grid.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.grid.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.grid.storage.infinite.persist.SavedDataInfiniteStorageEngine;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

final class ECOInfiniteStorageModeController {
    private static final int REQUIRED_DISKS = 12;
    private static final String CLAIM_FAULT = "domain claim";
    private static final String LEGACY_FAULT = "legacy domain";
    private final ECOStorageSystemBlockEntity host;
    private Set<ECODriveBlockEntity> occupied = Set.of();
    private Set<ECODriveBlockEntity> candidates = Set.of();
    private boolean dirty = true;
    private boolean updating;
    private boolean hostAccess;
    private long generation;
    private long retryLegacyAt;
    @Nullable private MinecraftServer server;
    @Nullable private UUID domain;
    @Nullable private SavedDataInfiniteStorageEngine engine;
    @Nullable private CompletableFuture<ECOInfiniteStorageEngine.CommitResult> creation;

    ECOInfiniteStorageModeController(ECOStorageSystemBlockEntity host) { this.host = host; }

    void membersChanged() {
        dirty = true;
        update();
    }

    void update() {
        if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || host.isRemoved() || host.isStorageServerStopping() || updating) return;
        updating = true;
        try {
            var cluster = host.getCluster();
            boolean formed = host.isFormed() && cluster != null && !cluster.isDestroyed();
            if (!host.getTier().supportsFeature(ECOFeature.INFINITE_STORAGE_HOST)) {
                publish(formed, false);
                return;
            }
            var claims = ECOInfiniteStorageDomains.hostClaims(level.getServer());
            if (level.getGameTime() % 100L == 0L) claims.retry();
            GlobalPos location = location(level);
            UUID associated = claims.domainAt(location);
            UUID saved = host.infiniteDomainId();
            if (associated != null && !associated.equals(saved)) {
                release();
                host.setInfiniteDomainId(associated);
                saved = associated;
            }
            if (saved != null && engine == null) openAssociated(level, claims, location, saved);

            List<ECODriveBlockEntity> drives = formed ? cluster.getDrives() : List.of();
            if (engine != null && (dirty || !engine.canUseMountedStorage() && level.getGameTime() >= retryLegacyAt)) {
                if (host.hasLegacyRoster()) {
                    ECOInfiniteStorageMember.recordRoster(engine, host.legacyMigrationSources(), host.hasMalformedLegacyRoster());
                }
                List<ECODriveBlockEntity> loaded = drives.stream().filter(ECODriveBlockEntity::isInstalledStorageAvailable)
                        .filter(drive -> drive.getStorageController() == host).toList();
                for (ECODriveBlockEntity drive : loaded) drive.upgradeLegacyCell();
                String reason = ECOInfiniteStorageMember.reconcile(level, domain, engine, loaded);
                if (reason != null) {
                    retryLegacyAt = level.getGameTime() + 20L;
                    host.storageFaults().report(LEGACY_FAULT, domain + ": " + reason, level.getGameTime());
                } else if (engine.canUseMountedStorage()) {
                    host.storageFaults().recovered(LEGACY_FAULT);
                    host.clearLegacyRoster();
                    for (ECODriveBlockEntity drive : loaded) drive.upgradeLegacyCell();
                }
                if (!engine.canUseMountedStorage() && level.getGameTime() % 100L == 0L) engine.checkpoint();
                dirty = true;
            }

            if (dirty) {
                var next = new HashSet<ECODriveBlockEntity>();
                if (formed) {
                    for (ECODriveBlockEntity drive : drives) {
                        if (drive.refreshInfiniteQualification(host)) next.add(drive);
                    }
                }
                candidates = next;
            }
            boolean eligible = formed && host.hasRequiredInfiniteComponents() && candidates.size() >= REQUIRED_DISKS;
            if (eligible && saved == null && claims.ready()) createDomain(level, claims, location);
            if (creation != null && creation.isDone() && !creation.getNow(null).successful()
                    && level.getGameTime() % 100L == 0L) creation = engine.checkpoint();
            if (engine != null && creation != null && creation.isDone() && creation.getNow(null).successful()) {
                UUID pendingToken = host.pendingInfiniteClaimToken();
                if (pendingToken != null) {
                    engine.settleClaim(pendingToken);
                    host.setInfiniteClaimTokens(pendingToken, null);
                    creation = engine.checkpoint();
                }
            }
            if (engine != null && claims.locationOf(domain) == null && creation != null && creation.isDone()
                    && creation.getNow(null).successful() && claims.ready()) {
                claims.bind(domain, location, false);
            }
            boolean active = eligible && engine != null && location.equals(claims.locationOf(domain)) && claims.readyFor(domain)
                    && host.pendingInfiniteClaimToken() == null
                    && (creation == null || creation.isDone() && creation.getNow(null).successful())
                    && engine.canUseMountedStorage();
            publish(formed, active);
            dirty = false;
        } catch (RuntimeException failure) {
            publish(false, false);
            dirty = true;
            host.storageFaults().report("domain", "Domain " + domain + ": " + failure, level.getGameTime(), failure);
        } finally {
            updating = false;
        }
    }


    private void openAssociated(ServerLevel level, ECOInfiniteStorageDomains.HostClaims claims, GlobalPos location, UUID id) {
        if (!claims.readable() || claims.locationOf(id) != null && !location.equals(claims.locationOf(id))) {
            reportClaim(level, id, "Association is unavailable or belongs to another position");
            return;
        }
        if (!ECOInfiniteStorageDomains.exists(level.getServer(), id)) {
            reportClaim(level, id, "Saved domain is missing; no replacement was created");
            return;
        }
        boolean alreadyBoundHere = location.equals(claims.locationOf(id));
        if (!(alreadyBoundHere ? claims.readyFor(id) : claims.ready())
                || ECOInfiniteStorageDomains.mountedHosts(level.getServer(), id) != 0) return;
        var loaded = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquire(level, id);
        if (!alreadyBoundHere) {
            UUID current = host.infiniteClaimToken();
            UUID pending = host.pendingInfiniteClaimToken();
            if (!loaded.acceptsClaim(current) && (pending == null || !loaded.acceptsClaim(pending))) {
                ECOInfiniteStorageDomains.release(level.getServer(), id);
                reportClaim(level, id, "Host does not hold a valid claim for this infinite domain");
                return;
            }
            hold(level, id, loaded);
            if (pending == null) {
                pending = UUID.randomUUID();
                host.setInfiniteClaimTokens(current, pending);
            }
            loaded.rotateClaim(current, pending);
            ECOInfiniteStorageMember.recordRoster(loaded, host.legacyMigrationSources(), host.hasMalformedLegacyRoster());
            creation = loaded.checkpoint();
        } else {
            hold(level, id, loaded);
            if (host.infiniteClaimToken() == null && host.pendingInfiniteClaimToken() == null) {
                UUID next = UUID.randomUUID();
                host.setInfiniteClaimTokens(null, next);
                loaded.rotateClaim(null, next);
                creation = loaded.checkpoint();
            } else if (host.pendingInfiniteClaimToken() != null) {
                loaded.rotateClaim(host.infiniteClaimToken(), host.pendingInfiniteClaimToken());
                creation = loaded.checkpoint();
            }
        }
    }

    private void createDomain(ServerLevel level, ECOInfiniteStorageDomains.HostClaims claims, GlobalPos location) {
        UUID id = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        var fresh = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquire(level, id);
        fresh.settleClaim(token);
        var initialSnapshot = fresh.checkpoint();
        if (!claims.bind(id, location, false)) {
            ECOInfiniteStorageDomains.release(level.getServer(), id);
            return;
        }
        hold(level, id, fresh);
        host.setInfiniteDomainId(id);
        host.setInfiniteClaimTokens(token, null);
        creation = initialSnapshot;
    }

    private void hold(ServerLevel level, UUID id, SavedDataInfiniteStorageEngine loaded) {
        server = level.getServer();
        domain = id;
        engine = loaded;
        host.storageFaults().recovered(CLAIM_FAULT);
        dirty = true;
    }

    private void reportClaim(ServerLevel level, UUID id, String reason) {
        host.storageFaults().report(CLAIM_FAULT, id + ": " + reason, level.getGameTime());
    }

    private static GlobalPos location(ECOStorageSystemBlockEntity host, ServerLevel level) {
        return GlobalPos.of(level.dimension(), host.getBlockPos());
    }

    private GlobalPos location(ServerLevel level) { return location(host, level); }

    private void publish(boolean formed, boolean active) {
        Set<ECODriveBlockEntity> next = active ? candidates : Set.of();
        var mode = active ? ECOStorageHostMode.FORMED_INFINITE
                : formed ? ECOStorageHostMode.FORMED_NORMAL : ECOStorageHostMode.UNFORMED;
        if (hostAccess == active && occupied.equals(next) && host.storageHostMode() == mode) return;
        invalidateViews();
        for (ECODriveBlockEntity drive : occupied) if (!next.contains(drive)) drive.setInfiniteOccupied(false);
        for (ECODriveBlockEntity drive : next) drive.setInfiniteOccupied(true);
        occupied = next;
        hostAccess = active;
        host.setStorageHostMode(mode);
        host.invalidateStorageStatistics();
        host.refreshDriveStorageProviders();
        host.markForUpdate();
    }

    void interfaceChanged() {
        invalidateViews();
        membersChanged();
    }

    private void invalidateViews() {
        generation++;
        host.storageInterfaceTransfer().invalidateInfiniteStorageView();
    }

    void topologyChanging() {
        publish(false, false);
        candidates = Set.of();
        dirty = true;
    }

    void release() {
        topologyChanging();
        invalidateViews();
        if (server != null && domain != null) ECOInfiniteStorageDomains.release(server, domain);
        server = null;
        domain = null;
        engine = null;
        creation = null;
    }

    @Nullable ECOInfiniteStorageEngine getEngine() { return engine; }

    MEStorage createStorageView(ECOInfiniteStorageEngine backend) {
        long issued = generation;
        return new ECOInfiniteStorage(backend, host.getBlockState().getBlock().getName(),
                () -> issued == generation && backend == engine && canAccess());
    }

    boolean canAccess() {
        return hostAccess && engine != null && host.isFormed() && !host.isRemoved() && !host.isStorageServerStopping()
                && host.hasRequiredInfiniteComponents() && engine.canUseMountedStorage();
    }

    boolean occupies(ECODriveBlockEntity drive) { return occupied.contains(drive); }

    int missingDisks() { return Math.max(0, REQUIRED_DISKS - candidates.size()); }

    @Nullable
    Component recoverDomain(UUID id) {
        String prefix = "command.neoecoae.storage_recover.";
        if (!(host.getLevel() instanceof ServerLevel level) || host.isStorageServerStopping() || !host.isFormed()
                || !host.getTier().supportsFeature(ECOFeature.INFINITE_STORAGE_HOST)) {
            return Component.translatable(prefix + "invalid_host");
        }
        var claims = ECOInfiniteStorageDomains.hostClaims(level.getServer());
        if (!claims.ready()) return Component.translatable(prefix + "unhealthy");
        if (!ECOInfiniteStorageDomains.exists(level.getServer(), id)) return Component.translatable(prefix + "not_found", id);
        GlobalPos location = location(level);
        UUID previous = claims.domainAt(location);
        if (previous == null) previous = host.infiniteDomainId();
        if (previous != null && !id.equals(previous)) {
            if (!ECOInfiniteStorageDomains.exists(level.getServer(), previous)) return Component.translatable(prefix + "occupied");
            var old = ECOInfiniteStorageDomains.acquire(level, previous);
            try {
                if (!old.canUseMountedStorage() || !old.isEmpty()) return Component.translatable(prefix + "occupied");
            } finally {
                ECOInfiniteStorageDomains.release(level.getServer(), previous);
            }
        }
        int current = ECOInfiniteStorageDomains.mountedHosts(level.getServer(), id);
        if (current > (id.equals(domain) ? 1 : 0)) return Component.translatable(prefix + "mounted");
        var recovered = ECOInfiniteStorageDomains.acquire(level, id);
        try {
            if (!recovered.canUseMountedStorage()) return Component.translatable(prefix + "unhealthy");
            if (!claims.bind(id, location, true)) return Component.translatable(prefix + "unhealthy");
            UUID nextToken = UUID.randomUUID();
            recovered.settleClaim(nextToken);
            var snapshot = recovered.checkpoint();
            release();
            host.setInfiniteDomainId(id);
            host.setInfiniteClaimTokens(nextToken, null);
            creation = snapshot;
        } finally {
            ECOInfiniteStorageDomains.release(level.getServer(), id);
        }
        membersChanged();
        return null;
    }
}
