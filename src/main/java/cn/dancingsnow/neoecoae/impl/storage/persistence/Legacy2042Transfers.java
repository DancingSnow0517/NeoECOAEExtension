package cn.dancingsnow.neoecoae.impl.storage.persistence;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Verifies the persisted 20.4.2 receipt format before the current transfer journal retires a source. */
public final class Legacy2042Transfers {
    private Legacy2042Transfers() {}

    public static Map<AEKey, Set<UUID>> aliases(ECOCellSavedData source, SavedDataInfiniteStorageEngine target,
            UUID domain, String dimension, long position, ItemStack item) {
        if (!source.legacy2042() || !target.imported2042()) return Map.of();
        Map<AEKey, Set<UUID>> aliases = new HashMap<>();
        Map<AEKey, BigInteger> pending = new HashMap<>();
        source.amounts().forEach((key, amount) -> {
            String value = domain + ":to-domain:" + position + ":" + key.toTagGeneric() + ":" + amount.toLongSaturated();
            UUID id = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
            CompoundTag receipt = target.receipt2042(id);
            if (receipt == null) pending.put(key, amount.toBigInteger());
            else {
                verify(receipt, digest(source, Map.of(key, amount.toBigInteger())), true);
                aliases.put(key, Set.of(id));
            }
        });
        List<UUID> batchIds = new ArrayList<>();
        batchIds.add(UUID.nameUUIDFromBytes((domain + ":to-domain-v2:" + position).getBytes(StandardCharsets.UTF_8)));
        CompoundTag tag = item.getTag();
        if (tag != null && tag.hasUUID(Legacy2042Storage.MATRIX_ID)) {
            String value = domain + ":to-domain-v3:" + dimension + ":" + tag.getUUID(Legacy2042Storage.MATRIX_ID);
            batchIds.add(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)));
        }
        if (!pending.isEmpty()) for (UUID id : batchIds) {
            CompoundTag receipt = target.receipt2042(id);
            if (receipt == null) continue;
            verify(receipt, digest(source, pending), false);
            pending.keySet().forEach(key -> aliases.put(key, Set.of(id)));
            break;
        }
        return Map.copyOf(aliases);
    }

    private static void verify(CompoundTag receipt, String digest, boolean perKey) {
        if (perKey && !receipt.contains("contents_sha256")) return;
        if (!digest.equals(receipt.getString("contents_sha256")))
            throw new IllegalStateException("20.4.2 transfer receipt does not prove this source inventory; source retained");
    }

    public static String digest(ECOCellSavedData source, Map<AEKey, BigInteger> contents) {
        List<String> records = new ArrayList<>();
        contents.forEach((key, amount) -> {
            records.add(keyFingerprint(source.encodedKey(key)) + ":" + amount);
        });
        records.sort(String::compareTo);
        MessageDigest digest = sha256();
        integer(digest, records.size());
        for (String record : records) string(digest, record);
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String keyFingerprint(CompoundTag key) {
        MessageDigest digest = sha256();
        hashTag(digest, key);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void hashTag(MessageDigest digest, Tag tag) {
        integer(digest, tag.getId());
        if (tag instanceof CompoundTag compound) {
            var keys = new ArrayList<>(compound.getAllKeys()); keys.sort(String::compareTo);
            integer(digest, keys.size());
            for (String key : keys) { string(digest, key); hashTag(digest, compound.get(key)); }
        } else if (tag instanceof ListTag list) {
            integer(digest, list.size());
            for (Tag value : list) hashTag(digest, value);
        } else string(digest, tag.getAsString());
    }

    private static void integer(MessageDigest digest, int value) { digest.update(ByteBuffer.allocate(4).putInt(value).array()); }
    private static void string(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); integer(digest, bytes.length); digest.update(bytes);
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
