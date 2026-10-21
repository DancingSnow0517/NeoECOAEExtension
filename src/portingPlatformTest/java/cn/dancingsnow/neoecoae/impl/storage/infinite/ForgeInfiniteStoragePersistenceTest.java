package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the native 1.20.1 save cycle and AE2 codecs without a game launcher. */
class ForgeInfiniteStoragePersistenceTest {
    @BeforeAll static void bootstrap() throws Exception {
        PlatformTestBootstrap.initializeKeyRegistry();
    }

    private static ECOInfiniteStorageData mount(DimensionDataStorage storage, Path directory) {
        var factory = ECOInfiniteStorageData.factory(directory.resolve("domain.dat"), ECOInfiniteStorageData::createNew);
        return storage.computeIfAbsent(factory.deserializer(), factory.constructor(), "domain");
    }

    private static DimensionDataStorage storage(Path directory) {
        return new DimensionDataStorage(directory.toFile(), DataFixers.getDataFixer());
    }

    private static ECOInfiniteStorageData read(Path file) throws Exception {
        return ECOInfiniteStorageData.load(NbtIo.readCompressed(file.toFile()).getCompound("data"));
    }

    @Test void nativeSaveCyclePreservesOverflowNbtAndMigrationReceipt(@TempDir Path directory) throws Exception {
        var vanilla = storage(directory);
        var live = mount(vanilla, directory);
        var stack = new ItemStack(Items.DIAMOND);
        stack.getOrCreateTag().putInt("grade", 7);
        var key = AEItemKey.of(stack);
        var engine = new SavedDataInfiniteStorageEngine(live);
        var transaction = UUID.randomUUID();
        engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        engine.insertOnce(transaction, key, 17);
        assertFalse(Files.exists(directory.resolve("domain.dat")));
        vanilla.save();
        assertFalse(live.isDirty());

        var reopened = mount(storage(directory), directory);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(17)), reopened.getAmount(key).toBigInteger());
        var restored = new SavedDataInfiniteStorageEngine(reopened);
        assertTrue(restored.hasMigrationReceipt(transaction));
        assertEquals(17, restored.insertOnce(transaction, key, 17));
        assertEquals(live.getAmount(key), restored.getAmount(key));
        assertEquals(Set.of("domain.dat"), fileNames(directory));
    }

    @Test void damagedAuthoritativeFileNeverFallsBackToFreshData(@TempDir Path directory) throws Exception {
        byte[] damaged = {1, 2, 3, 4};
        var snapshot = directory.resolve("domain.dat");
        Files.write(snapshot, damaged);
        var vanilla = storage(directory);
        var live = mount(vanilla, directory);
        assertEquals(ECOInfiniteStorageData.DomainStatus.UNAVAILABLE, live.status());
        assertEquals(0, new SavedDataInfiniteStorageEngine(live).insert(AEItemKey.of(Items.STONE), 1, Actionable.MODULATE));
        live.setDirty();
        vanilla.save();
        assertArrayEquals(damaged, Files.readAllBytes(snapshot));
    }

    @Test void unknownKeyAndMetadataSurviveSaveAndBlockExit(@TempDir Path directory) throws Exception {
        var original = ECOInfiniteStorageData.createNew().save(new CompoundTag());
        var entry = new CompoundTag();
        var key = new CompoundTag();
        key.putString("#c", "missing_mod:resource");
        entry.put("key", key);
        entry.put("amount", HugeAmount.of(BigInteger.TEN.pow(40)).write());
        original.getList("entries", Tag.TAG_COMPOUND).add(entry);
        original.putString("migrations", "unreadable-receipt");
        var live = ECOInfiniteStorageData.load(original);
        assertEquals(ECOInfiniteStorageData.DomainStatus.DEGRADED, live.status());
        assertFalse(live.canExitOrRestore());
        live.setDirty();
        live.save(directory.resolve("domain.dat").toFile());
        var saved = NbtIo.readCompressed(directory.resolve("domain.dat").toFile()).getCompound("data");
        assertEquals(entry, saved.getList("entries", Tag.TAG_COMPOUND).getCompound(0));
        assertEquals("unreadable-receipt", saved.getCompound("unresolvedMetadata").getString("migrations"));
        assertFalse(read(directory.resolve("domain.dat")).canExitOrRestore());
    }

    @Test void pendingRestoreAllocationAndReceiptSurviveReload(@TempDir Path directory) throws Exception {
        var live = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(live);
        var key = AEItemKey.of(Items.STONE);
        var target = UUID.randomUUID();
        var transaction = UUID.randomUUID();
        engine.insert(key, 25, Actionable.MODULATE);
        assertTrue(live.reserveRestore(key, transaction, Set.of(target),
            java.util.Map.of(target, new ECOInfiniteStorageEngine.RestoreTargetAmounts(3, 28, 25))));
        live.save(directory.resolve("domain.dat").toFile());
        var reopened = new SavedDataInfiniteStorageEngine(read(directory.resolve("domain.dat")));
        assertEquals(transaction, reopened.restoreTransaction(key));
        assertEquals(Set.of(target), reopened.restoreTargetIds(key));
        assertEquals(0, reopened.extract(key, 25, Actionable.MODULATE));
        assertEquals(HugeAmount.ZERO, reopened.getAmount(key));
        assertEquals(HugeAmount.of(25), reopened.getRestoreAmount(key));
    }

    @Test void failedSaveKeepsDirtyStateAndRetries(@TempDir Path directory) throws Exception {
        var live = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(live);
        var key = AEItemKey.of(Items.STONE);
        engine.insert(key, 100, Actionable.MODULATE);
        var blocked = directory.resolve("occupied");
        Files.writeString(blocked, "file");
        live.save(blocked.resolve("domain.dat").toFile());
        assertTrue(live.isDirty());
        assertEquals(ECOInfiniteStorageData.DomainStatus.DEGRADED, live.status());
        assertFalse(live.canExitOrRestore());
        assertEquals(10, engine.extract(key, 10, Actionable.MODULATE));
        live.save(directory.resolve("domain.dat").toFile());
        assertFalse(live.isDirty());
        assertEquals(ECOInfiniteStorageData.DomainStatus.HEALTHY, live.status());
        assertEquals(HugeAmount.of(90), read(directory.resolve("domain.dat")).getAmount(key));
    }

    @Test void failedAtomicReplacementLeavesTargetUntouchedAndCleansTemporaryFile(@TempDir Path directory) throws Exception {
        var snapshot = directory.resolve("domain.dat");
        Files.createDirectory(snapshot);
        Files.writeString(snapshot.resolve("keep"), "untouched");
        var live = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(live);
        var key = AEItemKey.of(Items.STONE);
        engine.insert(key, 10, Actionable.MODULATE);
        live.save(snapshot.toFile());
        assertTrue(live.isDirty());
        assertEquals(ECOInfiniteStorageData.DomainStatus.DEGRADED, live.status());
        assertEquals("untouched", Files.readString(snapshot.resolve("keep")));
        assertEquals(Set.of("domain.dat"), fileNames(directory));
    }

    @Test void legacyJournalReplaysOnceAndIsRemovedOnlyAfterSnapshot(@TempDir Path directory) throws Exception {
        var snapshot = directory.resolve("domain.dat");
        var journal = directory.resolve("domain.dat.journal");
        var key = AEItemKey.of(Items.STONE);
        append(journal, 1, key, 50, true);
        append(journal, 2, key, 7, false);
        var live = ECOInfiniteStorageData.createNew();
        ECOInfiniteStorageData.replayJournal(live, snapshot);
        assertEquals(HugeAmount.of(43), live.getAmount(key));
        assertTrue(Files.exists(journal));
        live.save(snapshot.toFile());
        assertFalse(Files.exists(journal));
        append(journal, 1, key, 50, true);
        var reopened = read(snapshot);
        ECOInfiniteStorageData.replayJournal(reopened, snapshot);
        assertEquals(HugeAmount.of(43), reopened.getAmount(key));
    }

    @Test void malformedJournalMakesLoadedDomainUnavailable(@TempDir Path directory) throws Exception {
        var snapshot = directory.resolve("domain.dat");
        var live = ECOInfiniteStorageData.createNew();
        live.setDirty();
        live.save(snapshot.toFile());
        byte[] original = Files.readAllBytes(snapshot);
        append(directory.resolve("domain.dat.journal"), 2, AEItemKey.of(Items.STONE), 10, true);
        var vanilla = storage(directory);
        var reopened = mount(vanilla, directory);
        assertEquals(ECOInfiniteStorageData.DomainStatus.UNAVAILABLE, reopened.status());
        reopened.setDirty();
        vanilla.save();
        assertArrayEquals(original, Files.readAllBytes(snapshot));
    }

    @Test void incompleteLegacyDomainCannotWriteOrExtract(@TempDir Path directory) throws Exception {
        var live = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(live);
        var key = AEItemKey.of(Items.STONE);
        engine.insert(key, 10, Actionable.MODULATE);
        live.markIncompleteLegacy(java.util.List.of("missing shard"));
        assertEquals(ECOInfiniteStorageData.DomainStatus.RECOVERY_READ_ONLY, live.status());
        assertEquals(HugeAmount.of(10), engine.getAmount(key));
        assertEquals(0, engine.extract(key, 10, Actionable.MODULATE));
        live.save(directory.resolve("domain.dat").toFile());
        assertFalse(Files.exists(directory.resolve("domain.dat")));
    }

    @Test void legacyCompressedSnapshotIsReadWithoutChangingSource(@TempDir Path directory) throws Exception {
        var path = directory.resolve("legacy.dat");
        var root = new CompoundTag();
        var data = ECOInfiniteStorageData.createNew().save(new CompoundTag());
        data.putInt("version", 2);
        root.put("data", data);
        NbtIo.writeCompressed(root, path.toFile());
        byte[] original = Files.readAllBytes(path);
        assertEquals(data, LegacyInfiniteStorageReader.read(path));
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    private static Set<String> fileNames(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
    }

    private static void append(Path path, long sequence, AEKey key, long amount, boolean added) throws Exception {
        var record = new CompoundTag();
        record.putLong("sequence", sequence);
        record.put("key", key.toTagGeneric());
        record.putLong("amount", amount);
        record.putBoolean("added", added);
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { NbtIo.write(record, out); }
        try (var out = new DataOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            out.writeInt(bytes.size());
            out.write(bytes.toByteArray());
        }
    }
}
