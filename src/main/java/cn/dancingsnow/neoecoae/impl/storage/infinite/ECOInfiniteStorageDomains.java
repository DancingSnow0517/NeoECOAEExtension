package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the one world-global SavedData engine for each infinite-storage domain. */
public final class ECOInfiniteStorageDomains {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInfiniteStorageDomains.class);
    private static final String SAVED_DATA_DIRECTORY = "neoecoae_infinite";
    private static final String ARCHIVE_DIRECTORY = "neoecoae_storage_v1_archive";
    private static final String MIGRATION_DIRECTORY = "neoecoae_infinite_migration";
    private static final Map<String, DomainEntry> ENTRIES = new HashMap<>();

    private ECOInfiniteStorageDomains() {
    }

    /** Opens an existing V2 domain or starts an offline V1 migration. This method never creates an empty domain. */
    public static synchronized ECOInfiniteStorageEngine get(ServerLevel level, UUID domainId) {
        return openExisting(level, domainId);
    }

    public static synchronized ECOInfiniteStorageEngine openExisting(ServerLevel level, UUID domainId) {
        Path worldRoot = worldRoot(level);
        String key = keyFor(worldRoot, domainId);
        DomainEntry existing = ENTRIES.get(key);
        if (existing != null) {
            existing.advanceMigration(false);
            return existing;
        }
        DomainEntry created = new DomainEntry(level, domainId, false);
        ENTRIES.put(key, created);
        created.advanceMigration(false);
        return created;
    }

    /** Creates and durably verifies a brand-new empty domain. */
    public static synchronized ECOInfiniteStorageEngine create(ServerLevel level, UUID domainId) {
        Path worldRoot = worldRoot(level);
        String key = keyFor(worldRoot, domainId);
        DomainEntry existing = ENTRIES.get(key);
        if (existing != null) {
            return existing;
        }
        DomainEntry created = new DomainEntry(level, domainId, true);
        ENTRIES.put(key, created);
        return created;
    }

    public static boolean exists(ServerLevel level, UUID domainId) {
        Path root = worldRoot(level);
        try {
            return Files.exists(savedDataFile(root, domainId), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(archiveRoot(root).resolve(domainDirectory(domainId)), LinkOption.NOFOLLOW_LINKS)
                || !findLegacyDomainPaths(root, domainId).isEmpty();
        } catch (RuntimeException e) {
            LOGGER.error("Unable to inspect existing infinite-storage domain {}; treating it as present", domainId, e);
            return true;
        }
    }

    /** Returns every storage UUID discoverable from the current world's persisted layouts. */
    public static synchronized Collection<UUID> findExistingDomainIds(ServerLevel level) {
        return discoverDomainIds(worldRoot(level));
    }

    static Collection<UUID> discoverDomainIds(Path worldRoot) {
        Set<UUID> result = new TreeSet<>();
        collectV2DomainIds(worldRoot.resolve("data").resolve(SAVED_DATA_DIRECTORY), result);
        collectDomainDirectoryIds(archiveRoot(worldRoot), result);
        collectLegacyDomainIds(worldRoot.resolve("neoecoae_storage"), result);
        collectLegacyDomainIds(worldRoot.resolve("data").resolve("neoecoae_storage"), result);
        return List.copyOf(result);
    }

    private static void collectV2DomainIds(Path root, Set<UUID> result) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.list(root)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("domain_") && name.endsWith(".dat"))
                .forEach(name -> parseDomainId(name.substring("domain_".length(), name.length() - ".dat".length()))
                    .ifPresent(result::add));
        } catch (IOException e) {
            LOGGER.warn("Unable to discover V2 infinite-storage domains in {}", root, e);
        }
    }

    private static void collectDomainDirectoryIds(Path root, Set<UUID> result) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.list(root)) {
            paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("domain_"))
                .forEach(name -> parseDomainId(name.substring("domain_".length())).ifPresent(result::add));
        } catch (IOException e) {
            LOGGER.warn("Unable to discover archived infinite-storage domains in {}", root, e);
        }
    }

    private static void collectLegacyDomainIds(Path root, Set<UUID> result) {
        collectDomainDirectoryIds(root, result);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.list(root)) {
            paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> path.getFileName().toString().startsWith("dim_"))
                .forEach(path -> collectDomainDirectoryIds(path, result));
        } catch (IOException e) {
            LOGGER.warn("Unable to discover legacy infinite-storage domains in {}", root, e);
        }
    }

    private static Optional<UUID> parseDomainId(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static synchronized void close(ServerLevel level, UUID domainId) {
        // SavedData is world-owned. A controller chunk unloading must not stall the server or close a domain that
        // another controller still references; normal autosaves and closeAll() own persistence.
    }

    /** Runs a low-frequency read-back probe for an already-open domain without creating one. */
    public static synchronized void pollPersistence(ServerLevel level, UUID domainId, long gameTime) {
        DomainEntry entry = ENTRIES.get(keyFor(worldRoot(level), domainId));
        if (entry != null) {
            entry.pollPersistence(gameTime);
        }
    }

    /**
     * Explicitly replays an archived V1 domain after an administrator has confirmed the recovery.
     * Startup discovery intentionally refuses this path so a missing V2 file cannot silently roll back.
     */
    public static synchronized ECOInfiniteStorageEngine migrateArchivedV1(ServerLevel level, UUID domainId) {
        Path worldRoot = worldRoot(level);
        String key = keyFor(worldRoot, domainId);
        DomainEntry entry = ENTRIES.get(key);
        if (entry == null) {
            entry = new DomainEntry(level, domainId, false);
            ENTRIES.put(key, entry);
        }
        entry.startExplicitArchiveMigration();
        entry.advanceMigration(true);
        return entry;
    }

    public static synchronized void closeAll() {
        try {
            for (DomainEntry entry : new ArrayList<>(ENTRIES.values())) {
                try {
                    entry.closeAndFlush();
                } catch (RuntimeException e) {
                    LOGGER.error("Unable to close infinite-storage domain {}", entry.domainId, e);
                }
            }
        } finally {
            ENTRIES.clear();
        }
    }

    private static String keyFor(Path worldRoot, UUID domainId) {
        return worldRoot + ":" + domainId;
    }

    private static Path worldRoot(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    private static String domainDirectory(UUID domainId) {
        return "domain_" + domainId;
    }

    private static String savedDataName(UUID domainId) {
        return SAVED_DATA_DIRECTORY + "/" + domainDirectory(domainId);
    }

    private static Path savedDataFile(Path worldRoot, UUID domainId) {
        return worldRoot.resolve("data").resolve(SAVED_DATA_DIRECTORY).resolve(domainDirectory(domainId) + ".dat");
    }

    private static Path archiveRoot(Path worldRoot) {
        return worldRoot.resolve(ARCHIVE_DIRECTORY);
    }

    private static Path migrationRoot(Path worldRoot) {
        return worldRoot.resolve("data").resolve(MIGRATION_DIRECTORY);
    }

    private static List<Path> findLegacyDomainPaths(Path worldRoot, UUID domainId) {
        Set<Path> matches = new LinkedHashSet<>();
        matches.addAll(findDomainsInRoot(worldRoot.resolve("neoecoae_storage"), domainId));
        matches.addAll(findDomainsInRoot(worldRoot.resolve("data").resolve("neoecoae_storage"), domainId));
        return List.copyOf(matches);
    }

    private static List<Path> findDomainsInRoot(Path storageRoot, UUID domainId) {
        if (Files.exists(storageRoot, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(storageRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("V1 storage root is not a normal directory: " + storageRoot);
        }
        if (!Files.isDirectory(storageRoot, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        String directory = domainDirectory(domainId);
        List<Path> matches = new ArrayList<>();
        Path direct = storageRoot.resolve(directory);
        if (Files.exists(direct, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(direct, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("V1 domain path is not a normal directory: " + direct);
        }
        if (Files.isDirectory(direct, LinkOption.NOFOLLOW_LINKS)) {
            matches.add(direct.toAbsolutePath().normalize());
        }
        try (var children = Files.list(storageRoot)) {
            for (Path dimensionRoot : children
                .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> path.getFileName().toString().startsWith("dim_"))
                .toList()) {
                Path candidate = dimensionRoot.resolve(directory);
                if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("V1 domain path is not a normal directory: " + candidate);
                }
                if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    matches.add(candidate.toAbsolutePath().normalize());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect V1 infinite-storage domains", e);
        }
        return matches;
    }

    private static final class DomainEntry implements ECOInfiniteStorageEngine {
        private final HolderLookup.Provider registries;
        private final UUID domainId;
        private final Path worldRoot;
        private final Path dataFile;
        private final Path archiveDomain;
        private final Path migrationRoot;
        private final String savedDataName;
        private final DimensionDataStorage dataStorage;

        @Nullable private SavedDataInfiniteStorageEngine delegate;
        @Nullable private CompletableFuture<LegacyV1Reader.Snapshot> migrationFuture;
        @Nullable private Path migrationSource;
        private ECOInfiniteDomainState offlineState = ECOInfiniteDomainState.LOADING;
        @Nullable private String failureReason;
        private boolean loadJustCompleted;

        private DomainEntry(ServerLevel level, UUID domainId, boolean createNew) {
            this.registries = level.registryAccess();
            this.domainId = domainId;
            this.worldRoot = worldRoot(level);
            this.dataFile = savedDataFile(worldRoot, domainId);
            this.archiveDomain = archiveRoot(worldRoot).resolve(domainDirectory(domainId));
            this.migrationRoot = migrationRoot(worldRoot);
            this.savedDataName = savedDataName(domainId);
            this.dataStorage = level.getServer().overworld().getDataStorage();
            initialize(createNew);
        }

        private synchronized void initialize(boolean createNew) {
            try {
                boolean hasV2Path = Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS);
                boolean hasV2 = Files.isRegularFile(dataFile, LinkOption.NOFOLLOW_LINKS);
                List<Path> legacySources = findLegacyDomainPaths(worldRoot, domainId);
                boolean hasArchivePath = Files.exists(archiveDomain, LinkOption.NOFOLLOW_LINKS);
                boolean hasArchive = Files.isDirectory(archiveDomain, LinkOption.NOFOLLOW_LINKS);

                if (hasV2Path && !hasV2) {
                    quarantine("Infinite-storage SavedData path is not a normal file", null);
                    return;
                }
                if (hasArchivePath && !hasArchive) {
                    quarantine("V1 archive path is not a normal directory", null);
                    return;
                }

                if (createNew) {
                    if (hasV2 || hasArchive || !legacySources.isEmpty()) {
                        quarantine("Refusing to create an empty domain over existing storage data", null);
                        return;
                    }
                    createEmptyDomain();
                    return;
                }

                if (hasV2) {
                    openV2Domain();
                    if (delegate == null) {
                        return;
                    }
                    finishInterruptedArchive(legacySources, hasArchive);
                    return;
                }

                if (legacySources.size() > 1 || (!legacySources.isEmpty() && hasArchive)) {
                    quarantine("Multiple V1 authorities exist for this domain", null);
                    return;
                }
                if (!legacySources.isEmpty()) {
                    startMigration(legacySources.getFirst());
                    return;
                }
                if (hasArchive) {
                    // An archive is a historical recovery copy, never an independent authority. Re-importing it
                    // after a V2 file disappears would silently roll the domain back to the migration snapshot.
                    quarantine("V2 SavedData is missing while a V1 archive exists; refusing stale archive re-import", null);
                    return;
                }
                quarantine("Infinite-storage data is missing", null);
            } catch (Exception e) {
                quarantine("Unable to discover infinite-storage data", e);
            }
        }

        private void createEmptyDomain() throws IOException {
            Files.createDirectories(dataFile.getParent());
            SavedDataInfiniteStorageEngine engine = SavedDataInfiniteStorageEngine.createNew(
                registries,
                domainId,
                dataStorage,
                dataFile
            );
            dataStorage.set(savedDataName, engine);
            engine.setDirty();
            engine.flushAndAwait();
            delegate = engine;
            if (engine.getState() == ECOInfiniteDomainState.READY) {
                offlineState = ECOInfiniteDomainState.READY;
            } else {
                quarantine(engine.getFailureReason().orElse("Unable to create the SavedData domain"), null);
            }
        }

        private void openV2Domain() throws IOException {
            Files.createDirectories(dataFile.getParent());
            SavedData.Factory<SavedDataInfiniteStorageEngine> factory = SavedDataInfiniteStorageEngine.factory(
                registries,
                domainId,
                dataStorage,
                dataFile
            );
            SavedDataInfiniteStorageEngine engine = dataStorage.get(factory, savedDataName);
            if (engine == null) {
                quarantine("Existing infinite-storage SavedData failed strict loading", null);
                return;
            }
            delegate = engine;
            offlineState = ECOInfiniteDomainState.READY;
        }

        private void finishInterruptedArchive(List<Path> legacySources, boolean hasArchive) throws IOException {
            if (legacySources.isEmpty()) {
                if (hasArchive && delegate != null && delegate.legacyFingerprint() == null) {
                    quarantine("A V1 archive conflicts with a non-migrated V2 domain", null);
                }
                return;
            }
            if (legacySources.size() != 1 || hasArchive || delegate == null) {
                quarantine("Both V1 and V2 writable authorities exist for this domain", null);
                return;
            }
            String expectedFingerprint = delegate.legacyFingerprint();
            if (expectedFingerprint == null) {
                quarantine("V2 does not prove that it was imported from the remaining V1 source", null);
                return;
            }
            LegacyV1Reader.archive(legacySources.getFirst(), archiveDomain, expectedFingerprint);
            tryDiscardWorkingCopy();
            LOGGER.info("Completed interrupted V1 archive cutover for infinite-storage domain {}", domainId);
        }

        private void startMigration(Path source) {
            migrationSource = source.toAbsolutePath().normalize();
            offlineState = ECOInfiniteDomainState.MIGRATING_V1;
            failureReason = null;
            migrationFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return LegacyV1Reader.read(registries, domainId, migrationSource, migrationRoot);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, Util.ioPool());
        }

        private synchronized void startExplicitArchiveMigration() {
            if (Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS)) {
                quarantine("Refusing archive migration while a V2 SavedData file exists", null);
                return;
            }
            if (!Files.isDirectory(archiveDomain, LinkOption.NOFOLLOW_LINKS)) {
                quarantine("Requested V1 archive migration but the archive is missing", null);
                return;
            }
            if (migrationFuture != null) {
                quarantine("A V1 migration is already in progress", null);
                return;
            }
            delegate = null;
            startMigration(archiveDomain);
        }

        private synchronized boolean advanceMigration(boolean wait) {
            CompletableFuture<LegacyV1Reader.Snapshot> future = migrationFuture;
            if (future == null || (!wait && !future.isDone())) {
                return false;
            }
            try {
                LegacyV1Reader.Snapshot snapshot = wait ? future.get() : future.getNow(null);
                if (snapshot == null || migrationSource == null) {
                    throw new IllegalStateException("V1 migration completed without a snapshot");
                }
                if (Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("V2 SavedData appeared while V1 migration was running");
                }
                LegacyV1Reader.verifySource(migrationSource, snapshot.sourceFingerprint());
                Files.createDirectories(dataFile.getParent());
                SavedDataInfiniteStorageEngine engine = SavedDataInfiniteStorageEngine.createNew(
                    registries,
                    domainId,
                    dataStorage,
                    dataFile
                );
                dataStorage.set(savedDataName, engine);
                engine.importLegacy(
                    snapshot.amounts(),
                    snapshot.orphanedEntries(),
                    snapshot.receipts(),
                    snapshot.sourceFingerprint(),
                    snapshot.revision()
                );
                engine.flushAndAwait();
                if (engine.getState() != ECOInfiniteDomainState.READY) {
                    throw new IOException(engine.getFailureReason().orElse("V2 read-back verification failed"));
                }
                LegacyV1Reader.verifySource(migrationSource, snapshot.sourceFingerprint());
                LegacyV1Reader.archive(migrationSource, archiveDomain, snapshot.sourceFingerprint());
                tryDiscardWorkingCopy();

                delegate = engine;
                migrationFuture = null;
                migrationSource = null;
                offlineState = ECOInfiniteDomainState.READY;
                loadJustCompleted = true;
                LOGGER.info(
                    "Migrated V1 infinite-storage domain {} to SavedData with {} key types",
                    domainId,
                    snapshot.amounts().size()
                );
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                quarantine("V1 migration was interrupted", e);
            } catch (CancellationException | ExecutionException | CompletionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                quarantine("V1 migration failed and the source remains intact", cause);
            } catch (Exception e) {
                quarantine("V1 migration failed and the source remains intact", e);
            }
            migrationFuture = null;
            return false;
        }

        private synchronized void pollPersistence(long gameTime) {
            if (delegate != null) {
                delegate.pollPersistence(gameTime);
            }
        }

        private void tryDiscardWorkingCopy() {
            try {
                LegacyV1Reader.discardWorkingCopy(migrationRoot, domainId);
            } catch (IOException e) {
                LOGGER.warn("Unable to remove completed migration working copy for domain {}", domainId, e);
            }
        }

        private synchronized void quarantine(String message, @Nullable Throwable cause) {
            offlineState = ECOInfiniteDomainState.QUARANTINED;
            failureReason = cause == null || cause.getMessage() == null
                ? message
                : message + ": " + cause.getMessage();
            LOGGER.error("{}: {}", message, domainId, cause);
        }

        @Nullable
        private synchronized SavedDataInfiniteStorageEngine current() {
            advanceMigration(false);
            return offlineState == ECOInfiniteDomainState.READY ? delegate : null;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode) {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? 0L : engine.insert(key, amount, mode);
        }

        @Override
        public long insertOnce(UUID transactionId, AEKey key, long amount) {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? 0L : engine.insertOnce(transactionId, key, amount);
        }

        @Override
        public boolean applyTransferOnce(UUID transactionId, Collection<HugeStack> contents) {
            SavedDataInfiniteStorageEngine engine = current();
            return engine != null && engine.applyTransferOnce(transactionId, contents);
        }

        @Override
        public boolean hasLegacyTransferReceipt(UUID transactionId) {
            SavedDataInfiniteStorageEngine engine = current();
            return engine != null && engine.hasLegacyTransferReceipt(transactionId);
        }

        @Override
        public boolean hasTransferReceipt(UUID transactionId) {
            SavedDataInfiniteStorageEngine engine = current();
            return engine != null && engine.hasTransferReceipt(transactionId);
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode) {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? 0L : engine.extract(key, amount, mode);
        }

        @Override
        public HugeAmount getAmount(AEKey key) {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? HugeAmount.ZERO : engine.getAmount(key);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            SavedDataInfiniteStorageEngine engine = current();
            if (engine != null) {
                engine.getAvailableStacks(out);
            }
        }

        @Override
        public long getRevision() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? 0L : engine.getRevision();
        }

        @Override
        public boolean isEmpty() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine != null && engine.isEmpty();
        }

        @Override
        public HugeAmount getStoredAmount() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? HugeAmount.ZERO : engine.getStoredAmount();
        }

        @Override
        public int getStoredTypes() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? 0 : engine.getStoredTypes();
        }

        @Override
        public Collection<TypeStats> getTypeStats() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? List.of() : engine.getTypeStats();
        }

        @Override
        public Collection<HugeStack> getHugeStacks() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? List.of() : engine.getHugeStacks();
        }

        @Override
        public Collection<OrphanedStack> getOrphanedStacks() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? List.of() : engine.getOrphanedStacks();
        }

        @Override
        public int getOrphanedTypes() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? 0 : engine.getOrphanedTypes();
        }

        @Override
        public HugeAmount getOrphanedAmount() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? HugeAmount.ZERO : engine.getOrphanedAmount();
        }

        @Override
        public boolean hasUnacknowledgedOrphanedEntries() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine != null && engine.hasUnacknowledgedOrphanedEntries();
        }

        @Override
        public boolean acknowledgeOrphanedEntries() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine != null && engine.acknowledgeOrphanedEntries();
        }

        @Override
        public void flushAndAwait() {
            SavedDataInfiniteStorageEngine engine = current();
            if (engine != null) {
                engine.flushAndAwait();
            }
        }

        @Override
        public synchronized void closeAndFlush() {
            advanceMigration(true);
            if (delegate != null) {
                delegate.closeAndFlush();
            }
            offlineState = ECOInfiniteDomainState.CLOSED;
        }

        @Override
        public ECOInfiniteDomainState getState() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? offlineState : engine.getState();
        }

        @Override
        public Optional<String> getFailureReason() {
            SavedDataInfiniteStorageEngine engine = current();
            return engine == null ? Optional.ofNullable(failureReason) : engine.getFailureReason();
        }

        @Override
        public boolean isLoaded() {
            return getState() == ECOInfiniteDomainState.READY;
        }

        @Override
        public synchronized boolean tickLoad() {
            boolean completed = advanceMigration(false) || loadJustCompleted;
            loadJustCompleted = false;
            return completed;
        }
    }
}
