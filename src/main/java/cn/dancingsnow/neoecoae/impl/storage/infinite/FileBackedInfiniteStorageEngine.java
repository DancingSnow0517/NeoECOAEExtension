package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageKeyHash;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBackedInfiniteStorageEngine implements ECOInfiniteStorageEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileBackedInfiniteStorageEngine.class);
    private static final int SHARD_COUNT = 256;
    private static final int WAL_VERSION = 1;
    private static final int MAX_WAL_RECORD_BYTES = 16 * 1024 * 1024;
    private static final HugeAmount LONG_MAX_AMOUNT = HugeAmount.of(Long.MAX_VALUE);

    private final UUID domainId;
    private final Path domainPath;
    private final Path walPath;
    private final Map<AEKey, HugeAmount> amounts = new HashMap<>();
    private final Map<AEKey, Long> loadedKeyRevisions = new HashMap<>();
    private final Map<AEKey, Integer> loadedKeySourceShards = new HashMap<>();
    private final KeyCounter visibleStacks = new KeyCounter();
    private final Map<AEKeyType, MutableTypeStats> typeStats = new HashMap<>();
    private final Map<AEKey, HugeAmount> hugeStacks = new HashMap<>();
    private final Map<AEKey, BigInteger> dirtyDeltas = new HashMap<>();
    private final Set<Integer> dirtyShards = new HashSet<>();
    private final long[] shardRevisions = new long[SHARD_COUNT];
    private List<TypeStats> typeStatsSnapshot = List.of();
    private boolean typeStatsSnapshotDirty = true;
    private List<HugeStack> hugeStacksSnapshot = List.of();
    private boolean hugeStacksSnapshotDirty = true;
    private HugeAmount storedAmount = HugeAmount.ZERO;
    private long revision;

    @Nullable private DataOutputStream walOut;

    public FileBackedInfiniteStorageEngine(UUID domainId, Path domainPath) {
        this.domainId = domainId;
        this.domainPath = domainPath;
        this.walPath = domainPath.resolve("wal_000.log");
        load();
    }

    @Override
    public synchronized long insert(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            applyDelta(key, BigInteger.valueOf(amount), true);
        }
        return amount;
    }

    @Override
    public synchronized long extract(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L) {
            return 0L;
        }
        HugeAmount current = getAmount(key);
        HugeAmount extracted = HugeAmount.of(amount).min(current);
        if (extracted.isZero()) {
            return 0L;
        }
        long visible = extracted.toLongSaturated();
        if (mode == Actionable.MODULATE) {
            applyDelta(key, BigInteger.valueOf(visible).negate(), true);
        }
        return visible;
    }

    @Override
    public synchronized HugeAmount getAmount(AEKey key) {
        HugeAmount amount = amounts.get(key);
        return amount == null ? HugeAmount.ZERO : amount;
    }

    @Override
    public synchronized void getAvailableStacks(KeyCounter out) {
        out.addAll(visibleStacks);
    }

    @Override
    public synchronized long getRevision() {
        return revision;
    }

    @Override
    public synchronized boolean isEmpty() {
        return amounts.isEmpty();
    }

    @Override
    public synchronized HugeAmount getStoredAmount() {
        return storedAmount;
    }

    @Override
    public synchronized int getStoredTypes() {
        return amounts.size();
    }

    @Override
    public synchronized Collection<TypeStats> getTypeStats() {
        if (!typeStatsSnapshotDirty) {
            return typeStatsSnapshot;
        }
        List<TypeStats> snapshot = new ArrayList<>(typeStats.size());
        for (Map.Entry<AEKeyType, MutableTypeStats> entry : typeStats.entrySet()) {
            MutableTypeStats stats = entry.getValue();
            if (stats.storedTypes > 0L && stats.storedAmount.signum() > 0) {
                snapshot.add(new TypeStats(entry.getKey(), stats.storedTypes, HugeAmount.of(stats.storedAmount)));
            }
        }
        typeStatsSnapshot = List.copyOf(snapshot);
        typeStatsSnapshotDirty = false;
        return typeStatsSnapshot;
    }

    @Override
    public synchronized Collection<HugeStack> getHugeStacks() {
        if (!hugeStacksSnapshotDirty) {
            return hugeStacksSnapshot;
        }
        List<HugeStack> snapshot = new ArrayList<>(hugeStacks.size());
        for (Map.Entry<AEKey, HugeAmount> entry : hugeStacks.entrySet()) {
            snapshot.add(new HugeStack(entry.getKey(), entry.getValue()));
        }
        snapshot.sort((left, right) -> right.amount().compareTo(left.amount()));
        hugeStacksSnapshot = List.copyOf(snapshot);
        hugeStacksSnapshotDirty = false;
        return hugeStacksSnapshot;
    }

    @Override
    public synchronized void flushBudgeted(long maxNanos) {
        if (dirtyShards.isEmpty()) {
            return;
        }
        long deadline = maxNanos <= 0L ? Long.MAX_VALUE : System.nanoTime() + maxNanos;
        Set<Integer> pending = new HashSet<>(dirtyShards);
        for (int shard : pending) {
            writeShard(shard);
            dirtyShards.remove(shard);
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        if (dirtyShards.isEmpty()) {
            truncateWal();
            dirtyDeltas.clear();
        }
    }

    @Override
    public synchronized void closeAndFlush() {
        if (!dirtyShards.isEmpty()) {
            for (int shard : new HashSet<>(dirtyShards)) {
                writeShard(shard);
            }
            dirtyShards.clear();
        }
        truncateWal();
        dirtyDeltas.clear();
        closeWalOutput();
    }

    private void applyDelta(AEKey key, BigInteger delta, boolean writeWal) {
        if (delta.signum() == 0) {
            return;
        }
        HugeAmount current = getAmount(key);
        BigInteger nextValue = current.toBigInteger().add(delta);
        if (nextValue.signum() < 0) {
            nextValue = BigInteger.ZERO;
        }
        HugeAmount next = HugeAmount.of(nextValue);
        if (next.isZero()) {
            amounts.remove(key);
        } else {
            amounts.put(key, next);
        }
        storedAmount = HugeAmount.of(storedAmount.toBigInteger().add(nextValue.subtract(current.toBigInteger())));
        updateIndexes(key, current, next);
        dirtyDeltas.merge(key, delta, BigInteger::add);
        dirtyShards.add(shardFor(key));
        revision = revision == Long.MAX_VALUE ? 0L : revision + 1L;
        if (writeWal) {
            appendWal(key, delta);
        }
    }

    private void load() {
        try {
            Files.createDirectories(domainPath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create ECO infinite domain directory " + domainPath, e);
        }
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            readShard(shard);
        }
        discardSupersededLegacyShardEntries();
        replayWal();
        Set<Integer> recoveredDirtyShards = new HashSet<>(dirtyShards);
        rebuildIndexes();
        loadedKeyRevisions.clear();
        loadedKeySourceShards.clear();
        dirtyDeltas.clear();
        dirtyShards.clear();
        dirtyShards.addAll(recoveredDirtyShards);
    }

    private void discardSupersededLegacyShardEntries() {
        amounts.entrySet().removeIf(entry -> {
            AEKey key = entry.getKey();
            int targetShard = shardFor(key);
            int sourceShard = loadedKeySourceShards.getOrDefault(key, targetShard);
            long sourceRevision = loadedKeyRevisions.getOrDefault(key, 0L);
            if (sourceShard != targetShard && shardRevisions[targetShard] > sourceRevision) {
                loadedKeyRevisions.put(key, shardRevisions[targetShard]);
                loadedKeySourceShards.put(key, targetShard);
                return true;
            }
            return false;
        });
    }

    private void rebuildIndexes() {
        visibleStacks.clear();
        typeStats.clear();
        hugeStacks.clear();
        hugeStacksSnapshot = List.of();
        hugeStacksSnapshotDirty = true;
        storedAmount = HugeAmount.ZERO;
        for (Map.Entry<AEKey, HugeAmount> entry : amounts.entrySet()) {
            storedAmount = storedAmount.add(entry.getValue());
            updateIndexes(entry.getKey(), HugeAmount.ZERO, entry.getValue());
        }
    }

    private void updateIndexes(AEKey key, HugeAmount previous, HugeAmount next) {
        if (next.isZero()) {
            visibleStacks.remove(key);
        } else {
            visibleStacks.set(key, next.toLongSaturated());
        }
        if (next.compareTo(LONG_MAX_AMOUNT) > 0) {
            hugeStacks.put(key, next);
            hugeStacksSnapshotDirty = true;
        } else if (hugeStacks.remove(key) != null) {
            hugeStacksSnapshotDirty = true;
        }

        BigInteger delta = next.toBigInteger().subtract(previous.toBigInteger());
        int typeDelta = (previous.isZero() ? 0 : -1) + (next.isZero() ? 0 : 1);
        if (delta.signum() == 0 && typeDelta == 0) {
            return;
        }

        AEKeyType keyType = key.getType();
        MutableTypeStats stats = typeStats.computeIfAbsent(keyType, ignored -> new MutableTypeStats());
        stats.storedTypes += typeDelta;
        stats.storedAmount = stats.storedAmount.add(delta);
        if (stats.storedTypes <= 0L || stats.storedAmount.signum() <= 0) {
            typeStats.remove(keyType);
        }
        typeStatsSnapshotDirty = true;
    }

    private void readShard(int shard) {
        Path path = shardPath(shard);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            CompoundTag tag = NbtIo.readCompressed(input);
            long shardRevision = tag.getLong("revision");
            int hashVersion = tag.getInt(ECOStorageKeyHash.SHARD_HASH_VERSION_TAG);
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
                HugeAmount amount = HugeAmount.read(entry.getCompound("amount"));
                if (key != null && !amount.isZero()) {
                    int targetShard = shardFor(key);
                    if (targetShard != shard || hashVersion < ECOStorageKeyHash.VERSION) {
                        dirtyShards.add(targetShard);
                    }
                    Long previousRevision = loadedKeyRevisions.get(key);
                    if (previousRevision == null
                            || shardRevision > previousRevision
                            || (shardRevision == previousRevision && targetShard == shard)) {
                        amounts.put(key, amount);
                        loadedKeyRevisions.put(key, shardRevision);
                        loadedKeySourceShards.put(key, shard);
                    }
                }
            }
            revision = Math.max(revision, shardRevision);
            shardRevisions[shard] = shardRevision;
        } catch (RuntimeException | IOException e) {
            LOGGER.error("Unable to read ECO infinite storage shard {}", path, e);
        }
    }

    private void writeShard(int shard) {
        try {
            Files.createDirectories(domainPath);
            CompoundTag tag = new CompoundTag();
            tag.putInt("version", 1);
            tag.putInt(ECOStorageKeyHash.SHARD_HASH_VERSION_TAG, ECOStorageKeyHash.VERSION);
            tag.putLong("revision", revision);
            tag.putString("domain", domainId.toString());
            ListTag entries = new ListTag();
            for (Map.Entry<AEKey, HugeAmount> entry : amounts.entrySet()) {
                if (shardFor(entry.getKey()) != shard || entry.getValue().isZero()) {
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.put("key", entry.getKey().toTagGeneric());
                entryTag.put("amount", entry.getValue().write());
                entries.add(entryTag);
            }
            tag.put("entries", entries);
            Path tmp = domainPath.resolve(shardFileName(shard) + ".tmp");
            try (OutputStream output = Files.newOutputStream(tmp)) {
                NbtIo.writeCompressed(tag, output);
            }
            Files.move(tmp, shardPath(shard), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            shardRevisions[shard] = revision;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write ECO infinite storage shard " + shard, e);
        }
    }

    private void appendWal(AEKey key, BigInteger delta) {
        try {
            Files.createDirectories(domainPath);
            CompoundTag tag = new CompoundTag();
            tag.putInt("version", WAL_VERSION);
            tag.putLong("revision", revision);
            tag.putString("domain", domainId.toString());
            tag.put("key", key.toTagGeneric());
            tag.putString("delta", delta.toString());
            ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, payloadOut);
            byte[] payload = payloadOut.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(payload);
            DataOutputStream out = walOutput();
            out.writeInt(payload.length);
            out.writeInt((int) crc.getValue());
            out.write(payload);
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to append ECO infinite storage WAL", e);
        }
    }

    private DataOutputStream walOutput() throws IOException {
        if (walOut == null) {
            walOut = new DataOutputStream(Files.newOutputStream(
                    walPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND));
        }
        return walOut;
    }

    private void replayWal() {
        if (!Files.isRegularFile(walPath)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(walPath))) {
            while (true) {
                int length;
                int expectedCrc;
                try {
                    length = in.readInt();
                    expectedCrc = in.readInt();
                } catch (EOFException e) {
                    break;
                }
                if (length <= 0 || length > MAX_WAL_RECORD_BYTES) {
                    break;
                }
                byte[] payload = new byte[length];
                try {
                    in.readFully(payload);
                } catch (EOFException e) {
                    break;
                }
                CRC32 crc = new CRC32();
                crc.update(payload);
                if ((int) crc.getValue() != expectedCrc) {
                    break;
                }
                CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(payload));
                AEKey key = AEKey.fromTagGeneric(tag.getCompound("key"));
                long recordRevision = tag.getLong("revision");
                if (key != null) {
                    if (recordRevision > loadedKeyRevisions.getOrDefault(key, 0L)) {
                        applyDelta(key, new BigInteger(tag.getString("delta")), false);
                    }
                    revision = Math.max(revision, recordRevision);
                }
            }
        } catch (RuntimeException | IOException e) {
            LOGGER.error("Unable to replay ECO infinite storage WAL {}", walPath, e);
        }
    }

    private void truncateWal() {
        try {
            closeWalOutput();
            Files.createDirectories(domainPath);
            Files.deleteIfExists(walPath);
            Files.createFile(walPath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to checkpoint ECO infinite storage WAL", e);
        }
    }

    private void closeWalOutput() {
        if (walOut == null) {
            return;
        }
        try {
            walOut.close();
        } catch (IOException e) {
            LOGGER.warn("Unable to close ECO infinite storage WAL {}", walPath, e);
        } finally {
            walOut = null;
        }
    }

    private Path shardPath(int shard) {
        return domainPath.resolve(shardFileName(shard));
    }

    private static String shardFileName(int shard) {
        return "shard_%03d.dat".formatted(shard);
    }

    private static int shardFor(AEKey key) {
        return ECOStorageKeyHash.shardFor(key, SHARD_COUNT);
    }

    private static final class MutableTypeStats {
        private long storedTypes;
        private BigInteger storedAmount = BigInteger.ZERO;
    }
}
