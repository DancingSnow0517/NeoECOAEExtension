package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageKeyHash;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a domain written by the previous file-backed engine (sharded NBT plus a write-ahead log) into
 * {@link ECOInfiniteStorageData}. This runs once per domain. The legacy directory is only renamed after the new world
 * data has been written successfully, so a failure anywhere leaves the old files exactly as they were and the server
 * can go back to the previous build.
 */
final class LegacyDomainImporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyDomainImporter.class);

    private static final String STORAGE_ROOT = "neoecoae_storage";
    private static final int SHARD_COUNT = 256;
    private static final int LEGACY_WAL_VERSION = 1;
    private static final int WAL_VERSION = 2;
    private static final int MAX_WAL_RECORD_BYTES = 16 * 1024 * 1024;

    private LegacyDomainImporter() {}

    /**
     * Imports the legacy files of {@code domainId} if any are present, then marks the domain as imported. Does nothing
     * if the domain was already imported.
     *
     * @param dataFile the file backing {@code data}, written before the legacy directory is renamed
     */
    static void importInto(
        ECOInfiniteStorageData data,
        HolderLookup.Provider registries,
        Path worldRoot,
        UUID domainId,
        Path dataFile
    ) {
        if (data.isLegacyImported()) {
            return;
        }
        Path domainPath = findLegacyDomainPath(worldRoot.resolve("data").resolve(STORAGE_ROOT), domainId);
        if (domainPath == null) {
            data.markLegacyImported();
            return;
        }

        try {
            LOGGER.info("Importing legacy ECO infinite storage domain {} from {}", domainId, domainPath);
            LegacyContents contents = read(domainPath);
            int unresolved = apply(data, registries, contents);
            data.markLegacyImported();
            data.setDirty();
            // Make the imported state durable before touching anything on the legacy side. This reports a failed
            // write instead of only logging it, so a full disk cannot cost us both copies.
            data.writeIfDirty(dataFile.toFile(), registries);
            LOGGER.info(
                "Imported {} entries ({} unresolved) into ECO infinite storage domain {}",
                contents.amounts().size() - unresolved,
                unresolved,
                domainId
            );
        } catch (RuntimeException | IOException e) {
            // Leave the legacy directory untouched so the previous build can still read it.
            LOGGER.error(
                "Unable to import legacy ECO infinite storage domain {} from {}; the domain stays locked and the"
                    + " legacy files are left in place",
                domainId,
                domainPath,
                e
            );
            data.markUnreadable();
            return;
        }
        retire(domainPath, domainId);
    }

    /**
     * Renames the imported directory so it is no longer picked up, while staying available as a way back to the
     * previous build. The contents are already safe at this point, so a failure here is not fatal.
     */
    private static void retire(Path domainPath, UUID domainId) {
        Path retired = domainPath.resolveSibling(domainPath.getFileName() + ".legacy");
        try {
            Files.move(domainPath, retired);
            LOGGER.info("The legacy files of ECO infinite storage domain {} are kept at {}", domainId, retired);
        } catch (IOException e) {
            LOGGER.warn(
                "Imported ECO infinite storage domain {} but could not rename {} to {}; it will be skipped on the next"
                    + " start and can be removed by hand",
                domainId,
                domainPath,
                retired,
                e
            );
        }
    }

    /**
     * Locates the legacy directory, preferring the world-global layout over the older per-dimension one.
     */
    @Nullable
    private static Path findLegacyDomainPath(Path storageRoot, UUID domainId) {
        String domainDirectory = "domain_" + domainId;
        Path globalPath = storageRoot.resolve(domainDirectory);
        if (Files.isDirectory(globalPath)) {
            return globalPath;
        }
        if (!Files.isDirectory(storageRoot)) {
            return null;
        }
        List<Path> matches = new ArrayList<>();
        try (var children = Files.list(storageRoot)) {
            children
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().startsWith("dim_"))
                .map(path -> path.resolve(domainDirectory))
                .filter(Files::isDirectory)
                .forEach(matches::add);
        } catch (IOException e) {
            LOGGER.error("Unable to inspect legacy ECO infinite storage domains in {}", storageRoot, e);
            return null;
        }
        if (matches.size() > 1) {
            LOGGER.error("Multiple legacy ECO infinite storage domains share UUID {}: {}", domainId, matches);
            return null;
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Working state of an import. Entries are keyed by their serialized key tag, so an entry whose mod was removed
     * travels through the import untouched instead of being dropped.
     */
    private record LegacyContents(Map<CompoundTag, BigInteger> amounts, Set<UUID> transactions) {}

    private static LegacyContents read(Path domainPath) throws IOException {
        Map<CompoundTag, BigInteger> amounts = new HashMap<>();
        Map<CompoundTag, Long> revisions = new HashMap<>();
        Map<CompoundTag, Integer> sourceShards = new HashMap<>();
        Set<UUID> transactions = new LinkedHashSet<>();

        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            readShard(domainPath.resolve("shard_%03d.dat".formatted(shard)), shard, amounts, revisions, sourceShards);
        }
        replayWal(domainPath.resolve("wal_000.log"), amounts, revisions, transactions);
        readTransactionReceipts(domainPath.resolve("transactions"), transactions);

        amounts.values().removeIf(amount -> amount.signum() <= 0);
        return new LegacyContents(amounts, transactions);
    }

    private static void readShard(
        Path path,
        int shard,
        Map<CompoundTag, BigInteger> amounts,
        Map<CompoundTag, Long> revisions,
        Map<CompoundTag, Integer> sourceShards
    ) throws IOException {
        if (!Files.isRegularFile(path)) {
            return;
        }
        CompoundTag tag;
        try (var input = Files.newInputStream(path)) {
            tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        }
        long shardRevision = tag.getLong("revision");
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            CompoundTag keyTag = entry.getCompound("key").copy();
            HugeAmount amount = HugeAmount.read(entry.getCompound("amount"));
            if (keyTag.isEmpty() || amount.isZero()) {
                continue;
            }
            // The same key can appear in a stale shard as well as its target shard. Resolve it the way the old engine
            // did: the target shard wins, otherwise the newer revision wins.
            int targetShard = ECOStorageKeyHash.shardForTag(keyTag, SHARD_COUNT);
            Long previousRevision = revisions.get(keyTag);
            int previousSource = sourceShards.getOrDefault(keyTag, -1);
            boolean currentIsTarget = targetShard == shard;
            boolean previousIsTarget = targetShard == previousSource;
            if (previousRevision == null
                || (currentIsTarget && !previousIsTarget)
                || (currentIsTarget == previousIsTarget && shardRevision > previousRevision)) {
                amounts.put(keyTag, amount.toBigInteger());
                revisions.put(keyTag, shardRevision);
                sourceShards.put(keyTag, shard);
            }
        }
    }

    /**
     * Replays the write-ahead log. Domains that were never checkpointed keep all of their contents here, so this is
     * not optional. A truncated or corrupted frame ends the replay; everything read before it is kept.
     */
    private static void replayWal(
        Path walPath,
        Map<CompoundTag, BigInteger> amounts,
        Map<CompoundTag, Long> revisions,
        Set<UUID> transactions
    ) throws IOException {
        if (!Files.isRegularFile(walPath)) {
            return;
        }
        long fileSize = Files.size(walPath);
        try (DataInputStream in = new DataInputStream(Files.newInputStream(walPath))) {
            long offset = 0L;
            while (offset < fileSize) {
                if (fileSize - offset < Integer.BYTES * 2L) {
                    LOGGER.warn("Ignoring incomplete tail of ECO infinite storage WAL {}", walPath);
                    return;
                }
                int length = in.readInt();
                int expectedCrc = in.readInt();
                offset += Integer.BYTES * 2L;
                if (length <= 0 || length > MAX_WAL_RECORD_BYTES || fileSize - offset < length) {
                    LOGGER.warn("Ignoring incomplete tail of ECO infinite storage WAL {} at byte {}", walPath, offset);
                    return;
                }
                byte[] payload = new byte[length];
                in.readFully(payload);
                offset += length;
                CRC32 crc = new CRC32();
                crc.update(payload);
                if ((int) crc.getValue() != expectedCrc) {
                    LOGGER.warn(
                        "Ignoring ECO infinite storage WAL {} from byte {} onwards: CRC mismatch",
                        walPath,
                        offset - length
                    );
                    return;
                }

                CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(payload), NbtAccounter.unlimitedHeap());
                int version = tag.getInt("version");
                if (version == WAL_VERSION) {
                    ListTag records = tag.getList("records", Tag.TAG_COMPOUND);
                    for (int i = 0; i < records.size(); i++) {
                        replayWalRecord(records.getCompound(i), amounts, revisions, transactions);
                    }
                } else if (version == LEGACY_WAL_VERSION) {
                    replayWalRecord(tag, amounts, revisions, transactions);
                } else {
                    LOGGER.warn(
                        "Ignoring ECO infinite storage WAL {} from byte {} onwards: unsupported version {}",
                        walPath,
                        offset - length,
                        version
                    );
                    return;
                }
            }
        }
    }

    private static void replayWalRecord(
        CompoundTag tag,
        Map<CompoundTag, BigInteger> amounts,
        Map<CompoundTag, Long> revisions,
        Set<UUID> transactions
    ) {
        CompoundTag keyTag = tag.getCompound("key").copy();
        if (keyTag.isEmpty()) {
            return;
        }
        if (tag.hasUUID("transaction")) {
            transactions.add(tag.getUUID("transaction"));
        }
        // Records older than the checkpoint that produced the shard entry are already accounted for.
        if (tag.getLong("revision") <= revisions.getOrDefault(keyTag, 0L)) {
            return;
        }
        BigInteger delta;
        try {
            delta = new BigInteger(tag.getString("delta"));
        } catch (NumberFormatException e) {
            LOGGER.warn("Skipping ECO infinite storage WAL record with an unreadable delta");
            return;
        }
        BigInteger next = amounts.getOrDefault(keyTag, BigInteger.ZERO).add(delta).max(BigInteger.ZERO);
        if (next.signum() == 0) {
            amounts.remove(keyTag);
        } else {
            amounts.put(keyTag, next);
        }
    }

    private static void readTransactionReceipts(Path receiptsPath, Set<UUID> transactions) {
        if (!Files.isDirectory(receiptsPath)) {
            return;
        }
        try (var children = Files.list(receiptsPath)) {
            children.forEach(path -> {
                String name = path.getFileName().toString();
                if (!name.endsWith(".done")) {
                    return;
                }
                try {
                    transactions.add(UUID.fromString(name.substring(0, name.length() - ".done".length())));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Ignoring unrecognised ECO infinite storage transaction receipt {}", path);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Unable to read ECO infinite storage transaction receipts in {}", receiptsPath, e);
        }
    }

    /**
     * Copies the imported contents into {@code data} and reports how many entries stayed unresolved.
     */
    private static int apply(ECOInfiniteStorageData data, HolderLookup.Provider registries, LegacyContents contents) {
        int unresolved = 0;
        for (Map.Entry<CompoundTag, BigInteger> entry : contents.amounts().entrySet()) {
            HugeAmount amount = HugeAmount.of(entry.getValue());
            if (amount.isZero()) {
                continue;
            }
            AEKey key = AEKey.fromTagGeneric(registries, entry.getKey());
            if (key == null) {
                // No repair is attempted: the entry is carried over verbatim so it comes back if its mod does.
                data.addRawEntry(new ECOInfiniteStorageData.RawEntry(entry.getKey(), amount));
                unresolved++;
            } else {
                data.setAmount(key, data.getAmount(key).add(amount));
            }
        }
        if (unresolved > 0) {
            LOGGER.warn(
                "{} legacy ECO infinite storage entries could not be resolved and were kept unchanged",
                unresolved
            );
        }
        for (UUID transactionId : contents.transactions()) {
            data.addMigrationReceipt(transactionId);
        }
        return unresolved;
    }
}
