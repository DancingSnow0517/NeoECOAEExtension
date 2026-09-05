package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main-thread inventory authority for one infinite domain. DimensionDataStorage owns the format marker and save
 * barrier; independent journals own durable contents, with background compression and versioned completion.
 */
public final class ECOInfiniteStorageData extends SavedData {
    interface KeyCodec {
        CompoundTag encode(AEKey key);
        AEKey decode(CompoundTag tag);
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInfiniteStorageData.class);

    public static final int CURRENT_VERSION = 2;
    private static final int FLUSH_KEYS = 4096;
    private static final long FLUSH_BYTES = 8L * 1024 * 1024;

    private static final String TAG_VERSION = "version";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_KEY = "key";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_MIGRATIONS = "migrations";
    private static final String TAG_LEGACY_IMPORTED = "legacyImported";

    /**
     * A stored entry that could not be fully parsed on load, usually because the mod that contributed it was
     * removed, or because its amount tag was corrupted. The whole original entry tag is kept verbatim and written
     * back unchanged, so re-adding the mod (or otherwise fixing the underlying cause) resolves it again on the next
     * load without any separate repair step.
     */
    public record RawEntry(CompoundTag entryTag) {}

    /**
     * Fine-grained replacement for a single {@code isHealthy()} flag. Worse states are strictly more restrictive:
     * {@code UNAVAILABLE} is the only state that may refuse reads outright; a domain that merely cannot currently be
     * written to, or that carries unresolved entries, keeps serving its trusted in-memory data.
     */
    public enum DomainStatus {
        /** Nothing unusual: normal reads, writes, and migration are all allowed. */
        HEALTHY,
        /** Partial data, codec, receipt, or checkpoint failure; unaffected keys remain available. */
        DEGRADED,
        /** Trusted in-memory data, but the last save attempt failed; writes are refused until it succeeds again. */
        RECOVERY_READ_ONLY,
        /** No trustworthy data view could be constructed at all; the domain stays locked until repaired. */
        UNAVAILABLE
    }

    private final Map<AEKey, HugeAmount> amounts = new HashMap<>();
    private final List<RawEntry> rawEntries = new ArrayList<>();
    private final Set<UUID> migrationReceipts = new LinkedHashSet<>();
    private final ListTag unresolvedReceipts = new ListTag();
    private final boolean loadedFromDisk;
    private boolean firstLookupPending = true;
    private boolean legacyImported;
    private boolean unreadable;
    private boolean requiresJournal;
    private boolean unsupportedVersion;
    private boolean writeUnsafe;
    private String lastFailureReason;
    private UUID domainId;
    private long revision;
    private long durableRevision;
    private InfiniteStorageJournal journal;
    private HolderLookup.Provider journalRegistries;
    private KeyCodec keyCodec;
    private final Map<AEKey, CompoundTag> encodedKeys = new HashMap<>();
    private final Set<AEKey> dirtyKeys = new LinkedHashSet<>();
    private final Map<UUID, AEKey> pendingReceipts = new HashMap<>();
    private final Map<AEKey, String> encodingFailures = new java.util.LinkedHashMap<>();
    private long nextFlushTick;
    private java.util.concurrent.CompletableFuture<boolean[]> pendingWrite;
    private Map<AEKey, HugeAmount> pendingAmounts = Map.of();
    private Map<UUID, AEKey> inFlightReceipts = Map.of();
    private long pendingRevision;
    private final Map<AEKey, UUID> restoreReservations = new HashMap<>();
    private final Map<AEKey, Set<UUID>> restoreTargets = new HashMap<>();
    private final Map<AEKey, String> restoreFailures = new HashMap<>();
    private final Map<AEKey, Long> keyVersions = new HashMap<>();
    private Map<AEKey, Long> pendingVersions = Map.of();

    private ECOInfiniteStorageData(boolean loadedFromDisk) {
        this.loadedFromDisk = loadedFromDisk;
    }

    public static ECOInfiniteStorageData createNew() {
        return new ECOInfiniteStorageData(false);
    }

    public static ECOInfiniteStorageData load(CompoundTag tag, HolderLookup.Provider registries) {
        return load(tag, registries, null);
    }

    private static ECOInfiniteStorageData load(CompoundTag tag, HolderLookup.Provider registries, KeyCodec codec) {
        ECOInfiniteStorageData data = new ECOInfiniteStorageData(true);
        int version = tag.getInt(TAG_VERSION);
        if (version != 1 && version != CURRENT_VERSION) {
            // Never throw here. DimensionDataStorage swallows load failures and hands back a fresh empty instance,
            // which would look exactly like "this domain is empty" and let the host unmount every member cell.
            LOGGER.error("Unsupported ECO infinite storage data version {}; refusing to use this domain", version);
            data.unreadable = true;
            data.unsupportedVersion = version > CURRENT_VERSION;
            return data;
        }
        data.legacyImported = tag.getBoolean(TAG_LEGACY_IMPORTED);

        if (tag.getBoolean("journal")) {
            data.requiresJournal = true;
            data.unreadable = true;
            return data;
        }
        if (!tag.contains(TAG_ENTRIES, Tag.TAG_LIST)
            || (!(tag.get(TAG_ENTRIES) instanceof ListTag entryList) || (!entryList.isEmpty() && entryList.getElementType() != Tag.TAG_COMPOUND))) {
            data.unreadable = true;
            return data;
        }
        ListTag entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        int unresolved = 0;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            try {
                if (!entry.contains(TAG_AMOUNT, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Missing amount");
                CompoundTag amountTag = entry.getCompound(TAG_AMOUNT);
                if (amountTag.contains("big") ? !amountTag.contains("big", Tag.TAG_STRING)
                    : !amountTag.contains("long", Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid amount representation");
                if (entry.contains("restore") && !entry.hasUUID("restore")) throw new IllegalArgumentException("Invalid restore reservation");
                CompoundTag keyTag = entry.getCompound(TAG_KEY);
                HugeAmount amount = HugeAmount.read(entry.getCompound(TAG_AMOUNT));
                Set<UUID> targets = new LinkedHashSet<>();
                if (entry.contains("restoreTargets")) {
                    if (!(entry.get("restoreTargets") instanceof ListTag list)
                        || (!list.isEmpty() && list.getElementType() != Tag.TAG_INT_ARRAY)) {
                        throw new IllegalArgumentException("Invalid restore target manifest");
                    }
                    for (Tag target : entry.getList("restoreTargets", Tag.TAG_INT_ARRAY)) targets.add(NbtUtils.loadUUID(target));
                }
                if (amount.isZero()) {
                    continue;
                }
                AEKey key = codec == null ? AEKey.fromTagGeneric(registries, keyTag) : codec.decode(keyTag);
                if (key == null) {
                    data.rawEntries.add(new RawEntry(entry.copy()));
                    unresolved++;
                } else {
                    data.amounts.merge(key, amount, HugeAmount::add);
                    data.encodedKeys.put(key, keyTag.copy());
                    if (entry.hasUUID("restore")) data.restoreReservations.put(key, entry.getUUID("restore"));
                    if (!targets.isEmpty()) data.restoreTargets.put(key, Set.copyOf(targets));
                    if (entry.contains("restoreFailure")) data.restoreFailures.put(key, entry.getString("restoreFailure"));
                }
            } catch (Exception e) {
                // A single corrupted entry (e.g. an unparseable huge-amount value) must not take the rest of the
                // domain down with it. Keep it verbatim; it re-parses on the next load if whatever broke it is fixed.
                LOGGER.warn("ECO infinite storage entry {} could not be parsed and is kept unchanged", i, e);
                data.rawEntries.add(new RawEntry(entry.copy()));
                unresolved++;
            }
        }
        if (unresolved > 0) {
            LOGGER.warn(
                "{} ECO infinite storage entries could not be resolved and are kept unchanged; the domain cannot be"
                    + " converted back to normal storage until the missing content is available again",
                unresolved
            );
        }

        for (Tag migration : tag.getList(TAG_MIGRATIONS, Tag.TAG_INT_ARRAY)) {
            try {
                data.migrationReceipts.add(NbtUtils.loadUUID(migration));
            } catch (RuntimeException e) {
                data.unresolvedReceipts.add(migration.copy());
                LOGGER.error("Invalid ECO migration receipt; normal inventory remains available but migration is blocked", e);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, CURRENT_VERSION);
        tag.putBoolean(TAG_LEGACY_IMPORTED, legacyImported);

        ListTag entries = new ListTag();
        for (Map.Entry<AEKey, HugeAmount> entry : amounts.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put(TAG_KEY, encodedKeys.computeIfAbsent(entry.getKey(), key -> encodeKey(key, registries)));
            entryTag.put(TAG_AMOUNT, entry.getValue().write());
            UUID restore = restoreReservations.get(entry.getKey());
            if (restore != null) entryTag.putUUID("restore", restore);
            writeRestoreTargets(entry.getKey(), entryTag);
            if (restoreFailures.containsKey(entry.getKey())) entryTag.putString("restoreFailure", restoreFailures.get(entry.getKey()));
            entries.add(entryTag);
        }
        for (RawEntry raw : rawEntries) {
            entries.add(raw.entryTag().copy());
        }
        tag.put(TAG_ENTRIES, entries);

        ListTag migrations = new ListTag();
        for (UUID transactionId : migrationReceipts) {
            migrations.add(NbtUtils.createUUID(transactionId));
        }
        migrations.addAll(unresolvedReceipts);
        tag.put(TAG_MIGRATIONS, migrations);
        return tag;
    }

    @Override
    public void save(File file, HolderLookup.Provider registries) {
        if (journal != null) {
            flushJournal();
            return;
        }
        try {
            writeIfDirty(file, registries);
            markWriteRecovered();
        } catch (Exception e) {
            // Not just IOException: a broken key-type codec or other serialization bug must not escape this call
            // and blow up whatever AE2/network operation ultimately triggered it (Errors like OutOfMemoryError still
            // propagate, since those are not merely "this save failed").
            LOGGER.error("Could not save ECO infinite storage data {}", file, e);
            markWriteFailed(e.getMessage());
        }
    }

    /**
     * Mirrors AE2's {@code AESavedData}: write to a temporary file and move it into place so a crash while saving
     * cannot leave a half-written domain behind. Unlike {@link #save(File, HolderLookup.Provider)} this reports a
     * failed write, which the legacy import needs before it may retire the files it read.
     */
    public void writeIfDirty(File file, HolderLookup.Provider registries) throws IOException {
        if (unreadable) {
            throw new IOException("Domain data is unreadable; refusing to overwrite it");
        }
        if (!isDirty()) {
            return;
        }
        var targetPath = file.toPath().toAbsolutePath();
        var tempFile = targetPath.getParent().resolve(file.getName() + ".temp");

        CompoundTag compoundTag = new CompoundTag();
        compoundTag.put("data", save(new CompoundTag(), registries));
        NbtUtils.addCurrentDataVersion(compoundTag);
        NbtIo.writeCompressed(compoundTag, tempFile);
        try (var channel = java.nio.channels.FileChannel.open(tempFile, java.nio.file.StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        setDirty(false);
        durableRevision = revision;
    }

    @Override
    public void setDirty() {
        revision++;
        super.setDirty();
    }

    public long revision() { return revision; }

    public long durableRevision() { return durableRevision; }

    public String lastFailureReason() { return lastFailureReason; }

    public HugeAmount getAmount(AEKey key) {
        HugeAmount amount = amounts.get(key);
        return amount == null ? HugeAmount.ZERO : amount;
    }

    public Map<AEKey, HugeAmount> getAmounts() {
        return amounts;
    }

    public void setAmount(AEKey key, HugeAmount amount) {
        if (amount == null || amount.isZero()) {
            amounts.remove(key);
        } else {
            amounts.put(key, amount);
        }
        dirtyKeys.add(key);
        setDirty();
        if (journal != null) keyVersions.put(key, revision);
    }

    public void addRawEntry(RawEntry entry) {
        rawEntries.add(entry);
        setDirty();
    }

    public boolean isEmpty() {
        return amounts.isEmpty() && rawEntries.isEmpty() && (journal == null || journal.allReadable());
    }

    public boolean hasRawEntries() {
        return !rawEntries.isEmpty();
    }

    public int rawEntryCount() {
        return rawEntries.size();
    }

    public boolean hasMigrationReceipt(UUID transactionId) {
        return migrationReceipts.contains(transactionId);
    }

    public void addMigrationReceipt(UUID transactionId) {
        if (migrationReceipts.add(transactionId)) {
            setDirty();
        }
    }

    public void addMigrationReceipt(UUID transactionId, AEKey key) {
        if (!migrationReceipts.contains(transactionId)) {
            addMigrationReceipt(transactionId);
            pendingReceipts.put(transactionId, key);
        }
    }

    public void clearMigrationReceipts() {
        // Receipts are ownership tombstones while a journal exists. A stale chunk must never re-import its copy.
        if (journal != null) return;
        if (!migrationReceipts.isEmpty()) {
            migrationReceipts.clear();
            setDirty();
        }
    }

    public boolean isLegacyImported() {
        return legacyImported;
    }

    public void markLegacyImported() {
        legacyImported = true;
        setDirty();
    }

    /**
     * The domain's current fine-grained state; see {@link DomainStatus}.
     */
    public DomainStatus status() {
        if (unreadable) {
            return DomainStatus.UNAVAILABLE;
        }
        if (writeUnsafe) {
            return DomainStatus.RECOVERY_READ_ONLY;
        }
        if (!rawEntries.isEmpty() || !unresolvedReceipts.isEmpty() || !restoreFailures.isEmpty()) {
            return DomainStatus.DEGRADED;
        }
        if (journal != null && (journal.degraded() || !encodingFailures.isEmpty())) return DomainStatus.DEGRADED;
        return DomainStatus.HEALTHY;
    }

    /**
     * {@code true} unless the domain is {@link DomainStatus#UNAVAILABLE}: reads and UI browsing stay available in
     * every other state, since they only need the trusted in-memory data, never the disk file itself.
     */
    public boolean canRead() {
        return !unreadable;
    }

    /**
     * {@code true} only in {@link DomainStatus#HEALTHY} or {@link DomainStatus#DEGRADED}: whether new writes may be
     * accepted, i.e. whether the last save attempt is known to have succeeded (or none has been needed yet).
     */
    public boolean canWrite() {
        return !unreadable && !writeUnsafe;
    }

    /**
     * Stricter than {@link #canWrite()}: migrating out of the domain or leaving infinite mode additionally requires
     * every entry to be a fully resolved {@link AEKey}, since raw entries are never visible to {@code getAvailableStacks}
     * and would otherwise be silently left behind.
     */
    public boolean canExitOrRestore() {
        return canWrite() && rawEntries.isEmpty() && unresolvedReceipts.isEmpty() && (journal == null || journal.allReadable());
    }

    public boolean canMigrate() { return unresolvedReceipts.isEmpty() && (journal == null || journal.allReadable()); }

    public boolean canWrite(AEKey key) {
        if (restoreReservations.containsKey(key)) return false;
        if (encodingFailures.containsKey(key)) return false;
        if (!canWrite()) return false;
        if (journal == null) return true;
        if (dirtyKeys.size() >= cn.dancingsnow.neoecoae.config.NEConfig.infiniteStorageMaxDirtyKeys && !dirtyKeys.contains(key)) return false;
        CompoundTag encoded = encodedKeys.get(key);
        if (encoded == null) {
            try {
                encoded = encodeKey(key, journalRegistries);
                encodedKeys.put(key, encoded);
                encodingFailures.remove(key);
            } catch (RuntimeException e) {
                if (!encodingFailures.containsKey(key)) {
                    if (encodingFailures.size() >= 128) encodingFailures.remove(encodingFailures.keySet().iterator().next());
                    encodingFailures.put(key, e.toString());
                    LOGGER.error("ECO domain {} rejected an unencodable key; other keys remain available", domainId, e);
                }
                return false;
            }
        }
        return journal.writable(InfiniteStorageJournal.shard(encoded));
    }

    public boolean canRead(AEKey key) {
        if (restoreReservations.containsKey(key)) return false;
        if (!canRead()) return false;
        CompoundTag encoded = encodedKeys.get(key);
        return journal == null || encoded == null || journal.readable(InfiniteStorageJournal.shard(encoded));
    }

    public boolean hasFilteredKeys() {
        return !restoreReservations.isEmpty() || (journal != null && !journal.allReadable());
    }

    public int pendingKeyCount() { return dirtyKeys.size(); }

    public List<String> failures() {
        List<String> failures = new ArrayList<>();
        if (lastFailureReason != null) failures.add(lastFailureReason);
        if (journal != null) failures.addAll(journal.failures());
        if (!rawEntries.isEmpty()) failures.add("unresolved entries: " + rawEntries.size());
        if (!unresolvedReceipts.isEmpty()) failures.add("unresolved migration receipts: " + unresolvedReceipts.size());
        if (!encodingFailures.isEmpty()) failures.add("unencodable keys: " + encodingFailures.size());
        failures.addAll(restoreFailures.values());
        return List.copyOf(failures);
    }

    public void openJournal(java.nio.file.Path dataFile, HolderLookup.Provider registries) throws IOException {
        openJournal(dataFile, registries, null);
    }

    void openJournal(java.nio.file.Path dataFile, HolderLookup.Provider registries, KeyCodec codec) throws IOException {
        if (unsupportedVersion) throw new IOException("Domain was written by a newer storage format");
        keyCodec = codec;
        java.nio.file.Path directory = dataFile.resolveSibling(dataFile.getFileName() + ".store");
        InfiniteStorageJournal candidate = new InfiniteStorageJournal(directory);
        if (!candidate.exists()) {
            if (Files.exists(directory)) throw new IOException("Storage journal has no initialization marker; refusing to replace it");
            if (unreadable) throw new IOException("Cannot initialize journal from unreadable data");
            CompoundTag baseline = save(new CompoundTag(), registries);
            // Raw entries are kept separately, including duplicates and entries with no valid key.
            ListTag valid = baseline.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < rawEntries.size(); i++) valid.remove(valid.size() - 1);
            baseline.put(TAG_ENTRIES, valid);
            ListTag raw = new ListTag();
            for (RawEntry entry : rawEntries) raw.add(entry.entryTag().copy());
            baseline.put("rawEntries", raw);
            candidate.initialize(baseline);
        } else {
            ECOInfiniteStorageData recovered = load(candidate.load(), registries, codec);
            amounts.clear();
            amounts.putAll(recovered.amounts);
            encodedKeys.clear();
            encodedKeys.putAll(recovered.encodedKeys);
            rawEntries.clear();
            rawEntries.addAll(recovered.rawEntries);
            migrationReceipts.clear();
            migrationReceipts.addAll(recovered.migrationReceipts);
            unresolvedReceipts.clear();
            unresolvedReceipts.addAll(recovered.unresolvedReceipts);
            restoreReservations.clear();
            restoreReservations.putAll(recovered.restoreReservations);
            restoreTargets.clear();
            restoreTargets.putAll(recovered.restoreTargets);
            restoreFailures.clear();
            restoreFailures.putAll(recovered.restoreFailures);
            unreadable = recovered.unreadable;
        }
        journal = candidate;
        journalRegistries = registries;
        dirtyKeys.clear();
        pendingReceipts.clear();
        durableRevision = revision;
        setDirty(false);
        writeJournalMarker(dataFile);
    }

    private void writeJournalMarker(java.nio.file.Path file) throws IOException {
        java.nio.file.Path legacy = file.resolveSibling(file.getFileName() + ".legacy");
        if (!requiresJournal && Files.isRegularFile(file) && !Files.exists(legacy)) Files.copy(file, legacy);
        CompoundTag marker = new CompoundTag();
        marker.putInt(TAG_VERSION, CURRENT_VERSION);
        marker.putBoolean("journal", true);
        CompoundTag root = new CompoundTag();
        root.put("data", marker);
        NbtUtils.addCurrentDataVersion(root);
        java.nio.file.Path temp = file.resolveSibling(file.getFileName() + ".marker.temp");
        NbtIo.writeCompressed(root, temp);
        try (var channel = java.nio.channels.FileChannel.open(temp, java.nio.file.StandardOpenOption.WRITE)) { channel.force(true); }
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        requiresJournal = true;
    }

    private CompoundTag encodeKey(AEKey key, HolderLookup.Provider registries) {
        CompoundTag encoded = (keyCodec == null ? key.toTagGeneric(registries) : keyCodec.encode(key)).copy();
        if (encoded.sizeInBytes() > 4 * 1024 * 1024) throw new IllegalArgumentException("Storage key exceeds the 4 MiB encoding limit");
        return encoded;
    }

    private void flushJournal() {
        if (pendingWrite != null) {
            try { pendingWrite.join(); }
            catch (java.util.concurrent.CompletionException ignored) { /* Recorded by completion handling. */ }
            finishPendingWrite();
        }
        Set<Integer> failed = new java.util.HashSet<>();
        while (true) {
            FlushBatch batch = prepareBatch(failed);
            if (batch.batches().isEmpty()) break;
            for (var shard : batch.batches().entrySet()) {
                try {
                    journal.append(shard.getKey(), shard.getValue());
                } catch (IOException | RuntimeException e) {
                    failed.add(shard.getKey());
                    String reason = "shard " + shard.getKey() + ": " + e;
                    if (!reason.equals(lastFailureReason)) LOGGER.error("ECO domain {} save failed: {}", domainId, reason);
                    lastFailureReason = reason;
                }
            }
            batch.amounts().keySet().removeIf(key -> failed.contains(InfiniteStorageJournal.shard(encodedKeys.get(key))));
            dirtyKeys.removeAll(batch.amounts().keySet());
            batch.receipts().forEach((id, key) -> { if (batch.amounts().containsKey(key)) pendingReceipts.remove(id); });
            batch.amounts().keySet().forEach(this::forgetEmptyKey);
        }
        if (dirtyKeys.isEmpty() && pendingReceipts.isEmpty()) {
            durableRevision = revision;
            lastFailureReason = null;
            setDirty(false);
        }
    }

    public void tick(long tick) {
        finishPendingWrite();
        if (journal != null && isDirty() && tick >= nextFlushTick) {
            nextFlushTick = tick + cn.dancingsnow.neoecoae.config.NEConfig.infiniteStorageFlushIntervalTicks;
            try { startPendingWrite(); }
            catch (RuntimeException e) {
                String reason = "Journal preparation failed: " + e;
                if (!reason.equals(lastFailureReason)) LOGGER.error("ECO domain {}: {}", domainId, reason, e);
                lastFailureReason = reason;
            }
        }
    }

    private record FlushBatch(Map<Integer, CompoundTag> batches, Map<AEKey, HugeAmount> amounts,
                              Map<UUID, AEKey> receipts) {}

    private FlushBatch prepareBatch(Set<Integer> excludedShards) {
        Map<Integer, CompoundTag> batches = new HashMap<>();
        Map<AEKey, HugeAmount> frozen = new HashMap<>();
        Map<UUID, AEKey> receipts = new HashMap<>();
        Map<AEKey, List<UUID>> receiptsByKey = new HashMap<>();
        pendingReceipts.forEach((id, key) -> receiptsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(id));
        long bytes = 0L;
        for (AEKey key : dirtyKeys) {
            CompoundTag encoded = encodedKeys.get(key);
            if (encoded == null || encodingFailures.containsKey(key)) continue;
            int shard = InfiniteStorageJournal.shard(encoded);
            if (excludedShards.contains(shard) || !journal.readable(shard)) continue;
            CompoundTag entry = new CompoundTag();
            HugeAmount amount = getAmount(key);
            entry.put(TAG_KEY, encoded);
            entry.put(TAG_AMOUNT, amount.write());
            UUID restore = restoreReservations.get(key);
            if (restore != null) entry.putUUID("restore", restore);
            writeRestoreTargets(key, entry);
            if (restoreFailures.containsKey(key)) entry.putString("restoreFailure", restoreFailures.get(key));
            List<UUID> keyReceipts = receiptsByKey.getOrDefault(key, List.of());
            // Twice the NBT heap estimate also covers modified UTF-8 strings. One key and all of its
            // receipts are indivisible, so replay can never acknowledge a migration without its quantity.
            long weight = 2L * entry.sizeInBytes() + 64L * keyReceipts.size() + 256L;
            if (weight > 32L * 1024 * 1024) {
                encodingFailures.put(key, "Storage entry and receipts exceed the journal batch limit");
                continue;
            }
            if (!frozen.isEmpty() && (frozen.size() >= FLUSH_KEYS || bytes + weight > FLUSH_BYTES)) break;
            CompoundTag batch = batches.computeIfAbsent(shard, ignored -> {
                CompoundTag tag = new CompoundTag();
                tag.put(TAG_ENTRIES, new ListTag());
                tag.put(TAG_MIGRATIONS, new ListTag());
                return tag;
            });
            batch.getList(TAG_ENTRIES, Tag.TAG_COMPOUND).add(entry);
            for (UUID id : keyReceipts) {
                batch.getList(TAG_MIGRATIONS, Tag.TAG_INT_ARRAY).add(NbtUtils.createUUID(id));
                receipts.put(id, key);
            }
            frozen.put(key, amount);
            bytes += weight;
        }
        return new FlushBatch(batches, frozen, receipts);
    }

    private void startPendingWrite() {
        if (pendingWrite != null || unreadable) return;
        FlushBatch batch = prepareBatch(Set.of());
        if (batch.batches().isEmpty()) return;
        try {
            pendingWrite = journal.appendAsync(batch.batches());
            pendingAmounts = batch.amounts();
            Map<AEKey, Long> versions = new HashMap<>();
            batch.amounts().keySet().forEach(key -> versions.put(key, keyVersions.getOrDefault(key, 0L)));
            pendingVersions = versions;
            inFlightReceipts = batch.receipts();
            pendingRevision = revision;
            if (dirtyKeys.size() > pendingAmounts.size()) nextFlushTick = 0L;
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Bounded executor backpressure: keep dirty state and retry on a later tick.
        }
    }

    private void finishPendingWrite() {
        if (pendingWrite == null || !pendingWrite.isDone()) return;
        boolean[] saved;
        try { saved = pendingWrite.join(); }
        catch (java.util.concurrent.CompletionException e) {
            lastFailureReason = e.toString();
            pendingWrite = null;
            return;
        }
        pendingAmounts.forEach((key, amount) -> {
            if (saved[InfiniteStorageJournal.shard(encodedKeys.get(key))]
                && java.util.Objects.equals(keyVersions.getOrDefault(key, 0L), pendingVersions.get(key))) dirtyKeys.remove(key);
        });
        inFlightReceipts.forEach((id, key) -> {
            if (saved[InfiniteStorageJournal.shard(encodedKeys.get(key))]) pendingReceipts.remove(id);
        });
        pendingAmounts.keySet().forEach(this::forgetEmptyKey);
        if (dirtyKeys.isEmpty() && pendingReceipts.isEmpty() && revision == pendingRevision) {
            durableRevision = pendingRevision;
            setDirty(false);
        }
        pendingWrite = null;
        pendingAmounts = Map.of();
        pendingVersions = Map.of();
        inFlightReceipts = Map.of();
    }

    public void closeJournal() {
        if (journal != null) { flushJournal(); journal.close(); }
    }

    private void forgetEmptyKey(AEKey key) {
        if (!amounts.containsKey(key) && !dirtyKeys.contains(key) && !restoreReservations.containsKey(key)) {
            encodedKeys.remove(key);
            keyVersions.remove(key);
            encodingFailures.remove(key);
        }
    }

    public boolean reserveRestore(AEKey key, UUID transaction) {
        return reserveRestore(key, transaction, Set.of());
    }

    public boolean reserveRestore(AEKey key, UUID transaction, Set<UUID> targets) {
        if (restoreFailures.containsKey(key)) return false;
        UUID existing = restoreReservations.get(key);
        if (existing != null) return existing.equals(transaction) && targets.containsAll(restoreTargetIds(key));
        if (!canWrite(key)) return false;
        restoreReservations.put(key, transaction);
        restoreTargets.put(key, Set.copyOf(targets));
        dirtyKeys.add(key);
        setDirty();
        keyVersions.put(key, revision);
        return true;
    }

    public UUID restoreTransaction(AEKey key) { return restoreReservations.get(key); }

    public Set<UUID> restoreTargetIds(AEKey key) { return restoreTargets.getOrDefault(key, Set.of()); }

    private void writeRestoreTargets(AEKey key, CompoundTag entry) {
        Set<UUID> targets = restoreTargetIds(key);
        if (targets.isEmpty()) return;
        ListTag list = new ListTag();
        targets.forEach(id -> list.add(NbtUtils.createUUID(id)));
        entry.put("restoreTargets", list);
    }

    public boolean hasPendingRestore() { return !restoreReservations.isEmpty(); }

    public void failRestore(AEKey key, String reason) {
        restoreFailures.put(key, reason);
        dirtyKeys.add(key);
        setDirty();
        keyVersions.put(key, revision);
    }

    public boolean finishRestore(AEKey key, UUID transaction) {
        if (!transaction.equals(restoreReservations.get(key))) return false;
        restoreReservations.remove(key);
        restoreTargets.remove(key);
        setAmount(key, HugeAmount.ZERO);
        return true;
    }

    /**
     * {@code false} once this domain is known to be unusable. Kept as an alias of {@link #canWrite()} for existing
     * call sites: it already only ever gated writes, never reads.
     */
    public boolean isHealthy() {
        return canWrite();
    }

    public void markUnreadable() {
        unreadable = true;
    }

    /**
     * Associates this instance with the domain UUID it belongs to, purely so subsequent status-transition log lines
     * can identify which domain they refer to. Idempotent; safe to call every time the owning engine is looked up.
     */
    public void bindDomainId(UUID domainId) {
        this.domainId = domainId;
    }

    /**
     * Records that the most recent save attempt failed, moving the domain into {@link DomainStatus#RECOVERY_READ_ONLY}
     * until a save succeeds again. In-memory data and reads are unaffected; only new writes are refused.
     */
    public void markWriteFailed(String reason) {
        lastFailureReason = reason;
        if (!writeUnsafe) {
            writeUnsafe = true;
            LOGGER.error(
                "ECO infinite storage domain {} can no longer be written safely: {}; it stays read-only until a save"
                    + " succeeds again",
                domainId,
                reason
            );
        }
    }

    /**
     * Records that a save succeeded, clearing {@link DomainStatus#RECOVERY_READ_ONLY} if it was set.
     */
    public void markWriteRecovered() {
        if (writeUnsafe) {
            writeUnsafe = false;
            lastFailureReason = null;
            LOGGER.info("ECO infinite storage domain {} can be written to again", domainId);
        }
    }

    /**
     * {@code true} when this instance came from {@link #load}. A {@code false} value for a domain whose file exists on
     * disk means vanilla swallowed a read error and handed back an empty instance.
     */
    public boolean wasLoadedFromDisk() {
        return loadedFromDisk;
    }

    /**
     * {@code true} the first time it is called, {@code false} afterwards. {@code DimensionDataStorage} keeps handing
     * back the same instance, so a check that is only meaningful right after construction — such as comparing
     * {@link #wasLoadedFromDisk()} against the presence of the file — has to be claimed this way.
     */
    public boolean claimFirstLookup() {
        boolean first = firstLookupPending;
        firstLookupPending = false;
        return first;
    }
}
