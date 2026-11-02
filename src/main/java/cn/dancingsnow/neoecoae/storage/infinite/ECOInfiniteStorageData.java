package cn.dancingsnow.neoecoae.storage.infinite;

import appeng.api.stacks.AEKey;

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
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
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
    private boolean mountedStorageAvailable = true;
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
     * Vanilla load path. The codec runs only when {@code dataFile} was read successfully; if vanilla failed to
     * read an existing file, the constructor fallback leaves the domain unreadable so that file is never overwritten.
     *
     * <p>26.1 port: {@code SavedData.Factory} was replaced by {@link SavedDataType} (codec based).
     */
    public static SavedDataType<ECOInfiniteStorageData> type(net.minecraft.resources.Identifier id, Path dataFile,
                                                             HolderLookup.Provider serverRegistries,
                                                             java.util.function.Supplier<ECOInfiniteStorageData> fresh) {
        return new SavedDataType<>(id, level -> {
            if (Files.exists(dataFile)) {
                ECOInfiniteStorageData data = createNew();
                data.markUnreadable("Cannot read authoritative domain snapshot " + dataFile.getFileName()
                        + "; original file retained");
                return data;
            }
            return fresh.get();
        }, level -> {
            HolderLookup.Provider registries = level != null ? level.registryAccess() : serverRegistries;
            return CompoundTag.CODEC.xmap(tag -> loadWithJournal(tag, registries, dataFile),
                    data -> data.save(new CompoundTag(), registries));
        });
    }

    static ECOInfiniteStorageData loadWithJournal(CompoundTag tag, HolderLookup.Provider registries, Path dataFile) {
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
    }

    public static ECOInfiniteStorageData load(CompoundTag tag, HolderLookup.Provider registries) {
        return load(tag, registries, null);
    }

    static ECOInfiniteStorageData load(CompoundTag tag, HolderLookup.Provider registries, KeyCodec codec) {
        ECOInfiniteStorageData data = createNew();
        data.codec = codec;
        int version = tag.getIntOr("version", 0);
        if (version < 1 || version > CURRENT_VERSION || tag.getBooleanOr("journal", false)) {
            data.markUnreadable("Unsupported or unresolved infinite storage format: " + version);
            return data;
        }
        if (!validList(tag, "entries", Tag.TAG_COMPOUND)) {
            data.markUnreadable("Missing or invalid inventory entries");
            return data;
        }
        data.unresolvedMetadata = tag.getCompoundOrEmpty("unresolvedMetadata").copy();
        data.journalSequence = tag.getLongOr("journalSequence", 0L);
        for (Tag raw : tag.getListOrEmpty("entries")) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                CompoundTag amountTag = entry.getCompoundOrEmpty("amount");
                if (amountTag.contains("big") ? !amountTag.contains("big")
                        : !amountTag.contains("long"))
                    throw new IllegalArgumentException("Invalid amount");
                HugeAmount amount = HugeAmount.read(amountTag);
                AEKey key = codec == null ? cn.dancingsnow.neoecoae.crafting.ECOLegacyAENbt.readKey(registries, entry.getCompoundOrEmpty("key"))
                        : codec.decode(entry.getCompoundOrEmpty("key"));
                if (key == null) throw new IllegalArgumentException("Unknown key");
                Restore restore = null;
                if (entry.contains("restore")) {
                    if (!entry.read("restore", net.minecraft.core.UUIDUtil.CODEC).isPresent()) throw new IllegalArgumentException("Invalid restore transaction");
                    Set<UUID> targets = new HashSet<>();
                    if (entry.contains("restoreTargets")) {
                        if (!validList(entry, "restoreTargets", Tag.TAG_INT_ARRAY))
                            throw new IllegalArgumentException("Invalid restore targets");
                        for (Tag target : entry.getListOrEmpty("restoreTargets"))
                            targets.add(loadUUID(target));
                    }
                    Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> plan = new HashMap<>();
                    if (entry.contains("restorePlan")) {
                        if (!validList(entry, "restorePlan", Tag.TAG_COMPOUND))
                            throw new IllegalArgumentException("Invalid restore plan");
                        for (Tag value : entry.getListOrEmpty("restorePlan")) {
                            CompoundTag target = (CompoundTag) value;
                            if (!target.read("target", net.minecraft.core.UUIDUtil.CODEC).isPresent() || !target.contains("before")
                                    || !target.contains("after") || !target.contains("transferred")) {
                                throw new IllegalArgumentException("Incomplete restore target");
                            }
                            if (plan.put(target.read("target", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), new ECOInfiniteStorageEngine.RestoreTargetAmounts(
                                    target.getLongOr("before", 0L), target.getLongOr("after", 0L), target.getLongOr("transferred", 0L))) != null) {
                                throw new IllegalArgumentException("Duplicate restore target");
                            }
                        }
                    }
                    if (!validRestorePlan(amount, targets, plan))
                        throw new IllegalArgumentException("Incomplete restore allocation");
                    restore = new Restore(entry.read("restore", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), targets, entry.getStringOr("restoreFailure", ""), plan);
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
            for (Tag receipt : tag.getListOrEmpty("migrations")) {
                try {
                    data.migrationReceipts.add(loadUUID(receipt));
                } catch (RuntimeException e) {
                    invalid.add(receipt.copy());
                }
            }
            if (!invalid.isEmpty()) data.unresolvedMetadata.put("migrations", invalid);
        }
        data.refreshMountedStorageAvailability();
        return data;
    }

    private static boolean validList(CompoundTag tag, String name, int type) {
        if (!(tag.get(name) instanceof ListTag list)) return false;
        for (Tag element : list) {
            if (element.getId() != type) return false;
        }
        return true;
    }

    // 26.1 port: NbtUtils.loadUUID was removed; UUIDs are still int[4] tags.
    private static UUID loadUUID(Tag tag) {
        if (!(tag instanceof IntArrayTag array) || array.getAsIntArray().length != 4)
            throw new IllegalArgumentException("Invalid UUID tag " + tag);
        return UUIDUtil.uuidFromIntArray(array.getAsIntArray());
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unreadable || incompleteLegacy) throw new IllegalStateException("Refusing to save an incomplete domain");
        tag.putInt("version", CURRENT_VERSION);
        ListTag entries = new ListTag();
        amounts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("key", codec == null ? cn.dancingsnow.neoecoae.crafting.ECOLegacyAENbt.writeKey(registries, key) : codec.encode(key));
            entry.put("amount", amount.write());
            Restore restore = restores.get(key);
            if (restore != null) {
                entry.store("restore", net.minecraft.core.UUIDUtil.CODEC, restore.transaction());
                ListTag targets = new ListTag();
                restore.targets().forEach(id -> targets.add(new IntArrayTag(UUIDUtil.uuidToIntArray(id))));
                entry.put("restoreTargets", targets);
                ListTag plan = new ListTag();
                restore.plan().forEach((id, amounts) -> {
                    CompoundTag target = new CompoundTag();
                    target.store("target", net.minecraft.core.UUIDUtil.CODEC, id);
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
        migrationReceipts.forEach(id -> receipts.add(new IntArrayTag(UUIDUtil.uuidToIntArray(id))));
        tag.put("migrations", receipts);
        if (!unresolvedMetadata.isEmpty()) tag.put("unresolvedMetadata", unresolvedMetadata.copy());
        tag.putLong("journalSequence", journalSequence);
        return tag;
    }

    /**
     * 26.1 port: vanilla {@code SavedDataStorage} now owns the (asynchronous, atomic) snapshot write and there is no
     * per-data save hook any more. Unreadable or partially imported domains are kept permanently clean so the storage
     * never encodes or overwrites them.
     * TODO(26.1 port): write failures are no longer observable here ({@code writeFailure} stays null), and the retired
     * write-ahead journal is no longer deleted after a successful write (harmless: replay skips folded sequences).
     */
    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty && !unreadable && !incompleteLegacy);
    }

    /** Structural change: also advances {@link #revision()} so revision-keyed caches refresh. */
    @Override
    public void setDirty() {
        revision++;
        // Restore reservations and similar structural changes hide or reveal keys.
        amounts.touch();
        refreshMountedStorageAvailability();
        super.setDirty();
    }

    /**
     * A key appeared or disappeared through a quantity change that the amount table already logged: advances
     * {@link #revision()} like {@link #setDirty()} but keeps incremental listings valid.
     */
    void keySetChanged() {
        revision++;
        refreshMountedStorageAvailability();
        super.setDirty();
    }

    /** See {@link cn.dancingsnow.neoecoae.storage.StorageListingDelta}; false whenever a full listing is required. */
    boolean visitListingChanges(long since, long current, java.util.function.Consumer<AEKey> changed) {
        return canRead() && amounts.visitChangesSince(since, current, changed);
    }

    private void refreshMountedStorageAvailability() {
        mountedStorageAvailable = canWrite() && restores.isEmpty();
    }

    boolean canUseMountedStorage() { return mountedStorageAvailable; }

    public long revision() {
        return revision;
    }

    /** Advances whenever the visible listing may change, including plain quantity changes. */
    public long listingVersion() {
        return amounts.changes();
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

    /** The reason {@link #canMigrate()} is false, or {@code null} when it is true. */
    public ECOInfiniteStorageEngine.MigrationBlock migrationBlock() {
        if (unreadable) return block(ECOInfiniteStorageEngine.MigrationBlock.Kind.UNREADABLE, failure);
        if (incompleteLegacy) return block(ECOInfiniteStorageEngine.MigrationBlock.Kind.RECOVERY_READ_ONLY, failure);
        String write = writeFailure;
        if (write != null) return block(ECOInfiniteStorageEngine.MigrationBlock.Kind.WRITE_FAILED, write);
        if (!rawEntries.isEmpty()) {
            return block(ECOInfiniteStorageEngine.MigrationBlock.Kind.UNDECODABLE_ENTRIES,
                    String.valueOf(rawEntries.size()));
        }
        if (!unresolvedMetadata.isEmpty()) {
            return block(ECOInfiniteStorageEngine.MigrationBlock.Kind.UNRESOLVED_METADATA,
                    String.join(", ", unresolvedMetadata.keySet()));
        }
        return null;
    }

    private static ECOInfiniteStorageEngine.MigrationBlock block(ECOInfiniteStorageEngine.MigrationBlock.Kind kind,
                                                               String detail) {
        return new ECOInfiniteStorageEngine.MigrationBlock(kind, detail);
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
                long sequence = record.getLongOr("sequence", 0L);
                if (sequence <= data.journalSequence) continue;
                if (sequence != data.journalSequence + 1L) {
                    throw new IOException("Non-contiguous infinite-domain journal sequence " + sequence);
                }
                AEKey key = data.codec == null
                        ? cn.dancingsnow.neoecoae.crafting.ECOLegacyAENbt.readKey(registries, record.getCompoundOrEmpty("key"))
                        : data.codec.decode(record.getCompoundOrEmpty("key"));
                long amount = record.getLongOr("amount", 0L);
                if (key == null || amount <= 0L) throw new IOException("Invalid infinite-domain journal change");
                if (record.getBooleanOr("added", false)) {
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
        amounts.touch();
        refreshMountedStorageAvailability();
        failure = reason;
    }

    public void markIncompleteLegacy(List<String> failures) {
        incompleteLegacy = true;
        amounts.touch();
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
