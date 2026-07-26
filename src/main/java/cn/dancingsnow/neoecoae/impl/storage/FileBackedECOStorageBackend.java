package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import com.google.common.math.LongMath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistence for a single ECO storage cell.
 *
 * <p>A cell lives in exactly one file, {@code <uuid>/cell.dat}. The type caps this mod hands out (315 item types at
 * the very top, 25 for fluids, 1 for mana/FE/source - see {@code ECOAETypeCounts}) mean the whole inventory is a few
 * tens of kilobytes, so a checkpoint is one small atomic write rather than a scatter across shard files.
 *
 * <p>Durability comes from the shared WAL owned by {@link ECOCellStorageManager}: mutations are appended there every
 * tick and fsynced once for all cells together, while {@code cell.dat} is rewritten lazily in the background. This
 * class therefore exposes its pending records and its checkpoint state to the manager instead of scheduling its own
 * writes.
 */
public final class FileBackedECOStorageBackend implements ECOStorageBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileBackedECOStorageBackend.class);
    private static final int CELL_VERSION = 3;
    private static final String CELL_FILE = "cell.dat";

    private final UUID storageId;
    private final Path storagePath;
    private final Map<AEKey, Long> amounts = new LinkedHashMap<>();
    private final KeyCounter visibleStacks = new KeyCounter();
    private final Map<AEKey, PendingDelta> pendingDeltas = new LinkedHashMap<>();

    @Nullable private ECOLegacyCellShardReader legacyReader;

    @Nullable private Future<?> checkpointWrite;

    private long checkpointWriteRevision;

    private boolean loaded;
    private boolean loadRequested;
    private boolean loading;
    private long storedAmount;
    private int storedTypes;
    private long revision;
    private long checkpointedRevision;
    private boolean checkpointDirty;
    private volatile boolean degraded;

    @Nullable private volatile Throwable persistenceFailure;

    public FileBackedECOStorageBackend(UUID storageId, Path storagePath, int summaryTypes, long summaryAmount) {
        this.storageId = storageId;
        this.storagePath = storagePath;
        this.storedTypes = Math.max(0, summaryTypes);
        this.storedAmount = Math.max(0L, summaryAmount);
    }

    static boolean hasPersistentData(Path storagePath) {
        return Files.isRegularFile(storagePath.resolve(CELL_FILE)) || ECOLegacyCellShardReader.isPresent(storagePath);
    }

    static Path cellFile(Path storagePath) {
        return storagePath.resolve(CELL_FILE);
    }

    public synchronized boolean hasPersistentData() {
        return hasPersistentData(storagePath);
    }

    /**
     * A cell whose file could not be read. It refuses every operation and is never written back, so a transient read
     * failure cannot end with the good data being overwritten by an apparently empty cell.
     */
    @Override
    public boolean isDegraded() {
        return degraded;
    }

    UUID storageId() {
        return storageId;
    }

    Path storagePath() {
        return storagePath;
    }

    public synchronized void importLegacyNow(List<GenericStack> stacks) {
        if (stacks.isEmpty()) {
            loaded = true;
            loadRequested = false;
            loading = false;
            return;
        }
        amounts.clear();
        visibleStacks.clear();
        storedAmount = 0L;
        storedTypes = 0;
        for (GenericStack stack : stacks) {
            if (stack.amount() <= 0L) {
                continue;
            }
            long current = amounts.getOrDefault(stack.what(), 0L);
            amounts.put(stack.what(), LongMath.saturatedAdd(current, stack.amount()));
        }
        rebuildVisibleStacks();
        loaded = true;
        loadRequested = false;
        loading = false;
        revision++;
        checkpointDirty = true;
        checkpointNow();
    }

    @Override
    public synchronized long insert(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L || degraded) {
            return 0L;
        }
        ensureLoadedBlocking();
        if (degraded) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            applyDelta(key, amount);
        }
        return amount;
    }

    @Override
    public synchronized long extract(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L || degraded) {
            return 0L;
        }
        ensureLoadedBlocking();
        if (degraded) {
            return 0L;
        }
        long current = amounts.getOrDefault(key, 0L);
        long extracted = Math.min(current, amount);
        if (extracted <= 0L) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            applyDelta(key, -extracted);
        }
        return extracted;
    }

    @Override
    public synchronized long getAmount(AEKey key) {
        if (key == null || degraded) {
            return 0L;
        }
        ensureLoadedBlocking();
        return degraded ? 0L : amounts.getOrDefault(key, 0L);
    }

    @Override
    public synchronized void getAvailableStacks(KeyCounter out) {
        if (degraded) {
            return;
        }
        if (!loaded) {
            requestLoad();
            return;
        }
        out.addAll(visibleStacks);
    }

    @Override
    public synchronized boolean isEmpty() {
        if (degraded) {
            // Never advertise a locked cell as empty; AE2 would happily start filling it.
            return false;
        }
        return loaded ? amounts.isEmpty() : storedTypes <= 0 && storedAmount <= 0L;
    }

    @Override
    public synchronized HugeAmount getStoredAmount() {
        return HugeAmount.of(storedAmount);
    }

    @Override
    public synchronized int getStoredTypes() {
        return storedTypes;
    }

    @Override
    public synchronized long getRevision() {
        return revision;
    }

    @Override
    public synchronized boolean isLoaded() {
        return loaded;
    }

    @Override
    public synchronized void requestLoad() {
        if (!loaded) {
            loadRequested = true;
        }
    }

    @Override
    public synchronized boolean loadBudgeted(long maxNanos) {
        if (loaded || !loadRequested) {
            return false;
        }
        startLoading();
        ECOLegacyCellShardReader reader = legacyReader;
        if (reader != null) {
            if (!reader.readBudgeted(maxNanos)) {
                return false;
            }
            finishLegacyLoading(reader);
            return true;
        }
        readCellFile();
        if (degraded) {
            loading = false;
            loadRequested = false;
            return true;
        }
        rebuildVisibleStacks();
        finishLoading();
        return true;
    }

    @Override
    public boolean flushBudgeted(long maxNanos) {
        return ECOCellStorageManager.flushCell(this, maxNanos);
    }

    @Override
    public void closeAndFlush() {
        ECOCellStorageManager.closeCell(this);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Shared-WAL plumbing. Called by ECOCellStorageManager, which owns the log and the pacing.
    // ---------------------------------------------------------------------------------------------------------

    synchronized List<ECOCellWalRecord> drainPendingWalRecords() {
        if (pendingDeltas.isEmpty() || degraded) {
            pendingDeltas.clear();
            return List.of();
        }
        List<ECOCellWalRecord> records = new ArrayList<>(pendingDeltas.size());
        for (Map.Entry<AEKey, PendingDelta> entry : pendingDeltas.entrySet()) {
            PendingDelta pending = entry.getValue();
            if (pending.delta != 0L) {
                records.add(new ECOCellWalRecord(storageId, entry.getKey(), pending.delta, pending.revision));
            }
        }
        pendingDeltas.clear();
        return records;
    }

    synchronized boolean hasPendingWalRecords() {
        return !pendingDeltas.isEmpty();
    }

    synchronized boolean isCheckpointDirty() {
        return checkpointDirty && !degraded;
    }

    synchronized boolean hasPendingCheckpointWrite() {
        return checkpointWrite != null;
    }

    /** Builds the snapshot on the calling thread and hands the bytes to the checkpoint thread. */
    synchronized void scheduleCheckpoint() {
        if (degraded || checkpointWrite != null || !checkpointDirty) {
            return;
        }
        if (!pendingDeltas.isEmpty()) {
            // A record folds several mutations into one delta stamped with the newest of them. A snapshot taken now
            // would hold part of that delta yet compare as older than the record, and recovery cannot apply only the
            // missing half. The manager always drains first; this defers rather than corrupts if one day it does not.
            LOGGER.warn("Deferring the ECO cell storage {} snapshot: log records are still pending", storageId);
            ECOCellStorageManager.markDirty(this);
            return;
        }
        long snapshotRevision = revision;
        byte[] payload;
        try {
            payload = createCheckpointPayload(snapshotRevision);
        } catch (IOException | RuntimeException e) {
            failPersistence(e);
            throw new IllegalStateException("Unable to serialize ECO cell storage " + storageId, e);
        }
        Path target = cellFile(storagePath);
        checkpointWriteRevision = snapshotRevision;
        checkpointWrite = ECOStorageIoWorker.submitCheckpoint(() -> {
            try {
                ECOStorageFiles.writeAtomically(target, payload);
            } catch (IOException | RuntimeException e) {
                failPersistence(e);
                throw new IllegalStateException("Unable to write ECO cell storage " + target, e);
            }
        });
    }

    /** Returns {@code true} when no checkpoint write is left in flight. */
    synchronized boolean completeCheckpointWrite(boolean wait) {
        Future<?> write = checkpointWrite;
        if (write == null) {
            return true;
        }
        if (!wait && !write.isDone()) {
            return false;
        }
        try {
            write.get();
            checkpointedRevision = checkpointWriteRevision;
            if (revision == checkpointWriteRevision) {
                checkpointDirty = false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing ECO cell storage " + storageId, e);
        } catch (ExecutionException e) {
            failPersistence(e.getCause());
            throw new IllegalStateException("Unable to write ECO cell storage " + storageId, e.getCause());
        } finally {
            checkpointWrite = null;
        }
        return true;
    }

    /** Blocking full checkpoint. Used on shutdown, on fork and right after a legacy migration. */
    synchronized void checkpointNow() {
        if (degraded) {
            return;
        }
        completeCheckpointWrite(true);
        if (!checkpointDirty) {
            return;
        }
        scheduleCheckpoint();
        completeCheckpointWrite(true);
    }

    synchronized long checkpointedRevision() {
        return checkpointedRevision;
    }

    /**
     * Replays one WAL record recovered at startup. Records the checkpoint already contains carry a revision no higher
     * than the one stamped into {@code cell.dat}, so they are dropped rather than applied twice.
     */
    synchronized boolean applyRecoveredRecord(AEKey key, long delta, long recordRevision) {
        if (degraded || key == null) {
            return false;
        }
        ensureLoadedBlocking();
        if (degraded || recordRevision <= revision) {
            return false;
        }
        long previous = amounts.getOrDefault(key, 0L);
        long next = Math.max(0L, LongMath.saturatedAdd(previous, delta));
        if (next <= 0L) {
            amounts.remove(key);
            visibleStacks.remove(key);
        } else {
            amounts.put(key, next);
            visibleStacks.set(key, next);
        }
        storedAmount = LongMath.saturatedAdd(storedAmount, next - previous);
        if (previous <= 0L && next > 0L) {
            storedTypes++;
        } else if (previous > 0L && next <= 0L) {
            storedTypes = Math.max(0, storedTypes - 1);
        }
        revision = recordRevision;
        checkpointDirty = true;
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------

    private void ensureLoadedBlocking() {
        if (loaded || degraded) {
            return;
        }
        requestLoad();
        while (!loaded && !degraded) {
            loadBudgeted(0L);
        }
    }

    private void startLoading() {
        if (loading) {
            return;
        }
        loading = true;
        amounts.clear();
        visibleStacks.clear();
        try {
            Files.createDirectories(storagePath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create ECO cell storage directory " + storagePath, e);
        }
        // cell.dat's presence is the migration marker: if it exists the legacy shards are stale leftovers.
        legacyReader = Files.isRegularFile(cellFile(storagePath)) || !ECOLegacyCellShardReader.isPresent(storagePath)
                ? null
                : new ECOLegacyCellShardReader(storagePath);
    }

    private void finishLoading() {
        loaded = true;
        loadRequested = false;
        loading = false;
    }

    private void finishLegacyLoading(ECOLegacyCellShardReader reader) {
        legacyReader = null;
        if (reader.isFailed()) {
            degraded = true;
            loading = false;
            loadRequested = false;
            LOGGER.error(
                    "Refusing to migrate ECO cell storage {}: a legacy shard could not be read, so the cell is locked"
                            + " instead of being rewritten from partial contents",
                    storagePath);
            return;
        }
        amounts.putAll(reader.finish());
        revision = reader.revision() + 1L;
        rebuildVisibleStacks();
        finishLoading();

        // Publish the single-file form first; only retire the shards once cell.dat is durable. A crash in between
        // simply repeats the legacy read on the next load.
        checkpointDirty = true;
        checkpointNow();
        if (degraded) {
            return;
        }
        try {
            ECOLegacyCellShardReader.delete(storagePath);
            LOGGER.info("Migrated ECO cell storage {} from 256 shards to a single cell.dat", storageId);
        } catch (IOException e) {
            LOGGER.warn("Unable to remove legacy ECO cell storage shards in {}", storagePath, e);
        }
    }

    private void readCellFile() {
        Path path = cellFile(storagePath);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            CompoundTag tag = NbtIo.readCompressed(input);
            revision = tag.getLong("revision");
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
                long amount = Math.max(0L, entry.getLong("amount"));
                if (key != null && amount > 0L) {
                    amounts.put(key, amount);
                }
            }
            checkpointedRevision = revision;
        } catch (RuntimeException | IOException e) {
            // Leave storedTypes/storedAmount at the item-stack summary so the tooltip still shows what was in here.
            degraded = true;
            LOGGER.error("Unable to read ECO cell storage {}; locking the cell to protect its contents", path, e);
        }
    }

    private byte[] createCheckpointPayload(long snapshotRevision) throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", CELL_VERSION);
        tag.putString("kind", "cell");
        tag.putUUID("id", storageId);
        tag.putLong("revision", snapshotRevision);
        tag.putInt("storedTypes", storedTypes);
        tag.putString("storedAmount", Long.toString(storedAmount));
        ListTag entries = new ListTag();
        for (Map.Entry<AEKey, Long> entry : amounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("key", entry.getKey().toTagGeneric());
            entryTag.putLong("amount", entry.getValue());
            entries.add(entryTag);
        }
        tag.put("entries", entries);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }

    private void applyDelta(AEKey key, long delta) {
        if (delta == 0L) {
            return;
        }
        long previous = amounts.getOrDefault(key, 0L);
        long next = Math.max(0L, LongMath.saturatedAdd(previous, delta));
        if (next <= 0L) {
            amounts.remove(key);
            visibleStacks.remove(key);
        } else {
            amounts.put(key, next);
            visibleStacks.set(key, next);
        }
        storedAmount = LongMath.saturatedAdd(storedAmount, next - previous);
        if (previous <= 0L && next > 0L) {
            storedTypes++;
        } else if (previous > 0L && next <= 0L) {
            storedTypes = Math.max(0, storedTypes - 1);
        }
        revision = revision == Long.MAX_VALUE ? 0L : revision + 1L;
        checkpointDirty = true;

        // The record must carry this mutation's own revision, not the cell's revision at drain time, or a checkpoint
        // taken between the mutation and the drain would look older than a record it already contains.
        long mutationRevision = revision;
        pendingDeltas.merge(key, new PendingDelta(delta, mutationRevision), PendingDelta::mergedWith);
        ECOCellStorageManager.markDirty(this);
    }

    private void rebuildVisibleStacks() {
        visibleStacks.clear();
        storedAmount = 0L;
        storedTypes = 0;
        amounts.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0L);
        for (Map.Entry<AEKey, Long> entry : amounts.entrySet()) {
            long amount = entry.getValue();
            visibleStacks.set(entry.getKey(), amount);
            storedAmount = LongMath.saturatedAdd(storedAmount, amount);
            storedTypes++;
        }
    }

    private void failPersistence(@Nullable Throwable cause) {
        degraded = true;
        if (persistenceFailure == null) {
            persistenceFailure = cause;
        }
        LOGGER.error("ECO cell storage {} failed to persist and is now locked", storageId, cause);
    }

    private record PendingDelta(long delta, long revision) {
        PendingDelta mergedWith(PendingDelta newer) {
            return new PendingDelta(LongMath.saturatedAdd(delta, newer.delta), Math.max(revision, newer.revision));
        }
    }
}
