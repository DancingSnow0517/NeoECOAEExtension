package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InfiniteStorageSnapshotTest {
    @TempDir
    Path directory;

    @Test
    void replacesExistingSnapshotOnlyWithVerifiedContents() throws Exception {
        Path target = directory.resolve("domain.dat");
        CompoundTag first = data(1);
        InfiniteStorageSnapshot.write(target, first, 3465);
        CompoundTag next = data(2);
        next.putString("unicode", "无限存储\u0000😀");
        InfiniteStorageSnapshot.write(target, next, 3465);
        assertEquals(next, InfiniteStorageSnapshot.read(target));
        try (var files = Files.list(directory)) {
            assertEquals(java.util.List.of(target), files.toList());
        }
    }

    @Test
    void unencodableSnapshotDoesNotDestroyLastCommittedFile() throws Exception {
        Path target = directory.resolve("domain.dat");
        InfiniteStorageSnapshot.write(target, data(1), 3465);
        byte[] original = Files.readAllBytes(target);
        CompoundTag oversized = data(2);
        oversized.putString("oversized", "x".repeat(70_000));
        assertThrows(IOException.class, () -> InfiniteStorageSnapshot.write(target, oversized, 3465));
        assertArrayEquals(original, Files.readAllBytes(target));
        assertEquals(data(1), InfiniteStorageSnapshot.read(target));
    }

    @Test
    void missingDataCompoundIsAnErrorNotAnEmptyDomain() throws Exception {
        Path target = directory.resolve("domain.dat");
        try (var output = Files.newOutputStream(target)) {
            NbtIo.writeCompressed(new CompoundTag(), output);
        }
        byte[] original = Files.readAllBytes(target);
        assertThrows(IOException.class, () -> InfiniteStorageSnapshot.read(target));
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void readsExistingMinecraftSavedDataWithoutRewritingIt() throws Exception {
        Path target = directory.resolve("domain.dat");
        CompoundTag root = new CompoundTag();
        root.put("data", data(7));
        root.putInt("DataVersion", 3465);
        try (var output = Files.newOutputStream(target)) {
            NbtIo.writeCompressed(root, output);
        }
        byte[] original = Files.readAllBytes(target);
        assertEquals(data(7), InfiniteStorageSnapshot.read(target));
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void malformedUtfIsReportedAndNeverRewritten() throws Exception {
        Path target = directory.resolve("domain.dat");
        try (var output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(target)))) {
            output.writeByte(10); // Root compound.
            output.writeUTF("");
            output.writeByte(10); // Child compound with a malformed UTF name, as in the reported failure.
            output.writeShort(1);
            output.writeByte(0xC0);
            output.writeByte(0);
            output.writeByte(0);
        }
        byte[] original = Files.readAllBytes(target);
        assertThrows(Exception.class, () -> InfiniteStorageSnapshot.read(target));
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    private static CompoundTag data(long revision) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("revision", revision);
        return tag;
    }

    @Test
    void corruptedRecordDoesNotHideOtherRecordsAndItsBytesSurviveAnotherSave() throws Exception {
        Path target = directory.resolve("domain.dat");
        CompoundTag original = data(1);
        ListTag entries = new ListTag();
        entries.add(data(101));
        entries.add(data(202));
        original.put("entries", entries);
        InfiniteStorageSnapshot.write(target, original, 3465);

        CompoundTag root;
        try (var input = Files.newInputStream(target)) {
            root = NbtIo.readCompressed(input);
        }
        assertEquals(3465, root.getInt("DataVersion"));
        byte[] member =
                root.getCompound("data").getList("entries", 10).getCompound(0).getByteArray("payload");
        member[member.length / 2] ^= 1;
        try (var output = Files.newOutputStream(target)) {
            NbtIo.writeCompressed(root, output);
        }
        CompoundTag recovered = InfiniteStorageSnapshot.read(target);
        ListTag recoveredEntries = recovered.getList("entries", 10);
        assertEquals(data(202), recoveredEntries.getCompound(1));
        CompoundTag isolated = recoveredEntries.getCompound(0);
        assertEquals("entries/0", isolated.getString("isolated_record"));
        assertArrayEquals(member, isolated.getByteArray("isolated_bytes"));
        InfiniteStorageSnapshot.write(target, recovered, 3465);
        assertEquals(recovered, InfiniteStorageSnapshot.read(target));
    }
}
