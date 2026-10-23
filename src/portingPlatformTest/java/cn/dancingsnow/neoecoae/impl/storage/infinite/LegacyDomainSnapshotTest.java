package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.impl.storage.persistence.Legacy2042Transfers;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.CRC32;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyDomainSnapshotTest {
    @BeforeAll static void bootstrap() throws Exception { PlatformTestBootstrap.initializeKeyRegistry(); }

    private static CompoundTag entry(boolean gold, long amount) {
        CompoundTag tag = new CompoundTag();
        tag.put("key", AEItemKey.of(gold ? Items.GOLD_INGOT : Items.DIAMOND).toTagGeneric());
        tag.putLong("amount_long", amount); return tag;
    }

    private static CompoundTag snapshot(UUID domain, long revision) {
        CompoundTag data = new CompoundTag();
        data.putInt("format", 2); data.putUUID("domain", domain); data.putLong("revision", revision);
        ListTag entries = new ListTag(); entries.add(entry(false, 70)); entries.add(entry(true, 20));
        data.put("entries", entries); data.put("transfer_receipts", new ListTag()); return data;
    }

    private static CompoundTag envelope(CompoundTag source) throws Exception {
        CompoundTag data = source.copy();
        for (String name : new String[] {"entries", "transfer_receipts"}) {
            ListTag records = new ListTag();
            for (Tag tag : source.getList(name, Tag.TAG_COMPOUND)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NbtIo.write((CompoundTag) tag, new DataOutputStream(bytes));
                byte[] payload = bytes.toByteArray(); CRC32 checksum = new CRC32(); checksum.update(payload);
                CompoundTag record = new CompoundTag(); record.putByteArray("payload", payload);
                record.putLong("crc32", checksum.getValue());
                CompoundTag entry = (CompoundTag) tag;
                if (entry.contains("key")) record.putString("key_fingerprint", Legacy2042Transfers.keyFingerprint(entry.getCompound("key")));
                records.add(record);
            }
            data.put(name, records);
        }
        CompoundTag root = new CompoundTag(); root.putInt("neoecoae_record_encoding", 1); root.put("data", data); return root;
    }

    private static void write(Path path, CompoundTag root) throws Exception {
        Files.createDirectories(path.getParent()); NbtIo.writeCompressed(root, path.toFile());
    }

    private static Path file(Path directory, UUID id) { return directory.resolve("neoecoae_infinite/domain_" + id + ".dat"); }
    private static Path sidecar(Path base, String suffix) { return base.resolveSibling(base.getFileName() + suffix); }
    private static ECOInfiniteStorageData open(Path directory, UUID id) {
        return ECOInfiniteStorageDomains.importLegacy(id, directory.resolve("neoecoae_infinite_" + id + ".dat"), true);
    }

    @Test void importsEncodedWideAmountsAndReceiptsWithoutChangingSource(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(), receipt = UUID.randomUUID(); CompoundTag data = snapshot(id, 7);
        BigInteger huge = BigInteger.TEN.pow(200).add(BigInteger.valueOf(37));
        CompoundTag first = data.getList("entries", Tag.TAG_COMPOUND).getCompound(0);
        first.remove("amount_long"); first.putByteArray("amount_wide", huge.toByteArray());
        CompoundTag proof = new CompoundTag(); proof.putUUID("id", receipt); proof.putString("contents_sha256", "2".repeat(64));
        data.getList("transfer_receipts", Tag.TAG_COMPOUND).add(proof);
        Path source = file(directory, id); write(source, envelope(data)); byte[] before = Files.readAllBytes(source);
        var imported = open(directory, id);
        assertTrue(imported.canMigrate()); assertEquals(huge, imported.getAmount(AEItemKey.of(Items.DIAMOND)).toBigInteger());
        assertTrue(imported.hasMigrationReceipt(receipt)); assertEquals("2".repeat(64), imported.receipt2042(receipt).getString("contents_sha256"));
        Path current = directory.resolve("neoecoae_infinite_" + id + "_v3.dat"); imported.bind(current); assertTrue(imported.flush());
        var restored = ECOInfiniteStorageData.load(NbtIo.readCompressed(current.toFile()).getCompound("data"));
        assertEquals(huge, restored.getAmount(AEItemKey.of(Items.DIAMOND)).toBigInteger());
        assertTrue(restored.hasMigrationReceipt(receipt)); assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test void importsDeltaReplacementDeletionAndLatestReceiptSet(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(), receipt = UUID.randomUUID(); Path source = file(directory, id);
        write(source, envelope(snapshot(id, 7))); CompoundTag delta = snapshot(id, 9); delta.putLong("base_revision", 7);
        ListTag changes = delta.getList("entries", Tag.TAG_COMPOUND); changes.getCompound(0).putLong("amount_long", 11);
        changes.getCompound(1).putBoolean("deleted", true); changes.getCompound(1).remove("amount_long");
        CompoundTag proof = new CompoundTag(); proof.putUUID("id", receipt); delta.getList("transfer_receipts", Tag.TAG_COMPOUND).add(proof);
        Path overlay = sidecar(source, ".delta.dat"); write(overlay, envelope(delta)); byte[] before = Files.readAllBytes(overlay);
        CompoundTag marker = new CompoundTag(); marker.putUUID("domain", id); marker.putLong("revision", 9);
        CompoundTag markerRoot = new CompoundTag(); markerRoot.put("data", marker); write(sidecar(source, ".commit"), markerRoot);
        var imported = open(directory, id);
        assertTrue(imported.canMigrate()); assertEquals(11, imported.getAmount(AEItemKey.of(Items.DIAMOND)).toLongSaturated());
        assertTrue(imported.getAmount(AEItemKey.of(Items.GOLD_INGOT)).isZero()); assertTrue(imported.hasMigrationReceipt(receipt));
        assertArrayEquals(before, Files.readAllBytes(overlay));
    }

    @Test void rejectsBadChecksumsFingerprintsAndTrailingRecordBytes(@TempDir Path directory) throws Exception {
        for (int fault = 0; fault < 3; fault++) {
            UUID id = UUID.randomUUID(); CompoundTag root = envelope(snapshot(id, 7));
            CompoundTag record = root.getCompound("data").getList("entries", Tag.TAG_COMPOUND).getCompound(0);
            if (fault == 0) record.putLong("crc32", record.getLong("crc32") + 1);
            if (fault == 1) record.putString("key_fingerprint", "0".repeat(64));
            if (fault == 2) {
                byte[] payload = java.util.Arrays.copyOf(record.getByteArray("payload"), record.getByteArray("payload").length + 1);
                CRC32 checksum = new CRC32(); checksum.update(payload);
                record.putByteArray("payload", payload); record.putLong("crc32", checksum.getValue());
            }
            Path source = file(directory, id); write(source, root); byte[] before = Files.readAllBytes(source);
            var imported = open(directory, id); assertFalse(imported.canRead()); assertFalse(imported.canWrite()); assertFalse(imported.flush());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test void rejectsUnsupportedEncodingEvenForAnEmptyDomain(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); CompoundTag data = snapshot(id, 0); data.put("entries", new ListTag());
        CompoundTag root = envelope(data); root.putInt("neoecoae_record_encoding", 2); write(file(directory, id), root);
        assertFalse(open(directory, id).canRead());
    }

    @Test void commitMarkerPreventsImportingAStaleOrDifferentDomain(@TempDir Path directory) throws Exception {
        for (boolean wrongIdentity : new boolean[] {false, true}) {
            UUID id = UUID.randomUUID(); Path source = file(directory, id); write(source, envelope(snapshot(id, 7)));
            CompoundTag marker = new CompoundTag(); marker.putUUID("domain", wrongIdentity ? UUID.randomUUID() : id);
            marker.putLong("revision", wrongIdentity ? 7 : 8); CompoundTag root = new CompoundTag(); root.put("data", marker);
            write(sidecar(source, ".commit"), root); assertFalse(open(directory, id).canRead());
        }
    }

    @Test void rejectsMismatchedDeltaAndDuplicateDeltaKeys(@TempDir Path directory) throws Exception {
        for (boolean duplicate : new boolean[] {false, true}) {
            UUID id = UUID.randomUUID(); Path source = file(directory, id); write(source, envelope(snapshot(id, 7)));
            CompoundTag delta = snapshot(id, 9); delta.putLong("base_revision", duplicate ? 7 : 6);
            if (duplicate) delta.getList("entries", Tag.TAG_COMPOUND).add(entry(false, 15));
            write(sidecar(source, ".delta.dat"), envelope(delta)); assertFalse(open(directory, id).canRead());
        }
    }

    @Test void ignoresAnOverlayAlreadyIncludedInACompactedBase(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); Path source = file(directory, id); write(source, envelope(snapshot(id, 10)));
        CompoundTag oldDelta = snapshot(id, 9); oldDelta.putLong("base_revision", 6);
        oldDelta.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putLong("amount_long", 1);
        write(sidecar(source, ".delta.dat"), envelope(oldDelta)); var imported = open(directory, id);
        assertTrue(imported.canMigrate()); assertEquals(70, imported.getAmount(AEItemKey.of(Items.DIAMOND)).toLongSaturated());
    }

    @Test void orphanedSidecarsCannotBecomeAnEmptyNewDomain(@TempDir Path directory) throws Exception {
        for (String suffix : new String[] {".delta.dat", ".delta.dat.bak", ".commit", ".commit.bak"}) {
            UUID id = UUID.randomUUID(); Path source = file(directory, id); write(sidecar(source, suffix), envelope(snapshot(id, 7)));
            var imported = ECOInfiniteStorageDomains.importLegacy(id, directory.resolve("neoecoae_infinite_" + id + ".dat"), false);
            assertFalse(imported.canRead()); assertFalse(imported.flush());
        }
    }

    @Test void compactedBaseCanRetainOldDeltaHistoryOnlyWhenItsCommitProvesTheRevision(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); Path source = file(directory, id); write(source, envelope(snapshot(id, 10)));
        CompoundTag oldDelta = snapshot(id, 9); oldDelta.putLong("base_revision", 6);
        write(sidecar(source, ".delta.dat.bak"), envelope(oldDelta));
        assertFalse(open(directory, id).canRead());
        CompoundTag marker = new CompoundTag(); marker.putUUID("domain", id); marker.putLong("revision", 10);
        CompoundTag root = new CompoundTag(); root.put("data", marker); write(sidecar(source, ".commit"), root);
        var imported = open(directory, id);
        assertTrue(imported.canMigrate()); assertEquals(70, imported.getAmount(AEItemKey.of(Items.DIAMOND)).toLongSaturated());
    }
}
