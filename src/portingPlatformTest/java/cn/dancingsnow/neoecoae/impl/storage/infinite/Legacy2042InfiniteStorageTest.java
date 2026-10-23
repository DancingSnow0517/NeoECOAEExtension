package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.impl.storage.persistence.Legacy2042Storage;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Legacy2042InfiniteStorageTest {
    @BeforeAll static void bootstrap() throws Exception { PlatformTestBootstrap.initializeKeyRegistry(); }
    private static CompoundTag snapshot(UUID id, BigInteger amount, UUID receipt) {
        CompoundTag data = new CompoundTag(), entry = new CompoundTag(), proof = new CompoundTag();
        data.putInt("format", 2); data.putUUID("domain", id); data.putLong("revision", 27);
        entry.put("key", AEItemKey.of(Items.DIAMOND).toTagGeneric());
        if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) entry.putByteArray("amount_wide", amount.toByteArray());
        else entry.putLong("amount_long", amount.longValueExact());
        ListTag entries = new ListTag(); entries.add(entry); data.put("entries", entries);
        ListTag receipts = new ListTag(); proof.putUUID("id", receipt); proof.putString("contents_sha256", "1".repeat(64));
        receipts.add(proof); data.put("transfer_receipts", receipts); return data;
    }
    private static Path write(Path directory, UUID id, CompoundTag data) throws Exception {
        Path path = directory.resolve("neoecoae_infinite/domain_" + id + ".dat"); Files.createDirectories(path.getParent());
        CompoundTag root = new CompoundTag(); root.put("data", data); NbtIo.writeCompressed(root, path.toFile()); return path;
    }
    private static Path legacy(Path directory, UUID id) { return directory.resolve("neoecoae_infinite_" + id + ".dat"); }

    @Test void importsExactQuantitiesAndReceiptsThenColdReloadsWithoutTouchingSource(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(), receipt = UUID.randomUUID(); BigInteger huge = BigInteger.TEN.pow(240).add(BigInteger.valueOf(123));
        Path original = write(directory, id, snapshot(id, huge, receipt)); byte[] before = Files.readAllBytes(original);
        var data = ECOInfiniteStorageDomains.importLegacy(id, legacy(directory, id), true);
        assertTrue(data.canMigrate()); assertTrue(data.imported2042());
        assertEquals(huge, data.getAmount(AEItemKey.of(Items.DIAMOND)).toBigInteger()); assertTrue(data.hasMigrationReceipt(receipt));
        Path nativeFile = directory.resolve("neoecoae_infinite_" + id + "_v3.dat"); data.bind(nativeFile); assertTrue(data.flush());
        var storage = new DimensionDataStorage(directory.toFile(), DataFixers.getDataFixer());
        var factory = ECOInfiniteStorageData.factory(nativeFile, () -> fail("A current snapshot must take precedence"));
        var restored = storage.computeIfAbsent(factory.deserializer(), factory.constructor(), "neoecoae_infinite_" + id + "_v3");
        assertEquals(huge, restored.getAmount(AEItemKey.of(Items.DIAMOND)).toBigInteger());
        assertEquals("1".repeat(64), restored.receipt2042(receipt).getString("contents_sha256"));
        var engine = new SavedDataInfiniteStorageEngine(restored);
        assertEquals(17, engine.insertOnce(receipt, AEItemKey.of(Items.DIAMOND), 17));
        assertEquals(huge, engine.getAmount(AEItemKey.of(Items.DIAMOND)).toBigInteger());
        assertArrayEquals(before, Files.readAllBytes(original));
    }

    @Test void missingExistingDomainIsLockedButExplicitNewDomainCanStartEmpty(@TempDir Path directory) {
        UUID id = UUID.randomUUID();
        assertFalse(ECOInfiniteStorageDomains.importLegacy(id, legacy(directory, id), true).canRead());
        assertTrue(ECOInfiniteStorageDomains.importLegacy(id, legacy(directory, id), false).canMigrate());
    }

    @Test void corruptSourceOrMissingSourceWithBackupCannotCreateEmptyDomain(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); Path path = write(directory, id, snapshot(id, BigInteger.TEN, UUID.randomUUID()));
        Path backup = path.resolveSibling(path.getFileName() + ".bak"); Files.copy(path, backup); byte[] bad = {7,6,5}; Files.write(path, bad);
        var data = ECOInfiniteStorageDomains.importLegacy(id, legacy(directory, id), true);
        assertFalse(data.canRead()); assertFalse(data.flush()); assertArrayEquals(bad, Files.readAllBytes(path));
        Files.delete(path); assertFalse(ECOInfiniteStorageDomains.importLegacy(id, legacy(directory, id), true).canRead());
    }

    @Test void wrongIdentityAndAmbiguousAmountCannotBeImported(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var data = snapshot(UUID.randomUUID(), BigInteger.TEN, UUID.randomUUID());
        write(directory, id, data); assertFalse(ECOInfiniteStorageDomains.importLegacy(id, legacy(directory, id), true).canRead());
        data = snapshot(id, BigInteger.TEN, UUID.randomUUID()); data.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putByteArray("amount_wide", new byte[]{1});
        CompoundTag malformed = data; assertThrows(IllegalArgumentException.class, () -> Legacy2042Storage.domain(malformed, id));
    }

    @Test void unknownResourceBlocksPublishingAndPreservesOldFile(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var source = snapshot(id, BigInteger.TEN, UUID.randomUUID());
        CompoundTag unknown = new CompoundTag(); unknown.putString("#c", "absent:key_type");
        source.getList("entries", Tag.TAG_COMPOUND).getCompound(0).put("key", unknown);
        Path path = write(directory, id, source); byte[] original = Files.readAllBytes(path);
        var data = ECOInfiniteStorageDomains.importLegacy(id, legacy(directory, id), true);
        assertFalse(data.canMigrate()); assertFalse(data.canWrite()); assertFalse(data.flush()); assertArrayEquals(original, Files.readAllBytes(path));
    }

    @Test void malformedAndDuplicateReceiptsAreRejected() {
        UUID id = UUID.randomUUID(); var source = snapshot(id, BigInteger.TEN, UUID.randomUUID());
        ListTag receipts = source.getList("transfer_receipts", Tag.TAG_COMPOUND); receipts.add(receipts.get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> Legacy2042Storage.domain(source, id));
        receipts.remove(1); receipts.getCompound(0).putString("contents_sha256", "wrong");
        assertThrows(IllegalArgumentException.class, () -> Legacy2042Storage.domain(source, id));
    }

    @Test void authoritativeNativeDamageDoesNotResurrectOldSnapshot(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); write(directory, id, snapshot(id, BigInteger.TEN, UUID.randomUUID()));
        Path file = directory.resolve("neoecoae_infinite_" + id + "_v3.dat"); byte[] corrupt = {1,4,7}; Files.write(file, corrupt);
        var factory = ECOInfiniteStorageData.factory(file, () -> ECOInfiniteStorageDomains.importLegacy(id, legacy(directory, id), true));
        var storage = new DimensionDataStorage(directory.toFile(), DataFixers.getDataFixer());
        var data = storage.computeIfAbsent(factory.deserializer(), factory.constructor(), "neoecoae_infinite_" + id + "_v3");
        assertFalse(data.canRead()); assertFalse(data.flush()); assertArrayEquals(corrupt, Files.readAllBytes(file));
    }

    @Test void restoreSafetyBarrierRemainsDurableAfterRemovingOriginalHost(@TempDir Path directory) throws Exception {
        UUID id = UUID.randomUUID(); var converted = Legacy2042Storage.domain(snapshot(id, BigInteger.TEN, UUID.randomUUID()), id);
        var data = ECOInfiniteStorageData.load(converted); data.bind(directory.resolve("blocked.dat"));
        var engine = new SavedDataInfiniteStorageEngine(data); engine.blockLegacyRestore();
        assertFalse(engine.isHealthy()); assertFalse(engine.canUseMountedStorage());
        var reloaded = ECOInfiniteStorageData.load(NbtIo.readCompressed(data.dataFile().toFile()).getCompound("data"));
        var blocked = new SavedDataInfiniteStorageEngine(reloaded);
        assertEquals(ECOInfiniteStorageData.DomainStatus.RECOVERY_READ_ONLY, blocked.status());
        assertEquals(0, blocked.extract(AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE));
        assertEquals(0, blocked.insert(AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE));
        assertEquals(10, blocked.getAmount(AEItemKey.of(Items.DIAMOND)).toLongSaturated());
    }
}
