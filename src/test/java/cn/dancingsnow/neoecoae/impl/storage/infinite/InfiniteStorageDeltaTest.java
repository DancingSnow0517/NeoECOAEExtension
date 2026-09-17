package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InfiniteStorageDeltaTest {
    @TempDir
    Path directory;

    private final UUID domain = UUID.randomUUID();

    @Test
    void cumulativeOverlayReplacesDeletesAndAddsWithoutRewritingBase() throws Exception {
        Path path = directory.resolve("domain.dat");
        CompoundTag base = snapshot(1, entry("a", 10), entry("b", 20), entry("c", 30));
        InfiniteStorageSnapshot.write(path, base, 3465);
        byte[] original = Files.readAllBytes(path);
        CompoundTag deleted = entry("b", 0);
        deleted.putBoolean(InfiniteStorageDelta.DELETED, true);
        CompoundTag delta = snapshot(2, entry("a", 11), deleted, entry("d", Long.MAX_VALUE));
        delta.putLong(InfiniteStorageDelta.BASE_REVISION, 1);
        InfiniteStorageSnapshot.write(InfiniteStorageDelta.path(path), delta, 3465);
        CompoundTag recovered = InfiniteStorageSnapshot.read(path);
        assertEquals(snapshot(2, entry("c", 30), entry("a", 11), entry("d", Long.MAX_VALUE)), recovered);
        assertArrayEquals(original, Files.readAllBytes(path));
        // The next overlay includes previous changes too; replay must not add quantities twice.
        delta.putLong("revision", 3);
        delta.getList("entries", 10).getCompound(0).putLong("amount_long", 12);
        InfiniteStorageSnapshot.write(InfiniteStorageDelta.path(path), delta, 3465);
        assertEquals(
                snapshot(3, entry("c", 30), entry("a", 12), entry("d", Long.MAX_VALUE)),
                InfiniteStorageSnapshot.read(path));
    }

    @Test
    void compactedBaseWinsIfCrashLeavesPreviousOverlay() throws Exception {
        Path path = directory.resolve("domain.dat");
        CompoundTag delta = snapshot(2, entry("a", 20));
        delta.putLong(InfiniteStorageDelta.BASE_REVISION, 1);
        InfiniteStorageSnapshot.write(InfiniteStorageDelta.path(path), delta, 3465);
        CompoundTag compacted = snapshot(3, entry("a", 30));
        InfiniteStorageSnapshot.write(path, compacted, 3465);
        assertEquals(compacted, InfiniteStorageSnapshot.read(path));
    }

    @Test
    void wrongBaseOrDomainIsNeverSilentlyReplayed() {
        CompoundTag base = snapshot(2, entry("a", 10));
        CompoundTag delta = snapshot(3, entry("a", 20));
        delta.putLong(InfiniteStorageDelta.BASE_REVISION, 1);
        assertThrows(IOException.class, () -> InfiniteStorageDelta.apply(base, delta));
        delta.putLong(InfiniteStorageDelta.BASE_REVISION, 2);
        delta.putUUID("domain", UUID.randomUUID());
        assertThrows(IOException.class, () -> InfiniteStorageDelta.apply(base, delta));
    }

    @Test
    void damagedChangeHidesItsOldQuantityAndPreservesOtherKeys() throws Exception {
        Path path = directory.resolve("domain.dat");
        InfiniteStorageSnapshot.write(path, snapshot(1, entry("a", 10), entry("b", 20)), 3465);
        CompoundTag delta = snapshot(2, entry("a", 11));
        delta.putLong(InfiniteStorageDelta.BASE_REVISION, 1);
        Path deltaPath = InfiniteStorageDelta.path(path);
        InfiniteStorageSnapshot.write(deltaPath, delta, 3465);
        CompoundTag root;
        try (var input = Files.newInputStream(deltaPath)) {
            root = NbtIo.readCompressed(input);
        }
        byte[] bytes =
                root.getCompound("data").getList("entries", 10).getCompound(0).getByteArray("payload");
        bytes[bytes.length / 2] ^= 1;
        try (var output = Files.newOutputStream(deltaPath)) {
            NbtIo.writeCompressed(root, output);
        }
        ListTag recovered = InfiniteStorageSnapshot.read(path).getList("entries", 10);
        assertEquals(2, recovered.size());
        assertEquals(entry("b", 20), recovered.getCompound(0));
        assertArrayEquals(bytes, recovered.getCompound(1).getByteArray("isolated_bytes"));
    }

    @Test
    void failedOverlayReplacementKeepsPreviousCommit() throws Exception {
        Path path = directory.resolve("domain.dat");
        InfiniteStorageSnapshot.write(path, snapshot(1, entry("a", 10)), 3465);
        CompoundTag delta = snapshot(2, entry("a", 11));
        delta.putLong(InfiniteStorageDelta.BASE_REVISION, 1);
        InfiniteStorageSnapshot.write(InfiniteStorageDelta.path(path), delta, 3465);
        CompoundTag expected = InfiniteStorageSnapshot.read(path);
        delta.putString("invalid_utf_length", "x".repeat(70000));
        assertThrows(
                IOException.class, () -> InfiniteStorageSnapshot.write(InfiniteStorageDelta.path(path), delta, 3465));
        assertEquals(expected, InfiniteStorageSnapshot.read(path));
    }

    private CompoundTag snapshot(long revision, CompoundTag... records) {
        CompoundTag data = new CompoundTag();
        data.putUUID("domain", domain);
        data.putLong("revision", revision);
        ListTag entries = new ListTag();
        for (CompoundTag record : records) entries.add(record);
        data.put("entries", entries);
        data.put("transfer_receipts", new ListTag());
        return data;
    }

    private static CompoundTag entry(String id, long amount) {
        CompoundTag key = new CompoundTag();
        key.putString("id", "test:" + id);
        CompoundTag entry = new CompoundTag();
        entry.put("key", key);
        entry.putLong("amount_long", amount);
        return entry;
    }
}
