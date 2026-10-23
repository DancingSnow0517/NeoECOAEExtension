package cn.dancingsnow.neoecoae.impl.storage.persistence;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import cn.dancingsnow.neoecoae.platform.nbt.NECellNbt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Legacy2042StorageTest {
    @BeforeAll static void bootstrap() throws Exception { PlatformTestBootstrap.initializeKeyRegistry(); }
    private static ECOStorageRepository.Session session(Path path) {
        return new ECOStorageRepository.Session(new DimensionDataStorage(path.toFile(), DataFixers.getDataFixer()), path);
    }
    private static AEKey key() {
        ItemStack diamond = new ItemStack(Items.DIAMOND); diamond.getOrCreateTag().putInt("grade", 7);
        return AEItemKey.of(diamond);
    }
    private static ItemStack disk(UUID id) {
        var disk = new ItemStack(Items.PAPER);
        disk.getOrCreateTag().putUUID("eco_cell_id", id); disk.getOrCreateTag().putInt("eco_cell_version", 2);
        disk.getOrCreateTag().putString("FuzzyMode", "PERCENT_25");
        disk.getOrCreateTag().putString("unrelated", "retained");
        return disk;
    }
    private static CompoundTag snapshot(UUID id, long amount) {
        CompoundTag data = new CompoundTag(), entry = new CompoundTag(); ListTag entries = new ListTag();
        data.putInt("format", 1); data.putUUID("cell", id); data.putLong("revision", 19);
        entry.put("key", key().toTagGeneric()); entry.putLong("amount", amount); entries.add(entry);
        data.put("entries", entries); return data;
    }
    private static Path write(Path directory, UUID id, CompoundTag data) throws Exception {
        Path file = directory.resolve("neoecoae_cells/" + id + ".dat"); Files.createDirectories(file.getParent());
        CompoundTag root = new CompoundTag(); root.put("data", data); NbtIo.writeCompressed(root, file.toFile()); return file;
    }

    @Test void convertsUuidInventoryNbtAndConfigurationAndPreservesOriginalBytes(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); ItemStack disk = disk(id), stale = disk.copy();
        Path file = write(directory, id, snapshot(id, 700)); byte[] before = Files.readAllBytes(file);
        var access = session(directory).open(disk, null);
        assertTrue(access.writable()); assertEquals(id, access.data().id());
        assertEquals(700, access.data().amounts().visible(key()));
        assertEquals("retained", disk.getTag().getString("unrelated"));
        assertEquals(FuzzyMode.PERCENT_25, NECellNbt.fuzzyMode(disk));
        assertEquals(id, disk.getTag().getCompound("neoecoae_storage_ref").getUUID("id"));
        Path archive = directory.resolve("neoecoae_20_4_2_archive/cells/" + id + ".dat");
        assertArrayEquals(before, Files.readAllBytes(archive));
        access.data().amounts().subtract(key(), 200); access.data().setDirty(); assertTrue(access.data().flush());
        assertEquals(500, session(directory).open(stale, null).data().amounts().visible(key()));
        assertArrayEquals(before, Files.readAllBytes(archive));
        assertFalse(NbtIo.readCompressed(file.toFile()).getCompound("data").contains("cell"));
    }

    @Test void fluidKeyNbtAndLongMaximumSurviveTheSameConversion(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var fluid = new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        fluid.getOrCreateTag().putString("batch", "legacy-water"); var key = appeng.api.stacks.AEFluidKey.of(fluid);
        var snapshot = snapshot(id, Long.MAX_VALUE); snapshot.getList("entries", Tag.TAG_COMPOUND).getCompound(0).put("key", key.toTagGeneric());
        write(directory, id, snapshot); var disk = disk(id);
        var converted = session(directory).open(disk, null); assertTrue(converted.writable());
        assertEquals(Long.MAX_VALUE, converted.data().amounts().visible(key));
        assertEquals(java.math.BigInteger.valueOf(Long.MAX_VALUE), session(directory).open(disk, null).data().amounts().get(key).toBigInteger());
    }

    @Test void importsItemPayloadOnlyAfterSavingAndArchivesTheWholeItem(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); ItemStack disk = disk(id);
        ListTag contents = new ListTag(); contents.add(appeng.api.stacks.GenericStack.writeTag(new appeng.api.stacks.GenericStack(key(), 51)));
        disk.getOrCreateTag().put("eco_cell_contents", contents); CompoundTag original = disk.save(new CompoundTag());
        var access = session(directory).open(disk, null);
        assertTrue(access.writable()); assertEquals(51, access.data().amounts().visible(key()));
        assertFalse(disk.getTag().contains("eco_cell_contents"));
        assertEquals(original, NbtIo.readCompressed(directory.resolve("neoecoae_20_4_2_archive/items/" + id + ".dat").toFile()).getCompound("item"));
        assertEquals(51, session(directory).open(disk, null).data().amounts().visible(key()));
    }

    @Test void missingOldFileNeverCreatesAnEmptyDisk(@TempDir Path directory) {
        UUID id = UUID.randomUUID(); ItemStack disk = disk(id); CompoundTag original = disk.save(new CompoundTag());
        var access = session(directory).open(disk, null);
        assertFalse(access.writable()); assertFalse(access.data().canRead()); assertEquals(id, ECOCellReference.id(disk));
        assertEquals(original, disk.save(new CompoundTag())); assertFalse(access.data().flush());
    }

    @Test void corruptOldFileAndBackupAreNotTreatedAsEmpty(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var disk = disk(id); Path file = write(directory, id, snapshot(id, 90));
        Files.copy(file, file.resolveSibling(file.getFileName() + ".bak")); byte[] corrupt = {1,2,3}; Files.write(file, corrupt);
        var access = session(directory).open(disk, null);
        assertFalse(access.writable()); assertFalse(access.data().flush()); assertArrayEquals(corrupt, Files.readAllBytes(file));
    }

    @Test void archiveFailureCanRetryWithoutPublishingAnEmptyReference(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var disk = disk(id); Path file = write(directory, id, snapshot(id, 90));
        byte[] original = Files.readAllBytes(file); Path blocked = directory.resolve("neoecoae_20_4_2_archive"); Files.writeString(blocked, "keep");
        var session = session(directory); var failed = session.open(disk, null);
        assertFalse(failed.writable()); assertFalse(disk.getTag().contains("neoecoae_storage_ref"));
        session.storage().save(); assertArrayEquals(original, Files.readAllBytes(file));
        Files.delete(blocked); var retried = session.open(disk, null);
        assertTrue(retried.writable()); assertEquals(90, retried.data().amounts().visible(key()));
    }

    @Test void vanillaSaveCanCompleteAFailedImportBeforeItsNextMount(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var disk = disk(id); Path file = write(directory, id, snapshot(id, 90));
        byte[] original = Files.readAllBytes(file); var session = session(directory);
        try (var writes = org.mockito.Mockito.mockStatic(cn.dancingsnow.neoecoae.platform.nbt.NEAtomicNbtFile.class)) {
            writes.when(() -> cn.dancingsnow.neoecoae.platform.nbt.NEAtomicNbtFile.write(
                org.mockito.ArgumentMatchers.any(CompoundTag.class), org.mockito.ArgumentMatchers.eq(file.toAbsolutePath())))
                .thenThrow(new java.io.IOException("Injected first import write failure"));
            assertFalse(session.open(disk, null).writable());
        }
        session.storage().save();
        assertTrue(NbtIo.readCompressed(file.toFile()).getCompound("data").hasUUID("id"));
        assertTrue(session.open(disk, null).writable());
        assertEquals(90, session.open(disk, null).data().amounts().visible(key()));
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("neoecoae_20_4_2_archive/cells/" + id + ".dat")));
    }

    @Test void unknownEntryLocksWholeImportAndPreservesOriginal(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var disk = disk(id); var data = snapshot(id, 90);
        CompoundTag unknown = new CompoundTag(), encoded = new CompoundTag(); encoded.putString("#c", "absent:channel");
        unknown.put("key", encoded); unknown.putLong("amount", 33); data.getList("entries", Tag.TAG_COMPOUND).add(unknown);
        Path file = write(directory, id, data); byte[] original = Files.readAllBytes(file);
        var access = session(directory).open(disk, null);
        assertFalse(access.writable()); assertFalse(access.data().flush()); assertFalse(disk.getTag().contains("neoecoae_storage_ref"));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test void wrongIdentityAndDuplicateKeysAreRejected(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var data = snapshot(UUID.randomUUID(), 90); Path file = write(directory, id, data);
        var access = session(directory).open(disk(id), null); assertFalse(access.writable());
        byte[] original = Files.readAllBytes(file); assertFalse(access.data().flush()); assertArrayEquals(original, Files.readAllBytes(file));
        var duplicate = snapshot(id, 90); duplicate.getList("entries", Tag.TAG_COMPOUND).add(duplicate.getList("entries", Tag.TAG_COMPOUND).get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> Legacy2042Storage.cell(duplicate, id));
    }

    @Test void malformedItemIdentityIsRetainedAndLocked(@TempDir Path directory) {
        ItemStack disk = disk(UUID.randomUUID()); disk.getOrCreateTag().putString("eco_cell_id", "broken");
        CompoundTag original = disk.save(new CompoundTag());
        assertFalse(session(directory).open(disk, null).writable()); assertEquals(original, disk.save(new CompoundTag()));
    }

    @Test void nativeReferenceWinsOverStaleOldIdentity(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var disk = disk(id); write(directory, id, snapshot(id, 90));
        var session = session(directory); var first = session.open(disk, null);
        disk.getOrCreateTag().putUUID("eco_cell_id", UUID.randomUUID());
        assertSame(first.data(), session.open(disk, null).data());
        assertEquals(90, session(directory).open(disk, null).data().amounts().visible(key()));
    }

    @Test void legacyMemberIdentityIsStableAcrossFreshMetadataCreation(@TempDir Path directory) {
        UUID domain = UUID.randomUUID(), matrix = UUID.randomUUID(); var disk = new ItemStack(Items.PAPER);
        disk.getOrCreateTag().putBoolean("neoecoae_infinite_member", true); disk.getOrCreateTag().putUUID("neoecoae_infinite_domain", domain);
        disk.getOrCreateTag().putUUID("neoecoae_infinite_matrix_id", matrix);
        var access = session(directory).open(disk, null);
        assertTrue(access.writable()); assertTrue(access.data().amounts().isEmpty());
        assertEquals(matrix, access.data().metadata().getUUID("neoecoae_member_identity"));
        assertEquals(domain, disk.getTag().getUUID("neoecoae_infinite_domain"));
    }

    @Test void missingConvertedItemSnapshotNeverReimportsArchivedInlinePayload(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var disk = disk(id); ListTag contents = new ListTag();
        contents.add(appeng.api.stacks.GenericStack.writeTag(new appeng.api.stacks.GenericStack(key(), 51)));
        disk.getOrCreateTag().put("eco_cell_contents", contents); var stale = disk.copy();
        var converted = session(directory).open(disk, null).data();
        Files.delete(converted.dataFile());
        assertFalse(session(directory).open(stale, null).writable());
        assertTrue(stale.getTag().contains("eco_cell_contents"));
    }

    @Test void legacyRestoreBarrierSurvivesSavedDataAndStaleItemReload(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var disk = disk(id); write(directory, id, snapshot(id, 90));
        var source = session(directory).open(disk, null).data(); var stale = disk.copy();
        var metadata = source.metadata(); CompoundTag receipts = new CompoundTag(); receipts.putString("reason", "needs proof");
        metadata.put(Legacy2042Storage.RESTORE_BARRIER, receipts); source.metadata(metadata); assertTrue(source.flush());
        var restarted = session(directory).open(stale, null);
        assertTrue(restarted.data().canRead()); assertFalse(restarted.writable());
        assertEquals(90, restarted.data().amounts().visible(key()));
        assertEquals(receipts, stale.getTag().getCompound(Legacy2042Storage.RESTORE_BARRIER));
    }
}
