package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.platform.nbt.NEAtomicNbtFile;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.LoggerFactory;

/**
 * World-owned inventory and transfer receipts, saved together in one atomic snapshot by the vanilla
 * {@link net.minecraft.world.level.storage.DimensionDataStorage} cycle (autosave, {@code /save-all}, shutdown).
 */
public final class ECOInfiniteStorageData extends cn.dancingsnow.neoecoae.impl.storage.persistence.StorageSavedData {
    public static final int CURRENT_VERSION = 4;

    public enum DomainStatus {HEALTHY, DEGRADED, RECOVERY_READ_ONLY, UNAVAILABLE}

    interface KeyCodec {
        CompoundTag encode(AEKey key);

        AEKey decode(CompoundTag tag);
    }

    private record Restore(UUID transaction, Set<UUID> targets, String failure,
                           Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan) {
    }

    final InfiniteStorageAmounts amounts = new InfiniteStorageAmounts();
    private final List<CompoundTag> rawEntries = new ArrayList<>();
    private final Set<UUID> migrationReceipts = new HashSet<>();
    private final Map<AEKey, Restore> restores = new HashMap<>();
    private CompoundTag unresolvedMetadata = new CompoundTag();
    private boolean unreadable;
    private boolean incompleteLegacy;
    private boolean mountedStorageAvailable = true;
    private String failure;
    // A failed snapshot stays dirty and is retried by the next vanilla save.
    private long revision;
    // Last sequence of the retired write-ahead journal already folded into this snapshot.
    private long journalSequence;
    private KeyCodec codec;
    private UUID domainIdentity;
    private final Map<AEKey, CompoundTag> encodedKeys = new HashMap<>();

    public static ECOInfiniteStorageData createNew() {
        return new ECOInfiniteStorageData();
    }

    /**
     * Vanilla load path. The deserializer runs only when {@code dataFile} was read successfully; if vanilla failed to
     * read an existing file, the constructor fallback leaves the domain unreadable so that file is never overwritten.
     */
    public record LoadFactory(java.util.function.Supplier<ECOInfiniteStorageData> constructor,
                              java.util.function.Function<CompoundTag, ECOInfiniteStorageData> deserializer) {}

    public static LoadFactory factory(Path dataFile,
                                                                    java.util.function.Supplier<ECOInfiniteStorageData> fresh) {
        return new LoadFactory(() -> {
            if (Files.exists(dataFile) || Files.exists(dataFile.resolveSibling(dataFile.getFileName() + ".bak"))) {
                ECOInfiniteStorageData data = createNew();
                data.markUnreadable("Cannot read authoritative domain snapshot " + dataFile.getFileName()
                        + "; original file retained");
                return data;
            }
            return fresh.get();
        }, tag -> {
            ECOInfiniteStorageData data = load(tag);
            if (data.canRead()) {
                try {
                    // Upgrade worlds written by the old write-ahead journal; the next save folds it in.
                    replayJournal(data, dataFile);
                } catch (IOException | RuntimeException e) {
                    data = createNew();
                    data.markUnreadable("Cannot replay legacy domain journal; original files retained: " + e);
                }
            }
            return data;
        });
    }

    public static ECOInfiniteStorageData load(CompoundTag tag) {
        return load(tag, null);
    }

    static ECOInfiniteStorageData load(CompoundTag tag, KeyCodec codec) {
        ECOInfiniteStorageData data = createNew();
        data.codec = codec;
        data.loadPersistence(tag);
        if (tag.contains("domainIdentity")) {
            if (!tag.hasUUID("domainIdentity")) { data.markUnreadable("Invalid domain identity"); return data; }
            data.domainIdentity = tag.getUUID("domainIdentity");
        }
        int version = tag.getInt("version");
        if (version < 1 || version > CURRENT_VERSION || tag.getBoolean("journal")) {
            data.markUnreadable("Unsupported or unresolved infinite storage format: " + version);
            return data;
        }
        if (!validList(tag, "entries", Tag.TAG_COMPOUND)) {
            data.markUnreadable("Missing or invalid inventory entries");
            return data;
        }
        data.unresolvedMetadata = tag.getCompound("unresolvedMetadata").copy();
        data.journalSequence = tag.getLong("journalSequence");
        for (Tag raw : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                CompoundTag amountTag = entry.getCompound("amount");
                if (amountTag.contains("big") ? !amountTag.contains("big", Tag.TAG_STRING)
                        : !amountTag.contains("long", Tag.TAG_LONG))
                    throw new IllegalArgumentException("Invalid amount");
                HugeAmount amount = HugeAmount.read(amountTag);
                AEKey key = codec == null ? AEKey.fromTagGeneric(entry.getCompound("key"))
                        : codec.decode(entry.getCompound("key"));
                if (key == null) throw new IllegalArgumentException("Unknown key");
                Restore restore = null;
                if (entry.contains("restore")) {
                    if (!entry.hasUUID("restore")) throw new IllegalArgumentException("Invalid restore transaction");
                    Set<UUID> targets = new HashSet<>();
                    if (entry.contains("restoreTargets")) {
                        if (!validList(entry, "restoreTargets", Tag.TAG_INT_ARRAY))
                            throw new IllegalArgumentException("Invalid restore targets");
                        for (Tag target : entry.getList("restoreTargets", Tag.TAG_INT_ARRAY))
                            targets.add(NbtUtils.loadUUID(target));
                    }
                    Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan = new HashMap<>();
                    if (entry.contains("restorePlan")) {
                        if (!validList(entry, "restorePlan", Tag.TAG_COMPOUND))
                            throw new IllegalArgumentException("Invalid restore plan");
                        for (Tag value : entry.getList("restorePlan", Tag.TAG_COMPOUND)) {
                            CompoundTag target = (CompoundTag) value;
                            if (!target.hasUUID("target") || !validExactAmount(target, "before")
                                    || !validExactAmount(target, "after") || !validExactAmount(target, "transferred")) {
                                throw new IllegalArgumentException("Incomplete restore target");
                            }
                            if (plan.put(target.getUUID("target"), new ECOInfiniteStorageEngine.RestoreTargetAmounts(
                                    readExactAmount(target, "before"), readExactAmount(target, "after"), readExactAmount(target, "transferred"))) != null) {
                                throw new IllegalArgumentException("Duplicate restore target");
                            }
                        }
                    }
                    if (!validRestorePlan(amount, targets, plan))
                        throw new IllegalArgumentException("Incomplete restore allocation");
                    restore = new Restore(entry.getUUID("restore"), targets, entry.getString("restoreFailure"), plan);
                }
                if (amount.isZero() && restore != null) throw new IllegalArgumentException("Empty reserved entry");
                // Decode all metadata before changing the authoritative quantity.
                data.amounts.set(key, data.amounts.get(key).add(amount));
                if (restore != null) data.restores.put(key, restore);
            } catch (RuntimeException e) {
                data.rawEntries.add(entry.copy());
            }
        }
        if (tag.contains("migrations") && !validList(tag, "migrations", Tag.TAG_INT_ARRAY)) {
            data.unresolvedMetadata.put("migrations", tag.get("migrations").copy());
        } else {
            ListTag invalid = new ListTag();
            for (Tag receipt : tag.getList("migrations", Tag.TAG_INT_ARRAY)) {
                try {
                    data.migrationReceipts.add(NbtUtils.loadUUID(receipt));
                } catch (RuntimeException e) {
                    invalid.add(receipt.copy());
                }
            }
            if (!invalid.isEmpty()) data.unresolvedMetadata.put("migrations", invalid);
        }
        data.refreshMountedStorageAvailability();
        return data;
    }

    private static boolean validExactAmount(CompoundTag tag, String name) {
        if (tag.contains(name, Tag.TAG_LONG)) return true;
        CompoundTag amount = tag.getCompound(name);
        return amount.contains("long", Tag.TAG_LONG) || amount.contains("big", Tag.TAG_STRING);
    }
    private static BigInteger readExactAmount(CompoundTag tag, String name) {
        return tag.contains(name, Tag.TAG_LONG) ? BigInteger.valueOf(tag.getLong(name)) : HugeAmount.read(tag.getCompound(name)).toBigInteger();
    }
    private static void writeExactAmount(CompoundTag tag, String name, BigInteger amount) {
        if (amount.bitLength() < 64) tag.putLong(name, amount.longValueExact()); else tag.put(name, HugeAmount.of(amount).write());
    }

    private static boolean validList(CompoundTag tag, String name, int type) {
        return tag.get(name) instanceof ListTag list && (list.isEmpty() || list.getElementType() == type);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (unreadable || incompleteLegacy) throw new IllegalStateException("Refusing to save an incomplete domain");
        tag.putInt("version", CURRENT_VERSION);
        if (domainIdentity != null) tag.putUUID("domainIdentity", domainIdentity);
        encodedKeys.keySet().removeIf(key -> amounts.visible(key) == 0);
        ListTag entries = new ListTag();
        amounts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("key", encodedKeys.computeIfAbsent(key, k -> codec == null ? k.toTagGeneric() : codec.encode(k)).copy());
            entry.put("amount", amount.write());
            Restore restore = restores.get(key);
            if (restore != null) {
                entry.putUUID("restore", restore.transaction());
                ListTag targets = new ListTag();
                restore.targets().forEach(id -> targets.add(NbtUtils.createUUID(id)));
                entry.put("restoreTargets", targets);
                ListTag plan = new ListTag();
                restore.plan().forEach((id, amounts) -> {
                    CompoundTag target = new CompoundTag();
                    target.putUUID("target", id);
                    writeExactAmount(target, "before", amounts.beforeExact());
                    writeExactAmount(target, "after", amounts.afterExact());
                    writeExactAmount(target, "transferred", amounts.transferredExact());
                    plan.add(target);
                });
                entry.put("restorePlan", plan);
                if (!restore.failure().isEmpty()) entry.putString("restoreFailure", restore.failure());
            }
            entries.add(entry);
        });
        rawEntries.forEach(entry -> entries.add(entry.copy()));
        tag.put("entries", entries);
        ListTag receipts = new ListTag();
        migrationReceipts.forEach(id -> receipts.add(NbtUtils.createUUID(id)));
        tag.put("migrations", receipts);
        if (!unresolvedMetadata.isEmpty()) tag.put("unresolvedMetadata", unresolvedMetadata.copy());
        tag.putLong("journalSequence", journalSequence);
        return tag;
    }

    @Override protected boolean canSaveSnapshot() { return !unreadable && !incompleteLegacy; }

    @Override protected void snapshotSaved() throws IOException {
        Files.deleteIfExists(journalPath(dataFile()));
    }

    /** Structural change: also advances {@link #revision()} so revision-keyed caches refresh. */
    @Override
    public void setDirty() {
        revision++;
        refreshMountedStorageAvailability();
        super.setDirty();
    }

    private void refreshMountedStorageAvailability() {
        mountedStorageAvailable = canWrite() && restores.isEmpty();
    }

    boolean canUseMountedStorage() { return mountedStorageAvailable; }

    public long revision() {
        return revision;
    }

    public String lastFailureReason() {
        String write = writeFailure();
        return write != null ? write : failure;
    }

    public HugeAmount getAmount(AEKey key) {
        return amounts.get(key);
    }

    public boolean isEmpty() {
        return !unreadable && !incompleteLegacy && amounts.isEmpty() && rawEntries.isEmpty() && restores.isEmpty();
    }

    public boolean canRead() {
        return !unreadable;
    }

    public void bindDomain(UUID identity) {
        if (domainIdentity != null && !domainIdentity.equals(identity)) {
            markUnreadable("Infinite domain identity differs from its SavedData name");
        } else if (domainIdentity == null && canRead()) { domainIdentity = identity; setDirty(); }
    }

    public boolean canRead(AEKey key) {
        return canRead() && !restores.containsKey(key);
    }

    public boolean canWrite() {
        return !unreadable && !incompleteLegacy;
    }

    public boolean canWrite(AEKey key) {
        return canWrite() && !restores.containsKey(key);
    }

    public boolean canExitOrRestore() {
        // Leaving infinite mode relies on the next snapshot landing; wait until a failed write has recovered.
        return canWrite() && writeFailure() == null && rawEntries.isEmpty() && unresolvedMetadata.isEmpty();
    }

    public boolean canMigrate() {
        return canExitOrRestore();
    }

    // Quantity changes only mark the snapshot due; they leave revision() alone so hot FE/item transfers don't
    // invalidate revision-keyed caches every operation. Callers bump the revision for structural changes.
    void add(AEKey key, long amount) {
        amounts.add(key, amount);
        setDirty(true);
    }

    void add(AEKey key, BigInteger amount) {
        amounts.add(key, amount);
        setDirty(true);
    }

    void subtract(AEKey key, long amount) {
        amounts.subtract(key, amount);
        setDirty(true);
    }

    public boolean hasMigrationReceipt(UUID transaction) {
        return migrationReceipts.contains(transaction);
    }

    public void addMigrationReceipt(UUID transaction) {
        if (migrationReceipts.add(transaction)) setDirty();
    }

    public void addMigrationReceipts(Set<UUID> transactions) {
        boolean changed = false;
        for (UUID transaction : transactions) {
            if (transaction != null) changed |= migrationReceipts.add(transaction);
        }
        if (changed) setDirty();
    }

    static void replayJournal(ECOInfiniteStorageData data, Path snapshot)
            throws IOException {
        Path path = journalPath(snapshot.toAbsolutePath());
        if (!Files.isRegularFile(path)) return;
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            while (true) {
                int length;
                try {
                    length = input.readInt();
                } catch (EOFException end) {
                    break;
                }
                if (length <= 0 || length > 16 * 1024 * 1024) {
                    throw new IOException("Invalid infinite-domain journal record length " + length);
                }
                byte[] payload = input.readNBytes(length);
                // An interrupted buffered FE batch can leave an unacknowledged tail.
                if (payload.length != length) break;
                CompoundTag record;
                try (DataInputStream recordInput = new DataInputStream(new ByteArrayInputStream(payload))) {
                    record = NbtIo.read(recordInput, new NbtAccounter(16L * 1024 * 1024));
                }
                long sequence = record.getLong("sequence");
                if (sequence <= data.journalSequence) continue;
                if (sequence != data.journalSequence + 1L) {
                    throw new IOException("Non-contiguous infinite-domain journal sequence " + sequence);
                }
                AEKey key = data.codec == null
                        ? AEKey.fromTagGeneric(record.getCompound("key"))
                        : data.codec.decode(record.getCompound("key"));
                long amount = record.getLong("amount");
                if (key == null || amount <= 0L) throw new IOException("Invalid infinite-domain journal change");
                if (record.getBoolean("added")) {
                    data.amounts.add(key, amount);
                } else {
                    if (data.amounts.get(key).compareTo(HugeAmount.of(amount)) < 0) {
                        throw new IOException("Infinite-domain journal extraction exceeds snapshot amount");
                    }
                    data.amounts.subtract(key, amount);
                }
                data.journalSequence = sequence;
                data.setDirty();
            }
        }
    }

    private static Path journalPath(Path snapshot) {
        return snapshot.resolveSibling(snapshot.getFileName() + ".journal");
    }

    public DomainStatus status() {
        if (unreadable) return DomainStatus.UNAVAILABLE;
        if (incompleteLegacy) return DomainStatus.RECOVERY_READ_ONLY;
        if (writeFailure() != null || !rawEntries.isEmpty() || !unresolvedMetadata.isEmpty()
                || restores.values().stream().anyMatch(r -> !r.failure().isEmpty())) return DomainStatus.DEGRADED;
        return DomainStatus.HEALTHY;
    }

    public List<String> failures() {
        List<String> result = new ArrayList<>();
        String write = writeFailure();
        if (write != null) result.add("Last snapshot write failed, retrying on next save: " + write);
        if (failure != null) result.add(failure);
        if (!rawEntries.isEmpty()) result.add("Unresolved entries retained: " + rawEntries.size());
        if (!unresolvedMetadata.isEmpty()) result.add("Unresolved transfer metadata retained");
        restores.values().stream().map(Restore::failure).filter(s -> !s.isEmpty()).forEach(result::add);
        return result;
    }

    public void markUnreadable(String reason) {
        unreadable = true;
        refreshMountedStorageAvailability();
        failure = reason;
    }

    public void markIncompleteLegacy(List<String> failures) {
        incompleteLegacy = true;
        refreshMountedStorageAvailability();
        failure = String.join("; ", failures);
    }

    public boolean reserveRestore(AEKey key, UUID transaction, Set<UUID> targets,
                                  Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan) {
        if (!canReserveRestore(key, transaction, targets, plan)) return false;
        Restore existing = restores.get(key);
        if (existing != null) {
            if (!existing.plan().isEmpty()) return true;
            // Old reservations have no target quantities. Upgrade them before touching any target.
        }
        restores.put(key, new Restore(transaction, targets, "", plan));
        setDirty();
        return true;
    }

    public boolean canReserveRestore(AEKey key, UUID transaction, Set<UUID> targets,
                                     Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan) {
        if (!canWrite() || amounts.visible(key) == 0 || !validRestorePlan(getAmount(key), targets, plan)) return false;
        Restore existing = restores.get(key);
        return existing == null || (existing.failure().isEmpty() && existing.transaction().equals(transaction)
                && targets.containsAll(existing.targets()) && (existing.plan().isEmpty() || existing.plan().equals(plan)));
    }

    private static boolean validRestorePlan(HugeAmount amount, Set<UUID> targets,
                                            Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan) {
        if (plan.isEmpty()) return true; // v1/v2 reservation, upgraded before the next target mutation
        if (!targets.containsAll(plan.keySet())) return false;
        java.math.BigInteger allocated = java.math.BigInteger.ZERO;
        for (var target : plan.values()) allocated = allocated.add(target.transferredExact());
        return allocated.equals(amount.toBigInteger());
    }

    public Set<UUID> restoreTargetIds(AEKey key) {
        Restore restore = restores.get(key);
        return restore == null ? Set.of() : restore.targets();
    }

    public UUID restoreTransaction(AEKey key) {
        Restore restore = restores.get(key);
        return restore == null ? null : restore.transaction();
    }

    public boolean hasPendingRestore() {
        return !restores.isEmpty();
    }

    public void failRestore(AEKey key, String reason) {
        Restore restore = restores.get(key);
        if (restore != null) {
            restores.put(key, new Restore(restore.transaction(), restore.targets(), reason, restore.plan()));
            setDirty();
        }
    }

    public Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> restorePlan(AEKey key) {
        Restore restore = restores.get(key);
        return restore == null ? Map.of() : restore.plan();
    }

    public boolean finishRestore(AEKey key, UUID transaction) {
        if (!canFinishRestore(key, transaction)) return false;
        Restore restore = restores.get(key);
        BigInteger transferred = BigInteger.ZERO;
        for (var target : restore.plan().values()) transferred = transferred.add(target.transferredExact());
        HugeAmount remaining = HugeAmount.of(amounts.get(key).toBigInteger().subtract(transferred));
        amounts.set(key, remaining);
        restores.remove(key);
        setDirty();
        return true;
    }

    public boolean canFinishRestore(AEKey key, UUID transaction) {
        Restore restore = restores.get(key);
        return canWrite() && restore != null && restore.failure().isEmpty() && restore.transaction().equals(transaction);
    }
}
