package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.impl.storage.ECOSavedDataPersistence;
import java.math.BigInteger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavedDataInfiniteStoragePersistenceTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void version() {
        SharedConstants.tryDetectVersion();
    }

    @AfterEach
    void clearBackends() {
        ECOSavedDataPersistence.clear();
    }

    @Test
    void exactWideInsertionIsAtomicAndPersistsWithoutLongSaturation() throws Exception {
        Path path = directory.resolve("wide-insert.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var key = new InfiniteStorageTestKey(19);
        key.cacheEncoding(engine);
        BigInteger amount = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(123));

        long before = engine.getRevision();
        assertEquals(amount, engine.insert(key, amount, Actionable.SIMULATE));
        assertEquals(before, engine.getRevision());
        assertEquals(HugeAmount.ZERO, engine.getAmount(key));

        assertEquals(amount, engine.insert(key, amount, Actionable.MODULATE));
        assertEquals(before + 1, engine.getRevision());
        assertEquals(amount, engine.getAmount(key).toBigInteger());
        assertEquals(Long.MAX_VALUE, engine.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        engine.flushAndAwait();

        CompoundTag entry = InfiniteStorageSnapshot.read(path).getList("entries", 10).getCompound(0);
        assertEquals(amount, new BigInteger(entry.getByteArray("amount_wide")));
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
    }

    @Test
    void vanillaSaveCompletesBeforeReturningAndLaterChangesAreCommitted() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        engine.importLegacy(Map.of(), List.of(), "a".repeat(64), 1);
        engine.save(path.toFile());
        assertEquals(1, InfiniteStorageSnapshot.read(path).getLong("revision"));
        assertFalse(engine.needsPersistence());
        engine.importLegacy(Map.of(), List.of(), "a".repeat(64), 2);
        engine.flushAndAwait();
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
        assertEquals(2, InfiniteStorageSnapshot.read(path).getLong("revision"));
        assertFalse(engine.needsPersistence());
        engine.closeAndFlush();
    }

    @Test
    void mutationsDuringBackgroundFullSnapshotRemainDirtyAndSurviveForcedSave() throws Exception {
        Path path = directory.resolve("background.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var key = new InfiniteStorageTestKey(1);
        key.cacheEncoding(engine);
        engine.insert(key, 100, Actionable.MODULATE);
        engine.flushBudgeted(500_000L);
        engine.extract(key, 40, Actionable.MODULATE);
        engine.insert(key, 7, Actionable.MODULATE);
        assertTrue(engine.isDirty());
        engine.save(path.toFile());
        assertFalse(engine.needsPersistence());
        assertEquals(
                67,
                InfiniteStorageSnapshot.read(path)
                        .getList("entries", 10)
                        .getCompound(0)
                        .getLong("amount_long"));
    }

    @Test
    void missingCommittedDeltaCannotSilentlyRollBackInventory() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var key = new InfiniteStorageTestKey(1);
        key.cacheEncoding(engine);
        engine.insert(key, 10, Actionable.MODULATE);
        engine.flushAndAwait();
        engine.insert(key, 5, Actionable.MODULATE);
        engine.flushAndAwait();
        Files.delete(InfiniteStorageDelta.path(path));
        assertThrows(IOException.class, () -> InfiniteStorageSnapshot.read(path));
    }

    @Test
    void failedSaveCanRetryLiveInventoryWithoutReloadingOldQuantities() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var key = new InfiniteStorageTestKey(1);
        key.cacheEncoding(engine);
        engine.insert(key, 10, Actionable.MODULATE);
        engine.flushAndAwait();
        engine.insert(key, 5, Actionable.MODULATE);
        engine.persistenceFailed(new IOException("temporary disk failure"));
        assertTrue(engine.retryPersistence());
        assertEquals(
                15,
                InfiniteStorageSnapshot.read(path)
                        .getList("entries", 10)
                        .getCompound(0)
                        .getLong("amount_long"));
    }

    @Test
    void explicitPreviousSnapshotSurvivesCorruptCurrentDelta() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var key = new InfiniteStorageTestKey(1);
        key.cacheEncoding(engine);
        engine.insert(key, 10, Actionable.MODULATE);
        engine.flushAndAwait();
        engine.insert(key, 5, Actionable.MODULATE);
        engine.flushAndAwait();
        engine.insert(key, 7, Actionable.MODULATE);
        engine.flushAndAwait();
        Files.write(InfiniteStorageDelta.path(path), new byte[] {1, 2, 3});
        assertThrows(IOException.class, () -> InfiniteStorageSnapshot.read(path));
        assertEquals(
                15,
                InfiniteStorageSnapshot.previousSnapshot(path)
                        .getList("entries", 10)
                        .getCompound(0)
                        .getLong("amount_long"));
    }

    @Test
    void explicitRecoveryCanReadInventoryWhenCommitMarkerIsCorrupt() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var key = new InfiniteStorageTestKey(1);
        key.cacheEncoding(engine);
        engine.insert(key, 10, Actionable.MODULATE);
        engine.flushAndAwait();
        Files.write(InfiniteStorageSnapshot.commitMarker(path), new byte[] {1, 2, 3});
        assertThrows(IOException.class, () -> InfiniteStorageSnapshot.read(path));
        assertEquals(
                10,
                InfiniteStorageSnapshot.previousSnapshot(path)
                        .getList("entries", 10)
                        .getCompound(0)
                        .getLong("amount_long"));
    }

    @Test
    void quarantinedEngineCannotOverwriteExistingEvidenceDuringAutosave() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        engine.setDirty();
        engine.flushAndAwait();
        byte[] original = Files.readAllBytes(path);
        engine.persistenceFailed(new IOException("Injected persistence failure"));
        engine.setDirty(); // Even an external dirty mark must not bypass the save gate.
        engine.save(path.toFile());
        engine.flushAndAwait();
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    @Test
    void isolatedInventoryAndReceiptRecordsSurviveSaveWithoutDisablingDomain() throws Exception {
        UUID id = UUID.randomUUID();
        Path path = directory.resolve("domain.dat");
        var initial = SavedDataInfiniteStorageEngine.createNew(id, null, path);
        CompoundTag data = initial.save(new CompoundTag());
        CompoundTag broken = new CompoundTag();
        broken.putString("original", "broken-record-evidence");
        data.getList("entries", 10).add(broken.copy());
        ListTag receipts = new ListTag();
        receipts.add(broken.copy());
        data.put("transfer_receipts", receipts);
        var engine = SavedDataInfiniteStorageEngine.load(data, id, null, path);
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
        assertEquals(2, engine.getEntryFailures().size());
        assertFalse(engine.canTransfer());
        assertFalse(engine.isEmpty());
        engine.setDirty();
        engine.flushAndAwait();
        CompoundTag persisted = InfiniteStorageSnapshot.read(path);
        assertEquals(broken, persisted.getList("entries", 10).getCompound(0));
        assertEquals(broken, persisted.getList("transfer_receipts", 10).getCompound(0));
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
    }

    @Test
    void engineCoalescesIoAndPersistsZeroRefillAndWideAmounts() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var key = new InfiniteStorageTestKey(1);
        key.cacheEncoding(engine);
        assertEquals(10, engine.insert(key, 10, Actionable.MODULATE));
        engine.flushAndAwait();
        byte[] base = Files.readAllBytes(path);
        for (int i = 0; i < 100; i++) {
            assertEquals(10, engine.extract(key, 10, Actionable.MODULATE));
            // Reuses the validated encoding even after the previous extraction removed the key.
            assertEquals(10, engine.insert(key, 10, Actionable.MODULATE));
        }
        assertEquals(Long.MAX_VALUE, engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE));
        engine.flushAndAwait();
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
        assertFalse(engine.needsPersistence());
        assertArrayEquals(base, Files.readAllBytes(path));
        var entries = InfiniteStorageSnapshot.read(path).getList("entries", 10);
        assertEquals(1, entries.size());
        assertEquals(
                java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.TEN),
                new java.math.BigInteger(entries.getCompound(0).getByteArray("amount_wide")));
        engine.extract(key, Long.MAX_VALUE, Actionable.MODULATE);
        engine.extract(key, 10, Actionable.MODULATE);
        engine.flushAndAwait();
        assertTrue(InfiniteStorageSnapshot.read(path).getList("entries", 10).isEmpty());
        assertArrayEquals(base, Files.readAllBytes(path));
        engine.insert(key, 7, Actionable.MODULATE);
        engine.flushAndAwait();
        assertEquals(
                7,
                InfiniteStorageSnapshot.read(path)
                        .getList("entries", 10)
                        .getCompound(0)
                        .getLong("amount_long"));
    }

    @Test
    void transferReceiptAndQuantityShareTheSameDeltaCommit() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var key = new InfiniteStorageTestKey(1);
        key.cacheEncoding(engine);
        engine.insert(key, 10, Actionable.MODULATE);
        engine.flushAndAwait();
        UUID transaction = UUID.randomUUID();
        assertEquals(5, engine.insertOnce(transaction, key, 5));
        assertEquals(5, engine.insertOnce(transaction, key, 5));
        CompoundTag persisted = InfiniteStorageSnapshot.read(path);
        assertEquals(15, persisted.getList("entries", 10).getCompound(0).getLong("amount_long"));
        assertEquals(
                transaction,
                persisted.getList("transfer_receipts", 10).getCompound(0).getUUID("id"));
        assertFalse(engine.needsPersistence());
        assertEquals(15, engine.extract(key, 20, Actionable.SIMULATE));
        assertEquals(20, engine.insert(key, 20, Actionable.SIMULATE));
        assertFalse(engine.needsPersistence());
    }

    @Test
    void netZeroIoKeepsCommittedQuantitiesWithoutRedundantDiskWrites() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var keys = new InfiniteStorageTestKey[1100];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new InfiniteStorageTestKey(i);
            keys[i].cacheEncoding(engine);
            engine.insert(keys[i], 1000, Actionable.MODULATE);
        }
        engine.flushAndAwait();
        byte[] base = Files.readAllBytes(path);
        long committedRevision = engine.getRevision();
        for (var key : keys) {
            engine.extract(key, 64, Actionable.MODULATE);
            engine.insert(key, 64, Actionable.MODULATE);
        }
        engine.flushAndAwait();
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
        assertArrayEquals(base, Files.readAllBytes(path));
        assertFalse(Files.exists(InfiniteStorageDelta.path(path)));
        var recovered = InfiniteStorageSnapshot.read(path);
        assertEquals(committedRevision, recovered.getLong("revision"));
        assertTrue(engine.getRevision() > committedRevision);
        assertEquals(keys.length, recovered.getList("entries", 10).size());
        for (var entry : recovered.getList("entries", 10)) {
            assertEquals(1000, ((CompoundTag) entry).getLong("amount_long"));
        }
    }

    @Test
    void revertingCommittedWideDeltaRestoresBaseButRetainsTransferReceipt() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var key = new InfiniteStorageTestKey(1);
        key.cacheEncoding(engine);
        engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        engine.flushAndAwait();
        byte[] base = Files.readAllBytes(path);
        UUID transaction = UUID.randomUUID();
        engine.insertOnce(transaction, key, 5);
        engine.extract(key, 5, Actionable.MODULATE);
        engine.flushAndAwait();
        assertArrayEquals(base, Files.readAllBytes(path));
        var delta = InfiniteStorageSnapshot.read(InfiniteStorageDelta.path(path));
        assertTrue(delta.getList("entries", 10).isEmpty());
        var recovered = InfiniteStorageSnapshot.read(path);
        assertEquals(
                Long.MAX_VALUE, recovered.getList("entries", 10).getCompound(0).getLong("amount_long"));
        assertEquals(
                transaction,
                recovered.getList("transfer_receipts", 10).getCompound(0).getUUID("id"));
        assertEquals(5, engine.insertOnce(transaction, key, 5));
        assertEquals(HugeAmount.of(Long.MAX_VALUE), engine.getAmount(key));
    }

    @Test
    void deletingANewKeyCancelsItsPreviouslyCommittedOverlayEntry() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        engine.setDirty();
        engine.flushAndAwait();
        var key = new InfiniteStorageTestKey(1);
        key.cacheEncoding(engine);
        engine.insert(key, 5, Actionable.MODULATE);
        engine.flushAndAwait();
        assertEquals(
                1, InfiniteStorageSnapshot.read(path).getList("entries", 10).size());
        engine.extract(key, 5, Actionable.MODULATE);
        engine.flushAndAwait();
        assertTrue(InfiniteStorageSnapshot.read(path).getList("entries", 10).isEmpty());
        assertTrue(InfiniteStorageSnapshot.read(InfiniteStorageDelta.path(path))
                .getList("entries", 10)
                .isEmpty());
    }

    @Test
    void denseChangesCompactAndRemoveObsoleteOverlay() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        var first = new InfiniteStorageTestKey(0);
        first.cacheEncoding(engine);
        engine.insert(first, 1, Actionable.MODULATE);
        engine.flushAndAwait();
        engine.insert(first, 1, Actionable.MODULATE);
        engine.flushAndAwait();
        assertTrue(Files.exists(InfiniteStorageDelta.path(path)));
        for (int i = 1; i <= 1024; i++) {
            var key = new InfiniteStorageTestKey(i);
            key.cacheEncoding(engine);
            engine.insert(key, 1, Actionable.MODULATE);
        }
        engine.flushAndAwait();
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
        assertFalse(Files.exists(InfiniteStorageDelta.path(path)));
        assertEquals(
                1025, InfiniteStorageSnapshot.read(path).getList("entries", 10).size());
    }
}
