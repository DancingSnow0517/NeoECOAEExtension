package cn.dancingsnow.neoecoae.storage.persistence;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.platform.nbt.NEAtomicNbtFile;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/** Wire-format adapter for release 20.4.2 (f1739d1f); the current backends retain ownership. */
public final class Legacy2042Storage {
    public static final String CELL_ID = "eco_cell_id";
    public static final String CONTENTS = "eco_cell_contents";
    public static final String MATRIX_ID = "neoecoae_infinite_matrix_id";
    public static final String ORIGIN = "legacy2042";
    public static final String RECEIPTS = "legacy2042Receipts";
    public static final String RESTORE_BARRIER = "neoecoae_legacy2042_restore";
    private Legacy2042Storage() {}

    public static CompoundTag metadata(CompoundTag item) {
        CompoundTag result = new CompoundTag();
        for (String key : ECOCellReference.METADATA_KEYS)
            if (item.contains(key)) result.put(key, item.get(key).copy());
        if (!result.hasUUID("neoecoae_member_identity") && item.hasUUID(MATRIX_ID))
            result.putUUID("neoecoae_member_identity", item.getUUID(MATRIX_ID));
        return result;
    }

    public static boolean isCell(CompoundTag tag) { return tag.contains("cell") && !tag.contains("id"); }

    public static CompoundTag cell(CompoundTag source, UUID id) {
        requireHeader(source, 1, "cell", id);
        CompoundTag result = new CompoundTag();
        result.putInt("format", 2); result.putUUID("id", id);
        result.putLong("storageRevision", source.getLong("revision"));
        ListTag entries = new ListTag();
        Set<CompoundTag> seen = new HashSet<>();
        for (Tag raw : compounds(source, "entries")) {
            CompoundTag entry = (CompoundTag) raw;
            requireKey(entry, seen);
            if (!entry.contains("amount", Tag.TAG_LONG) || entry.getLong("amount") <= 0)
                throw new IllegalArgumentException("Invalid 20.4.2 cell amount");
            entries.add(entry.copy());
        }
        result.put("entries", entries); result.putBoolean(ORIGIN, true);
        return result;
    }

    public static CompoundTag domain(CompoundTag source, UUID id) {
        requireHeader(source, 2, "domain", id);
        CompoundTag result = new CompoundTag();
        result.putInt("version", 4); result.putUUID("domainIdentity", id);
        result.putLong("storageRevision", source.getLong("revision"));
        ListTag entries = new ListTag();
        Set<CompoundTag> seen = new HashSet<>();
        Set<AEKey> decoded = new HashSet<>();
        for (Tag raw : compounds(source, "entries")) {
            CompoundTag old = (CompoundTag) raw;
            requireKey(old, seen);
            AEKey key = AEKey.fromTagGeneric(old.getCompound("key"));
            if (resolved(key) && !decoded.add(key)) throw new IllegalArgumentException("Duplicate decoded 20.4.2 domain key");
            boolean narrow = old.contains("amount_long", Tag.TAG_LONG);
            boolean wide = old.contains("amount_wide", Tag.TAG_BYTE_ARRAY);
            if (narrow == wide || old.contains("amount_long") != narrow || old.contains("amount_wide") != wide)
                throw new IllegalArgumentException("Ambiguous 20.4.2 domain amount");
            BigInteger amount;
            if (wide) {
                byte[] encoded = old.getByteArray("amount_wide");
                if (encoded.length == 0) throw new IllegalArgumentException("Empty 20.4.2 wide amount");
                amount = new BigInteger(encoded);
                if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0 || !Arrays.equals(encoded, amount.toByteArray()))
                    throw new IllegalArgumentException("Noncanonical 20.4.2 wide amount");
            } else amount = BigInteger.valueOf(old.getLong("amount_long"));
            if (amount.signum() <= 0) throw new IllegalArgumentException("Nonpositive 20.4.2 domain amount");
            CompoundTag entry = new CompoundTag();
            entry.put("key", old.getCompound("key").copy()); entry.put("amount", HugeAmount.of(amount).write());
            entries.add(entry);
        }
        result.put("entries", entries);
        ListTag migrations = new ListTag(), receipts = compounds(source, "transfer_receipts");
        Set<UUID> seenReceipts = new HashSet<>();
        for (Tag raw : receipts) {
            CompoundTag receipt = (CompoundTag) raw;
            if (!receipt.hasUUID("id") || !seenReceipts.add(receipt.getUUID("id")))
                throw new IllegalArgumentException("Invalid 20.4.2 transfer receipt");
            if (receipt.contains("contents_sha256") && (!receipt.contains("contents_sha256", Tag.TAG_STRING)
                || !receipt.getString("contents_sha256").matches("[0-9a-f]{64}")))
                throw new IllegalArgumentException("Invalid 20.4.2 receipt digest");
            migrations.add(NbtUtils.createUUID(receipt.getUUID("id")));
        }
        result.put("migrations", migrations); result.put(RECEIPTS, receipts.copy());
        CompoundTag provenance = source.copy();
        provenance.remove("entries"); provenance.remove("transfer_receipts");
        result.put(ORIGIN, provenance);
        return result;
    }

    private static void requireHeader(CompoundTag tag, int format, String key, UUID id) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != format
            || !tag.hasUUID(key) || !id.equals(tag.getUUID(key))
            || !tag.contains("revision", Tag.TAG_LONG) || tag.getLong("revision") < 0)
            throw new IllegalArgumentException("Invalid 20.4.2 storage format, identity or revision");
    }

    public static ListTag compounds(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid 20.4.2 list: " + key);
        return list;
    }

    private static void requireKey(CompoundTag entry, Set<CompoundTag> seen) {
        if (!entry.contains("key", Tag.TAG_COMPOUND) || !seen.add(entry.getCompound("key")))
            throw new IllegalArgumentException("Missing or duplicate 20.4.2 storage key");
    }

    public static boolean resolved(AEKey key) {
        return key != null && !key.getId().toString().equals("ae2:missing_content");
    }

    /** Keep the exact original bytes before the ordinary-cell path is atomically replaced. Never replay this archive. */
    public static void archive(Path source, Path archive) throws IOException {
        if (Files.exists(archive)) {
            if (!Files.isRegularFile(archive) || Files.mismatch(source, archive) != -1)
                throw new IOException("20.4.2 source differs from its preserved archive: " + source);
            return;
        }
        Files.createDirectories(archive.getParent());
        Path temporary = Files.createTempFile(archive.getParent(), "legacy-", ".pending");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            if (Files.mismatch(source, temporary) != -1) throw new IOException("20.4.2 archive verification failed");
            Files.move(temporary, archive, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }

    public static void archiveItem(CompoundTag item, Path archive) throws IOException {
        CompoundTag root = new CompoundTag(); root.put("item", item.copy());
        if (Files.exists(archive)) {
            if (!root.equals(net.minecraft.nbt.NbtIo.readCompressed(archive.toFile())))
                throw new IOException("20.4.2 item differs from its preserved archive");
        } else NEAtomicNbtFile.write(root, archive);
    }
}
