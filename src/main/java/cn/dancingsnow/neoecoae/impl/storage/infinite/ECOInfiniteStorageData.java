package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;

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

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.IOUtilities;
import org.slf4j.LoggerFactory;

/**
 * World-owned inventory and transfer receipts, saved together in one atomic snapshot by the vanilla
 * {@link net.minecraft.world.level.storage.DimensionDataStorage} cycle (autosave, {@code /save-all}, shutdown).
 */
public final class ECOInfiniteStorageData extends SavedData {
    public static final int CURRENT_VERSION = 3;

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
    private String failure;
    // Written by the IO worker when an asynchronous snapshot write fails; the next save retries.
    private volatile String writeFailure;
    private long revision;
    // Last sequence of the retired write-ahead journal already folded into this snapshot.
    private long journalSequence;
    private KeyCodec codec;

    public static ECOInfiniteStorageData createNew() {
        return new ECOInfiniteStorageData();
    }

    /**
     * Vanilla load path. The deserializer runs only when {@code dataFile} was read successfully; if vanilla failed to
     * read an existing file, the constructor fallback leaves the domain unreadable so that file is never overwritten.
     */
    public static SavedData.Factory<ECOInfiniteStorageData> factory(Path dataFile,
                                                                    java.util.function.Supplier<ECOInfiniteStorageData> fresh) {
        return new SavedData.Factory<>(() -> {
            if (Files.exists(dataFile)) {
                ECOInfiniteStorageData data = createNew();
                data.markUnreadable("Cannot read authoritative domain snapshot " + dataFile.getFileName()
                        + "; original file retained");
                return data;
            }
            return fresh.get();
        }, (tag, registries) -> {
            ECOInfiniteStorageData data = load(tag, registries);
            if (data.canRead()) {
                try {
                    // Upgrade worlds written by the old write-ahead journal; the next save folds it in.
                    replayJournal(data, dataFile, registries);
                } catch (IOException | RuntimeException e) {
                    data = createNew();
                    data.markUnreadable("Cannot replay legacy domain journal; original files retained: " + e);
                }
            }
            return data;
        });
    }

    public static ECOInfiniteStorageData load(CompoundTag tag, HolderLookup.Provider registries) {
        return load(tag, registries, null);
    }

    static ECOInfiniteStorageData load(CompoundTag tag, HolderLookup.Provider registries, KeyCodec codec) {
        ECOInfiniteStorageData data = createNew();
        data.codec = codec;
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
                AEKey key = codec == null ? AEKey.fromTagGeneric(registries, entry.getCompound("key"))
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
                            if (!target.hasUUID("target") || !target.contains("before", Tag.TAG_LONG)
                                    || !target.contains("after", Tag.TAG_LONG) || !target.contains("transferred", Tag.TAG_LONG)) {
                                throw new IllegalArgumentException("Incomplete restore target");
                            }
                            if (plan.put(target.getUUID("target"), new ECOInfiniteStorageEngine.RestoreTargetAmounts(
                                    target.getLong("before"), target.getLong("after"), target.getLong("transferred"))) != null) {
                                throw new IllegalArgumentException("Duplicate restore target");
                            }
                        }
                    }
                    if (!validRestorePlan(amount, targets, plan))
                        throw new IllegalArgumentException("Incomplete restore allocation");
                    restore = new Restore(entry.getUUID("restore"), Set.copyOf(targets), entry.getString("restoreFailure"), Map.copyOf(plan));
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
        return data;
    }

    private static boolean validList(CompoundTag tag, String name, int type) {
        return tag.get(name) instanceof ListTag list && (list.isEmpty() || list.getElementType() == type);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unreadable || incompleteLegacy) throw new IllegalStateException("Refusing to save an incomplete domain");
        tag.putInt("version", CURRENT_VERSION);
        ListTag entries = new ListTag();
        amounts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("key", codec == null ? key.toTagGeneric(registries) : codec.encode(key));
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
                    target.putLong("before", amounts.before());
                    target.putLong("after", amounts.after());
                    target.putLong("transferred", amounts.transferred());
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

    /**
     * Same contract as vanilla {@link SavedData#save(File, HolderLookup.Provider)}: serialize on the server thread,
     * write atomically on the IO worker. Unreadable or partially imported domains are never written, and a failed
     * write keeps the domain due for the next save.
     */
    @Override
    public void save(File file, HolderLookup.Provider registries) {
        if (unreadable || incompleteLegacy) return;
        if (!isDirty() && writeFailure == null) return;
        CompoundTag root = new CompoundTag();
        try {
            root.put("data", save(new CompoundTag(), registries));
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(ECOInfiniteStorageData.class).error("Cannot serialize infinite domain {}", file, e);
            writeFailure = e.toString();
            return;
        }
        NbtUtils.addCurrentDataVersion(root);
        Path target = file.toPath();
        IOUtilities.withIOWorker(() -> {
            try {
                IOUtilities.writeNbtCompressed(root, target);
                // The snapshot records journalSequence, so a journal left behind by a crash here is harmless.
                Files.deleteIfExists(journalPath(target.toAbsolutePath()));
                writeFailure = null;
            } catch (IOException | RuntimeException e) {
                if (writeFailure == null)
                    LoggerFactory.getLogger(ECOInfiniteStorageData.class).error("Cannot save infinite domain {}", file, e);
                writeFailure = e.toString();
            }
        });
        setDirty(false);
    }

    /** Structural change: also advances {@link #revision()} so revision-keyed caches refresh. */
    @Override
    public void setDirty() {
        revision++;
        super.setDirty();
    }

    public long revision() {
        return revision;
    }

    public String lastFailureReason() {
        String write = writeFailure;
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
        return canWrite() && writeFailure == null && rawEntries.isEmpty() && unresolvedMetadata.isEmpty();
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

    static void replayJournal(ECOInfiniteStorageData data, Path snapshot, HolderLookup.Provider registries)
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
                    record = NbtIo.read(recordInput, NbtAccounter.create(16L * 1024 * 1024));
                }
                long sequence = record.getLong("sequence");
                if (sequence <= data.journalSequence) continue;
                if (sequence != data.journalSequence + 1L) {
                    throw new IOException("Non-contiguous infinite-domain journal sequence " + sequence);
                }
                AEKey key = data.codec == null
                        ? AEKey.fromTagGeneric(registries, record.getCompound("key"))
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
        if (writeFailure != null || !rawEntries.isEmpty() || !unresolvedMetadata.isEmpty()
                || restores.values().stream().anyMatch(r -> !r.failure().isEmpty())) return DomainStatus.DEGRADED;
        return DomainStatus.HEALTHY;
    }

    public List<String> failures() {
        List<String> result = new ArrayList<>();
        String write = writeFailure;
        if (write != null) result.add("Last snapshot write failed, retrying on next save: " + write);
        if (failure != null) result.add(failure);
        if (!rawEntries.isEmpty()) result.add("Unresolved entries retained: " + rawEntries.size());
        if (!unresolvedMetadata.isEmpty()) result.add("Unresolved transfer metadata retained");
        restores.values().stream().map(Restore::failure).filter(s -> !s.isEmpty()).forEach(result::add);
        return result;
    }

    public void markUnreadable(String reason) {
        unreadable = true;
        failure = reason;
    }

    public void markIncompleteLegacy(List<String> failures) {
        incompleteLegacy = true;
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
        restores.put(key, new Restore(transaction, Set.copyOf(targets), "", Map.copyOf(plan)));
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
        for (var target : plan.values()) allocated = allocated.add(java.math.BigInteger.valueOf(target.transferred()));
        return allocated.equals(amount.toBigInteger().min(java.math.BigInteger.valueOf(Long.MAX_VALUE)));
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
        Restore restore = restores.remove(key);
        long transferred = 0L;
        for (var target : restore.plan().values()) {
            transferred = Math.addExact(transferred, target.transferred());
        }
        amounts.subtract(key, transferred);
        setDirty();
        return true;
    }

    public boolean canFinishRestore(AEKey key, UUID transaction) {
        Restore restore = restores.get(key);
        return canWrite() && restore != null && restore.failure().isEmpty() && restore.transaction().equals(transaction);
    }
}
