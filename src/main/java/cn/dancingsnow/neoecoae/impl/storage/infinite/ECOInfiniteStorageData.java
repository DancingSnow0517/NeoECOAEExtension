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
 * World data for a single ECO infinite storage domain. This is the only place domain contents live; it is loaded and
 * saved by the vanilla {@code DimensionDataStorage} of the overworld along with the rest of the world.
 */
public final class ECOInfiniteStorageData extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInfiniteStorageData.class);

    public static final int CURRENT_VERSION = 1;

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
        /** Trusted main dataset, but some entries are only kept as verbatim {@link RawEntry raw entries}. */
        DEGRADED,
        /** Trusted in-memory data, but the last save attempt failed; writes are refused until it succeeds again. */
        RECOVERY_READ_ONLY,
        /** No trustworthy data view could be constructed at all; the domain stays locked until repaired. */
        UNAVAILABLE
    }

    private final Map<AEKey, HugeAmount> amounts = new HashMap<>();
    private final List<RawEntry> rawEntries = new ArrayList<>();
    private final Set<UUID> migrationReceipts = new LinkedHashSet<>();
    private final boolean loadedFromDisk;
    private boolean firstLookupPending = true;
    private boolean legacyImported;
    private boolean unreadable;
    private boolean writeUnsafe;
    private String lastFailureReason;
    private UUID domainId;

    private ECOInfiniteStorageData(boolean loadedFromDisk) {
        this.loadedFromDisk = loadedFromDisk;
    }

    public static ECOInfiniteStorageData createNew() {
        return new ECOInfiniteStorageData(false);
    }

    public static ECOInfiniteStorageData load(CompoundTag tag, HolderLookup.Provider registries) {
        ECOInfiniteStorageData data = new ECOInfiniteStorageData(true);
        int version = tag.getInt(TAG_VERSION);
        if (version != CURRENT_VERSION) {
            // Never throw here. DimensionDataStorage swallows load failures and hands back a fresh empty instance,
            // which would look exactly like "this domain is empty" and let the host unmount every member cell.
            LOGGER.error("Unsupported ECO infinite storage data version {}; refusing to use this domain", version);
            data.unreadable = true;
            return data;
        }
        data.legacyImported = tag.getBoolean(TAG_LEGACY_IMPORTED);

        ListTag entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        int unresolved = 0;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            try {
                CompoundTag keyTag = entry.getCompound(TAG_KEY);
                HugeAmount amount = HugeAmount.read(entry.getCompound(TAG_AMOUNT));
                if (amount.isZero()) {
                    continue;
                }
                AEKey key = AEKey.fromTagGeneric(registries, keyTag);
                if (key == null) {
                    data.rawEntries.add(new RawEntry(entry.copy()));
                    unresolved++;
                } else {
                    data.amounts.merge(key, amount, HugeAmount::add);
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
            data.migrationReceipts.add(NbtUtils.loadUUID(migration));
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
            entryTag.put(TAG_KEY, entry.getKey().toTagGeneric(registries));
            entryTag.put(TAG_AMOUNT, entry.getValue().write());
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
        tag.put(TAG_MIGRATIONS, migrations);
        return tag;
    }

    @Override
    public void save(File file, HolderLookup.Provider registries) {
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
        if (!isDirty()) {
            return;
        }
        if (unreadable) {
            // The on-disk file could not be understood. Writing our empty state over it would destroy the contents
            // an updated build might still be able to read.
            setDirty(false);
            return;
        }

        var targetPath = file.toPath().toAbsolutePath();
        var tempFile = targetPath.getParent().resolve(file.getName() + ".temp");

        CompoundTag compoundTag = new CompoundTag();
        compoundTag.put("data", save(new CompoundTag(), registries));
        NbtUtils.addCurrentDataVersion(compoundTag);
        NbtIo.writeCompressed(compoundTag, tempFile);
        try {
            Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        setDirty(false);
    }

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
        setDirty();
    }

    public void addRawEntry(RawEntry entry) {
        rawEntries.add(entry);
        setDirty();
    }

    public boolean isEmpty() {
        return amounts.isEmpty() && rawEntries.isEmpty();
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

    public void clearMigrationReceipts() {
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
        if (!rawEntries.isEmpty()) {
            return DomainStatus.DEGRADED;
        }
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
        return canWrite() && rawEntries.isEmpty();
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
