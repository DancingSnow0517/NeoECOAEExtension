package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IBatchedECOCellSaveProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns live ordinary ECO cells backed by Minecraft {@code SavedData}.
 *
 * <p>Each cell UUID is one independent SavedData record. This removes the shared file/WAL fault domain: a bad cell
 * locks only that cell, while normal world saves own durability for every other cell. The former file layout is only
 * opened through {@link LegacyECOCellReader} during a verified, one-way migration.
 */
public final class ECOCellStorageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOCellStorageManager.class);
    private static final String SAVED_DATA_DIRECTORY = "neoecoae_cells";
    private static final String LEGACY_DIRECTORY = "neoecoae";
    private static final String LEGACY_ARCHIVE_DIRECTORY = "neoecoae_cell_storage_v1_archive";
    private static final String MIGRATION_DIRECTORY = "neoecoae_cell_migration";

    private static final Map<UUID, SavedDataECOStorageBackend> CELLS = new Object2ObjectOpenHashMap<>();
    private static final Map<UUID, ISaveProvider> OWNERS = new Object2ObjectOpenHashMap<>();
    private static final Map<ISaveProvider, UUID> OWNER_IDS = new IdentityHashMap<>();

    private ECOCellStorageManager() {}

    @Nullable public static synchronized ECOStorageBackend getOrCreate(
            ItemStack stack, IBasicECOCellItem cellItem, @Nullable ISaveProvider owner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        boolean hasLegacyContents = ECOCellHandle.hasLegacyContents(stack);
        boolean hadId = ECOCellHandle.getId(stack).isPresent();
        if (server == null || !server.isSameThread()) {
            if (hasLegacyContents) {
                ECOCellHandle.updateSummaryFromLegacy(stack, 0L);
            }
            return null;
        }
        if (!hadId && owner == null && !hasLegacyContents) {
            return null;
        }

        UUID id = ECOCellHandle.getOrCreateId(stack);
        if (owner != null) {
            try {
                id = forkIfClaimedByAnotherOwner(server, stack, id, owner);
            } catch (RuntimeException e) {
                LOGGER.error("Cannot safely detach duplicated storage cell {}; refusing this mount", id, e);
                ECOCellHandle.markLocked(stack);
                return null;
            }
        }

        if (hadId
                && !CELLS.containsKey(id)
                && !hasAnyStorageData(server, id)
                && ECOCellHandle.getVersion(stack) >= ECOCellHandle.VERSION
                && ECOCellHandle.hasNonEmptySummary(stack)
                && !hasLegacyContents) {
            ECOCellHandle.markMissing(stack);
            return null;
        }

        if (owner != null) {
            claim(id, owner);
        }
        ECOCellHandle.clearProblemState(stack);

        SavedDataECOStorageBackend backend = CELLS.get(id);
        boolean openedNow = backend == null;
        if (backend == null) {
            backend = openOrCreate(
                    server,
                    id,
                    ECOCellHandle.getStoredTypesSummary(stack),
                    ECOCellHandle.getStoredAmountSummary(stack));
            CELLS.put(id, backend);
        }
        ECOSavedDataPersistence.register(backend);
        if (backend.isDegraded()) {
            ECOCellHandle.markLocked(stack);
            return backend;
        }

        if (hasLegacyContents) {
            try {
                if (openedNow && backend.isFreshEmpty()) {
                    List<GenericStack> legacyStacks = ECOCellHandle.readLegacyStacks(stack);
                    backend.importItemStackLegacy(legacyStacks);
                    backend.flushAndAwait();
                }
                if (backend.isDegraded()) {
                    ECOCellHandle.markLocked(stack);
                    return backend;
                }
                ECOCellHandle.updateSummary(stack, backend, estimateUsedBytes(cellItem, backend));
                ECOCellHandle.clearLegacyContents(stack);
            } catch (RuntimeException e) {
                LOGGER.error("Unable to migrate item-stack contents into ECO storage cell {}", id, e);
                backend.quarantine("Unable to migrate item-stack ECO storage contents", e);
                ECOCellHandle.markLocked(stack);
            }
        }
        return backend;
    }

    public static synchronized void forkIfAlreadyPresent(ItemStack stack, Iterable<ItemStack> mountedStacks) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || stack == null || stack.isEmpty()) {
            return;
        }
        UUID id = ECOCellHandle.getId(stack).orElse(null);
        if (id == null) {
            return;
        }
        for (ItemStack mountedStack : mountedStacks) {
            if (mountedStack == null || mountedStack.isEmpty() || mountedStack == stack) {
                continue;
            }
            if (ECOCellHandle.getId(mountedStack).filter(id::equals).isPresent()) {
                try {
                    forkStorageId(server, stack, id, "duplicate mounted in the same ECO storage host");
                } catch (RuntimeException e) {
                    LOGGER.error("Cannot safely detach duplicated storage cell {}", id, e);
                    ECOCellHandle.markLocked(stack);
                }
                return;
            }
        }
    }

    public static synchronized boolean loadBudgeted(ECOStorageBackend backend, long maxNanos) {
        return backend != null && backend.loadBudgeted(maxNanos);
    }

    // ---------------------------------------------------------------------------------------------------------
    // SavedData lifecycle
    // ---------------------------------------------------------------------------------------------------------

    /** SavedData is recovered by Minecraft before cells are mounted; there is no ordinary-cell WAL to replay. */
    public static synchronized void onServerStarted(MinecraftServer server) {
        ECOSavedDataPersistence.clear();
        CELLS.clear();
        OWNERS.clear();
        OWNER_IDS.clear();
    }

    /** Flushes the open ordinary-cell SavedData records. The old time budget is not needed by SavedData. */
    public static synchronized void flushBudgeted(long maxNanos) {
        ECOSavedDataPersistence.flushAll();
    }

    public static synchronized void closeAll() {
        try {
            flushBudgeted(0L);
        } finally {
            CELLS.clear();
            OWNERS.clear();
            OWNER_IDS.clear();
        }
    }

    public static synchronized void close(UUID id) {
        ISaveProvider owner = OWNERS.remove(id);
        if (owner != null) {
            OWNER_IDS.remove(owner);
        }
        closeBackend(id);
    }

    public static synchronized void restorePrevious(MinecraftServer server, UUID id) throws IOException {
        Path transfers = worldRoot(server).resolve("data/neoecoae_transfers");
        if (Files.isDirectory(transfers)) {
            try (var files = Files.list(transfers)) {
                if (files.anyMatch(path -> path.toString().endsWith(".dat"))) {
                    throw new IOException(
                            "Pending transfers must be recovered by restarting before rolling back cells");
                }
            }
        }
        Path file = savedDataFile(worldRoot(server), id);
        var snapshot = AtomicSavedDataFile.read(StorageFileHistory.previous(file));
        var storage = server.overworld().getDataStorage();
        // Strict identity validation, then preserve the damaged/current evidence before rollback.
        var validated = SavedDataECOStorageBackend.load(snapshot, id, storage, file);
        ECOSavedDataPersistence.unregister(validated);
        StorageFileHistory.archive(file);
        AtomicSavedDataFile.write(
                file,
                snapshot,
                SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        var current = CELLS.get(id);
        if (current != null) current.restoreSnapshot(snapshot);
        else {
            current = validated;
            CELLS.put(id, current);
        }
        storage.set(savedDataName(id), current);
        ECOSavedDataPersistence.register(current);
        notifyRecovered(id);
    }

    public static synchronized boolean recover(MinecraftServer server, UUID id) throws IOException {
        Path file = savedDataFile(worldRoot(server), id);
        if (StorageTransferJournal.isBlocked(file)) throw new IOException("Restart to recover pending transfer first");
        var current = CELLS.get(id);
        if (current != null && !current.needsLoadRecovery()) {
            // Never replace unsaved live quantities with an older file after a failed retry.
            boolean recovered = current.retryPersistence();
            if (recovered) notifyRecovered(id);
            return recovered;
        }
        var snapshot = AtomicSavedDataFile.read(file);
        var storage = server.overworld().getDataStorage();
        if (current == null) current = SavedDataECOStorageBackend.load(snapshot, id, storage, file);
        else current.restoreSnapshot(snapshot);
        CELLS.put(id, current);
        storage.set(savedDataName(id), current);
        ECOSavedDataPersistence.register(current);
        notifyRecovered(id);
        return true;
    }

    private static void notifyRecovered(UUID id) {
        ISaveProvider owner = OWNERS.get(id);
        if (owner instanceof IBatchedECOCellSaveProvider batched) batched.onStorageRecovered();
        else if (owner != null) owner.saveChanges();
    }

    public static synchronized void release(@Nullable ItemStack stack, @Nullable ISaveProvider owner) {
        if (owner == null) {
            return;
        }
        UUID id = OWNER_IDS.get(owner);
        if (id == null) {
            id = ECOCellHandle.getId(stack).orElse(null);
        }
        if (id != null && OWNERS.get(id) == owner) {
            OWNERS.remove(id);
            OWNER_IDS.remove(owner);
            closeBackend(id);
        }
    }

    // These three bridges keep the retired FileBackedECOStorageBackend source-compatible while it remains available
    // as historical tooling. Normal storage never constructs that backend anymore.
    @Deprecated
    static void markDirty(FileBackedECOStorageBackend backend) {}

    @Deprecated
    static synchronized boolean flushCell(FileBackedECOStorageBackend backend, long maxNanos) {
        if (backend == null) {
            return false;
        }
        boolean pending = backend.isCheckpointDirty() || backend.hasPendingCheckpointWrite();
        backend.checkpointNow();
        return pending;
    }

    @Deprecated
    static synchronized void closeCell(FileBackedECOStorageBackend backend) {
        if (backend != null) {
            backend.checkpointNow();
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // SavedData discovery and legacy migration
    // ---------------------------------------------------------------------------------------------------------

    private static SavedDataECOStorageBackend openOrCreate(
            MinecraftServer server, UUID id, int summaryTypes, long summaryAmount) {
        Path worldRoot = worldRoot(server);
        Path dataFile = savedDataFile(worldRoot, id);
        Path legacySource = legacyCellDirectory(worldRoot, id);
        Path archive = archiveRoot(worldRoot).resolve(id.toString());
        DimensionDataStorage dataStorage = server.overworld().getDataStorage();
        if (StorageTransferJournal.isBlocked(dataFile)) {
            return quarantined(
                    id,
                    dataStorage,
                    dataFile,
                    summaryTypes,
                    summaryAmount,
                    "Pending transfer could not be replayed; repair disk access and restart");
        }
        try {
            boolean hasV2Path = Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS);
            boolean hasV2 = Files.isRegularFile(dataFile, LinkOption.NOFOLLOW_LINKS);
            boolean hasLegacyPath = Files.exists(legacySource, LinkOption.NOFOLLOW_LINKS);
            boolean hasLegacy = LegacyECOCellReader.isPresent(legacySource);
            boolean hasArchivePath = Files.exists(archive, LinkOption.NOFOLLOW_LINKS);
            boolean hasArchive = Files.isDirectory(archive, LinkOption.NOFOLLOW_LINKS);

            if (hasV2Path && !hasV2) {
                return quarantined(
                        id,
                        dataStorage,
                        dataFile,
                        summaryTypes,
                        summaryAmount,
                        "ECO storage SavedData path is not a normal file");
            }
            if (hasLegacyPath && !Files.isDirectory(legacySource, LinkOption.NOFOLLOW_LINKS)) {
                return quarantined(
                        id,
                        dataStorage,
                        dataFile,
                        summaryTypes,
                        summaryAmount,
                        "Legacy ECO storage path is not a normal directory");
            }
            if (hasArchivePath && !hasArchive) {
                return quarantined(
                        id,
                        dataStorage,
                        dataFile,
                        summaryTypes,
                        summaryAmount,
                        "Legacy ECO storage archive is not a normal directory");
            }
            if (hasV2) {
                SavedDataECOStorageBackend backend = dataStorage.get(
                        tag -> SavedDataECOStorageBackend.load(tag, id, dataStorage, dataFile), savedDataName(id));
                if (backend == null) {
                    return quarantined(
                            id,
                            dataStorage,
                            dataFile,
                            summaryTypes,
                            summaryAmount,
                            "Existing ECO storage SavedData failed strict loading");
                }
                finishInterruptedMigration(id, backend, legacySource, hasLegacy, archive, hasArchive);
                return backend;
            }
            if (hasLegacy && hasArchive) {
                return quarantined(
                        id,
                        dataStorage,
                        dataFile,
                        summaryTypes,
                        summaryAmount,
                        "Multiple legacy ECO storage authorities exist");
            }
            if (hasLegacy) {
                LegacyECOCellReader.Snapshot snapshot =
                        LegacyECOCellReader.copyAndRead(id, legacySource, migrationRoot(worldRoot));
                SavedDataECOStorageBackend backend = SavedDataECOStorageBackend.createNew(id, dataStorage, dataFile);
                dataStorage.set(savedDataName(id), backend);
                backend.importLegacyFile(snapshot);
                backend.flushAndAwait();
                if (backend.isDegraded()) {
                    return backend;
                }
                LegacyECOCellReader.archive(legacySource, archive, snapshot.sourceFingerprint());
                LOGGER.info("Migrated ECO storage cell {} from file storage to SavedData", id);
                return backend;
            }
            if (hasArchive) {
                return quarantined(
                        id,
                        dataStorage,
                        dataFile,
                        summaryTypes,
                        summaryAmount,
                        "ECO storage SavedData is missing while a legacy archive exists");
            }
            SavedDataECOStorageBackend backend = SavedDataECOStorageBackend.createNew(id, dataStorage, dataFile);
            dataStorage.set(savedDataName(id), backend);
            backend.setDirty();
            backend.flushAndAwait();
            return backend;
        } catch (Exception e) {
            LOGGER.error("Unable to open ECO storage SavedData cell {}", id, e);
            return quarantined(
                    id,
                    dataStorage,
                    dataFile,
                    summaryTypes,
                    summaryAmount,
                    "Unable to discover or migrate ECO storage data: " + e.getMessage());
        }
    }

    private static void finishInterruptedMigration(
            UUID id,
            SavedDataECOStorageBackend backend,
            Path legacySource,
            boolean hasLegacy,
            Path archive,
            boolean hasArchive)
            throws IOException {
        if (!hasLegacy) {
            if (hasArchive && backend.legacyFingerprint() == null) {
                throw new IOException("A legacy ECO storage archive conflicts with a non-migrated SavedData cell");
            }
            return;
        }
        if (hasArchive) {
            throw new IOException("A legacy ECO storage source and archive both exist");
        }
        String fingerprint = backend.legacyFingerprint();
        if (fingerprint == null) {
            throw new IOException("SavedData does not prove ownership of the remaining legacy ECO storage source");
        }
        LegacyECOCellReader.archive(legacySource, archive, fingerprint);
        LOGGER.info("Completed interrupted legacy archive cutover for ECO storage cell {}", id);
    }

    private static SavedDataECOStorageBackend quarantined(
            UUID id,
            DimensionDataStorage dataStorage,
            Path dataFile,
            int summaryTypes,
            long summaryAmount,
            String reason) {
        LOGGER.error("Quarantining ECO storage cell {}: {}", id, reason);
        return SavedDataECOStorageBackend.quarantined(id, dataStorage, dataFile, summaryTypes, summaryAmount, reason);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Ownership and copying
    // ---------------------------------------------------------------------------------------------------------

    private static void closeBackend(UUID id) {
        SavedDataECOStorageBackend backend = CELLS.remove(id);
        if (backend != null) {
            backend.closeAndFlush();
            ECOSavedDataPersistence.unregister(backend);
        }
    }

    private static UUID forkIfClaimedByAnotherOwner(
            MinecraftServer server, ItemStack stack, UUID id, ISaveProvider owner) {
        ISaveProvider currentOwner = OWNERS.get(id);
        if (currentOwner == null || currentOwner == owner) {
            return id;
        }
        return forkStorageId(server, stack, id, "storage id is already claimed by another host");
    }

    private static void claim(UUID id, ISaveProvider owner) {
        UUID previousId = OWNER_IDS.get(owner);
        if (previousId != null && !previousId.equals(id) && OWNERS.get(previousId) == owner) {
            OWNERS.remove(previousId);
        }
        OWNERS.put(id, owner);
        OWNER_IDS.put(owner, id);
    }

    private static UUID forkStorageId(MinecraftServer server, ItemStack stack, UUID oldId, String reason) {
        SavedDataECOStorageBackend source = CELLS.get(oldId);
        if (source == null) {
            source = openOrCreate(
                    server,
                    oldId,
                    ECOCellHandle.getStoredTypesSummary(stack),
                    ECOCellHandle.getStoredAmountSummary(stack));
            CELLS.put(oldId, source);
        }
        source.flushAndAwait();
        if (source.isDegraded()) {
            throw new IllegalStateException(
                    "Cannot fork quarantined ECO storage cell " + oldId + ": " + source.failureReason());
        }

        UUID newId;
        do {
            newId = UUID.randomUUID();
        } while (CELLS.containsKey(newId) || hasAnyStorageData(server, newId));

        Path worldRoot = worldRoot(server);
        Path newDataFile = savedDataFile(worldRoot, newId);
        DimensionDataStorage dataStorage = server.overworld().getDataStorage();
        SavedDataECOStorageBackend copy = SavedDataECOStorageBackend.createNew(newId, dataStorage, newDataFile);
        dataStorage.set(savedDataName(newId), copy);
        copy.importFork(source.copyContents(), source.copyRevision());
        copy.flushAndAwait();
        if (copy.isDegraded()) {
            throw new IllegalStateException(
                    "Unable to persist forked ECO storage cell " + newId + ": " + copy.failureReason());
        }
        CELLS.put(newId, copy);
        ECOCellHandle.setId(stack, newId);
        ECOCellHandle.clearProblemState(stack);
        LOGGER.warn("Forked duplicated ECO storage matrix UUID {} -> {} ({})", oldId, newId, reason);
        return newId;
    }

    private static long estimateUsedBytes(IBasicECOCellItem cellItem, ECOStorageBackend backend) {
        long typeBytes = (long) backend.getStoredTypes() * cellItem.getBytesPerType();
        long amountBytes = backend.getStoredAmount().toLongSaturated()
                / Math.max(1, cellItem.getKeyType().getAmountPerByte());
        return Math.max(0L, typeBytes + amountBytes);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Paths
    // ---------------------------------------------------------------------------------------------------------

    private static boolean hasAnyStorageData(MinecraftServer server, UUID id) {
        Path root = worldRoot(server);
        return Files.exists(savedDataFile(root, id), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(legacyCellDirectory(root, id), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(archiveRoot(root).resolve(id.toString()), LinkOption.NOFOLLOW_LINKS);
    }

    private static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    private static String savedDataName(UUID id) {
        return SAVED_DATA_DIRECTORY + "/" + id;
    }

    private static Path savedDataFile(Path worldRoot, UUID id) {
        return worldRoot.resolve("data").resolve(SAVED_DATA_DIRECTORY).resolve(id + ".dat");
    }

    private static Path legacyCellDirectory(Path worldRoot, UUID id) {
        return worldRoot
                .resolve("data")
                .resolve(LEGACY_DIRECTORY)
                .resolve("storage_v2")
                .resolve("cells")
                .resolve(id.toString());
    }

    private static Path archiveRoot(Path worldRoot) {
        return worldRoot.resolve(LEGACY_ARCHIVE_DIRECTORY);
    }

    private static Path migrationRoot(Path worldRoot) {
        return worldRoot.resolve("data").resolve(MIGRATION_DIRECTORY);
    }
}
