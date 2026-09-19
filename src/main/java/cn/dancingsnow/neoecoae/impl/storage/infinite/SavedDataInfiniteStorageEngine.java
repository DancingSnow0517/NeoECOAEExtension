package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.ECOSavedDataPersistence;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageKeyHash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minecraft-native V2 infinite storage. One instance is stored in one SavedData file. */
final class SavedDataInfiniteStorageEngine extends SavedData
        implements ECOInfiniteStorageEngine, ECOSavedDataPersistence.Backend {
    private static final Logger LOGGER = LoggerFactory.getLogger(SavedDataInfiniteStorageEngine.class);
    private static final int FORMAT_VERSION = 2;
    private static final String TAG_FORMAT = "format";
    private static final String TAG_DOMAIN = "domain";
    private static final String TAG_REVISION = "revision";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_KEY = "key";
    private static final String TAG_AMOUNT_LONG = "amount_long";
    private static final String TAG_AMOUNT_WIDE = "amount_wide";
    private static final String TAG_RECEIPTS = "transfer_receipts";
    private static final String TAG_RECEIPT_ID = "id";
    private static final String TAG_RECEIPT_DIGEST = "contents_sha256";
    private static final String TAG_LEGACY_FINGERPRINT = "legacy_fingerprint";
    private static final String TAG_ACKNOWLEDGED_ORPHANED_FINGERPRINT = "acknowledged_orphaned_fingerprint";
    private static final ResourceLocation AE2_MISSING_CONTENT =
            ResourceLocation.fromNamespaceAndPath("ae2", "missing_content");
    private static final HugeAmount LONG_MAX_AMOUNT = HugeAmount.of(Long.MAX_VALUE);

    private final UUID domainId;
    private final DimensionDataStorage dataStorage;
    private final Path dataFile;
    private final HybridAmountStore<AEKey> amounts = new HybridAmountStore<>();
    private final Object2ObjectOpenHashMap<AEKey, CompoundTag> encodedKeys = new Object2ObjectOpenHashMap<>();
    private final Map<String, OrphanedStack> orphanedEntries = new HashMap<>();
    private final List<CompoundTag> retainedEntries = new ArrayList<>();
    private final List<CompoundTag> retainedReceipts = new ArrayList<>();
    private final Set<String> blockedFingerprints = new HashSet<>();
    private final List<String> entryFailures = new ArrayList<>();
    private final Set<AEKey> blockedKeys = new HashSet<>();
    private final Object2ObjectOpenHashMap<AEKeyType, MutableTypeStats> typeStats = new Object2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<AEKey> hugeKeys = new ObjectOpenHashSet<>();
    private final Set<UUID> legacyTransferReceipts = new HashSet<>();
    private final Map<UUID, String> transferReceipts = new HashMap<>();
    private final KeyCounter visibleStacks = new KeyCounter();

    private final WideAmount storedAmount = WideAmount.of(0L);
    private HugeAmount storedAmountSnapshot = HugeAmount.ZERO;
    private HugeAmount orphanedAmountSnapshot = HugeAmount.ZERO;
    private long revision;

    @Nullable private String legacyFingerprint;

    @Nullable private String acknowledgedOrphanedFingerprint;

    private ECOInfiniteDomainState state = ECOInfiniteDomainState.READY;

    @Nullable private String failureReason;

    private List<TypeStats> typeStatsSnapshot = List.of();
    private List<HugeStack> hugeStacksSnapshot = List.of();
    private boolean storedAmountSnapshotDirty;
    private boolean typeStatsSnapshotDirty = true;
    private boolean hugeStacksSnapshotDirty = true;

    @Nullable private CompoundTag lastSerializedSnapshot;

    @Nullable private CompletableFuture<Void> pendingSnapshot;

    // Cumulative differences from the last full snapshot, including across delta commits.
    // Returning to the base amount removes the difference, not a previously committed receipt.
    private final Map<AEKey, PendingChange> changedKeys = new HashMap<>();
    private long baseRevision = -1L;

    private record PendingChange(CompoundTag encodedKey, HugeAmount baseAmount) {}

    private SavedDataInfiniteStorageEngine(UUID domainId, DimensionDataStorage dataStorage, Path dataFile) {
        this.domainId = domainId;
        this.dataStorage = dataStorage;
        this.dataFile = dataFile.toAbsolutePath().normalize();
        ECOSavedDataPersistence.register(this);
    }

    static SavedDataInfiniteStorageEngine createNew(UUID domainId, DimensionDataStorage dataStorage, Path dataFile) {
        return new SavedDataInfiniteStorageEngine(domainId, dataStorage, dataFile);
    }

    static SavedDataInfiniteStorageEngine load(
            CompoundTag tag, UUID expectedDomainId, DimensionDataStorage dataStorage, Path dataFile) {
        ParsedData parsed = parse(tag, expectedDomainId);
        SavedDataInfiniteStorageEngine engine =
                new SavedDataInfiniteStorageEngine(expectedDomainId, dataStorage, dataFile);
        engine.amounts.clear();
        parsed.amounts().forEach(engine.amounts::set);
        engine.encodedKeys.putAll(parsed.encodedKeys());
        engine.orphanedEntries.putAll(parsed.orphanedEntries());
        engine.retainedEntries.addAll(parsed.retainedEntries());
        engine.retainedReceipts.addAll(parsed.retainedReceipts());
        for (CompoundTag retained : engine.retainedEntries) {
            if (retained.contains("key", Tag.TAG_COMPOUND)) {
                engine.blockedFingerprints.add(ECOStorageKeyHash.stableFingerprint(retained.getCompound("key")));
            }
            if (retained.contains("isolated_key_fingerprint", Tag.TAG_STRING)) {
                engine.blockedFingerprints.add(retained.getString("isolated_key_fingerprint"));
            }
        }
        engine.entryFailures.addAll(parsed.entryFailures());
        engine.blockedKeys.addAll(parsed.blockedKeys());
        engine.legacyTransferReceipts.addAll(parsed.legacyTransferReceipts());
        engine.transferReceipts.putAll(parsed.transferReceipts());
        engine.revision = parsed.revision();
        engine.legacyFingerprint = parsed.legacyFingerprint();
        engine.acknowledgedOrphanedFingerprint = parsed.acknowledgedOrphanedFingerprint();
        engine.lastSerializedSnapshot = tag.copy();
        engine.rebuildIndexes();
        if (!engine.entryFailures.isEmpty()) {
            LOGGER.warn(
                    "Infinite-storage domain {} retained {} isolated records; healthy resources remain available",
                    expectedDomainId,
                    engine.entryFailures.size());
        }
        return engine;
    }

    synchronized void importLegacy(
            Map<AEKey, HugeAmount> importedAmounts,
            Collection<UUID> importedReceipts,
            String sourceFingerprint,
            long importedRevision) {
        requireReady();
        if (!amounts.isEmpty()
                || !orphanedEntries.isEmpty()
                || !legacyTransferReceipts.isEmpty()
                || !transferReceipts.isEmpty()) {
            throw new IllegalStateException("Cannot import V1 data into a non-empty V2 domain");
        }
        Objects.requireNonNull(importedAmounts, "importedAmounts");
        Objects.requireNonNull(importedReceipts, "importedReceipts");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        if (!isSha256(sourceFingerprint)) {
            throw new IllegalArgumentException("Invalid V1 migration fingerprint");
        }

        for (Map.Entry<AEKey, HugeAmount> entry : importedAmounts.entrySet()) {
            AEKey key = Objects.requireNonNull(entry.getKey(), "legacy key");
            HugeAmount amount = Objects.requireNonNull(entry.getValue(), "legacy amount");
            if (amount.isZero()) {
                throw new IllegalArgumentException("V1 import contains a zero amount");
            }
            if (!ensureEncodedKey(key)) {
                throw new IllegalArgumentException("V1 import contains an AEKey that cannot be encoded");
            }
        }
        for (UUID transactionId : importedReceipts) {
            Objects.requireNonNull(transactionId, "legacy receipt");
        }
        for (Map.Entry<AEKey, HugeAmount> entry : importedAmounts.entrySet()) {
            amounts.set(entry.getKey(), entry.getValue());
        }
        legacyTransferReceipts.addAll(importedReceipts);
        legacyFingerprint = sourceFingerprint;
        revision = Math.max(0L, importedRevision);
        baseRevision = -1L;
        rebuildIndexes();
        setDirty();
    }

    UUID domainId() {
        return domainId;
    }

    @Nullable synchronized String legacyFingerprint() {
        return legacyFingerprint;
    }

    @Override
    public synchronized long insert(AEKey key, long amount, Actionable mode) {
        if (!canOperate(key, amount)) {
            return 0L;
        }
        if (mode == Actionable.SIMULATE) {
            return amount;
        }
        if (!ensureEncodedKey(key)) {
            return 0L;
        }

        HugeAmount previous = amounts.get(key);
        HugeAmount next = amounts.add(key, amount);
        onAmountChanged(key, previous, next, amount, true);
        markMutated();
        return amount;
    }

    @Override
    public synchronized long insertOnce(UUID transactionId, AEKey key, long amount) {
        if (transactionId == null || !canTransfer() || !canOperate(key, amount)) {
            return 0L;
        }
        if (legacyTransferReceipts.contains(transactionId)) {
            return amount;
        }
        if (!ensureEncodedKey(key)) {
            return 0L;
        }
        String digest = transferDigest(List.of(new HugeStack(key, HugeAmount.of(amount))));
        String persistedDigest = transferReceipts.get(transactionId);
        if (persistedDigest != null) {
            if (!persistedDigest.equals(digest)) {
                quarantineReceiptConflict(transactionId);
                return 0L;
            }
            return amount;
        }

        HugeAmount previous = amounts.get(key);
        HugeAmount next = amounts.add(key, amount);
        onAmountChanged(key, previous, next, amount, true);
        transferReceipts.put(transactionId, digest);
        markMutated();
        flushAndAwait();
        return state == ECOInfiniteDomainState.READY ? amount : 0L;
    }

    @Override
    public synchronized boolean applyTransferOnce(UUID transactionId, Collection<HugeStack> contents) {
        if (!canTransfer() || transactionId == null || contents == null) {
            return false;
        }
        if (legacyTransferReceipts.contains(transactionId)) {
            quarantineReceiptConflict(transactionId);
            return false;
        }

        List<HugeStack> batch = new ArrayList<>(contents);
        Set<AEKey> seen = new HashSet<>();
        for (HugeStack stack : batch) {
            if (stack == null
                    || stack.key() == null
                    || stack.amount() == null
                    || stack.amount().isZero()
                    || !seen.add(stack.key())
                    || !ensureEncodedKey(stack.key())) {
                return false;
            }
        }

        String digest = transferDigest(batch);
        String persistedDigest = transferReceipts.get(transactionId);
        if (persistedDigest != null) {
            if (!persistedDigest.equals(digest)) {
                quarantineReceiptConflict(transactionId);
                return false;
            }
            return true;
        }

        for (HugeStack stack : batch) {
            AEKey key = stack.key();
            HugeAmount previous = amounts.get(key);
            HugeAmount next = previous.add(stack.amount());
            amounts.set(key, next);
            onAmountChanged(key, previous, next, stack.amount(), true);
        }
        transferReceipts.put(transactionId, digest);
        markMutated();
        flushAndAwait();
        return state == ECOInfiniteDomainState.READY;
    }

    @Override
    public synchronized boolean hasLegacyTransferReceipt(UUID transactionId) {
        return transactionId != null && legacyTransferReceipts.contains(transactionId);
    }

    @Override
    public synchronized boolean hasTransferReceipt(UUID transactionId) {
        return transactionId != null && transferReceipts.containsKey(transactionId);
    }

    @Override
    public synchronized long extract(AEKey key, long amount, Actionable mode) {
        if (!canOperate(key, amount)) {
            return 0L;
        }
        long available = amounts.getSaturated(key);
        long extracted = Math.min(available, amount);
        if (extracted == 0L || mode == Actionable.SIMULATE) {
            return extracted;
        }

        HugeAmount previous = amounts.get(key);
        long removed = amounts.subtractAtMost(key, extracted);
        HugeAmount next = amounts.get(key);
        onAmountChanged(key, previous, next, removed, false);
        if (next.isZero()) {
            encodedKeys.remove(key);
        }
        markMutated();
        return removed;
    }

    @Override
    public synchronized HugeAmount getAmount(AEKey key) {
        return state == ECOInfiniteDomainState.READY && key != null ? amounts.get(key) : HugeAmount.ZERO;
    }

    @Override
    public synchronized void getAvailableStacks(KeyCounter out) {
        if (state == ECOInfiniteDomainState.READY) {
            out.addAll(visibleStacks);
        }
    }

    @Override
    public synchronized long getRevision() {
        return revision;
    }

    @Override
    public synchronized boolean isEmpty() {
        return state == ECOInfiniteDomainState.READY
                && amounts.isEmpty()
                && orphanedEntries.isEmpty()
                && retainedEntries.isEmpty()
                && retainedReceipts.isEmpty();
    }

    @Override
    public synchronized HugeAmount getStoredAmount() {
        if (state != ECOInfiniteDomainState.READY) {
            return HugeAmount.ZERO;
        }
        if (storedAmountSnapshotDirty) {
            storedAmountSnapshot = snapshot(storedAmount);
            storedAmountSnapshotDirty = false;
        }
        return storedAmountSnapshot.add(orphanedAmountSnapshot);
    }

    @Override
    public synchronized int getStoredTypes() {
        return state == ECOInfiniteDomainState.READY ? amounts.size() : 0;
    }

    @Override
    public synchronized Collection<TypeStats> getTypeStats() {
        if (state != ECOInfiniteDomainState.READY) {
            return List.of();
        }
        if (typeStatsSnapshotDirty) {
            List<TypeStats> snapshot = new ArrayList<>(typeStats.size());
            typeStats.forEach((type, stats) ->
                    snapshot.add(new TypeStats(type, stats.storedTypes, snapshot(stats.storedAmount))));
            typeStatsSnapshot = List.copyOf(snapshot);
            typeStatsSnapshotDirty = false;
        }
        return typeStatsSnapshot;
    }

    @Override
    public synchronized Collection<HugeStack> getHugeStacks() {
        if (state != ECOInfiniteDomainState.READY) {
            return List.of();
        }
        if (hugeStacksSnapshotDirty) {
            List<HugeStack> snapshot = new ArrayList<>(hugeKeys.size());
            for (AEKey key : hugeKeys) {
                snapshot.add(new HugeStack(key, amounts.get(key)));
            }
            snapshot.sort((left, right) -> right.amount().compareTo(left.amount()));
            hugeStacksSnapshot = List.copyOf(snapshot);
            hugeStacksSnapshotDirty = false;
        }
        return hugeStacksSnapshot;
    }

    @Override
    public synchronized Collection<OrphanedStack> getOrphanedStacks() {
        if (state != ECOInfiniteDomainState.READY) {
            return List.of();
        }
        return List.copyOf(orphanedEntries.values());
    }

    @Override
    public synchronized Collection<String> getEntryFailures() {
        return List.copyOf(entryFailures);
    }

    @Override
    public synchronized boolean canTransfer() {
        return state == ECOInfiniteDomainState.READY
                && retainedEntries.isEmpty()
                && retainedReceipts.isEmpty()
                && orphanedEntries.isEmpty();
    }

    @Override
    public synchronized int getOrphanedTypes() {
        return state == ECOInfiniteDomainState.READY ? orphanedEntries.size() : 0;
    }

    @Override
    public synchronized HugeAmount getOrphanedAmount() {
        return state == ECOInfiniteDomainState.READY ? orphanedAmountSnapshot : HugeAmount.ZERO;
    }

    @Override
    public synchronized boolean hasUnacknowledgedOrphanedEntries() {
        return state == ECOInfiniteDomainState.READY
                && !orphanedEntries.isEmpty()
                && !Objects.equals(acknowledgedOrphanedFingerprint, orphanedFingerprint());
    }

    @Override
    public synchronized boolean acknowledgeOrphanedEntries() {
        if (state != ECOInfiniteDomainState.READY || orphanedEntries.isEmpty()) {
            return false;
        }
        String currentFingerprint = orphanedFingerprint();
        if (currentFingerprint.equals(acknowledgedOrphanedFingerprint)) {
            return true;
        }
        acknowledgedOrphanedFingerprint = currentFingerprint;
        markMutated();
        flushAndAwait();
        return state == ECOInfiniteDomainState.READY;
    }

    @Override
    public synchronized void flushAndAwait() {
        finishSnapshot(true);
        if (state != ECOInfiniteDomainState.READY) {
            return;
        }
        // A domain commit must not save unrelated cells or domains as a side effect.
        save(dataFile.toFile());
        finishSnapshot(true);
    }

    @Override
    public synchronized void closeAndFlush() {
        if (state == ECOInfiniteDomainState.READY) {
            flushAndAwait();
        }
        if (state == ECOInfiniteDomainState.READY) {
            state = ECOInfiniteDomainState.CLOSED;
        }
    }

    /** Re-enables a runtime instance that was cleanly closed after its last successful verification. */
    synchronized boolean reopenAndVerify() {
        if (state != ECOInfiniteDomainState.CLOSED) {
            return state == ECOInfiniteDomainState.READY;
        }
        state = ECOInfiniteDomainState.READY;
        failureReason = null;
        try {
            verifyDiskSnapshot();
        } catch (Exception e) {
            persistenceFailed(e);
        }
        return state == ECOInfiniteDomainState.READY;
    }

    @Override
    public synchronized ECOInfiniteDomainState getState() {
        finishSnapshot(false);
        return state;
    }

    @Override
    public synchronized Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }

    @Override
    public synchronized void save(File file) {
        // 1.20.1 SavedData has a synchronous save contract. In particular /save-all flush must not
        // return before this domain is committed. The I/O worker may only consume frozen NBT.
        finishSnapshot(true);
        // DimensionDataStorage invokes this during autosave even for quarantined domains.
        if (state != ECOInfiniteDomainState.READY || !isDirty() || pendingSnapshot != null) {
            return;
        }
        try {
            if (!dataFile.equals(file.toPath().toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Unexpected infinite-storage snapshot path: " + file);
            }
            boolean incremental = baseRevision >= 0L
                    && revision > baseRevision
                    && revision < Long.MAX_VALUE
                    && changedKeys.size() <= Math.max(1024, amounts.size() / 4);
            CompoundTag snapshot = incremental ? deltaSnapshot() : save(new CompoundTag());
            int dataVersion =
                    SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            // The worker owns an immutable copy and never accesses AE keys, the live inventory, or the world.
            pendingSnapshot = CompletableFuture.runAsync(
                    () -> {
                        try {
                            if (incremental) {
                                InfiniteStorageSnapshot.write(
                                        InfiniteStorageDelta.path(dataFile), snapshot, dataVersion);
                            } else {
                                InfiniteStorageSnapshot.write(dataFile, snapshot, dataVersion);
                                Files.deleteIfExists(InfiniteStorageDelta.path(dataFile));
                            }
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    },
                    Util.ioPool());
            setDirty(false);
            finishSnapshot(true);
            if (state == ECOInfiniteDomainState.READY) {
                if (incremental) {
                    lastSerializedSnapshot = null;
                } else {
                    baseRevision = revision;
                    changedKeys.clear();
                }
            }
        } catch (Exception e) {
            persistenceFailed(e);
        }
        finishSnapshot(true);
    }

    private void finishSnapshot(boolean await) {
        if (pendingSnapshot == null || !await && !pendingSnapshot.isDone()) return;
        CompletableFuture<Void> pending = pendingSnapshot;
        pendingSnapshot = null;
        try {
            pending.join();
        } catch (CompletionException e) {
            persistenceFailed(e);
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        writeMetadata(tag);

        ListTag entries = new ListTag();
        amounts.forEach((key, amount) -> {
            CompoundTag encoded = encodedKeys.get(key);
            if (encoded == null) {
                throw new IllegalStateException("Missing cached AEKey encoding for " + key);
            }
            entries.add(amountEntry(encoded, amount));
        });
        for (OrphanedStack orphaned : orphanedEntries.values()) {
            entries.add(amountEntry(orphaned.encodedKey(), orphaned.amount()));
        }
        retainedEntries.forEach(entry -> entries.add(entry.copy()));
        tag.put(TAG_ENTRIES, entries);
        lastSerializedSnapshot = tag.copy();
        return tag;
    }

    private CompoundTag deltaSnapshot() {
        CompoundTag tag = new CompoundTag();
        writeMetadata(tag);
        tag.putLong(InfiniteStorageDelta.BASE_REVISION, baseRevision);
        ListTag entries = new ListTag();
        changedKeys.forEach((key, change) -> {
            HugeAmount amount = amounts.get(key);
            CompoundTag entry = amountEntry(change.encodedKey(), amount);
            if (amount.isZero()) entry.putBoolean(InfiniteStorageDelta.DELETED, true);
            entries.add(entry);
        });
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }

    private static CompoundTag amountEntry(CompoundTag encoded, HugeAmount amount) {
        CompoundTag entry = new CompoundTag();
        entry.put(TAG_KEY, encoded.copy());
        if (amount.isBig()) {
            entry.putByteArray(TAG_AMOUNT_WIDE, amount.toBigInteger().toByteArray());
        } else {
            entry.putLong(TAG_AMOUNT_LONG, amount.toLongSaturated());
        }
        return entry;
    }

    private void writeMetadata(CompoundTag tag) {
        tag.putInt(TAG_FORMAT, FORMAT_VERSION);
        tag.putUUID(TAG_DOMAIN, domainId);
        tag.putLong(TAG_REVISION, revision);
        if (legacyFingerprint != null) {
            tag.putString(TAG_LEGACY_FINGERPRINT, legacyFingerprint);
        }
        if (acknowledgedOrphanedFingerprint != null) {
            tag.putString(TAG_ACKNOWLEDGED_ORPHANED_FINGERPRINT, acknowledgedOrphanedFingerprint);
        }

        ListTag receipts = new ListTag();
        for (UUID transactionId : legacyTransferReceipts) {
            CompoundTag receipt = new CompoundTag();
            receipt.putUUID(TAG_RECEIPT_ID, transactionId);
            receipts.add(receipt);
        }
        transferReceipts.forEach((transactionId, digest) -> {
            CompoundTag receipt = new CompoundTag();
            receipt.putUUID(TAG_RECEIPT_ID, transactionId);
            receipt.putString(TAG_RECEIPT_DIGEST, digest);
            receipts.add(receipt);
        });
        retainedReceipts.forEach(receipt -> receipts.add(receipt.copy()));
        tag.put(TAG_RECEIPTS, receipts);
    }

    @Override
    public synchronized DimensionDataStorage dataStorage() {
        return dataStorage;
    }

    @Override
    public synchronized boolean needsPersistence() {
        return state == ECOInfiniteDomainState.READY && (isDirty() || pendingSnapshot != null);
    }

    @Override
    public synchronized void preparePersistence() throws Exception {
        Files.createDirectories(dataFile.getParent());
    }

    @Override
    public synchronized void commitPersistence() {
        flushAndAwait();
    }

    @Override
    public synchronized void verifyPersistence() throws Exception {
        flushAndAwait();
        verifyDiskSnapshot();
    }

    @Override
    public synchronized void persistenceFailed(Exception cause) {
        quarantine("Unable to persist and verify infinite-storage domain", cause);
    }

    private boolean canOperate(@Nullable AEKey key, long amount) {
        finishSnapshot(false);
        if (state != ECOInfiniteDomainState.READY || !isResolved(key) || blockedKeys.contains(key) || amount <= 0L) {
            return false;
        }
        if (blockedFingerprints.isEmpty()) return true;
        // Already decoded healthy keys must stay on the constant-time path even in a partially damaged domain.
        if (encodedKeys.containsKey(key)) return true;
        try {
            return !blockedFingerprints.contains(ECOStorageKeyHash.stableFingerprint(key.toTagGeneric()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean ensureEncodedKey(AEKey key) {
        if (encodedKeys.containsKey(key)) {
            return true;
        }
        PendingChange recentlyRemoved = changedKeys.get(key);
        if (recentlyRemoved != null) {
            // Empty/refill automation must not encode and validate the same key every operation.
            encodedKeys.put(key, recentlyRemoved.encodedKey());
            return true;
        }
        if (!isResolved(key)) {
            return false;
        }
        try {
            CompoundTag encoded = key.toTagGeneric();
            if (encoded == null || encoded.isEmpty()) {
                return false;
            }
            InfiniteStorageSnapshot.validateKey(encoded);
            encodedKeys.put(key, encoded.copy());
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            LOGGER.error("Unable to serialize AEKey {}; rejecting the storage operation", key, e);
            return false;
        }
    }

    private String transferDigest(Collection<HugeStack> contents) {
        List<String> records = new ArrayList<>(contents.size());
        for (HugeStack stack : contents) {
            CompoundTag encodedKey = encodedKeys.get(stack.key());
            if (encodedKey == null) {
                throw new IllegalStateException("Missing cached AEKey encoding for transfer receipt");
            }
            records.add(ECOStorageKeyHash.stableFingerprint(encodedKey) + ":" + stack.amount());
        }
        records.sort(String::compareTo);

        MessageDigest digest = sha256Digest();
        updateDigestInt(digest, records.size());
        for (String record : records) {
            byte[] bytes = record.getBytes(StandardCharsets.UTF_8);
            updateDigestInt(digest, bytes.length);
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String orphanedFingerprint() {
        List<String> records = new ArrayList<>(orphanedEntries.size());
        orphanedEntries.forEach((fingerprint, entry) ->
                records.add(fingerprint + ":" + entry.amount().toBigInteger()));
        records.sort(String::compareTo);

        MessageDigest digest = sha256Digest();
        updateDigestInt(digest, records.size());
        for (String record : records) {
            byte[] bytes = record.getBytes(StandardCharsets.UTF_8);
            updateDigestInt(digest, bytes.length);
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void updateDigestInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24 & 0xFF));
        digest.update((byte) (value >>> 16 & 0xFF));
        digest.update((byte) (value >>> 8 & 0xFF));
        digest.update((byte) (value & 0xFF));
    }

    private static boolean isSha256(String value) {
        if (value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character >= '0' && character <= '9') && !(character >= 'a' && character <= 'f')) {
                return false;
            }
        }
        return true;
    }

    private void markMutated() {
        if (revision < Long.MAX_VALUE) {
            revision++;
        }
        setDirty();
    }

    private void onAmountChanged(AEKey key, HugeAmount previous, HugeAmount next, long changed, boolean increased) {
        if (changed <= 0L) {
            throw new IllegalArgumentException("Changed amount must be positive");
        }
        onAmountChanged(key, previous, next, changed, null, increased);
    }

    private void onAmountChanged(
            AEKey key, HugeAmount previous, HugeAmount next, HugeAmount changed, boolean increased) {
        if (changed == null || changed.isZero()) {
            throw new IllegalArgumentException("Changed amount must be positive");
        }
        if (!changed.isBig()) {
            onAmountChanged(key, previous, next, changed.toLongSaturated(), increased);
            return;
        }
        onAmountChanged(key, previous, next, 0L, WideAmount.of(changed.toBigInteger()), increased);
    }

    private void onAmountChanged(
            AEKey key,
            HugeAmount previous,
            HugeAmount next,
            long changed,
            @Nullable WideAmount changedWide,
            boolean increased) {
        PendingChange change = changedKeys.get(key);
        if (change == null) {
            changedKeys.put(key, new PendingChange(encodedKeys.get(key), previous));
        } else if (change.baseAmount().equals(next)) {
            // The next cumulative overlay must omit this key so recovery uses its base value.
            // Metadata (revision and transfer receipts) is still committed even for an empty overlay.
            changedKeys.remove(key);
        }
        if (next.isZero()) {
            visibleStacks.remove(key);
        } else {
            visibleStacks.set(key, next.toLongSaturated());
        }

        if (next.compareTo(LONG_MAX_AMOUNT) > 0) {
            hugeKeys.add(key);
            hugeStacksSnapshotDirty = true;
        } else if (hugeKeys.remove(key)) {
            hugeStacksSnapshotDirty = true;
        }

        applyDelta(storedAmount, changed, changedWide, increased);
        storedAmountSnapshotDirty = true;

        int typeDelta = (previous.isZero() ? 0 : -1) + (next.isZero() ? 0 : 1);
        AEKeyType keyType = key.getType();
        MutableTypeStats stats = typeStats.computeIfAbsent(keyType, ignored -> new MutableTypeStats());
        stats.storedTypes += typeDelta;
        applyDelta(stats.storedAmount, changed, changedWide, increased);
        if (stats.storedTypes == 0L) {
            if (!stats.storedAmount.isZero()) {
                throw new IllegalStateException("Type statistics amount remained after its last key was removed");
            }
            typeStats.remove(keyType);
        }
        typeStatsSnapshotDirty = true;
    }

    private void rebuildIndexes() {
        visibleStacks.clear();
        typeStats.clear();
        hugeKeys.clear();
        storedAmount.clear();
        amounts.forEach((key, amount) -> {
            visibleStacks.set(key, amount.toLongSaturated());
            addAmount(storedAmount, amount);
            MutableTypeStats stats = typeStats.computeIfAbsent(key.getType(), ignored -> new MutableTypeStats());
            stats.storedTypes++;
            addAmount(stats.storedAmount, amount);
            if (amount.compareTo(LONG_MAX_AMOUNT) > 0) {
                hugeKeys.add(key);
            }
        });
        storedAmountSnapshot = HugeAmount.ZERO;
        WideAmount orphanedAmount = WideAmount.of(0L);
        for (OrphanedStack orphaned : orphanedEntries.values()) {
            addAmount(orphanedAmount, orphaned.amount());
        }
        orphanedAmountSnapshot = snapshot(orphanedAmount);
        typeStatsSnapshot = List.of();
        hugeStacksSnapshot = List.of();
        storedAmountSnapshotDirty = true;
        typeStatsSnapshotDirty = true;
        hugeStacksSnapshotDirty = true;
    }

    private static void applyDelta(
            WideAmount target, long changed, @Nullable WideAmount changedWide, boolean increased) {
        if (changedWide == null) {
            if (increased) {
                target.add(changed);
            } else {
                target.subtract(changed);
            }
        } else if (increased) {
            target.add(changedWide);
        } else {
            target.subtract(changedWide);
        }
    }

    private static void addAmount(WideAmount target, HugeAmount amount) {
        if (amount.isBig()) {
            target.add(WideAmount.of(amount.toBigInteger()));
        } else {
            target.add(amount.toLongSaturated());
        }
    }

    private static HugeAmount snapshot(WideAmount amount) {
        return amount.fitsLong() ? HugeAmount.of(amount.toLongExact()) : HugeAmount.of(amount.toBigInteger());
    }

    private void verifyDiskSnapshot() throws Exception {
        if (!Files.isRegularFile(dataFile)) {
            throw new IllegalStateException("SavedData file is missing after save: " + dataFile);
        }
        CompoundTag persistedTag = InfiniteStorageSnapshot.read(dataFile);
        ParsedData persisted = parse(persistedTag, domainId);
        if (lastSerializedSnapshot != null) {
            ParsedData expected = parse(lastSerializedSnapshot, domainId);
            boolean revisionMatches = persisted.revision() == expected.revision() && expected.revision() == revision;
            boolean amountsMatch = canonicalAmounts(persisted).equals(canonicalAmounts(expected));
            boolean encodedKeysMatch = canonicalKeys(persisted).equals(canonicalKeys(expected));
            boolean retainedMatch = persisted.retainedEntries().equals(expected.retainedEntries())
                    && persisted.retainedReceipts().equals(expected.retainedReceipts());
            boolean legacyReceiptsMatch = persisted.legacyTransferReceipts().equals(expected.legacyTransferReceipts());
            boolean transferReceiptsMatch = persisted.transferReceipts().equals(expected.transferReceipts());
            boolean legacyFingerprintMatches =
                    Objects.equals(persisted.legacyFingerprint(), expected.legacyFingerprint());
            boolean acknowledgedOrphanedFingerprintMatches = Objects.equals(
                    persisted.acknowledgedOrphanedFingerprint(), expected.acknowledgedOrphanedFingerprint());
            if (!(revisionMatches
                    && amountsMatch
                    && encodedKeysMatch
                    && retainedMatch
                    && legacyReceiptsMatch
                    && transferReceiptsMatch
                    && legacyFingerprintMatches
                    && acknowledgedOrphanedFingerprintMatches)) {
                LOGGER.error(
                        "Infinite-storage snapshot details domain={} file={} bytes={} revision={} amounts={} encodedKeys={} "
                                + "legacyReceipts={} transferReceipts={} legacyFingerprint={} (expected revision={} amounts={} "
                                + "encodedKeys={} legacyReceipts={} transferReceipts={} legacyFingerprint={})",
                        domainId,
                        dataFile,
                        Files.size(dataFile),
                        persisted.revision(),
                        persisted.amounts().size(),
                        persisted.encodedKeys().size(),
                        persisted.legacyTransferReceipts().size(),
                        persisted.transferReceipts().size(),
                        persisted.legacyFingerprint() != null,
                        expected.revision(),
                        expected.amounts().size(),
                        expected.encodedKeys().size(),
                        expected.legacyTransferReceipts().size(),
                        expected.transferReceipts().size(),
                        expected.legacyFingerprint() != null);
                if (!amountsMatch || !encodedKeysMatch) {
                    LOGGER.error(
                            "Infinite-storage key details domain={} persisted={} expected={} amountDifferences={}",
                            domainId,
                            describeKeys(persisted),
                            describeKeys(expected),
                            describeAmountDifferences(persisted, expected));
                }
                throw new IllegalStateException("SavedData read-back did not match the serialized snapshot");
            }
            return;
        }

        // A freshly loaded clean domain has no in-process serialized snapshot yet.
        Map<AEKey, HugeAmount> expectedAmounts = new HashMap<>();
        amounts.forEach(expectedAmounts::put);
        Map<String, OrphanedStack> expectedOrphanedEntries = new HashMap<>(orphanedEntries);
        if (persisted.revision() != revision
                || !persisted.amounts().equals(expectedAmounts)
                || !persisted.orphanedEntries().equals(expectedOrphanedEntries)
                || !persisted.retainedEntries().equals(retainedEntries)
                || !persisted.retainedReceipts().equals(retainedReceipts)
                || !persisted.legacyTransferReceipts().equals(legacyTransferReceipts)
                || !persisted.transferReceipts().equals(transferReceipts)
                || !Objects.equals(persisted.legacyFingerprint(), legacyFingerprint)
                || !Objects.equals(persisted.acknowledgedOrphanedFingerprint(), acknowledgedOrphanedFingerprint)) {
            throw new IllegalStateException("SavedData read-back did not match the in-memory domain");
        }
    }

    private void quarantine(String message, Throwable cause) {
        state = ECOInfiniteDomainState.QUARANTINED;
        failureReason = message + ": " + cause.getMessage();
        setDirty(false);
        LOGGER.error("{} {}", message, domainId, cause);
    }

    private void quarantineReceiptConflict(UUID transactionId) {
        // A disputed transfer blocks further whole-domain transfers, not unrelated player inventory access.
        CompoundTag retained = new CompoundTag();
        retained.putUUID(TAG_RECEIPT_ID, transactionId);
        retained.putString("conflict", "Transfer contents differ from the original receipt");
        retainedReceipts.add(retained);
        entryFailures.add("Conflicting transfer receipt: " + transactionId);
        markMutated();
        LOGGER.error("Isolated conflicting infinite-storage transfer {} in domain {}", transactionId, domainId);
    }

    private void requireReady() {
        if (state != ECOInfiniteDomainState.READY) {
            throw new IllegalStateException("Infinite-storage domain is not ready: " + state);
        }
    }

    private static ParsedData parse(CompoundTag tag, UUID expectedDomainId) {
        if (!tag.contains(TAG_FORMAT, Tag.TAG_INT) || tag.getInt(TAG_FORMAT) != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported infinite-storage SavedData format");
        }
        if (!tag.hasUUID(TAG_DOMAIN) || !expectedDomainId.equals(tag.getUUID(TAG_DOMAIN))) {
            throw new IllegalArgumentException("Infinite-storage SavedData domain mismatch");
        }
        if (!tag.contains(TAG_REVISION, Tag.TAG_LONG) || tag.getLong(TAG_REVISION) < 0L) {
            throw new IllegalArgumentException("Invalid infinite-storage revision");
        }
        if (!tag.contains(TAG_ENTRIES, Tag.TAG_LIST) || !tag.contains(TAG_RECEIPTS, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Infinite-storage SavedData is missing required lists");
        }

        ListTag entries = requireCompoundList(tag, TAG_ENTRIES);
        ListTag receiptTags = requireCompoundList(tag, TAG_RECEIPTS);
        Map<AEKey, HugeAmount> parsedAmounts = new HashMap<>();
        Map<AEKey, CompoundTag> parsedKeys = new HashMap<>();
        Map<String, OrphanedStack> parsedOrphanedEntries = new HashMap<>();
        var inventory = InfiniteStorageEntries.read(entries, encoded -> {
            AEKey key = AEKey.fromTagGeneric(encoded);
            return isResolved(key) ? key : null;
        });
        for (var entry : inventory.available()) {
            if (entry.key() == null) {
                parsedOrphanedEntries.put(
                        ECOStorageKeyHash.stableFingerprint(entry.encodedKey()),
                        new OrphanedStack(entry.encodedKey(), entry.amount()));
            } else {
                parsedAmounts.put(entry.key(), entry.amount());
                parsedKeys.put(entry.key(), entry.encodedKey());
            }
        }
        List<CompoundTag> retainedReceipts = new ArrayList<>();
        List<String> failures = new ArrayList<>(inventory.failures());
        Set<UUID> receiptIds = new HashSet<>();
        Set<UUID> legacyReceipts = new HashSet<>();
        Map<UUID, String> verifiedReceipts = new HashMap<>();
        for (int i = 0; i < receiptTags.size(); i++) {
            CompoundTag receipt = receiptTags.getCompound(i);
            try {
                if (!receipt.hasUUID(TAG_RECEIPT_ID)) {
                    throw new IllegalArgumentException("Invalid or duplicate infinite-storage transfer receipt");
                }
                UUID transactionId = receipt.getUUID(TAG_RECEIPT_ID);
                if (!receiptIds.add(transactionId)) {
                    throw new IllegalArgumentException("Invalid or duplicate infinite-storage transfer receipt");
                }
                if (!receipt.contains(TAG_RECEIPT_DIGEST)) {
                    legacyReceipts.add(transactionId);
                    continue;
                }
                if (!receipt.contains(TAG_RECEIPT_DIGEST, Tag.TAG_STRING)) {
                    throw new IllegalArgumentException("Invalid infinite-storage transfer receipt digest");
                }
                String digest = receipt.getString(TAG_RECEIPT_DIGEST);
                if (!isSha256(digest)) {
                    throw new IllegalArgumentException("Invalid infinite-storage transfer receipt digest");
                }
                verifiedReceipts.put(transactionId, digest);
            } catch (RuntimeException e) {
                retainedReceipts.add(receipt.copy());
                failures.add("receipt[" + i + "]: " + e.getMessage());
            }
        }

        String fingerprint = null;
        if (tag.contains(TAG_LEGACY_FINGERPRINT)) {
            if (!tag.contains(TAG_LEGACY_FINGERPRINT, Tag.TAG_STRING)
                    || !isSha256(tag.getString(TAG_LEGACY_FINGERPRINT))) {
                throw new IllegalArgumentException("Invalid V1 migration fingerprint");
            }
            fingerprint = tag.getString(TAG_LEGACY_FINGERPRINT);
        }
        String acknowledgedOrphanedFingerprint = null;
        if (tag.contains(TAG_ACKNOWLEDGED_ORPHANED_FINGERPRINT)) {
            if (!tag.contains(TAG_ACKNOWLEDGED_ORPHANED_FINGERPRINT, Tag.TAG_STRING)
                    || !isSha256(tag.getString(TAG_ACKNOWLEDGED_ORPHANED_FINGERPRINT))) {
                throw new IllegalArgumentException("Invalid orphaned-entry acknowledgement fingerprint");
            }
            acknowledgedOrphanedFingerprint = tag.getString(TAG_ACKNOWLEDGED_ORPHANED_FINGERPRINT);
        }
        return new ParsedData(
                Map.copyOf(parsedAmounts),
                Map.copyOf(parsedKeys),
                Map.copyOf(parsedOrphanedEntries),
                inventory.retained(),
                List.copyOf(failures),
                List.copyOf(retainedReceipts),
                inventory.blockedKeys(),
                Set.copyOf(legacyReceipts),
                Map.copyOf(verifiedReceipts),
                tag.getLong(TAG_REVISION),
                fingerprint,
                acknowledgedOrphanedFingerprint);
    }

    private static ListTag requireCompoundList(CompoundTag tag, String key) {
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list) || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Infinite-storage SavedData contains an invalid " + key + " list");
        }
        return list;
    }

    private static String describeKeys(ParsedData data) {
        List<String> keys = new ArrayList<>();
        data.encodedKeys()
                .forEach((key, encoded) -> keys.add(key.getId() + "=" + ECOStorageKeyHash.stableFingerprint(encoded)));
        data.orphanedEntries().forEach((fingerprint, ignored) -> keys.add("orphaned=" + fingerprint));
        keys.sort(String::compareTo);
        return keys.toString();
    }

    private static String describeAmountDifferences(ParsedData persisted, ParsedData expected) {
        Map<String, HugeAmount> actual = canonicalAmounts(persisted);
        Map<String, HugeAmount> wanted = canonicalAmounts(expected);
        Set<String> keys = new HashSet<>(actual.keySet());
        keys.addAll(wanted.keySet());
        List<String> differences = new ArrayList<>();
        for (String key : keys) {
            HugeAmount actualAmount = actual.get(key);
            HugeAmount wantedAmount = wanted.get(key);
            if (!Objects.equals(actualAmount, wantedAmount)) {
                differences.add(key + "=" + actualAmount + "/" + wantedAmount);
            }
        }
        differences.sort(String::compareTo);
        return differences.toString();
    }

    private static Map<String, HugeAmount> canonicalAmounts(ParsedData data) {
        Map<String, HugeAmount> result = new HashMap<>();
        data.amounts().forEach((key, amount) -> {
            CompoundTag encoded = data.encodedKeys().get(key);
            if (encoded != null) {
                result.put(ECOStorageKeyHash.stableFingerprint(encoded), amount);
            }
        });
        data.orphanedEntries().forEach((fingerprint, entry) -> result.put(fingerprint, entry.amount()));
        return result;
    }

    private static Set<String> canonicalKeys(ParsedData data) {
        Set<String> result = new HashSet<>();
        data.encodedKeys().values().forEach(encoded -> result.add(ECOStorageKeyHash.stableFingerprint(encoded)));
        result.addAll(data.orphanedEntries().keySet());
        return result;
    }

    private static boolean isResolved(@Nullable AEKey key) {
        return key != null && !AE2_MISSING_CONTENT.equals(key.getId());
    }

    private record ParsedData(
            Map<AEKey, HugeAmount> amounts,
            Map<AEKey, CompoundTag> encodedKeys,
            Map<String, OrphanedStack> orphanedEntries,
            List<CompoundTag> retainedEntries,
            List<String> entryFailures,
            List<CompoundTag> retainedReceipts,
            Set<AEKey> blockedKeys,
            Set<UUID> legacyTransferReceipts,
            Map<UUID, String> transferReceipts,
            long revision,
            @Nullable String legacyFingerprint,
            @Nullable String acknowledgedOrphanedFingerprint) {}

    private static final class MutableTypeStats {
        private long storedTypes;
        private final WideAmount storedAmount = WideAmount.of(0L);
    }
}
