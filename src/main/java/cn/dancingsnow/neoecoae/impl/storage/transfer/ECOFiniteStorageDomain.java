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
    public static final int CURRENT_VERSION = 1;
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

    private ECOFiniteStorageDomain(List<ECOStorageShard> shards, ECOStorageInterfaceMode mode, Component description) {
        this.shards = List.copyOf(shards);
        this.mode = mode;
        this.description = description;
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
        IActionSource source
    ) {
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
                shards.add(new ECOStorageShard(shards.size(), drive, storage));
            }
        }
        ECOFiniteStorageDomain domain = new ECOFiniteStorageDomain(shards, mode, description);
        domain.rebuildIndex(source);
        return domain;
    }

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
            accepted = NEMath.saturatingAdd(accepted,
                shard.insert(plan.key(), request, Actionable.MODULATE, source));
            updateShardAmount(plan.key(), shard, shard.stored(plan.key(), source));
            remaining -= request;
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

    public boolean materialize(IActionSource source) {
        state = State.MATERIALIZING;
        try {
            for (ECOStorageShard shard : shards) {
                shard.storage().materializeDeferredChanges();
            }
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
        ListTag shardTags = new ListTag();
        for (ECOStorageShard shard : shards) {
            CompoundTag shardTag = new CompoundTag();
            shardTag.putLong(TAG_DRIVE, shard.drivePosition());
            shardTag.putString(TAG_FINGERPRINT, shard.fingerprint());
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

    public void restore(CompoundTag tag, HolderLookup.Provider registries, IActionSource source) {
        if (tag.getInt(TAG_VERSION) != CURRENT_VERSION) {
            throw new IllegalStateException("Unsupported finite transfer domain version " + tag.getInt(TAG_VERSION));
        }
        validateShards(tag);
        for (ECOStorageShard shard : shards) shard.storage().clearAllStoredStacks();
        for (Tag rawEntry : tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) rawEntry;
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound(TAG_KEY));
            if (key == null) throw new IllegalStateException("Unresolved key in finite transfer domain");
            for (Tag rawFragment : entry.getList(TAG_FRAGMENTS, Tag.TAG_COMPOUND)) {
                CompoundTag fragment = (CompoundTag) rawFragment;
                ECOStorageShard shard = shardsByPosition.get(fragment.getLong(TAG_DRIVE));
                long amount = fragment.getLong(TAG_AMOUNT);
                if (shard == null || amount <= 0L
                    || shard.insert(key, amount, Actionable.MODULATE, source) != amount) {
                    throw new IllegalStateException("Could not restore finite transfer shard");
                }
            }
        }
        state = State.valueOf(tag.getString(TAG_STATE));
        mode = ECOStorageInterfaceMode.valueOf(tag.getString(TAG_MODE));
        sourceEpoch = tag.getLong(TAG_SOURCE_EPOCH);
        revision = tag.getLong(TAG_REVISION);
        rebuildIndex(source);
    }

    private void validateShards(CompoundTag tag) {
        ListTag storedShards = tag.getList(TAG_SHARDS, Tag.TAG_COMPOUND);
        if (storedShards.size() != shards.size()) {
            throw new IllegalStateException("Finite transfer drive set changed while domain was active");
        }
        for (Tag raw : storedShards) {
            CompoundTag stored = (CompoundTag) raw;
            ECOStorageShard current = shardsByPosition.get(stored.getLong(TAG_DRIVE));
            if (current == null || !current.fingerprint().equals(stored.getString(TAG_FINGERPRINT))) {
                throw new IllegalStateException("Finite transfer drive fingerprint changed");
            }
        }
    }
}
