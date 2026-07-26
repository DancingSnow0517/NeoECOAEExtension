package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the retired 256-shard-per-cell layout so worlds written before the single-file format can be migrated.
 *
 * <p>Strictly read-only - nothing here writes a shard back. The cross-shard reconciliation is not cosmetic: whenever
 * {@link ECOStorageKeyHash#VERSION} changed, a key could be left present in both its old and its new shard, so the
 * copy carrying the higher shard revision has to win.
 */
final class ECOLegacyCellShardReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOLegacyCellShardReader.class);
    private static final int SHARD_COUNT = 256;
    private static final String MANIFEST_FILE = "manifest.dat";

    private final Path storagePath;
    private final Map<AEKey, Long> amounts = new HashMap<>();
    private final Map<AEKey, Long> keyRevisions = new HashMap<>();
    private final Map<AEKey, Integer> keySourceShards = new HashMap<>();
    private final long[] shardRevisions = new long[SHARD_COUNT];

    private int nextShard;
    private long revision;
    private boolean failed;

    ECOLegacyCellShardReader(Path storagePath) {
        this.storagePath = storagePath;
    }

    static boolean isPresent(Path storagePath) {
        if (Files.isRegularFile(storagePath.resolve(MANIFEST_FILE))) {
            return true;
        }
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            if (Files.isRegularFile(shardPath(storagePath, shard))) {
                return true;
            }
        }
        return false;
    }

    /** Reads shards until the deadline expires. Returns {@code true} once every shard has been visited. */
    boolean readBudgeted(long maxNanos) {
        long deadline = maxNanos <= 0L ? Long.MAX_VALUE : System.nanoTime() + maxNanos;
        while (nextShard < SHARD_COUNT) {
            readShard(nextShard);
            nextShard++;
            if (System.nanoTime() >= deadline) {
                return false;
            }
        }
        return true;
    }

    boolean isFailed() {
        return failed;
    }

    long revision() {
        return revision;
    }

    /** Applies the cross-shard reconciliation and hands over the reconstructed contents. */
    Map<AEKey, Long> finish() {
        amounts.entrySet().removeIf(entry -> {
            AEKey key = entry.getKey();
            int targetShard = ECOStorageKeyHash.shardFor(key, SHARD_COUNT);
            int sourceShard = keySourceShards.getOrDefault(key, targetShard);
            return sourceShard != targetShard && shardRevisions[targetShard] > keyRevisions.getOrDefault(key, 0L);
        });
        return amounts;
    }

    static void delete(Path storagePath) throws IOException {
        Files.deleteIfExists(storagePath.resolve(MANIFEST_FILE));
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            Files.deleteIfExists(shardPath(storagePath, shard));
            Files.deleteIfExists(storagePath.resolve(shardFileName(shard) + ".tmp"));
        }
    }

    private void readShard(int shard) {
        Path path = shardPath(storagePath, shard);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            CompoundTag tag = NbtIo.readCompressed(input);
            long shardRevision = tag.getLong("revision");
            shardRevisions[shard] = shardRevision;
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
                long amount = Math.max(0L, entry.getLong("amount"));
                if (key == null || amount <= 0L) {
                    continue;
                }
                int targetShard = ECOStorageKeyHash.shardFor(key, SHARD_COUNT);
                Long previousRevision = keyRevisions.get(key);
                if (previousRevision == null
                        || shardRevision > previousRevision
                        || (shardRevision == previousRevision && targetShard == shard)) {
                    amounts.put(key, amount);
                    keyRevisions.put(key, shardRevision);
                    keySourceShards.put(key, shard);
                }
            }
            revision = Math.max(revision, shardRevision);
        } catch (RuntimeException | IOException e) {
            // Deliberately fatal for the whole cell. Continuing would migrate a silently truncated inventory into
            // cell.dat and then delete the shards that still held the missing entries.
            failed = true;
            LOGGER.error("Unable to read legacy ECO cell storage shard {}", path, e);
        }
    }

    private static Path shardPath(Path storagePath, int shard) {
        return storagePath.resolve(shardFileName(shard));
    }

    private static String shardFileName(int shard) {
        return "shard_%03d.dat".formatted(shard);
    }
}
