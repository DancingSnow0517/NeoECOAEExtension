package cn.dancingsnow.neoecoae.storage.persistence;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Reads legacy domain files as one committed snapshot; never selects a backup or modifies a source. */
public final class Legacy2042DomainSnapshot {
    private static final String ENCODING = "neoecoae_record_encoding";

    private Legacy2042DomainSnapshot() {}

    public static boolean exists(Path file) {
        for (String suffix : new String[] {"", ".bak", ".delta.dat", ".delta.dat.bak", ".commit", ".commit.bak"})
            if (Files.exists(file.resolveSibling(file.getFileName() + suffix))) return true;
        return false;
    }

    public static CompoundTag read(Path file) throws IOException {
        CompoundTag data = readSingle(file);
        Path delta = file.resolveSibling(file.getFileName() + ".delta.dat");
        if (Files.exists(delta)) {
            Legacy2042Storage.domain(data, data.getUUID("domain"));
            data = applyDelta(data, readSingle(delta));
        }
        boolean deltaHistoryOnly = !Files.exists(delta) && Files.exists(delta.resolveSibling(delta.getFileName() + ".bak"));
        Path marker = file.resolveSibling(file.getFileName() + ".commit");
        if (Files.exists(marker)) {
            CompoundTag committed = readSingle(marker);
            if (!committed.hasUUID("domain") || !data.hasUUID("domain")
                || !committed.getUUID("domain").equals(data.getUUID("domain"))
                || !committed.contains("revision", Tag.TAG_LONG) || committed.getLong("revision") < 0
                || !data.contains("revision", Tag.TAG_LONG)
                || committed.getLong("revision") > data.getLong("revision"))
                throw new IOException("Legacy domain snapshot is behind its commit marker or has a different identity");
        } else if (deltaHistoryOnly || Files.exists(marker.resolveSibling(marker.getFileName() + ".bak"))) {
            throw new IOException("Legacy domain sidecar is missing; retained history requires a matching commit marker");
        }
        return data;
    }

    private static CompoundTag readSingle(Path file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file.toFile());
        if (!root.contains("data", Tag.TAG_COMPOUND)) throw new IOException("Missing legacy domain data: " + file);
        CompoundTag data = root.getCompound("data");
        if (!root.contains(ENCODING)) return data;
        if (!root.contains(ENCODING, Tag.TAG_INT) || root.getInt(ENCODING) != 1)
            throw new IOException("Unsupported legacy domain record encoding");
        for (String name : new String[] {"entries", "transfer_receipts"}) {
            ListTag decoded = new ListTag();
            for (Tag raw : Legacy2042Storage.compounds(data, name)) {
                CompoundTag record = (CompoundTag) raw;
                if (!record.contains("payload", Tag.TAG_BYTE_ARRAY) || !record.contains("crc32", Tag.TAG_LONG))
                    throw new IOException("Missing legacy domain record payload or checksum: " + name);
                byte[] bytes = record.getByteArray("payload");
                CRC32 checksum = new CRC32(); checksum.update(bytes);
                if (checksum.getValue() != record.getLong("crc32"))
                    throw new IOException("Legacy domain record checksum mismatch: " + name);
                try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    CompoundTag entry = NbtIo.read(input);
                    if (entry == null || input.available() != 0)
                        throw new IOException("Malformed legacy domain record: " + name);
                    if (record.contains("key_fingerprint") && (!entry.contains("key", Tag.TAG_COMPOUND)
                        || !record.contains("key_fingerprint", Tag.TAG_STRING)
                        || !record.getString("key_fingerprint").equals(
                            Legacy2042Transfers.keyFingerprint(entry.getCompound("key")))))
                        throw new IOException("Legacy domain record key fingerprint mismatch");
                    decoded.add(entry);
                }
            }
            data.put(name, decoded);
        }
        return data;
    }

    private static CompoundTag applyDelta(CompoundTag base, CompoundTag delta) throws IOException {
        if (!base.hasUUID("domain") || !delta.hasUUID("domain")
            || !base.getUUID("domain").equals(delta.getUUID("domain"))
            || !base.contains("format", Tag.TAG_INT) || base.getInt("format") != 2
            || !delta.contains("format", Tag.TAG_INT) || delta.getInt("format") != 2
            || !base.contains("revision", Tag.TAG_LONG) || base.getLong("revision") < 0
            || !delta.contains("revision", Tag.TAG_LONG) || delta.getLong("revision") < 0
            || !delta.contains("base_revision", Tag.TAG_LONG) || delta.getLong("base_revision") < 0)
            throw new IOException("Invalid legacy domain delta identity or revision");
        if (delta.getLong("revision") <= base.getLong("revision")) return base;
        if (delta.getLong("base_revision") != base.getLong("revision"))
            throw new IOException("Legacy domain delta does not match its base snapshot");
        ListTag changes = Legacy2042Storage.compounds(delta, "entries");
        Set<String> replaced = new HashSet<>();
        for (Tag raw : changes) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.contains("key", Tag.TAG_COMPOUND)
                || !replaced.add(Legacy2042Transfers.keyFingerprint(entry.getCompound("key"))))
                throw new IOException("Missing or duplicate legacy domain delta key");
            if (entry.contains("deleted") && (!entry.contains("deleted", Tag.TAG_BYTE)
                || entry.getByte("deleted") != 0 && entry.getByte("deleted") != 1))
                throw new IOException("Malformed legacy domain deletion marker");
        }
        ListTag entries = new ListTag();
        for (Tag raw : Legacy2042Storage.compounds(base, "entries")) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.contains("key", Tag.TAG_COMPOUND)) throw new IOException("Missing legacy domain base key");
            if (!replaced.contains(Legacy2042Transfers.keyFingerprint(entry.getCompound("key")))) entries.add(entry.copy());
        }
        for (Tag raw : changes) if (!((CompoundTag) raw).getBoolean("deleted")) entries.add(raw.copy());
        CompoundTag result = delta.copy();
        result.remove("base_revision"); result.put("entries", entries);
        return result;
    }
}
