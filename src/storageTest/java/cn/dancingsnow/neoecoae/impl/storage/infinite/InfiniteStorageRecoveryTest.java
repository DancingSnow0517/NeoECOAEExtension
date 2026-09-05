package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.impl.storage.StorageTestKey;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InfiniteStorageRecoveryTest {
    @TempDir Path directory;

    private SavedDataInfiniteStorageEngine open() throws Exception {
        return open(0);
    }

    private SavedDataInfiniteStorageEngine open(int keyPadding) throws Exception {
        Path file = directory.resolve("domain.dat");
        ECOInfiniteStorageData data = java.nio.file.Files.exists(file)
            ? ECOInfiniteStorageData.load(net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("data"), StorageTestKey.REGISTRIES)
            : ECOInfiniteStorageData.createNew();
        data.openJournal(file, StorageTestKey.REGISTRIES, new ECOInfiniteStorageData.KeyCodec() {
            @Override public net.minecraft.nbt.CompoundTag encode(appeng.api.stacks.AEKey key) {
                var tag = key.toTag(null);
                if (keyPadding > 0) tag.putByteArray("padding", new byte[keyPadding]);
                return tag;
            }
            @Override public appeng.api.stacks.AEKey decode(net.minecraft.nbt.CompoundTag tag) { return new StorageTestKey(tag.getString("name")); }
        });
        return new SavedDataInfiniteStorageEngine(data, StorageTestKey.REGISTRIES, file);
    }

    @Test
    void boundedAsyncBatchesKeepMigrationReceiptsWithTheirAmounts() throws Exception {
        var engine = open();
        UUID[] receipts = new UUID[5000];
        for (int i = 0; i < receipts.length; i++) {
            receipts[i] = UUID.randomUUID();
            assertEquals(10L, engine.insertOnce(receipts[i], new StorageTestKey("batch" + i), 10L));
        }
        engine.tick(20L);
        assertTrue(engine.commit().successful());
        engine.close();
        var recovered = open();
        for (int i = 0; i < receipts.length; i++) {
            var key = new StorageTestKey("batch" + i);
            assertEquals(HugeAmount.of(10L), recovered.getAmount(key));
            assertEquals(10L, recovered.insertOnce(receipts[i], key, 10L));
            assertEquals(HugeAmount.of(10L), recovered.getAmount(key));
        }
        recovered.close();
    }

    @Test
    void largeEncodedKeysFlushInBoundedRecordsAndRecover() throws Exception {
        var engine = open(1024 * 1024);
        for (int i = 0; i < 20; i++) engine.insert(new StorageTestKey("large" + i), i + 1L, Actionable.MODULATE);
        engine.tick(20L);
        assertTrue(engine.commit().successful());
        engine.close();
        try (var files = java.nio.file.Files.walk(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().equals("journal.log")).toList()) {
                var bytes = java.nio.ByteBuffer.wrap(java.nio.file.Files.readAllBytes(path));
                while (bytes.hasRemaining()) {
                    bytes.getInt();
                    int length = bytes.getInt();
                    assertTrue(length <= 8 * 1024 * 1024, "record length " + length);
                    bytes.position(bytes.position() + 12 + length);
                }
            }
        }
        var recovered = open(1024 * 1024);
        for (int i = 0; i < 20; i++) assertEquals(HugeAmount.of(i + 1L), recovered.getAmount(new StorageTestKey("large" + i)));
        recovered.close();
    }

    @Test
    void mutationsAfterAsyncSnapshotRemainDirtyAndSurviveCommit() throws Exception {
        var key = new StorageTestKey("iron");
        var engine = open();
        engine.insert(key, 100L, Actionable.MODULATE);
        engine.tick(20L);
        engine.extract(key, 30L, Actionable.MODULATE);
        assertTrue(engine.commit().successful());
        engine.close();
        var recovered = open();
        assertEquals(HugeAmount.of(70L), recovered.getAmount(key));
        recovered.close();
    }

    @Test
    void reservationWithUnchangedAmountSurvivesAnOlderAsyncSnapshot() throws Exception {
        var key = new StorageTestKey("reserved");
        UUID transaction = UUID.randomUUID();
        var engine = open();
        engine.insert(key, 100L, Actionable.MODULATE);
        engine.tick(20L);
        assertTrue(engine.reserveRestore(key, transaction));
        assertEquals(0L, engine.extract(key, 1L, Actionable.MODULATE));
        engine.close();
        var recovered = open();
        assertEquals(transaction, recovered.restoreTransaction(key));
        assertEquals(HugeAmount.of(100L), recovered.getRestoreAmount(key));
        assertEquals(HugeAmount.ZERO, recovered.getAmount(key));
        assertTrue(recovered.finishRestore(key, transaction));
        recovered.close();
        var finished = open();
        assertTrue(finished.isEmpty());
        finished.close();
    }

    @Test
    void durableReceiptPreventsReimportAfterItemsWereAlreadyWithdrawn() throws Exception {
        var key = new StorageTestKey("migrated");
        UUID transaction = UUID.randomUUID();
        var engine = open();
        assertEquals(50L, engine.insertOnce(transaction, key, 50L));
        assertTrue(engine.commit().successful());
        assertEquals(50L, engine.extract(key, 50L, Actionable.MODULATE));
        engine.clearMigrationReceipts();
        assertTrue(engine.commit().successful());
        engine.close();
        var recovered = open();
        assertEquals(50L, recovered.insertOnce(transaction, key, 50L));
        assertEquals(HugeAmount.ZERO, recovered.getAmount(key));
        recovered.close();
    }

    @Test
    void uncertainRestoreCannotAutomaticallyRunAgainAfterRestart() throws Exception {
        var key = new StorageTestKey("uncertain");
        var healthy = new StorageTestKey("healthy");
        UUID transaction = UUID.randomUUID();
        var engine = open();
        engine.insert(key, 20L, Actionable.MODULATE);
        assertTrue(engine.reserveRestore(key, transaction));
        engine.failRestore(key, "injected destination failure");
        engine.close();
        var recovered = open();
        assertFalse(recovered.reserveRestore(key, transaction));
        assertEquals(30L, recovered.insert(healthy, 30L, Actionable.MODULATE));
        assertEquals(30L, recovered.extract(healthy, 30L, Actionable.MODULATE));
        recovered.close();
    }

    @Test
    void restoreRequiresOriginalTargetsAfterRestart() throws Exception {
        var key = new StorageTestKey("moving-target");
        UUID transaction = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        var engine = open();
        engine.insert(key, 12L, Actionable.MODULATE);
        assertTrue(engine.reserveRestore(key, transaction, java.util.Set.of(original)));
        engine.close();
        var recovered = open();
        assertEquals(java.util.Set.of(original), recovered.restoreTargetIds(key));
        assertFalse(recovered.reserveRestore(key, transaction, java.util.Set.of(UUID.randomUUID())));
        assertTrue(recovered.reserveRestore(key, transaction, java.util.Set.of(original)));
        recovered.close();
    }

    @Test
    void markerWithoutJournalCannotBeReplacedWithAnEmptyDomain() throws Exception {
        var marker = new net.minecraft.nbt.CompoundTag();
        marker.putInt("version", ECOInfiniteStorageData.CURRENT_VERSION);
        marker.putBoolean("journal", true);
        ECOInfiniteStorageData data = ECOInfiniteStorageData.load(marker, StorageTestKey.REGISTRIES);
        assertFalse(data.canRead());
        assertThrows(java.io.IOException.class, () -> data.openJournal(directory.resolve("missing.dat"), StorageTestKey.REGISTRIES));
        assertFalse(java.nio.file.Files.exists(directory.resolve("missing.dat.store")));
    }

    @Test
    void malformedLegacyAmountAndReceiptArePreservedWithoutBlockingNormalReads() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("version", 1);
        var entries = new net.minecraft.nbt.ListTag();
        entries.add(new net.minecraft.nbt.CompoundTag());
        tag.put("entries", entries);
        var receipts = new net.minecraft.nbt.ListTag();
        receipts.add(new net.minecraft.nbt.IntArrayTag(new int[]{1}));
        tag.put("migrations", receipts);
        ECOInfiniteStorageData data = ECOInfiniteStorageData.load(tag, null);
        assertTrue(data.canRead());
        assertTrue(data.canWrite());
        assertFalse(data.canMigrate());
        assertFalse(data.canExitOrRestore());
        assertEquals(1, data.rawEntryCount());
        assertEquals(receipts, data.save(new net.minecraft.nbt.CompoundTag(), null).getList("migrations", net.minecraft.nbt.Tag.TAG_INT_ARRAY));
    }
}
