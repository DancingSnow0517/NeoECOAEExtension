package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageKeyHash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minecraft-native V2 infinite storage. One instance is stored in one SavedData file. */
final class SavedDataInfiniteStorageEngine extends SavedData implements ECOInfiniteStorageEngine {
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
    private static final long PERSISTENCE_PROBE_INTERVAL_TICKS = 200L;

    private final HolderLookup.Provider registries;
    private final UUID domainId;
    private final DimensionDataStorage dataStorage;
    private final Path dataFile;
    private final HybridAmountStore<AEKey> amounts = new HybridAmountStore<>();
    private final Object2ObjectOpenHashMap<AEKey, CompoundTag> encodedKeys = new Object2ObjectOpenHashMap<>();
    private final Map<String, OrphanedStack> orphanedEntries = new HashMap<>();
    private final Object2ObjectOpenHashMap<AEKeyType, MutableTypeStats> typeStats =
        new Object2ObjectOpenHashMap<>();
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
    private long lastPersistenceProbeTick = Long.MIN_VALUE;
    private long lastVerifiedRevision;
    @Nullable private CompoundTag lastSerializedSnapshot;

    private SavedDataInfiniteStorageEngine(
        HolderLookup.Provider registries,
        UUID domainId,
        DimensionDataStorage dataStorage,
        Path dataFile
    ) {
        this.registries = registries;
        this.domainId = domainId;
        this.dataStorage = dataStorage;
        this.dataFile = dataFile.toAbsolutePath().normalize();
    }

    static SavedData.Factory<SavedDataInfiniteStorageEngine> factory(
        HolderLookup.Provider registries,
        UUID domainId,
        DimensionDataStorage dataStorage,
        Path dataFile
    ) {
        return new SavedData.Factory<>(
            () -> new SavedDataInfiniteStorageEngine(registries, domainId, dataStorage, dataFile),
            (tag, lookup) -> load(tag, lookup, domainId, dataStorage, dataFile)
        );
    }

    static SavedDataInfiniteStorageEngine createNew(
        HolderLookup.Provider registries,
        UUID domainId,
        DimensionDataStorage dataStorage,
        Path dataFile
    ) {
        return new SavedDataInfiniteStorageEngine(registries, domainId, dataStorage, dataFile);
    }

    private static SavedDataInfiniteStorageEngine load(
        CompoundTag tag,
        HolderLookup.Provider registries,
        UUID expectedDomainId,
        DimensionDataStorage dataStorage,
        Path dataFile
    ) {
        ParsedData parsed = parse(tag, registries, expectedDomainId);
        SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(
            registries,
            expectedDomainId,
            dataStorage,
            dataFile
        );
        engine.amounts.clear();
        parsed.amounts().forEach(engine.amounts::set);
        engine.encodedKeys.putAll(parsed.encodedKeys());
        engine.orphanedEntries.putAll(parsed.orphanedEntries());
        engine.legacyTransferReceipts.addAll(parsed.legacyTransferReceipts());
        engine.transferReceipts.putAll(parsed.transferReceipts());
        engine.revision = parsed.revision();
        engine.lastVerifiedRevision = parsed.revision();
        engine.legacyFingerprint = parsed.legacyFingerprint();
        engine.acknowledgedOrphanedFingerprint = parsed.acknowledgedOrphanedFingerprint();
        engine.lastSerializedSnapshot = tag.copy();
        engine.rebuildIndexes();
        return engine;
    }

    synchronized void importLegacy(
        Map<AEKey, HugeAmount> importedAmounts,
        Collection<UUID> importedReceipts,
        String sourceFingerprint,
        long importedRevision
    ) {
        importLegacy(importedAmounts, Map.of(), importedReceipts, sourceFingerprint, importedRevision);
    }

    synchronized void importLegacy(
        Map<AEKey, HugeAmount> importedAmounts,
        Map<String, OrphanedStack> importedOrphanedEntries,
        Collection<UUID> importedReceipts,
        String sourceFingerprint,
        long importedRevision
    ) {
        requireReady();
        if (!amounts.isEmpty() || !orphanedEntries.isEmpty()
                || !legacyTransferReceipts.isEmpty() || !transferReceipts.isEmpty()) {
            throw new IllegalStateException("Cannot import V1 data into a non-empty V2 domain");
        }
        Objects.requireNonNull(importedAmounts, "importedAmounts");
        Objects.requireNonNull(importedOrphanedEntries, "importedOrphanedEntries");
        Objects.requireNonNull(importedReceipts, "importedReceipts");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        if (!isSha256(sourceFingerprint)) {
            throw new IllegalArgumentException("Invalid V1 migration fingerprint");
        }

        Set<String> importedFingerprints = new HashSet<>();
        for (Map.Entry<AEKey, HugeAmount> entry : importedAmounts.entrySet()) {
            AEKey key = Objects.requireNonNull(entry.getKey(), "legacy key");
            HugeAmount amount = Objects.requireNonNull(entry.getValue(), "legacy amount");
            if (amount.isZero()) {
                throw new IllegalArgumentException("V1 import contains a zero amount");
            }
            if (!ensureEncodedKey(key)) {
                throw new IllegalArgumentException("V1 import contains an AEKey that cannot be encoded");
            }
            if (!importedFingerprints.add(ECOStorageKeyHash.stableFingerprint(encodedKeys.get(key)))) {
                throw new IllegalArgumentException("V1 import contains duplicate encoded keys");
            }
        }
        for (Map.Entry<String, OrphanedStack> entry : importedOrphanedEntries.entrySet()) {
            String fingerprint = Objects.requireNonNull(entry.getKey(), "orphaned key fingerprint");
            OrphanedStack orphaned = Objects.requireNonNull(entry.getValue(), "orphaned entry");
            if (!isSha256(fingerprint)
                    || orphaned.encodedKey() == null
                    || orphaned.amount() == null
                    || orphaned.amount().isZero()
                    || !fingerprint.equals(ECOStorageKeyHash.stableFingerprint(orphaned.encodedKey()))
                    || !importedFingerprints.add(fingerprint)) {
                throw new IllegalArgumentException("V1 import contains an invalid orphaned entry");
            }
        }
        for (UUID transactionId : importedReceipts) {
            Objects.requireNonNull(transactionId, "legacy receipt");
        }
        for (Map.Entry<AEKey, HugeAmount> entry : importedAmounts.entrySet()) {
            amounts.set(entry.getKey(), entry.getValue());
        }
        importedOrphanedEntries.forEach((fingerprint, orphaned) ->
            orphanedEntries.put(fingerprint, new OrphanedStack(orphaned.encodedKey().copy(), orphaned.amount()))
        );
        legacyTransferReceipts.addAll(importedReceipts);
        legacyFingerprint = sourceFingerprint;
        revision = Math.max(0L, importedRevision);
        lastVerifiedRevision = revision;
        rebuildIndexes();
        setDirty();
    }

    UUID domainId() {
        return domainId;
    }

    @Nullable
    synchronized String legacyFingerprint() {
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
        if (transactionId == null || !canOperate(key, amount)) {
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
        if (state != ECOInfiniteDomainState.READY || transactionId == null || contents == null) {
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
        return state == ECOInfiniteDomainState.READY && amounts.isEmpty() && orphanedEntries.isEmpty();
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
        return storedAmountSnapshot.add(getOrphanedAmount());
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
            typeStats.forEach((type, stats) -> snapshot.add(
                new TypeStats(type, stats.storedTypes, snapshot(stats.storedAmount))
            ));
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
        if (state != ECOInfiniteDomainState.READY) {
            return;
        }
        try {
            Files.createDirectories(dataFile.getParent());
            dataStorage.save();
            IOUtilities.waitUntilIOWorkerComplete();
            try {
                verifyDiskSnapshot();
            } catch (IllegalStateException mismatch) {
                // DimensionDataStorage delegates writes to a shared async queue. If an older queued
                // write wins the race, synchronously publish this engine's exact snapshot once before
                // quarantining the domain.
                LOGGER.warn(
                    "Infinite-storage snapshot mismatch for domain {}; retrying with synchronous snapshot write: {}",
                    domainId,
                    mismatch.getMessage()
                );
                writeSnapshotSynchronously();
                verifyDiskSnapshot();
            }
            lastVerifiedRevision = revision;
        } catch (Exception e) {
            quarantine("Unable to persist and verify infinite-storage domain", e);
        }
    }

    private void writeSnapshotSynchronously() throws IOException {
        if (lastSerializedSnapshot == null) {
            throw new IllegalStateException("No serialized snapshot is available for recovery");
        }
        CompoundTag root = new CompoundTag();
        root.put("data", lastSerializedSnapshot.copy());
        NbtUtils.addCurrentDataVersion(root);
        IOUtilities.writeNbtCompressed(root, dataFile);
        setDirty(false);
    }

    synchronized void pollPersistence(long gameTime) {
        if (state != ECOInfiniteDomainState.READY || revision == lastVerifiedRevision) {
            return;
        }
        if (lastPersistenceProbeTick != Long.MIN_VALUE
                && gameTime - lastPersistenceProbeTick < PERSISTENCE_PROBE_INTERVAL_TICKS) {
            return;
        }
        lastPersistenceProbeTick = gameTime;
        flushAndAwait();
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

    @Override
    public synchronized ECOInfiniteDomainState getState() {
        return state;
    }

    @Override
    public synchronized Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_FORMAT, FORMAT_VERSION);
        tag.putUUID(TAG_DOMAIN, domainId);
        tag.putLong(TAG_REVISION, revision);
        if (legacyFingerprint != null) {
            tag.putString(TAG_LEGACY_FINGERPRINT, legacyFingerprint);
        }
        if (acknowledgedOrphanedFingerprint != null) {
            tag.putString(TAG_ACKNOWLEDGED_ORPHANED_FINGERPRINT, acknowledgedOrphanedFingerprint);
        }

        ListTag entries = new ListTag();
        amounts.forEach((key, amount) -> {
            CompoundTag encoded = encodedKeys.get(key);
            if (encoded == null) {
                throw new IllegalStateException("Missing cached AEKey encoding for " + key);
            }
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_KEY, encoded.copy());
            if (amount.isBig()) {
                entry.putByteArray(TAG_AMOUNT_WIDE, amount.toBigInteger().toByteArray());
            } else {
                entry.putLong(TAG_AMOUNT_LONG, amount.toLongSaturated());
            }
            entries.add(entry);
        });
        for (OrphanedStack orphaned : orphanedEntries.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_KEY, orphaned.encodedKey().copy());
            if (orphaned.amount().isBig()) {
                entry.putByteArray(TAG_AMOUNT_WIDE, orphaned.amount().toBigInteger().toByteArray());
            } else {
                entry.putLong(TAG_AMOUNT_LONG, orphaned.amount().toLongSaturated());
            }
            entries.add(entry);
        }
        tag.put(TAG_ENTRIES, entries);

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
        tag.put(TAG_RECEIPTS, receipts);
        lastSerializedSnapshot = tag.copy();
        return tag;
    }

    private boolean canOperate(@Nullable AEKey key, long amount) {
        return state == ECOInfiniteDomainState.READY && isResolved(key) && amount > 0L;
    }

    private boolean ensureEncodedKey(AEKey key) {
        if (encodedKeys.containsKey(key)) {
            return true;
        }
        if (!isResolved(key)) {
            return false;
        }
        try {
            CompoundTag encoded = key.toTagGeneric(registries);
            if (encoded == null || encoded.isEmpty()) {
                return false;
            }
            encodedKeys.put(key, encoded.copy());
            return true;
        } catch (RuntimeException e) {
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
            records.add(
                ECOStorageKeyHash.stableFingerprint(encodedKey) + ":" + stack.amount()
            );
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
        orphanedEntries.forEach((fingerprint, entry) -> records.add(
            fingerprint + ":" + entry.amount().toBigInteger()
        ));
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
            if (!(character >= '0' && character <= '9')
                    && !(character >= 'a' && character <= 'f')) {
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

    private void onAmountChanged(
        AEKey key,
        HugeAmount previous,
        HugeAmount next,
        long changed,
        boolean increased
    ) {
        if (changed <= 0L) {
            throw new IllegalArgumentException("Changed amount must be positive");
        }
        onAmountChanged(key, previous, next, changed, null, increased);
    }

    private void onAmountChanged(
        AEKey key,
        HugeAmount previous,
        HugeAmount next,
        HugeAmount changed,
        boolean increased
    ) {
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
        boolean increased
    ) {
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
        WideAmount target,
        long changed,
        @Nullable WideAmount changedWide,
        boolean increased
    ) {
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
        return amount.fitsLong()
            ? HugeAmount.of(amount.toLongExact())
            : HugeAmount.of(amount.toBigInteger());
    }

    private void verifyDiskSnapshot() throws Exception {
        if (!Files.isRegularFile(dataFile)) {
            throw new IllegalStateException("SavedData file is missing after save: " + dataFile);
        }
        CompoundTag root;
        try (InputStream input = Files.newInputStream(dataFile)) {
            root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        }
        CompoundTag persistedTag = root.getCompound("data");
        ParsedData persisted = parse(persistedTag, registries, domainId);
        if (lastSerializedSnapshot != null) {
            ParsedData expected = parse(lastSerializedSnapshot, registries, domainId);
            boolean revisionMatches = persisted.revision() == expected.revision() && expected.revision() == revision;
            boolean amountsMatch = canonicalAmounts(persisted).equals(canonicalAmounts(expected));
            boolean encodedKeysMatch = canonicalKeys(persisted).equals(canonicalKeys(expected));
            boolean legacyReceiptsMatch = persisted.legacyTransferReceipts().equals(expected.legacyTransferReceipts());
            boolean transferReceiptsMatch = persisted.transferReceipts().equals(expected.transferReceipts());
            boolean legacyFingerprintMatches = Objects.equals(
                persisted.legacyFingerprint(), expected.legacyFingerprint()
            );
            boolean acknowledgedOrphanedFingerprintMatches = Objects.equals(
                persisted.acknowledgedOrphanedFingerprint(), expected.acknowledgedOrphanedFingerprint()
            );
            if (!(revisionMatches && amountsMatch && encodedKeysMatch && legacyReceiptsMatch
                    && transferReceiptsMatch && legacyFingerprintMatches && acknowledgedOrphanedFingerprintMatches)) {
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
                    expected.legacyFingerprint() != null
                );
                if (!amountsMatch || !encodedKeysMatch) {
                    LOGGER.error(
                        "Infinite-storage key details domain={} persisted={} expected={} amountDifferences={}",
                        domainId,
                        describeKeys(persisted),
                        describeKeys(expected),
                        describeAmountDifferences(persisted, expected)
                    );
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
        setDirty();
        LOGGER.error("{} {}", message, domainId, cause);
    }

    private void quarantineReceiptConflict(UUID transactionId) {
        quarantine(
            "Infinite-storage transfer receipt contents changed",
            new IllegalStateException("transaction " + transactionId)
        );
    }

    private void requireReady() {
        if (state != ECOInfiniteDomainState.READY) {
            throw new IllegalStateException("Infinite-storage domain is not ready: " + state);
        }
    }

    private static ParsedData parse(
        CompoundTag tag,
        HolderLookup.Provider registries,
        UUID expectedDomainId
    ) {
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
        Set<String> parsedFingerprints = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.contains(TAG_KEY, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Infinite-storage entry is missing its AEKey");
            }
            CompoundTag encodedKey = entry.getCompound(TAG_KEY);
            String keyFingerprint = ECOStorageKeyHash.stableFingerprint(encodedKey);
            if (!parsedFingerprints.add(keyFingerprint)) {
                throw new IllegalArgumentException("Duplicate AEKey in infinite-storage SavedData");
            }

            AEKey key;
            try {
                key = AEKey.fromTagGeneric(registries, encodedKey);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Infinite-storage entry contains an invalid AEKey", e);
            }
            boolean hasLong = entry.contains(TAG_AMOUNT_LONG, Tag.TAG_LONG);
            boolean hasWide = entry.contains(TAG_AMOUNT_WIDE, Tag.TAG_BYTE_ARRAY);
            if (hasLong == hasWide) {
                throw new IllegalArgumentException("Infinite-storage entry must contain exactly one amount encoding");
            }

            HugeAmount amount;
            if (hasLong) {
                long value = entry.getLong(TAG_AMOUNT_LONG);
                if (value <= 0L) {
                    throw new IllegalArgumentException("Infinite-storage long amount must be positive");
                }
                amount = HugeAmount.of(value);
            } else {
                byte[] encodedAmount = entry.getByteArray(TAG_AMOUNT_WIDE);
                if (encodedAmount.length == 0) {
                    throw new IllegalArgumentException("Infinite-storage wide amount is empty");
                }
                BigInteger value = new BigInteger(encodedAmount);
                if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0
                        || !Arrays.equals(encodedAmount, value.toByteArray())) {
                    throw new IllegalArgumentException("Infinite-storage wide amount is not canonical");
                }
                amount = HugeAmount.of(value);
            }
            if (key == null) {
                throw new IllegalArgumentException("Infinite-storage entry contains an undecodable AEKey");
            }
            if (!isResolved(key)) {
                parsedOrphanedEntries.put(
                    keyFingerprint,
                    new OrphanedStack(encodedKey.copy(), amount)
                );
            } else {
                if (parsedAmounts.putIfAbsent(key, amount) != null) {
                    throw new IllegalArgumentException("Duplicate AEKey in infinite-storage SavedData");
                }
                parsedKeys.put(key, encodedKey.copy());
            }
        }

        Set<UUID> receiptIds = new HashSet<>();
        Set<UUID> legacyReceipts = new HashSet<>();
        Map<UUID, String> verifiedReceipts = new HashMap<>();
        for (int i = 0; i < receiptTags.size(); i++) {
            CompoundTag receipt = receiptTags.getCompound(i);
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
            Set.copyOf(legacyReceipts),
            Map.copyOf(verifiedReceipts),
            tag.getLong(TAG_REVISION),
            fingerprint,
            acknowledgedOrphanedFingerprint
        );
    }

    private static ListTag requireCompoundList(CompoundTag tag, String key) {
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list)
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Infinite-storage SavedData contains an invalid " + key + " list");
        }
        return list;
    }

    private static String describeKeys(ParsedData data) {
        List<String> keys = new ArrayList<>();
        data.encodedKeys().forEach((key, encoded) -> keys.add(
            key.getId() + "=" + ECOStorageKeyHash.stableFingerprint(encoded)
        ));
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
        Set<UUID> legacyTransferReceipts,
        Map<UUID, String> transferReceipts,
        long revision,
        @Nullable String legacyFingerprint,
        @Nullable String acknowledgedOrphanedFingerprint
    ) {
    }

    private static final class MutableTypeStats {
        private long storedTypes;
        private final WideAmount storedAmount = WideAmount.of(0L);
    }
}
