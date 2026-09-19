package cn.dancingsnow.neoecoae.impl.storage.infinite;

import cn.dancingsnow.neoecoae.impl.storage.AtomicSavedDataFile;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageKeyHash;
import cn.dancingsnow.neoecoae.impl.storage.StorageFileHistory;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.CRC32;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Standard Minecraft SavedData NBT with independently decoded inventory records. */
public final class InfiniteStorageSnapshot {
    private static final String ENCODING = "neoecoae_record_encoding";

    private InfiniteStorageSnapshot() {}

    static CompoundTag read(Path path) throws IOException {
        CompoundTag base = readSingle(path);
        Path delta = InfiniteStorageDelta.path(path);
        CompoundTag result = Files.exists(delta) ? InfiniteStorageDelta.apply(base, readSingle(delta)) : base;
        Path marker = commitMarker(path);
        if (Files.exists(marker)) {
            CompoundTag committed = AtomicSavedDataFile.read(marker);
            if (!committed.hasUUID("domain")
                    || !result.hasUUID("domain")
                    || !committed.getUUID("domain").equals(result.getUUID("domain"))
                    || !committed.contains("revision", Tag.TAG_LONG)
                    || committed.getLong("revision") > result.getLong("revision")) {
                throw new IOException("Missing or stale infinite-storage commit; restore a matching snapshot set");
            }
        }
        return result;
    }

    static Path commitMarker(Path base) {
        return base.resolveSibling(base.getFileName() + ".commit");
    }

    public static void markCommitted(Path base, CompoundTag snapshot, int version) throws IOException {
        CompoundTag marker = new CompoundTag();
        marker.putUUID("domain", snapshot.getUUID("domain"));
        marker.putLong("revision", snapshot.getLong("revision"));
        AtomicSavedDataFile.write(commitMarker(base), marker, version);
    }

    /** Explicit rollback candidates only; normal loading must never silently choose an older quantity. */
    static CompoundTag previousSnapshot(Path path) throws IOException {
        CompoundTag best = null;
        for (Path basePath : List.of(path, StorageFileHistory.previous(path))) {
            if (!Files.isRegularFile(basePath)) continue;
            CompoundTag base;
            try {
                base = readSingle(basePath);
            } catch (IOException | RuntimeException e) {
                continue;
            }
            best = newer(best, base);
            for (Path deltaPath : List.of(
                    InfiniteStorageDelta.path(path), StorageFileHistory.previous(InfiniteStorageDelta.path(path)))) {
                if (!Files.isRegularFile(deltaPath)) continue;
                try {
                    CompoundTag delta = readSingle(deltaPath);
                    best = newer(best, InfiniteStorageDelta.apply(base, delta));
                } catch (IOException | RuntimeException ignored) {
                }
            }
        }
        if (best == null) throw new IOException("No readable compatible previous snapshot is available");
        return best;
    }

    private static CompoundTag newer(CompoundTag previous, CompoundTag candidate) {
        return previous == null || candidate.getLong("revision") > previous.getLong("revision") ? candidate : previous;
    }

    private static CompoundTag readSingle(Path path) throws IOException {
        CompoundTag root;
        try (InputStream input = Files.newInputStream(path)) {
            root = NbtIo.readCompressed(input);
        }
        if (!root.contains("data", Tag.TAG_COMPOUND)) {
            throw new IOException("Missing data compound in infinite-storage snapshot: " + path);
        }
        CompoundTag data = root.getCompound("data");
        if (!root.contains(ENCODING)) return data; // Existing V2 Minecraft SavedData.
        if (!root.contains(ENCODING, Tag.TAG_INT) || root.getInt(ENCODING) != 1) {
            throw new IOException("Unsupported infinite-storage record encoding");
        }
        for (String listName : List.of("entries", "transfer_receipts")) {
            if (!data.contains(listName, Tag.TAG_LIST)) continue;
            ListTag records = (ListTag) data.get(listName);
            if (!records.isEmpty() && records.getElementType() != Tag.TAG_COMPOUND) {
                throw new IOException("Invalid encoded record list: " + listName);
            }
            ListTag decoded = new ListTag();
            for (int i = 0; i < records.size(); i++) {
                CompoundTag record = records.getCompound(i);
                try {
                    if (!record.contains("payload", Tag.TAG_BYTE_ARRAY) || !record.contains("crc32", Tag.TAG_LONG)) {
                        throw new IOException("Missing record payload or checksum");
                    }
                    byte[] bytes = record.getByteArray("payload");
                    if (checksum(bytes) != record.getLong("crc32")) {
                        throw new IOException("Record checksum mismatch");
                    }
                    decoded.add(decode(bytes));
                } catch (IOException | RuntimeException failure) {
                    CompoundTag retained = new CompoundTag();
                    retained.putString("isolated_record", listName + "/" + i);
                    retained.putString("isolated_reason", failure.toString());
                    retained.putString("isolated_key_fingerprint", record.getString("key_fingerprint"));
                    retained.putByteArray("isolated_bytes", record.getByteArray("payload"));
                    retained.put("isolated_envelope", record.copy());
                    decoded.add(retained);
                }
            }
            data.put(listName, decoded);
        }
        return data;
    }

    private static CompoundTag decode(byte[] bytes) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        CompoundTag decoded = NbtIo.read(input);
        if (decoded == null) throw new IOException("Record is not a compound tag");
        if (input.available() != 0) throw new IOException("Trailing data in inventory record");
        return decoded;
    }

    private static byte[] encode(CompoundTag tag) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(buffer));
        return buffer.toByteArray();
    }

    static void validateKey(CompoundTag key) throws IOException {
        if (!key.equals(decode(encode(key)))) {
            throw new IOException("AEKey cannot be serialized without data loss");
        }
    }

    private static long checksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes);
        return checksum.getValue();
    }

    private static CompoundTag root(CompoundTag data, int dataVersion) throws IOException {
        CompoundTag encoded = new CompoundTag();
        // Inventory lists are encoded below; do not first deep-copy lists that will be discarded.
        for (String key : data.getAllKeys()) {
            if (!key.equals("entries") && !key.equals("transfer_receipts")) {
                encoded.put(key, data.get(key).copy());
            }
        }
        for (String listName : List.of("entries", "transfer_receipts")) {
            if (!data.contains(listName, Tag.TAG_LIST)) continue;
            ListTag records = data.getList(listName, Tag.TAG_COMPOUND);
            ListTag encodedRecords = new ListTag();
            for (int i = 0; i < records.size(); i++) {
                CompoundTag record = records.getCompound(i);
                byte[] bytes = encode(record);
                CompoundTag envelope = new CompoundTag();
                envelope.putByteArray("payload", bytes);
                envelope.putLong("crc32", checksum(bytes));
                if (record.contains("key", Tag.TAG_COMPOUND)) {
                    envelope.putString(
                            "key_fingerprint", ECOStorageKeyHash.stableFingerprint(record.getCompound("key")));
                }
                encodedRecords.add(envelope);
            }
            encoded.put(listName, encodedRecords);
        }
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", dataVersion);
        root.putInt(ENCODING, 1);
        root.put("data", encoded);
        return root;
    }

    public static void write(Path target, CompoundTag data, int dataVersion) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, target.getFileName() + ".", ".tmp");
        try {
            try (var output = new BufferedOutputStream(Files.newOutputStream(temporary), 256 * 1024)) {
                NbtIo.writeCompressed(root(data, dataVersion), output);
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (!data.equals(read(temporary))) {
                throw new IOException("Infinite-storage snapshot failed read-back verification");
            }
            // Never fall back to truncating the authoritative file on unsupported filesystems.
            StorageFileHistory.preserve(target);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
