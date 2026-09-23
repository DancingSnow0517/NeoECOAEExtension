package cn.dancingsnow.neoecoae.impl.storage.infinite;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.IOUtilities;
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
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(tag.getString("item")))
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

    private static ECOInfiniteStorageData readBack(Path snapshot) throws Exception {
        var root = NbtIo.readCompressed(snapshot, NbtAccounter.unlimitedHeap());
        return ECOInfiniteStorageData.load(root.getCompound("data"), RegistryAccess.EMPTY, ITEM_CODEC);
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

        live.save(snapshot.toFile(), RegistryAccess.EMPTY);
        IOUtilities.waitUntilIOWorkerComplete();
        assertFalse(live.isDirty());
        assertEquals(HugeAmount.of(900), readBack(snapshot).getAmount(key));
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
        live.save(snapshot.toFile(), RegistryAccess.EMPTY);
        IOUtilities.waitUntilIOWorkerComplete();
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

        live.save(snapshot.toFile(), RegistryAccess.EMPTY);
        IOUtilities.waitUntilIOWorkerComplete();
        assertFalse(Files.exists(journal));

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
        var data = ECOInfiniteStorageData.factory(snapshot, ECOInfiniteStorageData::createNew).constructor().get();
        assertEquals(ECOInfiniteStorageData.DomainStatus.UNAVAILABLE, data.status());
        assertEquals(0, new SavedDataInfiniteStorageEngine(data).insert(AEItemKey.of(Items.STONE), 1, Actionable.MODULATE));
        data.setDirty();
        data.save(snapshot.toFile(), RegistryAccess.EMPTY);
        IOUtilities.waitUntilIOWorkerComplete();
        assertArrayEquals(damaged, Files.readAllBytes(snapshot));
    }

    @Test
    void failedWriteIsReportedAndRetriedOnTheNextSave(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var live = fresh();
        var engine = new SavedDataInfiniteStorageEngine(live);
        assertEquals(100, engine.insert(key, 100, Actionable.MODULATE));
        Path blockedParent = directory.resolve("not-a-directory");
        Files.writeString(blockedParent, "occupied");
        live.save(blockedParent.resolve("domain.dat").toFile(), RegistryAccess.EMPTY);
        IOUtilities.waitUntilIOWorkerComplete();
        assertEquals(ECOInfiniteStorageData.DomainStatus.DEGRADED, live.status());
        assertFalse(live.canExitOrRestore());
        // Inventory stays usable in memory, like any vanilla SavedData.
        assertEquals(10, engine.extract(key, 10, Actionable.MODULATE));

        var snapshot = directory.resolve("domain.dat");
        live.save(snapshot.toFile(), RegistryAccess.EMPTY);
        IOUtilities.waitUntilIOWorkerComplete();
        assertEquals(ECOInfiniteStorageData.DomainStatus.HEALTHY, live.status());
        assertEquals(HugeAmount.of(90), readBack(snapshot).getAmount(key));
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
