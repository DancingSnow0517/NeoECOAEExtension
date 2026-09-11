package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.util.NEMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Controller-owned in-memory view of all ordinary finite ECO cells while transfer mode is active. */
public final class ECOFiniteStorageDomain implements MEStorage {
    public static final int CURRENT_VERSION = 2;
    public static final int MAX_MASKED_SHARDS = Long.SIZE;

    public enum State { ACTIVE, MATERIALIZING }

    private static final String TAG_VERSION = "version";
    private static final String TAG_STATE = "state";
    private static final String TAG_MODE = "mode";
    private static final String TAG_SOURCE_EPOCH = "sourceEpoch";
    private static final String TAG_REVISION = "revision";
    private static final String TAG_SHARDS = "shards";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_KEY = "key";
    private static final String TAG_FRAGMENTS = "fragments";
    private static final String TAG_DRIVE = "drive";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_FINGERPRINT = "fingerprint";
    private static final String TAG_DOMAIN_ID = "domainId";
    private static final String TAG_CELL_ID = "cellId";
    private static final String TAG_GENERATION = "generation";

    private final List<ECOStorageShard> shards;
    private final Map<Long, ECOStorageShard> shardsByPosition;
    private final Map<AEKey, Object2LongMap<ECOStorageShard>> fragments = new HashMap<>();
    private final Object2LongMap<AEKey> totals = new Object2LongOpenHashMap<>();
    private final Map<AEKey, Long> candidateMasks = new HashMap<>();
    private final Component description;
    private State state = State.ACTIVE;
    private ECOStorageInterfaceMode mode;
    private long sourceEpoch;
    private long revision;
    private final java.util.UUID domainId;

    private ECOFiniteStorageDomain(List<ECOStorageShard> shards, ECOStorageInterfaceMode mode,
                                   Component description, java.util.UUID domainId) {
        this.shards = List.copyOf(shards);
        this.mode = mode;
        this.description = description;
        this.domainId = domainId;
        this.shardsByPosition = new HashMap<>();
        for (ECOStorageShard shard : shards) {
            shardsByPosition.put(shard.drivePosition(), shard);
            shard.storage().deferPersistence();
        }
    }

    public static ECOFiniteStorageDomain create(
        List<ECODriveBlockEntity> drives,
        IECOTier controllerTier,
        ECOStorageInterfaceMode mode,
        Component description,
        IActionSource source, CompoundTag recovery
    ) {
        boolean restoring = recovery != null && recovery.getInt(TAG_VERSION) == CURRENT_VERSION;
        java.util.UUID domainId = restoring && recovery.hasUUID(TAG_DOMAIN_ID)
            ? recovery.getUUID(TAG_DOMAIN_ID) : java.util.UUID.randomUUID();
        List<ECODriveBlockEntity> ordered = drives.stream()
            .sorted(Comparator.comparingLong(drive -> drive.getBlockPos().asLong()))
            .toList();
        List<ECOStorageShard> shards = new ArrayList<>();
        for (ECODriveBlockEntity drive : ordered) {
            IECOStorageCell inventory = drive.getCellInventory();
            if (inventory instanceof ECOStorageCell storage && controllerTier.compareTo(storage.getTier()) >= 0) {
                if (shards.size() >= MAX_MASKED_SHARDS) {
                    throw new IllegalStateException("Finite transfer domain supports at most 64 drives");
                }
                CompoundTag recoveryShard = restoring ? findRecoveryShard(recovery, drive.getBlockPos().asLong()) : null;
                shards.add(new ECOStorageShard(shards.size(), drive, storage, domainId, recoveryShard));
            }
        }
        ECOFiniteStorageDomain domain = new ECOFiniteStorageDomain(shards, mode, description, domainId);
        domain.rebuildIndex(source);
        return domain;
    }

    private static CompoundTag findRecoveryShard(CompoundTag recovery, long drivePosition) {
        for (Tag raw : recovery.getList(TAG_SHARDS, Tag.TAG_COMPOUND)) {
            CompoundTag shard = (CompoundTag) raw;
            if (shard.getLong(TAG_DRIVE) == drivePosition) return shard;
        }
        throw new IllegalStateException("Finite transfer recovery drive set changed");
    }

    public java.util.UUID domainId() { return domainId; }

    public State state() {
        return state;
    }

    public ECOStorageInterfaceMode mode() {
        return mode;
    }

    public void setMode(ECOStorageInterfaceMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            sourceEpoch++;
            candidateMasks.clear();
        }
    }

    public long revision() {
        return revision;
    }

    public long storedAmount(AEKey key) {
        return Math.max(0L, totals.getLong(key));
    }

    /** Rebuilds one key from physical shards after an exception-induced uncertain acknowledgement. */
    public long reconcileKey(AEKey key, IActionSource source) {
        for (ECOStorageShard shard : shards) {
            updateShardAmount(key, shard, shard.stored(key, source));
        }
        changed(key);
        return storedAmount(key);
    }

    public long sourceEpoch() {
        return sourceEpoch;
    }

    public int shardCount() {
        return shards.size();
    }

    static long maskForShardCount(int count) {
        if (count < 0 || count > MAX_MASKED_SHARDS) {
            throw new IllegalArgumentException("Shard count must be between 0 and 64");
        }
        return count == MAX_MASKED_SHARDS ? -1L : (1L << count) - 1L;
    }

    public long candidateMask(AEKey key, IActionSource source) {
        return candidateMasks.computeIfAbsent(key, ignored -> computeCandidateMask(key, source));
    }

    private long computeCandidateMask(AEKey key, IActionSource source) {
        long mask = 0L;
        for (ECOStorageShard shard : shards) {
            if (shard.insert(key, 1L, Actionable.SIMULATE, source) > 0L) {
                mask |= 1L << shard.index();
            }
        }
        return mask;
    }

    public ECOTransferTransaction reserveInsert(AEKey key, long amount, IActionSource source) {
        if (state != State.ACTIVE || amount <= 0L) {
            return new ECOTransferTransaction(ECOTransferPlan.empty(key));
        }
        List<ECOStorageAllocation> allocations = new ArrayList<>();
        long remaining = amount;
        Set<Integer> visited = new HashSet<>();
        Object2LongMap<ECOStorageShard> existing = fragments.get(key);
        if (existing != null) {
            for (ECOStorageShard shard : shards) {
                if (!existing.containsKey(shard)) continue;
                remaining = planInsert(shard, key, remaining, source, allocations);
                visited.add(shard.index());
                if (remaining == 0L) break;
            }
        }
        long candidates = candidateMask(key, source);
        while (remaining > 0L && candidates != 0L) {
            int index = Long.numberOfTrailingZeros(candidates);
            candidates &= candidates - 1L;
            if (visited.add(index)) {
                remaining = planInsert(shards.get(index), key, remaining, source, allocations);
            }
        }
        return new ECOTransferTransaction(new ECOTransferPlan(key, amount - remaining, allocations));
    }

    private static long planInsert(
        ECOStorageShard shard,
        AEKey key,
        long remaining,
        IActionSource source,
        List<ECOStorageAllocation> allocations
    ) {
        long accepted = shard.insert(key, remaining, Actionable.SIMULATE, source);
        if (accepted > 0L) {
            allocations.add(new ECOStorageAllocation(shard.index(), accepted));
            return remaining - accepted;
        }
        return remaining;
    }

    public long commitInsert(ECOTransferTransaction transaction, long amount, IActionSource source) {
        transaction.requireReserved();
        ECOTransferPlan plan = transaction.plan();
        long remaining = Math.min(amount, plan.amount());
        long accepted = 0L;
        for (ECOStorageAllocation allocation : plan.allocations()) {
            if (remaining <= 0L) break;
            long request = Math.min(remaining, allocation.amount());
            ECOStorageShard shard = shards.get(allocation.shardIndex());
            long actual = shard.insert(plan.key(), request, Actionable.MODULATE, source);
            if (actual < 0L || actual > request) throw new IllegalStateException("Invalid shard acknowledgement");
            accepted = NEMath.saturatingAdd(accepted, actual);
            updateShardAmount(plan.key(), shard, shard.stored(plan.key(), source));
            remaining -= actual;
        }
        transaction.committed();
        changed(plan.key());
        return accepted;
    }

    public ECOTransferTransaction reserveExtract(AEKey key, long amount, IActionSource source) {
        if (state != State.ACTIVE || amount <= 0L) {
            return new ECOTransferTransaction(ECOTransferPlan.empty(key));
        }
        List<ECOStorageAllocation> allocations = new ArrayList<>();
        long remaining = amount;
        Object2LongMap<ECOStorageShard> existing = fragments.get(key);
        if (existing != null) {
            for (ECOStorageShard shard : shards) {
                long stored = existing.getLong(shard);
                if (stored <= 0L) continue;
                long extracted = Math.min(remaining, stored);
                if (extracted > 0L) {
                    allocations.add(new ECOStorageAllocation(shard.index(), extracted));
                    remaining -= extracted;
                }
                if (remaining == 0L) break;
            }
        }
        return new ECOTransferTransaction(new ECOTransferPlan(key, amount - remaining, allocations));
    }

    public long commitExtract(ECOTransferTransaction transaction, long amount, IActionSource source) {
        transaction.requireReserved();
        ECOTransferPlan plan = transaction.plan();
        long remaining = Math.min(amount, plan.amount());
        long extracted = 0L;
        for (ECOStorageAllocation allocation : plan.allocations()) {
            if (remaining <= 0L) break;
            long request = Math.min(remaining, allocation.amount());
            ECOStorageShard shard = shards.get(allocation.shardIndex());
            long actual = shard.extract(plan.key(), request, Actionable.MODULATE, source);
            extracted = NEMath.saturatingAdd(extracted, actual);
            updateShardAmount(plan.key(), shard, Math.max(0L, shardAmount(plan.key(), shard) - actual));
            remaining -= actual;
        }
        transaction.committed();
        changed(plan.key());
        return extracted;
    }

    private void changed(AEKey key) {
        revision = revision == Long.MAX_VALUE ? 0L : revision + 1L;
        // Any mutation can change a shard's free-byte or free-type-slot status for other keys.
        candidateMasks.clear();
    }

    private void rebuildIndex(IActionSource source) {
        totals.clear();
        fragments.clear();
        for (ECOStorageShard shard : shards) {
            KeyCounter available = new KeyCounter();
            shard.storage().getAvailableStacks(available);
            for (Object2LongMap.Entry<AEKey> entry : available) {
                if (entry.getLongValue() > 0L) {
                    fragments.computeIfAbsent(entry.getKey(), ignored -> new Object2LongOpenHashMap<>())
                        .put(shard, entry.getLongValue());
                    totals.put(entry.getKey(), NEMath.saturatingAdd(totals.getLong(entry.getKey()), entry.getLongValue()));
                }
            }
        }
    }

    private long shardAmount(AEKey key, ECOStorageShard shard) {
        Object2LongMap<ECOStorageShard> keyFragments = fragments.get(key);
        return keyFragments == null ? 0L : keyFragments.getLong(shard);
    }

    private void updateShardAmount(AEKey key, ECOStorageShard shard, long amount) {
        long previous = shardAmount(key, shard);
        Object2LongMap<ECOStorageShard> keyFragments = fragments.get(key);
        if (amount <= 0L) {
            if (keyFragments != null) {
                keyFragments.removeLong(shard);
                if (keyFragments.isEmpty()) fragments.remove(key);
            }
        } else {
            if (keyFragments == null) {
                keyFragments = new Object2LongOpenHashMap<>();
                fragments.put(key, keyFragments);
            }
            keyFragments.put(shard, amount);
        }
        long withoutPrevious = Math.max(0L, totals.getLong(key) - previous);
        long total = NEMath.saturatingAdd(withoutPrevious, Math.max(0L, amount));
        if (total == 0L) totals.removeLong(key);
        else totals.put(key, total);
    }

    @Override
    public long insert(AEKey key, long amount, Actionable action, IActionSource source) {
        ECOTransferTransaction transaction = reserveInsert(key, amount, source);
        if (action == Actionable.SIMULATE) {
            transaction.rollback();
            return transaction.plan().amount();
        }
        return commitInsert(transaction, transaction.plan().amount(), source);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable action, IActionSource source) {
        ECOTransferTransaction transaction = reserveExtract(key, amount, source);
        if (action == Actionable.SIMULATE) {
            transaction.rollback();
            return transaction.plan().amount();
        }
        return commitExtract(transaction, transaction.plan().amount(), source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (Object2LongMap.Entry<AEKey> entry : totals.object2LongEntrySet()) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public Component getDescription() {
        return description;
    }

    public boolean materializePhaseA() {
        state = State.MATERIALIZING;
        try {
            for (ECOStorageShard shard : shards) {
                shard.materialize(domainId);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean verifyMaterialized() {
        try {
            KeyCounter verified = new KeyCounter();
            for (ECOStorageShard shard : shards) {
                shard.storage().getAvailableStacks(verified);
            }
            for (Object2LongMap.Entry<AEKey> entry : totals.object2LongEntrySet()) {
                if (verified.get(entry.getKey()) != entry.getLongValue()) return false;
            }
            return verified.size() == totals.size();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_VERSION, CURRENT_VERSION);
        tag.putString(TAG_STATE, state.name());
        tag.putString(TAG_MODE, mode.name());
        tag.putLong(TAG_SOURCE_EPOCH, sourceEpoch);
        tag.putLong(TAG_REVISION, revision);
        tag.putUUID(TAG_DOMAIN_ID, domainId);
        ListTag shardTags = new ListTag();
        for (ECOStorageShard shard : shards) {
            CompoundTag shardTag = new CompoundTag();
            shardTag.putLong(TAG_DRIVE, shard.drivePosition());
            shardTag.putString(TAG_FINGERPRINT, shard.fingerprint());
            shardTag.putUUID(TAG_CELL_ID, shard.cellId());
            shardTag.putLong(TAG_GENERATION, shard.leaseGeneration());
            shardTags.add(shardTag);
        }
        tag.put(TAG_SHARDS, shardTags);
        ListTag entries = new ListTag();
        for (Map.Entry<AEKey, Object2LongMap<ECOStorageShard>> entry : fragments.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put(TAG_KEY, entry.getKey().toTagGeneric(registries));
            ListTag fragmentTags = new ListTag();
            for (Object2LongMap.Entry<ECOStorageShard> fragment : entry.getValue().object2LongEntrySet()) {
                CompoundTag fragmentTag = new CompoundTag();
                fragmentTag.putLong(TAG_DRIVE, fragment.getKey().drivePosition());
                fragmentTag.putLong(TAG_AMOUNT, fragment.getLongValue());
                fragmentTags.add(fragmentTag);
            }
            entryTag.put(TAG_FRAGMENTS, fragmentTags);
            entries.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }

    public RestoreResult restore(CompoundTag tag, HolderLookup.Provider registries, IActionSource source) {
        if (tag.getInt(TAG_VERSION) == 1) return restoreLegacyIfAlreadyMaterialized(tag, registries);
        if (tag.getInt(TAG_VERSION) != CURRENT_VERSION || !tag.hasUUID(TAG_DOMAIN_ID)
            || !domainId.equals(tag.getUUID(TAG_DOMAIN_ID))) {
            throw new IllegalStateException("Unsupported or mismatched finite transfer domain snapshot");
        }
        validateShards(tag);
        Map<ECOStorageShard, KeyCounter> desired = decodeAndPreflight(tag, registries);
        boolean allHandedOff = true;
        boolean hasNewerState = false;
        for (ECOStorageShard shard : shards) {
            ECOFiniteCellMetadata.State metadata = shard.metadata();
            long expected = shard.leaseGeneration() == Long.MAX_VALUE ? Long.MAX_VALUE : shard.leaseGeneration() + 1L;
            boolean active = domainId.equals(metadata.leaseId()) && !metadata.leaseCommitted()
                && metadata.leaseGeneration() == shard.leaseGeneration()
                && metadata.generation() == shard.leaseGeneration();
            boolean committed = domainId.equals(metadata.leaseId()) && metadata.leaseCommitted()
                && metadata.leaseGeneration() == shard.leaseGeneration() && metadata.generation() == expected;
            boolean newer = metadata.generation() >= expected && !active && !committed;
            if (!active && !committed && !newer) {
                throw new IllegalStateException("Finite transfer cell generation changed before restore");
            }
            if (committed && !sameContents(shard, desired.get(shard))) {
                throw new IllegalStateException("Committed finite transfer cell contents do not match snapshot");
            }
            allHandedOff &= committed || newer;
            hasNewerState |= newer;
        }
        if (allHandedOff) return RestoreResult.ALREADY_MATERIALIZED;
        if (hasNewerState) {
            throw new IllegalStateException("Finite transfer snapshot spans active and newer cell generations");
        }

        Map<ECOStorageShard, KeyCounter> originals = snapshotContents();
        try {
            for (ECOStorageShard shard : shards) {
                if (shard.metadata().leaseCommitted()) continue;
                replaceContents(shard, desired.get(shard), source);
            }
        } catch (RuntimeException failure) {
            try {
                for (ECOStorageShard shard : shards) replaceContents(shard, originals.get(shard), source);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        state = State.valueOf(tag.getString(TAG_STATE));
        mode = ECOStorageInterfaceMode.valueOf(tag.getString(TAG_MODE));
        sourceEpoch = tag.getLong(TAG_SOURCE_EPOCH);
        revision = tag.getLong(TAG_REVISION);
        rebuildIndex(source);
        return RestoreResult.RESTORED;
    }

    public enum RestoreResult { RESTORED, ALREADY_MATERIALIZED }

    private Map<ECOStorageShard, KeyCounter> decodeAndPreflight(CompoundTag tag, HolderLookup.Provider registries) {
        Map<ECOStorageShard, KeyCounter> desired = new HashMap<>();
        for (ECOStorageShard shard : shards) desired.put(shard, new KeyCounter());
        long[] storedTypes = new long[shards.size()];
        long[] storedAmounts = new long[shards.size()];
        for (Tag rawEntry : tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) rawEntry;
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound(TAG_KEY));
            if (key == null) throw new IllegalStateException("Unresolved key in finite transfer domain");
            for (Tag rawFragment : entry.getList(TAG_FRAGMENTS, Tag.TAG_COMPOUND)) {
                CompoundTag fragment = (CompoundTag) rawFragment;
                ECOStorageShard shard = shardsByPosition.get(fragment.getLong(TAG_DRIVE));
                long amount = fragment.getLong(TAG_AMOUNT);
                if (shard == null || amount <= 0L) throw new IllegalStateException("Invalid finite transfer fragment");
                KeyCounter contents = desired.get(shard);
                long currentAmount = contents.get(key);
                if (currentAmount != 0L) throw new IllegalStateException("Duplicate finite transfer fragment");
                int shardIndex = shard.index();
                if (shard.storage().simulateInsertForMigration(key, amount, currentAmount,
                    storedTypes[shardIndex], storedAmounts[shardIndex]) != amount)
                    throw new IllegalStateException("Finite transfer snapshot exceeds cell capacity");
                contents.add(key, amount);
                if (currentAmount <= 0L) {
                    storedTypes[shardIndex] = NEMath.saturatingAdd(storedTypes[shardIndex], 1L);
                }
                storedAmounts[shardIndex] = NEMath.saturatingAdd(storedAmounts[shardIndex], amount);
            }
        }
        return desired;
    }

    private Map<ECOStorageShard, KeyCounter> snapshotContents() {
        Map<ECOStorageShard, KeyCounter> result = new HashMap<>();
        for (ECOStorageShard shard : shards) {
            KeyCounter contents = new KeyCounter();
            shard.storage().getAvailableStacks(contents);
            result.put(shard, contents);
        }
        return result;
    }

    private static boolean sameContents(ECOStorageShard shard, KeyCounter expected) {
        KeyCounter actual = new KeyCounter();
        shard.storage().getAvailableStacks(actual);
        if (actual.size() != expected.size()) return false;
        for (Object2LongMap.Entry<AEKey> entry : expected) {
            if (actual.get(entry.getKey()) != entry.getLongValue()) return false;
        }
        return true;
    }

    private static void replaceContents(ECOStorageShard shard, KeyCounter contents, IActionSource source) {
        shard.storage().clearAllStoredStacks();
        for (Object2LongMap.Entry<AEKey> entry : contents) {
            if (entry.getLongValue() <= 0L
                || shard.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source) != entry.getLongValue()) {
                throw new IllegalStateException("Could not apply finite transfer recovery snapshot");
            }
        }
    }

    private RestoreResult restoreLegacyIfAlreadyMaterialized(CompoundTag tag, HolderLookup.Provider registries) {
        validateLegacyShardProducts(tag);
        Map<ECOStorageShard, KeyCounter> desired = decodeAndPreflight(tag, registries);
        for (ECOStorageShard shard : shards) {
            if (!sameContents(shard, desired.get(shard))) {
                throw new IllegalStateException("Legacy finite transfer snapshot differs from drive state; refusing destructive restore");
            }
        }
        state = State.MATERIALIZING;
        return RestoreResult.RESTORED;
    }

    private void validateShards(CompoundTag tag) {
        ListTag storedShards = tag.getList(TAG_SHARDS, Tag.TAG_COMPOUND);
        if (storedShards.size() != shards.size()) {
            throw new IllegalStateException("Finite transfer drive set changed while domain was active");
        }
        for (Tag raw : storedShards) {
            CompoundTag stored = (CompoundTag) raw;
            ECOStorageShard current = shardsByPosition.get(stored.getLong(TAG_DRIVE));
            if (current == null || !stored.hasUUID(TAG_CELL_ID)
                || !current.cellId().equals(stored.getUUID(TAG_CELL_ID))
                || current.leaseGeneration() != stored.getLong(TAG_GENERATION)
                || !current.fingerprint().equals(stored.getString(TAG_FINGERPRINT))) {
                throw new IllegalStateException("Finite transfer drive fingerprint changed");
            }
        }
    }

    private void validateLegacyShardProducts(CompoundTag tag) {
        ListTag storedShards = tag.getList(TAG_SHARDS, Tag.TAG_COMPOUND);
        if (storedShards.size() != shards.size()) throw new IllegalStateException("Legacy finite transfer drive set changed");
        for (Tag raw : storedShards) {
            CompoundTag stored = (CompoundTag) raw;
            ECOStorageShard current = shardsByPosition.get(stored.getLong(TAG_DRIVE));
            if (current == null || !current.fingerprint().startsWith(stored.getString(TAG_FINGERPRINT) + ":"))
                throw new IllegalStateException("Legacy finite transfer drive product changed");
        }
    }
}
