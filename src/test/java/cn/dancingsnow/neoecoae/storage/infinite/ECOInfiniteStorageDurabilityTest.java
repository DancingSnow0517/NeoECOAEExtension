package cn.dancingsnow.neoecoae.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;

import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ECOInfiniteStorageDurabilityTest {
    // Exercise real snapshot I/O without the AE2 key-type registry installed by the mod launcher.
    private static final ECOInfiniteStorageData.KeyCodec ITEM_CODEC = new ECOInfiniteStorageData.KeyCodec() {
        @Override
        public CompoundTag encode(AEKey key) {
            var tag = new CompoundTag();
            tag.putString("item", BuiltInRegistries.ITEM.getKey(((AEItemKey) key).getItem()).toString());
            return tag;
        }

        @Override
        public AEKey decode(CompoundTag tag) {
            return BuiltInRegistries.ITEM.getOptional(Identifier.parse(tag.getStringOr("item", "")))
                    .map(AEItemKey::of).orElse(null);
        }
    };

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private static CompoundTag emptySnapshot() {
        return ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
    }

    private static ECOInfiniteStorageData fresh() {
        return ECOInfiniteStorageData.load(emptySnapshot(), RegistryAccess.EMPTY, ITEM_CODEC);
    }

    /** Appends one record in the format of the retired write-ahead journal. */
    private static void appendLegacyRecord(Path journal, long sequence, AEKey key, long amount, boolean added)
            throws Exception {
        var record = new CompoundTag();
        record.putLong("sequence", sequence);
        record.put("key", ITEM_CODEC.encode(key));
        record.putLong("amount", amount);
        record.putBoolean("added", added);
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            NbtIo.write(record, out);
        }
        try (var out = new DataOutputStream(Files.newOutputStream(journal,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            out.writeInt(bytes.size());
            out.write(bytes.toByteArray());
        }
    }

    /** 26.1: emulates vanilla SavedDataStorage writing a dirty SavedData as {data: ...} and clearing the flag. */
    private static void vanillaSave(ECOInfiniteStorageData data, Path snapshot) throws Exception {
        data.bindPersistence(snapshot, RegistryAccess.EMPTY);
        data.savePendingSnapshot().get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static ECOInfiniteStorageData readBack(Path snapshot) throws Exception {
        var root = NbtIo.readCompressed(snapshot, NbtAccounter.unlimitedHeap());
        return ECOInfiniteStorageData.load(root.getCompoundOrEmpty("data"), RegistryAccess.EMPTY, ITEM_CODEC);
    }

    @Test
    void transfersStayInMemoryUntilTheVanillaSave(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var live = fresh();
        var engine = new SavedDataInfiniteStorageEngine(live);

        assertEquals(1_000, engine.insert(key, 1_000, Actionable.MODULATE));
        for (int i = 0; i < 100; i++) assertEquals(1, engine.extract(key, 1, Actionable.MODULATE));
        assertEquals(900, engine.extract(key, 1_000, Actionable.SIMULATE));
        assertEquals(HugeAmount.of(900), engine.getAmount(key));
        assertTrue(live.isDirty());
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count(), "no per-transfer file I/O");
        }

        vanillaSave(live, snapshot);
        assertFalse(live.isDirty());
        assertEquals(HugeAmount.of(900), readBack(snapshot).getAmount(key));
    }

    @Test
    void migrationCreditsWaitForTheirOwnAcknowledgedSnapshot(@TempDir Path directory) throws Exception {
        var live = fresh();
        var file = directory.resolve("domain.dat");
        live.bindPersistence(file, RegistryAccess.EMPTY);
        var engine = new SavedDataInfiniteStorageEngine(live);
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        UUID receipt = UUID.randomUUID();
        var release = new java.util.concurrent.CompletableFuture<Void>();
        var pending = ECOInfiniteStorageData.class.getDeclaredField("pendingWrites");
        pending.setAccessible(true);
        pending.set(live, release);
        java.util.concurrent.CompletableFuture<ECOInfiniteStorageEngine.CommitResult> first;
        try {
            assertEquals(10, engine.insertOnce(receipt, stone, 10));
            first = engine.checkpoint();
            assertFalse(first.isDone());
            assertFalse(engine.canUseMountedStorage());
            assertEquals(0, engine.extract(stone, 10, Actionable.MODULATE));
            assertEquals(20, engine.insertOnce(UUID.randomUUID(), dirt, 20));
        } finally {
            release.complete(null);
        }
        assertTrue(first.get(10, java.util.concurrent.TimeUnit.SECONDS).successful());
        assertFalse(engine.canUseMountedStorage(), "an older snapshot must not release newer migration credits");
        assertEquals(HugeAmount.of(10), readBack(file).getAmount(stone));
        assertEquals(HugeAmount.ZERO, readBack(file).getAmount(dirt));
        assertTrue(engine.checkpoint().get(10, java.util.concurrent.TimeUnit.SECONDS).successful());
        assertTrue(engine.canUseMountedStorage());
        var reloaded = new SavedDataInfiniteStorageEngine(readBack(file));
        assertEquals(10, reloaded.insertOnce(receipt, stone, 10));
        assertEquals(HugeAmount.of(10), reloaded.getAmount(stone), "replaying the sealed source does not duplicate it");
        assertEquals(HugeAmount.of(20), reloaded.getAmount(dirt));
    }

    @Test
    void failedMigrationCheckpointRetainsCreditsAndRetriesWithoutReinsertion(@TempDir Path directory) throws Exception {
        var live = fresh();
        var parent = directory.resolve("blocked");
        Files.writeString(parent, "occupied");
        var target = parent.resolve("domain.dat");
        live.bindPersistence(target, RegistryAccess.EMPTY);
        var engine = new SavedDataInfiniteStorageEngine(live);
        var stone = AEItemKey.of(Items.STONE);
        var receipt = UUID.randomUUID();
        assertEquals(64, engine.insertOnce(receipt, stone, 64));
        assertFalse(engine.checkpoint().get(10, java.util.concurrent.TimeUnit.SECONDS).successful());
        assertEquals(HugeAmount.of(64), live.getAmount(stone));
        assertEquals(0, engine.extract(stone, 64, Actionable.MODULATE));
        assertFalse(engine.canExitOrRestore());
        Files.delete(parent);
        Files.createDirectory(parent);
        assertTrue(engine.checkpoint().get(10, java.util.concurrent.TimeUnit.SECONDS).successful());
        assertEquals(64, engine.insertOnce(receipt, stone, 64));
        assertEquals(HugeAmount.of(64), readBack(target).getAmount(stone));
        assertEquals(64, engine.extract(stone, 64, Actionable.SIMULATE));
    }

    @Test
    void restoreReservationAndFinalizationSurviveSeparateSnapshots(@TempDir Path directory) throws Exception {
        var file = directory.resolve("domain.dat");
        var live = fresh();
        live.bindPersistence(file, RegistryAccess.EMPTY);
        var engine = new SavedDataInfiniteStorageEngine(live);
        var key = AEItemKey.of(Items.STONE);
        engine.insert(key, 10, Actionable.MODULATE);
        var transaction = UUID.randomUUID();
        var target = UUID.randomUUID();
        var goal = new ECOInfiniteStorageEngine.RestoreTargetAmounts(0, 10, 10);
        assertTrue(engine.reserveRestores(Map.of(key, transaction), Set.of(target), Map.of(key, Map.of(target, goal))));
        assertTrue(engine.checkpoint().get(10, java.util.concurrent.TimeUnit.SECONDS).successful());
        var reserved = readBack(file);
        assertEquals(HugeAmount.of(10), reserved.getAmount(key));
        assertEquals(Map.of(target, goal), reserved.restorePlan(key));
        assertEquals(transaction, reserved.restoreTransaction(key));

        // The controller calls finish only after the target chunk's contents and receipt have been acknowledged.
        assertTrue(engine.finishRestores(Map.of(key, transaction)));
        assertFalse(engine.canExitOrRestore());
        assertEquals(Map.of(target, goal), readBack(file).restorePlan(key), "the old snapshot remains replayable");
        assertTrue(engine.checkpoint().get(10, java.util.concurrent.TimeUnit.SECONDS).successful());
        var completed = readBack(file);
        assertEquals(HugeAmount.ZERO, completed.getAmount(key));
        assertFalse(completed.hasPendingRestore());
        assertTrue(engine.canExitOrRestore());
    }

    @Test
    void quantityChangesDoNotAdvanceRevisionButNewKeysDo() {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var live = fresh();
        var engine = new SavedDataInfiniteStorageEngine(live);

        engine.insert(stone, 10, Actionable.MODULATE);
        long afterNewKey = engine.revision();
        assertTrue(afterNewKey > 0);
        for (int i = 0; i < 50; i++) {
            engine.insert(stone, 5, Actionable.MODULATE);
            engine.extract(stone, 5, Actionable.MODULATE);
        }
        assertEquals(afterNewKey, engine.revision(), "hot transfers of an existing key keep the revision");
        engine.insert(dirt, 1, Actionable.MODULATE);
        assertTrue(engine.revision() > afterNewKey);
        long afterDirt = engine.revision();
        engine.extract(dirt, 1, Actionable.MODULATE);
        assertTrue(engine.revision() > afterDirt, "a key disappearing is structural");
        assertEquals(1, engine.getTypeStats().iterator().next().storedTypes());
    }

    @Test
    void longOverflowSurvivesTheSnapshot(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var live = fresh();
        var engine = new SavedDataInfiniteStorageEngine(live);
        assertEquals(Long.MAX_VALUE, engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(5, engine.insert(key, 5, Actionable.MODULATE));
        vanillaSave(live, snapshot);
        assertEquals(HugeAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(5))),
                readBack(snapshot).getAmount(key));
    }

    @Test
    void legacyJournalIsFoldedIntoTheNextSnapshotOnce(@TempDir Path directory) throws Exception {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var snapshot = directory.resolve("domain.dat");
        var journal = directory.resolve("domain.dat.journal");
        appendLegacyRecord(journal, 1, stone, 100, true);
        appendLegacyRecord(journal, 2, stone, 20, false);
        appendLegacyRecord(journal, 3, dirt, 7, true);

        var live = fresh();
        ECOInfiniteStorageData.replayJournal(live, snapshot, RegistryAccess.EMPTY);
        assertEquals(HugeAmount.of(80), live.getAmount(stone));
        assertEquals(HugeAmount.of(7), live.getAmount(dirt));
        assertTrue(live.isDirty());

        vanillaSave(live, snapshot);
        assertFalse(Files.exists(journal), "an acknowledged snapshot retires its folded journal");

        // A journal left behind by a crash after the snapshot write is skipped by its recorded sequence.
        appendLegacyRecord(journal, 1, stone, 100, true);
        var recovered = readBack(snapshot);
        ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);
        assertEquals(HugeAmount.of(80), recovered.getAmount(stone));
    }

    @Test
    void interruptedLegacyJournalTailKeepsCompleteRecords(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var journal = directory.resolve("domain.dat.journal");
        appendLegacyRecord(journal, 1, key, 10, true);
        try (var out = new DataOutputStream(Files.newOutputStream(journal, StandardOpenOption.APPEND))) {
            out.writeInt(100);
            out.writeByte(1);
        }
        var recovered = fresh();
        ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);
        assertEquals(HugeAmount.of(10), recovered.getAmount(key));
    }

    @Test
    void unreadableExistingSnapshotIsNeverOverwritten(@TempDir Path directory) throws Exception {
        var snapshot = directory.resolve("domain.dat");
        byte[] damaged = {1, 2, 3, 4};
        Files.write(snapshot, damaged);
        // Vanilla falls back to the constructor when it cannot read an existing file.
        var data = ECOInfiniteStorageData.type(Identifier.parse("neoecoae:test_domain"), snapshot, RegistryAccess.EMPTY,
            ECOInfiniteStorageData::createNew).factory().create(null);
        assertEquals(ECOInfiniteStorageData.DomainStatus.UNAVAILABLE, data.status());
        assertEquals(0, new SavedDataInfiniteStorageEngine(data).insert(AEItemKey.of(Items.STONE), 1, Actionable.MODULATE));
        data.setDirty();
        // 26.1: vanilla only writes dirty data; an unreadable domain never becomes dirty, so the file is kept.
        assertFalse(data.isDirty());
        assertArrayEquals(damaged, Files.readAllBytes(snapshot));
    }


    @Test
    void pendingRestoreHidesOnlyItsKey() {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var live = fresh();
        var engine = new SavedDataInfiniteStorageEngine(live);
        engine.insert(stone, 10, Actionable.MODULATE);
        engine.insert(dirt, 3, Actionable.MODULATE);
        var all = new KeyCounter();
        engine.getAvailableStacks(all);
        assertEquals(10, all.get(stone));
        assertEquals(3, all.get(dirt));

        assertTrue(engine.reserveRestore(stone, UUID.randomUUID(), Set.of()));
        var visible = new KeyCounter();
        engine.getAvailableStacks(visible);
        assertEquals(0, visible.get(stone));
        assertEquals(3, visible.get(dirt));
    }

    @Test
    void hugeRestoreFinishesInLongSizedSegments() {
        var key = AEItemKey.of(Items.STONE);
        var data = ECOInfiniteStorageData.createNew();
        data.amounts.set(key, HugeAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(5L))));
        UUID transaction = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        var goal = new ECOInfiniteStorageEngine.RestoreTargetAmounts(0L, Long.MAX_VALUE, Long.MAX_VALUE);

        assertTrue(data.reserveRestore(key, transaction, Set.of(target), Map.of(target, goal)));
        assertTrue(data.finishRestore(key, transaction));
        assertEquals(HugeAmount.of(5L), data.getAmount(key));
    }
}
