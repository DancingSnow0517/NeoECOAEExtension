package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import com.google.common.math.LongMath;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minecraft-native persistence for one ordinary ECO storage cell. */
final class SavedDataECOStorageBackend extends SavedData
        implements ECOStorageBackend, ECOSavedDataPersistence.Backend {
    private static final Logger LOGGER = LoggerFactory.getLogger(SavedDataECOStorageBackend.class);

    private static final int FORMAT_VERSION = 1;
    private static final String TAG_FORMAT = "format";
    private static final String TAG_CELL = "cell";
    private static final String TAG_REVISION = "revision";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_KEY = "key";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_LEGACY_FINGERPRINT = "legacy_fingerprint";
    private static final ResourceLocation AE2_MISSING_CONTENT =
            ResourceLocation.fromNamespaceAndPath("ae2", "missing_content");

    private final UUID storageId;
    private final DimensionDataStorage dataStorage;
    private final Path dataFile;
    private final Map<AEKey, Long> amounts = new LinkedHashMap<>();
    private final Map<AEKey, CompoundTag> encodedKeys = new LinkedHashMap<>();
    private final KeyCounter visibleStacks = new KeyCounter();

    private long storedAmount;
    private int storedTypes;
    private long revision;
    private boolean degraded;

    @Nullable private String failureReason;

    @Nullable private String legacyFingerprint;

    @Nullable private CompoundTag lastSerializedSnapshot;

    private SavedDataECOStorageBackend(UUID storageId, DimensionDataStorage dataStorage, Path dataFile) {
        this.storageId = storageId;
        this.dataStorage = dataStorage;
        this.dataFile = dataFile.toAbsolutePath().normalize();
        ECOSavedDataPersistence.register(this);
    }

    static SavedDataECOStorageBackend createNew(UUID storageId, DimensionDataStorage dataStorage, Path dataFile) {
        return new SavedDataECOStorageBackend(storageId, dataStorage, dataFile);
    }

    static SavedDataECOStorageBackend load(
            CompoundTag tag, UUID expectedStorageId, DimensionDataStorage dataStorage, Path dataFile) {
        ParsedData parsed = parse(tag, expectedStorageId);
        SavedDataECOStorageBackend backend = new SavedDataECOStorageBackend(expectedStorageId, dataStorage, dataFile);
        backend.amounts.putAll(parsed.amounts());
        backend.encodedKeys.putAll(parsed.encodedKeys());
        backend.revision = parsed.revision();
        backend.legacyFingerprint = parsed.legacyFingerprint();
        backend.lastSerializedSnapshot = tag.copy();
        backend.rebuildIndexes();
        return backend;
    }

    static SavedDataECOStorageBackend quarantined(
            UUID storageId,
            DimensionDataStorage dataStorage,
            Path dataFile,
            int summaryTypes,
            long summaryAmount,
            String reason) {
        SavedDataECOStorageBackend backend = new SavedDataECOStorageBackend(storageId, dataStorage, dataFile);
        backend.storedTypes = Math.max(0, summaryTypes);
        backend.storedAmount = Math.max(0L, summaryAmount);
        backend.degraded = true;
        backend.failureReason = reason;
        return backend;
    }

    synchronized void importLegacyFile(LegacyECOCellReader.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!amounts.isEmpty() || revision != 0L || legacyFingerprint != null) {
            throw new IllegalStateException("Cannot import legacy cell data into a non-empty SavedData cell");
        }
        replaceContents(snapshot.amounts());
        revision = Math.max(0L, snapshot.revision());
        legacyFingerprint = snapshot.sourceFingerprint();
        setDirty();
    }

    synchronized void importItemStackLegacy(List<GenericStack> stacks) {
        if (!amounts.isEmpty() || revision != 0L || degraded) {
            throw new IllegalStateException("Cannot import item-stack cell data into an existing SavedData cell");
        }
        Map<AEKey, Long> imported = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null || !isResolved(stack.what()) || stack.amount() <= 0L) {
                continue;
            }
            imported.merge(stack.what(), stack.amount(), LongMath::saturatedAdd);
        }
        replaceContents(imported);
        markMutated();
    }

    synchronized void importFork(Map<AEKey, Long> sourceAmounts, long sourceRevision) {
        if (!amounts.isEmpty() || revision != 0L || degraded) {
            throw new IllegalStateException("Cannot copy data into a non-empty SavedData cell");
        }
        replaceContents(sourceAmounts);
        revision = Math.max(0L, sourceRevision);
        setDirty();
    }

    synchronized Map<AEKey, Long> copyContents() {
        if (degraded) {
            throw new IllegalStateException("Cannot copy a quarantined ECO storage cell: " + failureReason);
        }
        return Map.copyOf(amounts);
    }

    synchronized long copyRevision() {
        return revision;
    }

    synchronized boolean isFreshEmpty() {
        return !degraded && amounts.isEmpty() && revision == 0L && legacyFingerprint == null;
    }

    @Nullable synchronized String legacyFingerprint() {
        return legacyFingerprint;
    }

    @Nullable synchronized String failureReason() {
        return failureReason;
    }

    synchronized void quarantine(String message, Throwable cause) {
        degraded = true;
        failureReason = message + ": " + cause.getMessage();
        LOGGER.error("{} {}", message, storageId, cause);
    }

    @Override
    public synchronized long insert(AEKey key, long amount, Actionable mode) {
        if (!canOperate(key, amount)) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            if (!ensureEncodedKey(key)) {
                return 0L;
            }
            long previous = amounts.getOrDefault(key, 0L);
            long next = LongMath.saturatedAdd(previous, amount);
            amounts.put(key, next);
            visibleStacks.set(key, next);
            storedAmount = LongMath.saturatedAdd(storedAmount, next - previous);
            if (previous <= 0L) {
                storedTypes++;
            }
            markMutated();
        }
        return amount;
    }

    @Override
    public synchronized long extract(AEKey key, long amount, Actionable mode) {
        if (!canOperate(key, amount)) {
            return 0L;
        }
        long previous = amounts.getOrDefault(key, 0L);
        long extracted = Math.min(previous, amount);
        if (extracted <= 0L) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            long next = previous - extracted;
            if (next == 0L) {
                amounts.remove(key);
                encodedKeys.remove(key);
                visibleStacks.remove(key);
                storedTypes = Math.max(0, storedTypes - 1);
            } else {
                amounts.put(key, next);
                visibleStacks.set(key, next);
            }
            storedAmount = Math.max(0L, storedAmount - extracted);
            markMutated();
        }
        return extracted;
    }

    @Override
    public synchronized long getAmount(AEKey key) {
        return degraded || key == null ? 0L : amounts.getOrDefault(key, 0L);
    }

    @Override
    public synchronized void getAvailableStacks(KeyCounter out) {
        if (!degraded) {
            out.addAll(visibleStacks);
        }
    }

    @Override
    public synchronized boolean isEmpty() {
        return !degraded && amounts.isEmpty();
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
    public boolean isLoaded() {
        return true;
    }

    @Override
    public void requestLoad() {}

    @Override
    public boolean loadBudgeted(long maxNanos) {
        return false;
    }

    @Override
    public synchronized boolean flushBudgeted(long maxNanos) {
        boolean pending = !degraded && isDirty();
        if (pending) {
            flushAndAwait();
        }
        return pending;
    }

    synchronized void flushAndAwait() {
        if (degraded) {
            return;
        }
        ECOSavedDataPersistence.flush(this);
    }

    @Override
    public synchronized void closeAndFlush() {
        flushAndAwait();
    }

    @Override
    public synchronized boolean isDegraded() {
        return degraded;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        tag.putInt(TAG_FORMAT, FORMAT_VERSION);
        tag.putUUID(TAG_CELL, storageId);
        tag.putLong(TAG_REVISION, revision);
        if (legacyFingerprint != null) {
            tag.putString(TAG_LEGACY_FINGERPRINT, legacyFingerprint);
        }
        ListTag entries = new ListTag();
        for (Map.Entry<AEKey, Long> entry : amounts.entrySet()) {
            CompoundTag encodedKey = encodedKeys.get(entry.getKey());
            if (encodedKey == null || entry.getValue() == null || entry.getValue() <= 0L) {
                throw new IllegalStateException("Invalid in-memory ECO storage SavedData entry");
            }
            CompoundTag encoded = new CompoundTag();
            encoded.put(TAG_KEY, encodedKey.copy());
            encoded.putLong(TAG_AMOUNT, entry.getValue());
            entries.add(encoded);
        }
        tag.put(TAG_ENTRIES, entries);
        lastSerializedSnapshot = tag.copy();
        return tag;
    }

    @Override
    public synchronized DimensionDataStorage dataStorage() {
        return dataStorage;
    }

    @Override
    public synchronized boolean needsPersistence() {
        return !degraded && isDirty();
    }

    @Override
    public synchronized void preparePersistence() throws Exception {
        Files.createDirectories(dataFile.getParent());
    }

    @Override
    public synchronized void verifyPersistence() throws Exception {
        verifyDiskSnapshot();
    }

    @Override
    public synchronized void persistenceFailed(Exception cause) {
        quarantine("Unable to persist and verify ECO storage SavedData", cause);
    }

    private boolean canOperate(@Nullable AEKey key, long amount) {
        return !degraded && isResolved(key) && amount > 0L;
    }

    private boolean ensureEncodedKey(AEKey key) {
        if (encodedKeys.containsKey(key)) {
            return true;
        }
        try {
            CompoundTag encoded = key.toTagGeneric();
            if (encoded == null || encoded.isEmpty()) {
                return false;
            }
            encodedKeys.put(key, encoded.copy());
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Unable to serialize ECO storage key {}; rejecting operation", key, e);
            return false;
        }
    }

    private void replaceContents(Map<AEKey, Long> source) {
        Map<AEKey, Long> importedAmounts = new LinkedHashMap<>();
        Map<AEKey, CompoundTag> importedKeys = new LinkedHashMap<>();
        for (Map.Entry<AEKey, Long> entry : source.entrySet()) {
            AEKey key = entry.getKey();
            Long amount = entry.getValue();
            if (!isResolved(key) || amount == null || amount <= 0L) {
                throw new IllegalArgumentException("Invalid imported ECO storage cell entry");
            }
            CompoundTag encoded;
            try {
                encoded = key.toTagGeneric();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Unable to encode imported ECO storage cell key", e);
            }
            if (encoded == null || encoded.isEmpty() || importedAmounts.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Duplicate imported ECO storage cell key");
            }
            importedKeys.put(key, encoded.copy());
        }
        amounts.clear();
        encodedKeys.clear();
        amounts.putAll(importedAmounts);
        encodedKeys.putAll(importedKeys);
        rebuildIndexes();
    }

    private void rebuildIndexes() {
        visibleStacks.clear();
        storedTypes = 0;
        storedAmount = 0L;
        for (Map.Entry<AEKey, Long> entry : amounts.entrySet()) {
            long amount = entry.getValue();
            visibleStacks.set(entry.getKey(), amount);
            storedTypes++;
            storedAmount = LongMath.saturatedAdd(storedAmount, amount);
        }
    }

    private void markMutated() {
        if (revision < Long.MAX_VALUE) {
            revision++;
        }
        setDirty();
    }

    private void verifyDiskSnapshot() throws Exception {
        if (!Files.isRegularFile(dataFile)) {
            throw new IllegalStateException("SavedData file is missing after save: " + dataFile);
        }
        CompoundTag root;
        try (InputStream input = Files.newInputStream(dataFile)) {
            root = NbtIo.readCompressed(input);
        }
        ParsedData persisted = parse(root.getCompound("data"), storageId);
        if (persisted.revision() != revision
                || !persisted.amounts().equals(amounts)
                || !persisted.encodedKeys().equals(encodedKeys)
                || !Objects.equals(persisted.legacyFingerprint(), legacyFingerprint)) {
            throw new IllegalStateException("SavedData read-back did not match the ECO storage cell snapshot");
        }
        if (lastSerializedSnapshot != null) {
            ParsedData expected = parse(lastSerializedSnapshot, storageId);
            if (!persisted.equals(expected)) {
                throw new IllegalStateException("SavedData read-back differed from its serialized ECO cell snapshot");
            }
        }
    }

    private static ParsedData parse(CompoundTag tag, UUID expectedStorageId) {
        if (!tag.contains(TAG_FORMAT, Tag.TAG_INT) || tag.getInt(TAG_FORMAT) != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported ECO storage SavedData format");
        }
        if (!tag.hasUUID(TAG_CELL) || !expectedStorageId.equals(tag.getUUID(TAG_CELL))) {
            throw new IllegalArgumentException("ECO storage SavedData cell mismatch");
        }
        if (!tag.contains(TAG_REVISION, Tag.TAG_LONG) || tag.getLong(TAG_REVISION) < 0L) {
            throw new IllegalArgumentException("Invalid ECO storage SavedData revision");
        }
        Tag rawEntries = tag.get(TAG_ENTRIES);
        if (!(rawEntries instanceof ListTag entries)
                || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid ECO storage SavedData entry list");
        }
        Map<AEKey, Long> parsedAmounts = new LinkedHashMap<>();
        Map<AEKey, CompoundTag> parsedKeys = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.contains(TAG_KEY, Tag.TAG_COMPOUND) || !entry.contains(TAG_AMOUNT, Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Incomplete ECO storage SavedData entry");
            }
            CompoundTag encodedKey = entry.getCompound(TAG_KEY);
            AEKey key = AEKey.fromTagGeneric(encodedKey);
            long amount = entry.getLong(TAG_AMOUNT);
            if (!isResolved(key) || amount <= 0L || parsedAmounts.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Invalid or duplicate ECO storage SavedData entry");
            }
            parsedKeys.put(key, encodedKey.copy());
        }
        String fingerprint = null;
        if (tag.contains(TAG_LEGACY_FINGERPRINT)) {
            if (!tag.contains(TAG_LEGACY_FINGERPRINT, Tag.TAG_STRING)
                    || !LegacyECOCellReader.isSha256(tag.getString(TAG_LEGACY_FINGERPRINT))) {
                throw new IllegalArgumentException("Invalid ECO storage legacy migration fingerprint");
            }
            fingerprint = tag.getString(TAG_LEGACY_FINGERPRINT);
        }
        return new ParsedData(
                Map.copyOf(parsedAmounts), Map.copyOf(parsedKeys), tag.getLong(TAG_REVISION), fingerprint);
    }

    private static boolean isResolved(@Nullable AEKey key) {
        return key != null && !AE2_MISSING_CONTENT.equals(key.getId());
    }

    private record ParsedData(
            Map<AEKey, Long> amounts,
            Map<AEKey, CompoundTag> encodedKeys,
            long revision,
            @Nullable String legacyFingerprint) {}
}
