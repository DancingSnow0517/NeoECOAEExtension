package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.zip.CRC32;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class ECOStorageKeyHash {
    public static final int VERSION = 2;
    public static final String SHARD_HASH_VERSION_TAG = "shardHashVersion";

    private ECOStorageKeyHash() {}

    public static int shardFor(HolderLookup.Provider registries, AEKey key, int shardCount) {
        return shardForEncodedKey(key.toTagGeneric(registries), shardCount);
    }

    /** Uses the same hash as {@link #shardFor(HolderLookup.Provider, AEKey, int)} for an unresolved raw key. */
    public static int shardForEncodedKey(Tag encodedKey, int shardCount) {
        return Math.floorMod(stableHash(encodedKey), shardCount);
    }

    /** Returns a canonical fingerprint whose result does not depend on compound-tag insertion order. */
    public static String stableFingerprint(HolderLookup.Provider registries, AEKey key) {
        return stableFingerprint(key.toTagGeneric(registries));
    }

    public static String stableFingerprint(Tag tag) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        updateTag(digest, tag);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int stableHash(HolderLookup.Provider registries, AEKey key) {
        CRC32 crc = new CRC32();
        updateTag(crc, key.toTagGeneric(registries));
        return (int) crc.getValue();
    }

    private static int stableHash(Tag tag) {
        CRC32 crc = new CRC32();
        updateTag(crc, tag);
        return (int) crc.getValue();
    }

    private static void updateTag(CRC32 crc, Tag tag) {
        updateInt(crc, tag.getId());
        if (tag instanceof CompoundTag compound) {
            ArrayList<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            updateInt(crc, keys.size());
            for (String key : keys) {
                updateString(crc, key);
                Tag value = compound.get(key);
                if (value == null) {
                    updateInt(crc, 0);
                } else {
                    updateTag(crc, value);
                }
            }
        } else if (tag instanceof ListTag list) {
            updateInt(crc, list.size());
            for (int i = 0; i < list.size(); i++) {
                updateTag(crc, list.get(i));
            }
        } else {
            updateString(crc, tag.getAsString());
        }
    }

    private static void updateTag(MessageDigest digest, Tag tag) {
        updateInt(digest, tag.getId());
        if (tag instanceof CompoundTag compound) {
            ArrayList<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            updateInt(digest, keys.size());
            for (String key : keys) {
                updateString(digest, key);
                Tag value = compound.get(key);
                if (value == null) {
                    updateInt(digest, 0);
                } else {
                    updateTag(digest, value);
                }
            }
        } else if (tag instanceof ListTag list) {
            updateInt(digest, list.size());
            for (int i = 0; i < list.size(); i++) {
                updateTag(digest, list.get(i));
            }
        } else {
            updateString(digest, tag.getAsString());
        }
    }

    private static void updateString(CRC32 crc, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(crc, bytes.length);
        crc.update(bytes, 0, bytes.length);
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(CRC32 crc, int value) {
        crc.update(value >>> 24 & 0xFF);
        crc.update(value >>> 16 & 0xFF);
        crc.update(value >>> 8 & 0xFF);
        crc.update(value & 0xFF);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24 & 0xFF));
        digest.update((byte) (value >>> 16 & 0xFF));
        digest.update((byte) (value >>> 8 & 0xFF));
        digest.update((byte) (value & 0xFF));
    }
}
