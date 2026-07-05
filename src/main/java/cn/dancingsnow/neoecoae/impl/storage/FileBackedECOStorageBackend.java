package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import com.google.common.math.LongMath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBackedECOStorageBackend implements ECOStorageBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileBackedECOStorageBackend.class);
    private static final int SHARD_COUNT = 256;

    private final UUID storageId;
    private final Path storagePath;
    private final Map<AEKey, Long> amounts = new HashMap<>();
    private final KeyCounter visibleStacks = new KeyCounter();
    private final Set<Integer> dirtyShards = new HashSet<>();

    private boolean loaded;
    private boolean loadRequested;
    private boolean loading;
    private int nextLoadShard;
    private long storedAmount;
    private int storedTypes;
    private long revision;

    public FileBackedECOStorageBackend(UUID storageId, Path storagePath, int summaryTypes, long summaryAmount) {
        this.storageId = storageId;
        this.storagePath = storagePath;
        this.storedTypes = Math.max(0, summaryTypes);
        this.storedAmount = Math.max(0L, summaryAmount);
    }

    public synchronized boolean hasPersistentData() {
        if (Files.isRegularFile(storagePath.resolve("manifest.dat"))) {
            return true;
        }
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            if (Files.isRegularFile(shardPath(shard))) {
                return true;
            }
        }
        return false;
    }

    public synchronized void importLegacyNow(List<GenericStack> stacks) {
        if (stacks.isEmpty()) {
            loaded = true;
            loadRequested = false;
            loading = false;
            return;
        }
        amounts.clear();
        visibleStacks.clear();
        storedAmount = 0L;
        storedTypes = 0;
        for (GenericStack stack : stacks) {
            if (stack.amount() <= 0L) {
                continue;
            }
            long current = amounts.getOrDefault(stack.what(), 0L);
            long next = LongMath.saturatedAdd(current, stack.amount());
            amounts.put(stack.what(), next);
        }
        rebuildVisibleStacks();
        dirtyShards.clear();
        for (AEKey key : amounts.keySet()) {
            dirtyShards.add(shardFor(key));
        }
        loaded = true;
        loadRequested = false;
        loading = false;
        closeAndFlush();
    }

    @Override
    public synchronized long insert(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L) {
            return 0L;
        }
        ensureLoadedBlocking();
        if (mode == Actionable.MODULATE) {
            applyDelta(key, amount);
        }
        return amount;
    }

    @Override
    public synchronized long extract(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L) {
            return 0L;
        }
        ensureLoadedBlocking();
        long current = amounts.getOrDefault(key, 0L);
        long extracted = Math.min(current, amount);
        if (extracted <= 0L) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            applyDelta(key, -extracted);
        }
        return extracted;
    }

    @Override
    public synchronized long getAmount(AEKey key) {
        ensureLoadedBlocking();
        return key == null ? 0L : amounts.getOrDefault(key, 0L);
    }

    @Override
    public synchronized void getAvailableStacks(KeyCounter out) {
        if (!loaded) {
            requestLoad();
            return;
        }
        out.addAll(visibleStacks);
    }

    @Override
    public synchronized boolean isEmpty() {
        return loaded ? amounts.isEmpty() : storedTypes <= 0 && storedAmount <= 0L;
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
    public synchronized boolean isLoaded() {
        return loaded;
    }

    @Override
    public synchronized void requestLoad() {
        if (!loaded) {
            loadRequested = true;
        }
    }

    @Override
    public synchronized boolean loadBudgeted(long maxNanos) {
        if (loaded || !loadRequested) {
            return false;
        }
        startLoading();
        long deadline = maxNanos <= 0L ? Long.MAX_VALUE : System.nanoTime() + maxNanos;
        while (nextLoadShard < SHARD_COUNT) {
            readShard(nextLoadShard);
            nextLoadShard++;
            if (System.nanoTime() >= deadline) {
                return false;
            }
        }
        finishLoading();
        return true;
    }

    @Override
    public synchronized boolean flushBudgeted(long maxNanos) {
        if (dirtyShards.isEmpty()) {
            return false;
        }
        long deadline = maxNanos <= 0L ? Long.MAX_VALUE : System.nanoTime() + maxNanos;
        Set<Integer> pending = new HashSet<>(dirtyShards);
        boolean wrote = false;
        for (int shard : pending) {
            writeShard(shard);
            dirtyShards.remove(shard);
            wrote = true;
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        if (dirtyShards.isEmpty()) {
            writeManifest();
        }
        return wrote;
    }

    @Override
    public synchronized void closeAndFlush() {
        if (!loaded && loadRequested) {
            ensureLoadedBlocking();
        }
        if (!dirtyShards.isEmpty()) {
            for (int shard : new HashSet<>(dirtyShards)) {
                writeShard(shard);
            }
            dirtyShards.clear();
        }
        writeManifest();
    }

    private void ensureLoadedBlocking() {
        if (loaded) {
            return;
        }
        requestLoad();
        while (!loaded) {
            loadBudgeted(0L);
        }
    }

    private void startLoading() {
        if (loading) {
            return;
        }
        loading = true;
        nextLoadShard = 0;
        amounts.clear();
        visibleStacks.clear();
        try {
            Files.createDirectories(storagePath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create ECO cell storage directory " + storagePath, e);
        }
    }

    private void finishLoading() {
        rebuildVisibleStacks();
        loaded = true;
        loadRequested = false;
        loading = false;
    }

    private void applyDelta(AEKey key, long delta) {
        if (delta == 0L) {
            return;
        }
        long previous = amounts.getOrDefault(key, 0L);
        long next = Math.max(0L, LongMath.saturatedAdd(previous, delta));
        if (next <= 0L) {
            amounts.remove(key);
            visibleStacks.remove(key);
        } else {
            amounts.put(key, next);
            visibleStacks.set(key, next);
        }
        storedAmount = LongMath.saturatedAdd(storedAmount, next - previous);
        if (previous <= 0L && next > 0L) {
            storedTypes++;
        } else if (previous > 0L && next <= 0L) {
            storedTypes = Math.max(0, storedTypes - 1);
        }
        dirtyShards.add(shardFor(key));
        revision = revision == Long.MAX_VALUE ? 0L : revision + 1L;
    }

    private void rebuildVisibleStacks() {
        visibleStacks.clear();
        storedAmount = 0L;
        storedTypes = 0;
        amounts.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0L);
        for (Map.Entry<AEKey, Long> entry : amounts.entrySet()) {
            long amount = entry.getValue();
            visibleStacks.set(entry.getKey(), amount);
            storedAmount = LongMath.saturatedAdd(storedAmount, amount);
            storedTypes++;
        }
    }

    private void readShard(int shard) {
        Path path = shardPath(shard);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            CompoundTag tag = NbtIo.readCompressed(input);
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
                long amount = Math.max(0L, entry.getLong("amount"));
                if (key != null && amount > 0L) {
                    amounts.put(key, LongMath.saturatedAdd(amounts.getOrDefault(key, 0L), amount));
                }
            }
            revision = Math.max(revision, tag.getLong("revision"));
        } catch (RuntimeException | IOException e) {
            LOGGER.error("Unable to read ECO cell storage shard {}", path, e);
        }
    }

    private void writeShard(int shard) {
        try {
            Files.createDirectories(storagePath);
            CompoundTag tag = new CompoundTag();
            tag.putInt("version", 1);
            tag.putLong("revision", revision);
            tag.putString("cell", storageId.toString());
            ListTag entries = new ListTag();
            for (Map.Entry<AEKey, Long> entry : amounts.entrySet()) {
                if (shardFor(entry.getKey()) != shard || entry.getValue() <= 0L) {
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.put("key", entry.getKey().toTagGeneric());
                entryTag.putLong("amount", entry.getValue());
                entries.add(entryTag);
            }
            tag.put("entries", entries);

            Path tmp = storagePath.resolve(shardFileName(shard) + ".tmp");
            try (OutputStream output = Files.newOutputStream(tmp)) {
                NbtIo.writeCompressed(tag, output);
            }
            Files.move(tmp, shardPath(shard), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write ECO cell storage shard " + shard, e);
        }
    }

    private void writeManifest() {
        try {
            Files.createDirectories(storagePath);
            CompoundTag tag = new CompoundTag();
            tag.putInt("version", 2);
            tag.putString("kind", "cell");
            tag.putUUID("id", storageId);
            tag.putLong("revision", revision);
            tag.putInt("storedTypes", storedTypes);
            tag.putString("storedAmount", Long.toString(storedAmount));
            try (OutputStream output = Files.newOutputStream(storagePath.resolve("manifest.dat"))) {
                NbtIo.writeCompressed(tag, output);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write ECO cell storage manifest " + storagePath, e);
        }
    }

    private Path shardPath(int shard) {
        return storagePath.resolve(shardFileName(shard));
    }

    private static String shardFileName(int shard) {
        return "shard_%03d.dat".formatted(shard);
    }

    private static int shardFor(AEKey key) {
        return Math.floorMod(key.toTagGeneric().hashCode(), SHARD_COUNT);
    }
}
