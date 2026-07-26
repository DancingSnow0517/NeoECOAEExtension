package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns every live ECO storage cell and the write-ahead log that keeps them crash-safe.
 *
 * <p>All cells share one log. A tick's mutations - however many cells they touched - are drained into a single frame
 * and fsynced once, so durability costs one disk flush per tick instead of one per dirty cell. The {@code cell.dat}
 * snapshots are rewritten lazily in the background under a time budget; the log is what closes the gap in between, and
 * it is truncated as soon as every snapshot it protects has landed.
 *
 * <p>Locking: this class' monitor is always taken before a backend's. {@link #markDirty} is the one call that runs
 * with a backend monitor already held, so it only touches {@link #DIRTY_LOCK}, which is a leaf.
 */
public final class ECOCellStorageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOCellStorageManager.class);
    private static final long LOAD_BUDGET_NANOS = 750_000L;
    private static final long CHECKPOINT_BUDGET_NANOS = 500_000L;
    private static final int MAX_WAL_RECORD_BYTES = 16 * 1024 * 1024;
    private static final int WAL_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_RECORDS_PER_FRAME = 2048;
    private static final long WAL_TRUNCATE_THRESHOLD_BYTES = 32L * 1024L * 1024L;
    private static final int WAL_VERSION = 1;
    private static final String WAL_FILE = "wal_000.log";

    private static final Map<UUID, FileBackedECOStorageBackend> CELLS = new LinkedHashMap<>();
    private static final Map<UUID, ISaveProvider> OWNERS = new HashMap<>();
    private static final Map<ISaveProvider, UUID> OWNER_IDS = new IdentityHashMap<>();
    private static final Set<FileBackedECOStorageBackend> CHECKPOINT_QUEUE = new LinkedHashSet<>();
    private static final Object DIRTY_LOCK = new Object();
    private static final Set<FileBackedECOStorageBackend> WAL_DIRTY = new LinkedHashSet<>();

    @Nullable private static ECOStorageWal wal;

    @Nullable private static Future<?> pendingWalSync;

    private static volatile boolean walDisabled;
    private static int loadRotation;

    private ECOCellStorageManager() {}

    @Nullable public static synchronized ECOStorageBackend getOrCreate(
            ItemStack stack, IBasicECOCellItem cellItem, @Nullable ISaveProvider owner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        boolean hasLegacyContents = ECOCellHandle.hasLegacyContents(stack);
        boolean hadId = ECOCellHandle.getId(stack).isPresent();

        if (server == null) {
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
            id = forkIfClaimedByAnotherOwner(server, stack, id, owner);
        }

        Path path = cellPath(server, id);
        if (hadId
                && !CELLS.containsKey(id)
                && !FileBackedECOStorageBackend.hasPersistentData(path)
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
        UUID backendId = id;
        Path backendPath = path;
        FileBackedECOStorageBackend backend = CELLS.computeIfAbsent(
                backendId,
                ignored -> new FileBackedECOStorageBackend(
                        backendId,
                        backendPath,
                        ECOCellHandle.getStoredTypesSummary(stack),
                        ECOCellHandle.getStoredAmountSummary(stack)));

        if (backend.isDegraded()) {
            ECOCellHandle.markLocked(stack);
            return backend;
        }

        if (hasLegacyContents) {
            if (!backend.hasPersistentData()) {
                List<GenericStack> legacyStacks = ECOCellHandle.readLegacyStacks(stack);
                backend.importLegacyNow(legacyStacks);
            } else {
                backend.requestLoad();
                backend.loadBudgeted(0L);
            }
            if (backend.isDegraded()) {
                ECOCellHandle.markLocked(stack);
                return backend;
            }
            ECOCellHandle.updateSummary(stack, backend, estimateUsedBytes(cellItem, backend));
            ECOCellHandle.clearLegacyContents(stack);
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
                forkStorageId(server, stack, id, "duplicate mounted in the same ECO storage host");
                return;
            }
        }
    }

    public static synchronized boolean loadBudgeted(ECOStorageBackend backend, long maxNanos) {
        return backend != null && backend.loadBudgeted(maxNanos);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Server lifecycle
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Replays whatever the previous run left in the log. A non-empty log means the process died without a clean
     * shutdown, so the affected cells are loaded, brought forward and re-checkpointed before anything can touch them.
     */
    public static synchronized void onServerStarted(MinecraftServer server) {
        walDisabled = false;
        Path walPath = storageRoot(server).resolve(WAL_FILE);
        ECOStorageWal recovered = new ECOStorageWal(walPath, MAX_WAL_RECORD_BYTES, WAL_BUFFER_BYTES);

        Map<UUID, List<ECOCellWalRecord>> byCell = new LinkedHashMap<>();
        ECOStorageWal.Status status = recovered.replay(payload -> readFrame(payload, byCell));

        if (status == ECOStorageWal.Status.TAIL_REPAIRED) {
            LOGGER.warn("The ECO cell storage WAL ended mid-write; the incomplete tail was discarded");
        } else if (status == ECOStorageWal.Status.CORRUPT) {
            LOGGER.error(
                    "The ECO cell storage WAL is damaged before its end. Everything readable up to the damage will be"
                            + " recovered, but mutations logged after it are lost");
        }

        if (!byCell.isEmpty()) {
            LOGGER.warn("Recovering {} ECO cell(s) from the storage WAL after an unclean shutdown", byCell.size());
            for (Map.Entry<UUID, List<ECOCellWalRecord>> entry : byCell.entrySet()) {
                recoverCell(server, entry.getKey(), entry.getValue());
            }
        }

        if (status == ECOStorageWal.Status.CORRUPT) {
            // Keep the evidence: everything past the damage is unreadable here but may still be salvageable by hand.
            recovered.close();
            quarantineWal(walPath);
            recovered = new ECOStorageWal(walPath, MAX_WAL_RECORD_BYTES, WAL_BUFFER_BYTES);
        } else {
            try {
                recovered.truncate();
            } catch (IOException e) {
                LOGGER.error("Unable to reset the ECO cell storage WAL {}", walPath, e);
            }
        }
        wal = recovered;
    }

    /** Blocks until the previous tick's log frame is on the platter. Runs at the start of every server tick. */
    public static synchronized void awaitPreviousTick() {
        Future<?> pending = pendingWalSync;
        if (pending == null) {
            return;
        }
        pendingWalSync = null;
        try {
            pending.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing the ECO cell storage WAL", e);
        } catch (ExecutionException e) {
            LOGGER.error("Unable to write the ECO cell storage WAL", e.getCause());
        }
    }

    public static synchronized void tick() {
        runBudgetedLoads();
        submitPendingWal();
        if (walDisabled) {
            // Without a log the snapshots are the only durable copy, so they can no longer be paced.
            checkpointAllNow();
        } else {
            processCheckpoints(CHECKPOINT_BUDGET_NANOS);
            maybeTruncateWal();
        }
    }

    public static synchronized void flushBudgeted(long maxNanos) {
        flushWalNow();
        if (maxNanos <= 0L) {
            checkpointAllNow();
            maybeTruncateWal();
        } else {
            processCheckpoints(maxNanos);
        }
    }

    public static synchronized void closeAll() {
        flushWalNow();
        checkpointAllNow();
        ECOStorageWal target = wal;
        if (target != null) {
            try {
                // A clean shutdown leaves nothing to replay.
                target.truncate();
            } catch (IOException e) {
                LOGGER.error("Unable to reset the ECO cell storage WAL {}", target.path(), e);
            }
            target.close();
        }
        wal = null;
        CELLS.clear();
        OWNERS.clear();
        OWNER_IDS.clear();
        CHECKPOINT_QUEUE.clear();
        synchronized (DIRTY_LOCK) {
            WAL_DIRTY.clear();
        }
    }

    public static synchronized void close(UUID id) {
        ISaveProvider owner = OWNERS.remove(id);
        if (owner != null) {
            OWNER_IDS.remove(owner);
        }
        closeBackend(id);
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

    // ---------------------------------------------------------------------------------------------------------
    // Backend callbacks
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Records that a cell has mutations waiting for the log. Called with the cell's own monitor held, so it must not
     * reach for anything but {@link #DIRTY_LOCK}.
     */
    static void markDirty(FileBackedECOStorageBackend backend) {
        synchronized (DIRTY_LOCK) {
            WAL_DIRTY.add(backend);
        }
    }

    static synchronized boolean flushCell(FileBackedECOStorageBackend backend, long maxNanos) {
        if (backend == null) {
            return false;
        }
        boolean work = flushWalNow();
        if (maxNanos <= 0L) {
            work |= backend.isCheckpointDirty() || backend.hasPendingCheckpointWrite();
            backend.checkpointNow();
            CHECKPOINT_QUEUE.remove(backend);
        } else {
            work |= processCheckpoints(maxNanos);
        }
        return work;
    }

    static synchronized void closeCell(FileBackedECOStorageBackend backend) {
        if (backend == null) {
            return;
        }
        flushWalNow();
        backend.checkpointNow();
        CHECKPOINT_QUEUE.remove(backend);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------------------------------

    private static void runBudgetedLoads() {
        if (CELLS.isEmpty()) {
            return;
        }
        List<FileBackedECOStorageBackend> pending = null;
        for (FileBackedECOStorageBackend backend : CELLS.values()) {
            if (!backend.isLoaded()) {
                if (pending == null) {
                    pending = new ArrayList<>();
                }
                pending.add(backend);
            }
        }
        if (pending == null) {
            return;
        }
        // Rotate the starting point so a slow cell at the head cannot hold the budget forever.
        int size = pending.size();
        int start = Math.floorMod(loadRotation++, size);
        long deadline = System.nanoTime() + LOAD_BUDGET_NANOS;
        for (int i = 0; i < size; i++) {
            pending.get((start + i) % size).loadBudgeted(Math.max(1L, deadline - System.nanoTime()));
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Write-ahead log
    // ---------------------------------------------------------------------------------------------------------

    /** Drains every dirty cell into log frames and hands them to the log thread. */
    private static boolean submitPendingWal() {
        List<ECOCellWalRecord> records = drainDirtyCells();
        if (records.isEmpty()) {
            return false;
        }
        ECOStorageWal target = wal();
        if (target == null) {
            // No log available; the records are redundant with the snapshots those cells are already queued for.
            return false;
        }
        List<byte[]> frames = new ArrayList<>();
        for (int from = 0; from < records.size(); from += MAX_RECORDS_PER_FRAME) {
            int to = Math.min(records.size(), from + MAX_RECORDS_PER_FRAME);
            try {
                frames.add(encodeFrame(records.subList(from, to)));
            } catch (IOException | RuntimeException e) {
                disableWal("unable to encode a log frame", e);
                return false;
            }
        }
        pendingWalSync = ECOStorageIoWorker.submit(() -> {
            try {
                for (byte[] frame : frames) {
                    target.append(frame);
                }
                target.sync();
            } catch (IOException | RuntimeException e) {
                disableWal("unable to append to " + target.path(), e);
            }
        });
        return true;
    }

    /** Appends and fsyncs everything outstanding before returning. */
    private static boolean flushWalNow() {
        awaitPreviousTick();
        boolean submitted = submitPendingWal();
        awaitPreviousTick();
        return submitted;
    }

    private static List<ECOCellWalRecord> drainDirtyCells() {
        List<FileBackedECOStorageBackend> dirty;
        synchronized (DIRTY_LOCK) {
            if (WAL_DIRTY.isEmpty()) {
                return List.of();
            }
            dirty = new ArrayList<>(WAL_DIRTY);
            WAL_DIRTY.clear();
        }
        List<ECOCellWalRecord> records = new ArrayList<>();
        for (FileBackedECOStorageBackend backend : dirty) {
            records.addAll(backend.drainPendingWalRecords());
            CHECKPOINT_QUEUE.add(backend);
        }
        return records;
    }

    private static byte[] encodeFrame(List<ECOCellWalRecord> records) throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", WAL_VERSION);
        ListTag list = new ListTag();
        for (ECOCellWalRecord record : records) {
            list.add(record.write());
        }
        tag.put("records", list);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }

    private static void readFrame(byte[] payload, Map<UUID, List<ECOCellWalRecord>> byCell) {
        CompoundTag tag;
        try (InputStream input = new ByteArrayInputStream(payload)) {
            tag = NbtIo.readCompressed(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to decode an ECO cell storage WAL frame", e);
        }
        ListTag list = tag.getList("records", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ECOCellWalRecord record = ECOCellWalRecord.read(list.getCompound(i));
            if (record != null) {
                byCell.computeIfAbsent(record.cell(), ignored -> new ArrayList<>())
                        .add(record);
            }
        }
    }

    private static void maybeTruncateWal() {
        ECOStorageWal target = wal;
        if (target == null || walDisabled || target.sizeBytes() <= 0L) {
            return;
        }
        if (target.sizeBytes() >= WAL_TRUNCATE_THRESHOLD_BYTES) {
            // The log has outgrown the snapshots it protects. Cell snapshots are small, so sweeping them all and
            // starting over is cheaper than carrying - and one day replaying - a log this size.
            LOGGER.info("Checkpointing every ECO cell to retire a {} byte storage WAL", target.sizeBytes());
            awaitPreviousTick();
            checkpointAllNow();
        } else if (!CHECKPOINT_QUEUE.isEmpty()) {
            // Some snapshot is still behind its log records; they have to stay until it lands.
            return;
        }
        synchronized (DIRTY_LOCK) {
            if (!WAL_DIRTY.isEmpty()) {
                return;
            }
        }
        awaitPreviousTick();
        ECOStorageWal current = wal;
        if (current == null || current.sizeBytes() <= 0L) {
            return;
        }
        pendingWalSync = ECOStorageIoWorker.submit(() -> {
            try {
                current.truncate();
            } catch (IOException | RuntimeException e) {
                disableWal("unable to truncate " + current.path(), e);
            }
        });
    }

    private static void disableWal(String what, Throwable cause) {
        if (walDisabled) {
            return;
        }
        walDisabled = true;
        LOGGER.error(
                "Disabling the ECO cell storage WAL ({}). Cells will be written out in full every tick instead, which"
                        + " is slower but keeps their contents safe",
                what,
                cause);
    }

    private static void quarantineWal(Path walPath) {
        for (int i = 0; i < 1000; i++) {
            Path target = walPath.resolveSibling(walPath.getFileName() + (i == 0 ? ".corrupt" : ".corrupt." + i));
            if (Files.exists(target)) {
                continue;
            }
            try {
                Files.move(walPath, target);
                LOGGER.error("Preserved the damaged ECO cell storage WAL as {}", target);
            } catch (IOException e) {
                LOGGER.error("Unable to preserve the damaged ECO cell storage WAL {}", walPath, e);
            }
            return;
        }
    }

    @Nullable private static ECOStorageWal wal() {
        if (wal == null && !walDisabled) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                wal = new ECOStorageWal(storageRoot(server).resolve(WAL_FILE), MAX_WAL_RECORD_BYTES, WAL_BUFFER_BYTES);
            }
        }
        return wal;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Checkpoints
    // ---------------------------------------------------------------------------------------------------------

    /** Works the checkpoint queue round-robin: the head is always retried at the tail, so nothing starves. */
    private static boolean processCheckpoints(long maxNanos) {
        if (CHECKPOINT_QUEUE.isEmpty()) {
            return false;
        }
        long deadline = maxNanos <= 0L ? Long.MAX_VALUE : System.nanoTime() + maxNanos;
        boolean work = false;
        for (int remaining = CHECKPOINT_QUEUE.size(); remaining > 0; remaining--) {
            Iterator<FileBackedECOStorageBackend> iterator = CHECKPOINT_QUEUE.iterator();
            FileBackedECOStorageBackend backend = iterator.next();
            iterator.remove();
            boolean settled = backend.completeCheckpointWrite(false);
            if (settled && backend.isCheckpointDirty()) {
                backend.scheduleCheckpoint();
                settled = false;
                work = true;
            }
            if (!settled || backend.isCheckpointDirty() || backend.hasPendingCheckpointWrite()) {
                CHECKPOINT_QUEUE.add(backend);
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        return work;
    }

    private static void checkpointAllNow() {
        for (FileBackedECOStorageBackend backend : CELLS.values()) {
            backend.checkpointNow();
        }
        CHECKPOINT_QUEUE.clear();
    }

    private static void recoverCell(MinecraftServer server, UUID id, List<ECOCellWalRecord> records) {
        FileBackedECOStorageBackend backend = new FileBackedECOStorageBackend(id, cellPath(server, id), 0, 0L);
        backend.requestLoad();
        backend.loadBudgeted(0L);
        if (backend.isDegraded()) {
            LOGGER.error("Unable to recover ECO cell storage {}: its file could not be read", id);
            return;
        }
        int applied = 0;
        for (ECOCellWalRecord record : records) {
            if (backend.applyRecoveredRecord(record.key(), record.delta(), record.revision())) {
                applied++;
            }
        }
        if (applied <= 0) {
            // Every logged mutation was already in the snapshot.
            return;
        }
        backend.checkpointNow();
        if (backend.isDegraded()) {
            LOGGER.error("Unable to write the recovered contents of ECO cell storage {}", id);
            return;
        }
        LOGGER.info("Recovered {} of {} logged mutations for ECO cell storage {}", applied, records.size(), id);
    }

    // ---------------------------------------------------------------------------------------------------------

    private static void closeBackend(UUID id) {
        FileBackedECOStorageBackend backend = CELLS.remove(id);
        if (backend != null) {
            backend.closeAndFlush();
            synchronized (DIRTY_LOCK) {
                WAL_DIRTY.remove(backend);
            }
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
        FileBackedECOStorageBackend sourceBackend = CELLS.get(oldId);
        if (sourceBackend != null) {
            // The copy is taken from the file, so everything still sitting in the log has to reach it first.
            flushWalNow();
            sourceBackend.checkpointNow();
            CHECKPOINT_QUEUE.remove(sourceBackend);
        }

        UUID newId;
        Path newPath;
        do {
            newId = UUID.randomUUID();
            newPath = cellPath(server, newId);
        } while (CELLS.containsKey(newId) || Files.exists(newPath));

        copyCellFile(cellPath(server, oldId), newPath, newId, stack);

        ECOCellHandle.setId(stack, newId);
        ECOCellHandle.clearProblemState(stack);
        LOGGER.warn("Forked duplicated ECO storage matrix UUID {} -> {} ({})", oldId, newId, reason);
        return newId;
    }

    private static void copyCellFile(Path sourcePath, Path targetPath, UUID newId, ItemStack stack) {
        Path source = FileBackedECOStorageBackend.cellFile(sourcePath);
        Path target = FileBackedECOStorageBackend.cellFile(targetPath);
        byte[] payload = null;
        if (Files.isRegularFile(source)) {
            try {
                payload = Files.readAllBytes(source);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to fork ECO cell storage " + source, e);
            }
        }

        byte[] rewritten = payload == null ? null : rewriteCellId(payload, newId);
        if (rewritten == null && payload != null) {
            LOGGER.warn("Copying ECO cell storage {} verbatim; its header could not be rewritten", source);
        }
        if (rewritten == null && payload == null) {
            rewritten = emptyCellPayload(newId, stack);
        }

        try {
            ECOStorageFiles.writeAtomically(target, rewritten == null ? payload : rewritten);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to fork ECO cell storage " + source + " -> " + target, e);
        }
    }

    @Nullable private static byte[] rewriteCellId(byte[] payload, UUID newId) {
        try (InputStream input = new ByteArrayInputStream(payload)) {
            CompoundTag tag = NbtIo.readCompressed(input);
            tag.putUUID("id", newId);
            ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length);
            NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] emptyCellPayload(UUID newId, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 3);
        tag.putString("kind", "cell");
        tag.putUUID("id", newId);
        tag.putLong("revision", 0L);
        tag.putInt("storedTypes", ECOCellHandle.getStoredTypesSummary(stack));
        tag.putString("storedAmount", Long.toString(ECOCellHandle.getStoredAmountSummary(stack)));
        tag.put("entries", new ListTag());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(tag, out);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create the forked ECO cell storage file", e);
        }
        return out.toByteArray();
    }

    private static long estimateUsedBytes(IBasicECOCellItem cellItem, ECOStorageBackend backend) {
        long typeBytes = (long) backend.getStoredTypes() * cellItem.getBytesPerType();
        long amountBytes = backend.getStoredAmount().toLongSaturated()
                / Math.max(1, cellItem.getKeyType().getAmountPerByte());
        return Math.max(0L, typeBytes + amountBytes);
    }

    private static Path storageRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("neoecoae")
                .resolve("storage_v2");
    }

    private static Path cellPath(MinecraftServer server, UUID id) {
        return storageRoot(server).resolve("cells").resolve(id.toString());
    }
}
