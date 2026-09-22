package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;

import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ECOInfiniteStorageDurabilityTest {
    // Exercise real journal I/O without the AE2 key-type registry installed by the mod launcher.
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

    @Test
    void ordinaryIoJournalReplaysAnAcknowledgedChange(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var emptySnapshot = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        var live = ECOInfiniteStorageData.load(emptySnapshot, RegistryAccess.EMPTY, ITEM_CODEC);
        assertTrue(live.appendJournalChange(snapshot.toFile(), RegistryAccess.EMPTY, key, 64L, true));
        live.closeJournal();

        var recovered = ECOInfiniteStorageData.load(emptySnapshot, RegistryAccess.EMPTY, ITEM_CODEC);
        ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);

        assertEquals(HugeAmount.of(64L), recovered.getAmount(key));
    }

    @Test
    void bufferedEnergyCoalescesWithoutForcingEveryTransfer(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var other = AEItemKey.of(Items.DIRT);
        var snapshot = directory.resolve("domain.dat");
        var empty = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        var live = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
        var engine = new SavedDataInfiniteStorageEngine(live, RegistryAccess.EMPTY, snapshot);
        try {
            assertTrue(live.bufferEnergyChange(key, 100, true));
            live.add(key, 100);
            assertTrue(live.bufferEnergyChange(key, 20, false));
            live.subtract(key, 20);
            assertFalse(Files.exists(directory.resolve("domain.dat.journal")));

            assertEquals(3, engine.insert(other, 3, Actionable.MODULATE));
            live.flushBufferedEnergy(snapshot.toFile(), RegistryAccess.EMPTY);
            var recovered = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
            ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);
            assertEquals(HugeAmount.of(80), recovered.getAmount(key));
            assertEquals(HugeAmount.of(3), recovered.getAmount(other));
            assertEquals(2, recovered.save(new CompoundTag(), RegistryAccess.EMPTY).getLong("journalSequence"));
        } finally {
            engine.close();
        }
    }

    @Test
    void interruptedBufferedTailKeepsPreviouslyForcedRecords(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var empty = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        var live = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
        assertTrue(live.appendJournalChange(snapshot.toFile(), RegistryAccess.EMPTY, key, 10, true));
        live.closeJournal();
        try (var out = new DataOutputStream(Files.newOutputStream(directory.resolve("domain.dat.journal"),
                java.nio.file.StandardOpenOption.APPEND))) {
            out.writeInt(100);
            out.writeByte(1);
        }
        var recovered = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
        ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);
        assertEquals(HugeAmount.of(10), recovered.getAmount(key));
    }

    @Test
    void snapshotIncludesUnflushedEnergy(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var empty = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        var live = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
        assertTrue(live.bufferEnergyChange(key, 42, true));
        live.add(key, 42);
        live.save(snapshot.toFile(), RegistryAccess.EMPTY);
        assertFalse(Files.exists(directory.resolve("domain.dat.journal")));
        var root = NbtIo.readCompressed(snapshot, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var recovered = ECOInfiniteStorageData.load(root.getCompound("data"), RegistryAccess.EMPTY, ITEM_CODEC);
        assertEquals(HugeAmount.of(42), recovered.getAmount(key));
    }

    @Test
    void bufferedEnergySplitsNetChangesAboveLongMax(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var empty = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        var live = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
        assertTrue(live.bufferEnergyChange(key, Long.MAX_VALUE, true));
        live.add(key, Long.MAX_VALUE);
        assertTrue(live.bufferEnergyChange(key, 5, true));
        live.add(key, 5);
        live.flushBufferedEnergy(snapshot.toFile(), RegistryAccess.EMPTY);
        live.closeJournal();

        var recovered = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
        ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);
        assertEquals(HugeAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(5))),
                recovered.getAmount(key));
        assertEquals(2, recovered.save(new CompoundTag(), RegistryAccess.EMPTY).getLong("journalSequence"));
    }

    @Test
    void repeatedTransfersReplayBeforeCloseAndSurviveSnapshotRotation(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var empty = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        int[] encodes = {0};
        var countingCodec = new ECOInfiniteStorageData.KeyCodec() {
            public CompoundTag encode(AEKey value) {
                encodes[0]++;
                return ITEM_CODEC.encode(value);
            }
            public AEKey decode(CompoundTag tag) {
                return ITEM_CODEC.decode(tag);
            }
        };
        var live = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, countingCodec);
        var engine = new SavedDataInfiniteStorageEngine(live, RegistryAccess.EMPTY, snapshot);
        try {
            assertEquals(1_000, engine.insert(key, 1_000, Actionable.MODULATE));
            for (int i = 0; i < 100; i++) {
                assertEquals(1, engine.extract(key, 1, Actionable.MODULATE));
                assertEquals(HugeAmount.of(999 - i), engine.getAmount(key));
            }
            assertEquals(1, encodes[0], "same-key journal transfers reuse the encoded frame");
            long bytesBeforeSimulation = Files.size(directory.resolve("domain.dat.journal"));
            assertEquals(900, engine.extract(key, 1_000, Actionable.SIMULATE));
            assertEquals(bytesBeforeSimulation, Files.size(directory.resolve("domain.dat.journal")));

            // All acknowledged transfers are already replayable, with the writer still open.
            var recovered = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
            ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);
            assertEquals(HugeAmount.of(900), recovered.getAmount(key));

            assertTrue(engine.commit().successful());
            assertFalse(Files.exists(directory.resolve("domain.dat.journal")));
            CompoundTag checkpoint = live.save(new CompoundTag(), RegistryAccess.EMPTY);
            assertEquals(125, engine.extract(key, 125, Actionable.MODULATE));
            recovered = ECOInfiniteStorageData.load(checkpoint, RegistryAccess.EMPTY, ITEM_CODEC);
            ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);
            assertEquals(HugeAmount.of(775), recovered.getAmount(key));
        } finally {
            engine.close();
        }
        assertFalse(Files.exists(directory.resolve("domain.dat.journal")));
    }

    @Test
    void failedJournalDoesNotAcknowledgeOrChangeInventory(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var empty = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        var data = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
        data.add(key, 100);
        Path blockedParent = directory.resolve("not-a-directory");
        Files.writeString(blockedParent, "occupied");
        var engine = new SavedDataInfiniteStorageEngine(data, RegistryAccess.EMPTY, blockedParent.resolve("domain.dat"));
        assertEquals(0, engine.extract(key, 10, Actionable.MODULATE));
        assertEquals(HugeAmount.of(100), engine.getAmount(key));
        assertEquals(ECOInfiniteStorageData.DomainStatus.RECOVERY_READ_ONLY, data.status());
        data.closeJournal();
    }

    @Test
    void oldJournalRecordsAndNewMixedKeysReplayTogether(@TempDir Path directory) throws Exception {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var snapshot = directory.resolve("domain.dat");
        var empty = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        // Encode the first record exactly as the previous writer did.
        var legacy = new CompoundTag();
        legacy.putLong("sequence", 1);
        legacy.put("key", ITEM_CODEC.encode(stone));
        legacy.putLong("amount", 100);
        legacy.putBoolean("added", true);
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            NbtIo.write(legacy, out);
        }
        try (var out = new DataOutputStream(Files.newOutputStream(directory.resolve("domain.dat.journal")))) {
            out.writeInt(bytes.size());
            out.write(bytes.toByteArray());
        }
        var live = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
        ECOInfiniteStorageData.replayJournal(live, snapshot, RegistryAccess.EMPTY);
        var engine = new SavedDataInfiniteStorageEngine(live, RegistryAccess.EMPTY, snapshot);
        try {
            assertEquals(25, engine.extract(stone, 25, Actionable.MODULATE));
            assertEquals(Long.MAX_VALUE, engine.insert(dirt, Long.MAX_VALUE, Actionable.MODULATE));
            assertEquals(5, engine.insert(stone, 5, Actionable.MODULATE));
            assertEquals(7, engine.extract(dirt, 7, Actionable.MODULATE));
            var recovered = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
            ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);
            assertEquals(HugeAmount.of(80), recovered.getAmount(stone));
            assertEquals(HugeAmount.of(Long.MAX_VALUE - 7), recovered.getAmount(dirt));
        } finally {
            engine.close();
        }
    }

    @Test
    void failedSnapshotClosesWriterAndRetainsRecoverableJournal(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var empty = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        var live = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
        var engine = new SavedDataInfiniteStorageEngine(live, RegistryAccess.EMPTY, snapshot);
        Path blockedTemp = directory.resolve("domain.dat.temp");
        try {
            assertEquals(100, engine.insert(key, 100, Actionable.MODULATE));
            Files.createDirectory(blockedTemp);
            assertFalse(engine.commit().successful());
            assertEquals(ECOInfiniteStorageData.DomainStatus.RECOVERY_READ_ONLY, live.status());
            Path journal = directory.resolve("domain.dat.journal");
            Path moved = directory.resolve("closed.journal");
            Files.move(journal, moved); // Also checks that Windows no longer holds the writer open.
            Files.move(moved, journal);
            var recovered = ECOInfiniteStorageData.load(empty, RegistryAccess.EMPTY, ITEM_CODEC);
            ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);
            assertEquals(HugeAmount.of(100), recovered.getAmount(key));
        } finally {
            Files.deleteIfExists(blockedTemp);
            engine.close();
        }
        assertTrue(live.canWrite());
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
