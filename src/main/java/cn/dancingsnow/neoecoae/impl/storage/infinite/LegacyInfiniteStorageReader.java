package cn.dancingsnow.neoecoae.impl.storage.infinite;

import cn.dancingsnow.neoecoae.config.NEConfig;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32C;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * Read-only compatibility for v1/v2 snapshots and v2 journals. Never truncates, renames or removes legacy files.
 * A failed shard prevents conversion; intact shards remain available as a read-only view.
 */
final class LegacyInfiniteStorageReader {
    private static final int SHARDS = 16;
    private static final int MAGIC = 0x45434F32;
    private static final int MAX_RECORD_BYTES = 64 * 1024 * 1024;

    static boolean exists(Path dataFile) {
        return Files.exists(dataFile) || Files.exists(storePath(dataFile));
    }

    static CompoundTag read(Path dataFile) throws IOException {
        ReadResult result = readAvailable(dataFile);
        if (!result.failures().isEmpty()) throw new IOException(String.join("; ", result.failures()));
        return result.data();
    }

    record ReadResult(CompoundTag data, List<String> failures) {}

    static ReadResult readAvailable(Path dataFile) throws IOException {
        Path directory = storePath(dataFile);
        if (!Files.exists(directory)) {
            CompoundTag root = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
            if (!root.contains("data", Tag.TAG_COMPOUND)) throw new IOException("Missing legacy domain data");
            return new ReadResult(root.getCompound("data"), List.of());
        }
        byte[] ready = Files.readAllBytes(directory.resolve("ready"));
        if (ready.length != 1 || ready[0] != 2) throw new IOException("Invalid legacy journal initialization marker");
        ListTag entries = new ListTag();
        Set<Tag> receipts = new LinkedHashSet<>();
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < SHARDS; i++) {
            Shard state = new Shard(directory.resolve(Integer.toString(i)));
            try { state.load(); }
            catch (IOException | RuntimeException e) {
                failures.add("Legacy shard " + i + ": " + e);
                continue;
            }
            entries.addAll(state.entries.values());
            entries.addAll(state.raw);
            receipts.addAll(state.receipts);
        }
        CompoundTag result = new CompoundTag();
        result.putInt("version", 2);
        result.put("entries", entries);
        ListTag migrations = new ListTag();
        migrations.addAll(receipts);
        result.put("migrations", migrations);
        return new ReadResult(result, List.copyOf(failures));
    }

    private static Path storePath(Path dataFile) {
        return dataFile.resolveSibling(dataFile.getFileName() + ".store");
    }

    private static final class Shard {
        private final Path path;
        private final Map<CompoundTag, CompoundTag> entries = new HashMap<>();
        private final Set<Tag> receipts = new LinkedHashSet<>();
        private final ListTag raw = new ListTag();
        private long sequence;

        Shard(Path path) { this.path = path; }

        void load() throws IOException {
            CompoundTag snapshot;
            try { snapshot = readSnapshot(path.resolve("snapshot.dat")); }
            catch (IOException | RuntimeException e) { snapshot = readSnapshot(path.resolve("backup.dat")); }
            sequence = snapshot.getLong("sequence");
            raw.addAll(snapshot.getList("rawEntries", Tag.TAG_COMPOUND));
            apply(snapshot);
            replay(path.resolve("previous.log"));
            replay(path.resolve("journal.log"));
            try (DataInputStream head = new DataInputStream(Files.newInputStream(path.resolve("head")))) {
                long acknowledged = head.readLong();
                if (acknowledged < 0 || head.readInt() != checksum(acknowledged, new byte[0]) || sequence < acknowledged) {
                    throw new IOException("Missing committed legacy journal records in " + path);
                }
            }
        }

        void replay(Path file) throws IOException {
            if (!Files.exists(file)) return;
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                ByteBuffer header = ByteBuffer.allocate(20);
                while (channel.position() < channel.size()) {
                    header.clear();
                    if (!readFully(channel, header)) break; // Unacknowledged tail; head is validated after replay.
                    header.flip();
                    int magic = header.getInt();
                    int length = header.getInt();
                    long next = header.getLong();
                    int expectedChecksum = header.getInt();
                    if (magic != MAGIC || length < 0 || length > MAX_RECORD_BYTES) throw new IOException("Invalid legacy journal header");
                    ByteBuffer body = ByteBuffer.allocate(length);
                    if (!readFully(channel, body)) break;
                    byte[] bytes = body.array();
                    if (checksum(next, bytes) != expectedChecksum) throw new IOException("Legacy journal checksum mismatch");
                    if (next > sequence) {
                        if (next != sequence + 1) throw new IOException("Legacy journal sequence gap");
                        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                            apply(NbtIo.read(input, NbtAccounter.create(MAX_RECORD_BYTES)));
                        }
                        sequence = next;
                    }
                }
            }
        }

        void apply(CompoundTag changes) throws IOException {
            validateList(changes, "entries", Tag.TAG_COMPOUND);
            validateList(changes, "migrations", Tag.TAG_INT_ARRAY);
            for (Tag value : changes.getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) value;
                CompoundTag key = entry.getCompound("key");
                if (key.isEmpty()) { raw.add(entry.copy()); continue; }
                CompoundTag amount = entry.getCompound("amount");
                if (amount.contains("long", Tag.TAG_LONG) && amount.getLong("long") == 0
                        && !amount.contains("big") && !entry.contains("restore")) entries.remove(key);
                else entries.put(key, entry);
            }
            receipts.addAll(changes.getList("migrations", Tag.TAG_INT_ARRAY));
        }
    }

    private static CompoundTag readSnapshot(Path file) throws IOException {
        long limit = Math.max(1, NEConfig.infiniteStorageMaxSnapshotBytes);
        if (Files.size(file) > limit) throw new IOException("Legacy snapshot exceeds size limit");
        CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.create(limit));
        if (tag.getInt("format") != 2 || !tag.contains("sequence", Tag.TAG_LONG) || tag.getLong("sequence") < 0) {
            throw new IOException("Invalid legacy snapshot format");
        }
        validateList(tag, "entries", Tag.TAG_COMPOUND);
        validateList(tag, "rawEntries", Tag.TAG_COMPOUND);
        validateList(tag, "migrations", Tag.TAG_INT_ARRAY);
        long size = (long) tag.getList("entries", Tag.TAG_COMPOUND).size()
            + tag.getList("rawEntries", Tag.TAG_COMPOUND).size() + tag.getList("migrations", Tag.TAG_INT_ARRAY).size();
        if (size > NEConfig.infiniteStorageMaxSnapshotEntries) throw new IOException("Legacy snapshot exceeds entry limit");
        return tag;
    }

    private static void validateList(CompoundTag tag, String name, int type) throws IOException {
        if (!(tag.get(name) instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != type)) {
            throw new IOException("Invalid legacy list: " + name);
        }
    }

    private static boolean readFully(FileChannel channel, ByteBuffer target) throws IOException {
        while (target.hasRemaining()) if (channel.read(target) < 0) return false;
        return true;
    }

    private static int checksum(long sequence, byte[] bytes) {
        CRC32C crc = new CRC32C();
        crc.update(ByteBuffer.allocate(8).putLong(sequence).array());
        crc.update(bytes);
        return (int) crc.getValue();
    }
}
