package cn.dancingsnow.neoecoae.impl.storage.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import cn.dancingsnow.neoecoae.platform.nbt.NECellNbt;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SavedDataStorageArchitectureTest {
    @BeforeAll static void bootstrap() throws Exception { PlatformTestBootstrap.initializeKeyRegistry(); }
    private static ECOStorageRepository.Session session(Path path) {
        return new ECOStorageRepository.Session(new DimensionDataStorage(path.toFile(), DataFixers.getDataFixer()), path);
    }
    private static AEKey key() { return AEItemKey.of(Items.DIAMOND); }
    private static ItemStack legacy(long amount) {
        ItemStack disk = new ItemStack(Items.PAPER);
        NECellNbt.writeStacks(disk, List.of(new GenericStack(key(), amount)));
        disk.getOrCreateTag().putString("unrelated", "preserved");
        return disk;
    }
    private static SavedDataInfiniteStorageEngine domain(ECOStorageRepository.Session session) {
        Path path = session.directory().resolve("domain.dat");
        var factory = ECOInfiniteStorageData.factory(path, ECOInfiniteStorageData::createNew);
        var data = session.storage().computeIfAbsent(factory.deserializer(), factory.constructor(), "domain");
        data.bind(path); session.track(data);
        return new SavedDataInfiniteStorageEngine(data);
    }

    @Test void importsLegacyBeforeRemovingItemPayloadAndNativeSaveReloads(@TempDir Path path) throws Exception {
        var session = session(path);
        ItemStack disk = legacy(51);
        var first = session.open(disk, mock(ISaveProvider.class));
        UUID id = ECOCellReference.id(disk);
        assertNotNull(id);
        assertEquals(51, first.data().amounts().visible(key()));
        assertEquals("preserved", disk.getTag().getString("unrelated"));
        assertFalse(disk.getTag().contains(NECellNbt.CONTENTS));
        assertTrue(Files.isRegularFile(path.resolve("neoecoae_cells/" + id + ".dat")));
        first.data().amounts().add(key(), 9); first.data().setDirty();
        assertTrue(first.data().contentRevision() > first.data().durableRevision());
        session.storage().save();
        var reopened = session(path).open(disk, null);
        assertEquals(id, reopened.data().id());
        assertEquals(60, reopened.data().amounts().totalVisible());
        assertEquals(reopened.data().contentRevision(), reopened.data().durableRevision());
    }

    @Test void viewsShareLiveAmountsAndDuplicateMountDoesNotCloneResources(@TempDir Path path) {
        var session = session(path);
        ItemStack disk = legacy(30);
        ISaveProvider original = mock(ISaveProvider.class), other = mock(ISaveProvider.class);
        var first = session.open(disk, original);
        var inspection = session.open(disk, null);
        assertSame(first.data(), inspection.data());
        first.data().amounts().add(key(), 3); first.data().setDirty();
        assertEquals(33, inspection.data().amounts().visible(key()));
        var duplicate = session.open(disk.copy(), other);
        assertFalse(duplicate.writable());
        assertTrue(first.writable());
        session.release(disk, original);
        assertFalse(first.writable());
        assertTrue(session.open(disk, other).writable());
        assertEquals(33, first.data().amounts().visible(key()));
    }

    @Test void missingAuthoritativeDiskKeepsIdentityAndNeverCreatesEmptyData(@TempDir Path path) throws Exception {
        ItemStack disk = legacy(30);
        var original = session(path).open(disk, null).data();
        UUID id = original.id();
        Files.delete(original.dataFile());
        var reopened = session(path).open(disk, null);
        assertEquals(id, ECOCellReference.id(disk));
        assertFalse(reopened.writable());
        assertNotNull(reopened.data().failure());
        reopened.data().setDirty();
        assertFalse(reopened.data().flush());
        assertFalse(Files.exists(original.dataFile()));
    }

    @Test void corruptDiskDoesNotFallBackToHistoricalInventory(@TempDir Path path) throws Exception {
        ItemStack disk = legacy(30);
        var original = session(path).open(disk, null).data();
        original.amounts().add(key(), 10); original.setDirty(); assertTrue(original.flush());
        assertTrue(Files.exists(original.dataFile().resolveSibling(original.dataFile().getFileName() + ".bak")));
        byte[] damaged = {1, 4, 9}; Files.write(original.dataFile(), damaged);
        var reopened = session(path).open(disk, null);
        assertFalse(reopened.writable());
        reopened.data().setDirty(); reopened.data().save(original.dataFile().toFile());
        assertArrayEquals(damaged, Files.readAllBytes(original.dataFile()));
    }

    @Test void unresolvedKeysRemainInSnapshotAndDisableMutation(@TempDir Path path) throws Exception {
        ItemStack disk = legacy(30);
        var original = session(path).open(disk, null).data();
        CompoundTag root = NbtIo.readCompressed(original.dataFile().toFile());
        CompoundTag unknown = new CompoundTag(), encoded = new CompoundTag();
        encoded.putString("#c", "missing_mod:channel"); unknown.put("key", encoded); unknown.putLong("amount", 72);
        root.getCompound("data").getList("entries", Tag.TAG_COMPOUND).add(unknown);
        NbtIo.writeCompressed(root, original.dataFile().toFile());
        var reopened = session(path).open(disk, null).data();
        assertTrue(reopened.canRead()); assertFalse(reopened.canMutate());
        assertEquals(30, reopened.amounts().visible(key()));
        reopened.setDirty(); assertTrue(reopened.flush());
        var entries = NbtIo.readCompressed(original.dataFile().toFile()).getCompound("data").getList("entries", Tag.TAG_COMPOUND);
        assertTrue(entries.stream().anyMatch(unknown::equals));
    }

    @Test void wrongIdentityOrNewerFormatCannotOverwriteFile(@TempDir Path path) {
        UUID id = UUID.randomUUID();
        var data = new ECOCellSavedData(id);
        CompoundTag snapshot = data.save(new CompoundTag());
        snapshot.putUUID("id", UUID.randomUUID());
        assertFalse(ECOCellSavedData.load(snapshot, id).canRead());
        snapshot.putUUID("id", id); snapshot.putInt("format", 99);
        assertFalse(ECOCellSavedData.load(snapshot, id).canRead());
    }

    @Test void metadataSurvivesStaleItemReferenceAndPreventsResurrection(@TempDir Path path) {
        var disk = legacy(30);
        var data = session(path).open(disk, null).data();
        ItemStack stale = disk.copy();
        CompoundTag metadata = new CompoundTag();
        UUID domain = UUID.randomUUID();
        metadata.putBoolean("neoecoae_infinite_member", true); metadata.putUUID("neoecoae_infinite_domain", domain);
        data.amounts().clear(); data.metadata(metadata); assertTrue(data.flush());
        var reopened = session(path).open(stale, null);
        assertTrue(NEItemData.read(stale).getBoolean("neoecoae_infinite_member"));
        assertEquals(domain, NEItemData.read(stale).getUUID("neoecoae_infinite_domain"));
        assertTrue(reopened.data().amounts().isEmpty());
    }

    @Test void failedWriteKeepsRevisionDirtyAndCanRetryAtSameBoundPath(@TempDir Path path) throws Exception {
        var data = new ECOCellSavedData(UUID.randomUUID());
        Path occupied = path.resolve("occupied"); Files.writeString(occupied, "keep");
        data.bind(occupied.resolve("disk.dat")); data.amounts().add(key(), 5); data.setDirty();
        long revision = data.contentRevision();
        assertFalse(data.flushThrough(revision)); assertTrue(data.isDirty()); assertTrue(data.durableRevision() < revision);
        assertEquals("keep", Files.readString(occupied));
        Files.delete(occupied);
        assertTrue(data.flushThrough(revision)); assertFalse(data.isDirty());
        assertEquals(revision, data.durableRevision());
    }

    @Test void hotMutationsDoNotWriteSnapshotsOrSerializeWholeItem(@TempDir Path path) {
        var disk = legacy(30);
        var data = session(path).open(disk, null).data();
        long writes = data.savedSnapshots(); CompoundTag reference = disk.getTag().copy();
        for (int i = 0; i < 100_000; i++) { data.amounts().add(key(), 1); data.amounts().subtract(key(), 1); data.setDirty(); }
        assertEquals(30, data.amounts().totalVisible());
        assertEquals(writes, data.savedSnapshots()); assertEquals(reference, disk.getTag());
        assertTrue(data.flush()); assertEquals(writes + 1, data.savedSnapshots());
    }

    @Test void quantityTablePromotesAndDowngradesWithoutLosingExactTotals() {
        StorageAmounts amounts = new StorageAmounts();
        amounts.add(key(), Long.MAX_VALUE); amounts.add(key(), 50);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(50)), amounts.get(key()).toBigInteger());
        assertEquals(Long.MAX_VALUE, amounts.totalVisible());
        amounts.subtract(key(), 51);
        assertFalse(amounts.hasOverflow()); assertEquals(Long.MAX_VALUE - 1, amounts.totalVisible());
        long structure = amounts.structureRevision(), content = amounts.contentRevision();
        amounts.subtract(key(), 1);
        assertEquals(structure, amounts.structureRevision()); assertEquals(content + 1, amounts.contentRevision());
        amounts.clear(); assertEquals(0, amounts.totalVisible());
    }

    @ParameterizedTest @EnumSource(StorageTransferCoordinator.Phase.class)
    void eachCrashBoundaryReplaysExactlyOnce(StorageTransferCoordinator.Phase crashAt, @TempDir Path path) {
        exerciseCrash(crashAt, path, BigInteger.valueOf(100));
    }

    @ParameterizedTest @EnumSource(StorageTransferCoordinator.Phase.class)
    void enormousQuantitySurvivesEveryCrashBoundary(StorageTransferCoordinator.Phase crashAt, @TempDir Path path) {
        exerciseCrash(crashAt, path, BigInteger.TEN.pow(200).add(BigInteger.valueOf(12345)));
    }

    private void exerciseCrash(StorageTransferCoordinator.Phase crashAt, Path path, BigInteger amount) {
        var session = session(path); ItemStack disk = legacy(100);
        var source = session.open(disk, null).data(); var target = domain(session);
        source.amounts().set(key(), HugeAmount.of(amount)); source.setDirty();
        target.insert(key(), 7, Actionable.MODULATE); assertTrue(target.flush());
        UUID transaction = UUID.randomUUID(), domain = UUID.randomUUID();
        assertThrows(PowerLoss.class, () -> StorageTransferCoordinator.transfer(session, source, domain, target, transaction,
            phase -> { if (phase == crashAt) throw new PowerLoss(); }));
        var restarted = session(path);
        var reopenedSource = restarted.open(disk, null).data(); var reopenedTarget = domain(restarted);
        assertTrue(StorageTransferCoordinator.transfer(restarted, reopenedSource, domain, reopenedTarget, transaction));
        assertTrue(reopenedSource.amounts().isEmpty());
        assertEquals(HugeAmount.of(amount.add(BigInteger.valueOf(7))), reopenedTarget.getAmount(key()));
        assertTrue(reopenedTarget.hasMigrationReceipt(transaction));
        assertTrue(reopenedSource.metadata().getBoolean("neoecoae_infinite_member"));
        assertTrue(StorageTransferCoordinator.journal(restarted, source.id(), false).completed());
        assertTrue(StorageTransferCoordinator.journal(restarted, source.id(), false).sourceSnapshot().isEmpty());
        assertTrue(StorageTransferCoordinator.transfer(restarted, reopenedSource, domain, reopenedTarget, transaction));
        assertEquals(HugeAmount.of(amount.add(BigInteger.valueOf(7))), reopenedTarget.getAmount(key()));
    }

    @Test void destinationFailureRetainsSealedSourceAndRetriesWithoutDuplication(@TempDir Path path) throws Exception {
        var session = session(path); var source = session.open(legacy(100), null).data(); var target = domain(session);
        Path occupied = path.resolve("domain.dat"); Files.createDirectory(occupied); Files.writeString(occupied.resolve("keep"), "keep");
        UUID transaction = UUID.randomUUID(), domain = UUID.randomUUID();
        assertFalse(StorageTransferCoordinator.transfer(session, source, domain, target, transaction));
        assertEquals(100, source.amounts().visible(key())); assertFalse(target.canUseMountedStorage());
        assertTrue(source.metadata().hasUUID("neoecoae_migration_id"));
        Files.delete(occupied.resolve("keep")); Files.delete(occupied);
        assertTrue(StorageTransferCoordinator.transfer(session, source, domain, target, transaction));
        assertTrue(source.amounts().isEmpty()); assertEquals(HugeAmount.of(100), target.getAmount(key()));
        assertTrue(target.canUseMountedStorage());
    }

    @Test void bigIntegerDiskSnapshotAndLongProjectionRoundTrip(@TempDir Path path) {
        var disk = legacy(30); var data = session(path).open(disk, null).data();
        BigInteger exact = BigInteger.TEN.pow(300).add(BigInteger.valueOf(789));
        data.amounts().set(key(), HugeAmount.of(exact)); data.setDirty(); assertTrue(data.flush());
        ECOCellReference.update(disk, data);
        assertEquals(exact.toString(), disk.getTag().getCompound(ECOCellReference.TAG).getString("exactAmount"));
        var reopened = session(path).open(disk, null).data();
        assertEquals(exact, reopened.amounts().get(key()).toBigInteger());
        assertEquals(Long.MAX_VALUE, reopened.amounts().visible(key()));
        reopened.amounts().subtract(key(), Long.MAX_VALUE); reopened.setDirty(); assertTrue(reopened.flush());
        assertEquals(exact.subtract(BigInteger.valueOf(Long.MAX_VALUE)), session(path).open(disk, null).data().amounts().get(key()).toBigInteger());
    }

    @Test void exactRestorePlanSavesAndRetiresAllDigits(@TempDir Path path) throws Exception {
        var session = session(path); var target = domain(session);
        BigInteger exact = BigInteger.TEN.pow(180).add(BigInteger.valueOf(99));
        target.insert(key(), exact, Actionable.MODULATE); UUID transaction = UUID.randomUUID(), identity = UUID.randomUUID();
        var goal = new cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine.RestoreTargetAmounts(
            BigInteger.TEN, exact.add(BigInteger.TEN), exact);
        assertTrue(target.reserveRestores(java.util.Map.of(key(), transaction), java.util.Set.of(identity),
            java.util.Map.of(key(), java.util.Map.of(identity, goal)))); assertTrue(target.flush());
        var reopened = domain(session(path));
        assertEquals(goal, reopened.restorePlan(key()).get(identity));
        assertTrue(reopened.finishRestores(java.util.Map.of(key(), transaction))); assertTrue(reopened.flush());
        assertTrue(domain(session(path)).isEmpty());
    }

    @Test void randomQuantityOperationsMatchBigIntegerReference() {
        var table = new StorageAmounts(); var random = new java.util.Random(18431);
        var keys = new java.util.ArrayList<AEKey>(); var expected = new java.util.HashMap<AEKey, BigInteger>();
        for (int i = 0; i < 12; i++) {
            ItemStack item = new ItemStack(Items.DIAMOND); item.getOrCreateTag().putInt("variant", i);
            AEKey key = AEItemKey.of(item); keys.add(key);
            BigInteger initial = i % 2 == 0 ? BigInteger.TEN.pow(100) : BigInteger.valueOf(Long.MAX_VALUE).subtract(BigInteger.TEN);
            table.set(key, HugeAmount.of(initial)); expected.put(key, initial);
        }
        for (int i = 0; i < 30_000; i++) {
            AEKey key = keys.get(random.nextInt(keys.size())); BigInteger before = expected.get(key);
            long delta = i % 317 == 0 ? Long.MAX_VALUE : random.nextInt(10_000);
            if (random.nextBoolean()) { table.add(key, delta); expected.put(key, before.add(BigInteger.valueOf(delta))); }
            else {
                long taken = before.min(BigInteger.valueOf(delta)).longValue(); table.subtract(key, taken);
                expected.put(key, before.subtract(BigInteger.valueOf(taken)));
            }
            if (i % 97 == 0) {
                BigInteger total = expected.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
                assertEquals(total, table.totalExact());
                for (AEKey candidate : keys) assertEquals(expected.get(candidate), table.get(candidate).toBigInteger());
            }
        }
    }

    @Test void failureAfterSourceRetirementStillFlushesBeforeReportingCompletion(@TempDir Path path) throws Exception {
        var session = session(path); ItemStack disk = legacy(100); var source = session.open(disk, null).data(); var target = domain(session);
        UUID transaction = UUID.randomUUID(), domain = UUID.randomUUID();
        Path file = source.dataFile(), held = file.resolveSibling(file.getFileName() + ".held");
        boolean result = StorageTransferCoordinator.transfer(session, source, domain, target, transaction, phase -> {
            if (phase == StorageTransferCoordinator.Phase.DESTINATION_SAVED) {
                try { Files.move(file, held); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "keep"); }
                catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            }
        });
        assertFalse(result); assertTrue(source.isDirty()); assertFalse(target.canUseMountedStorage());
        Files.delete(file.resolve("keep")); Files.delete(file); Files.move(held, file);
        assertTrue(StorageTransferCoordinator.transfer(session, source, domain, target, transaction));
        assertTrue(session(path).open(disk, null).data().amounts().isEmpty());
        assertEquals(HugeAmount.of(100), domain(session(path)).getAmount(key()));
    }

    @Test void failedCompletionMarkerMustBeRetriedBeforeUnlocking(@TempDir Path path) throws Exception {
        var session = session(path); var source = session.open(legacy(100), null).data(); var target = domain(session);
        UUID transaction = UUID.randomUUID(), domain = UUID.randomUUID();
        Path file = path.resolve("neoecoae_transfers/" + source.id() + ".dat"), held = file.resolveSibling(file.getFileName() + ".held");
        boolean result = StorageTransferCoordinator.transfer(session, source, domain, target, transaction, phase -> {
            if (phase == StorageTransferCoordinator.Phase.SOURCE_SAVED) {
                try { Files.move(file, held); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "keep"); }
                catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            }
        });
        assertFalse(result); assertFalse(target.canUseMountedStorage());
        Files.delete(file.resolve("keep")); Files.delete(file); Files.move(held, file);
        assertTrue(StorageTransferCoordinator.transfer(session, source, domain, target, transaction));
        assertTrue(target.canUseMountedStorage());
        assertTrue(StorageTransferCoordinator.journal(session(path), source.id(), false).completed());
    }

    @Test void catalogRemembersPublishedDomainsAndRejectsMalformedIdentityList(@TempDir Path path) {
        UUID domain = UUID.randomUUID(); var catalog = StorageCatalogSavedData.open(session(path));
        assertTrue(catalog.publish(domain)); assertTrue(StorageCatalogSavedData.open(session(path)).contains(domain));
        CompoundTag malformed = catalog.save(new CompoundTag()); var invalid = new net.minecraft.nbt.ListTag();
        invalid.add(new CompoundTag()); malformed.put("domains", invalid);
        assertFalse(StorageCatalogSavedData.load(malformed).readable());
    }

    @Test void upgradesPartiallyImportedLegacyCellWithoutReinsertingPublishedKeys(@TempDir Path path) throws Exception {
        ItemStack disk = legacy(100); UUID transaction = UUID.randomUUID(), domainId = UUID.randomUUID(), oldReceipt = UUID.randomUUID();
        disk.getOrCreateTag().putUUID("neoecoae_migration_id", transaction);
        disk.getOrCreateTag().putUUID("neoecoae_migration_domain", domainId);
        var session = session(path); var source = session.open(disk, null).data();
        assertTrue(source.legacyMigration());
        var target = domain(session);
        assertEquals(100, target.insertOnce(oldReceipt, key(), 100)); assertTrue(target.flush());
        Path file = path.resolve("domain.dat"), held = path.resolve("domain.held");
        Files.move(file, held); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "keep");
        assertFalse(StorageTransferCoordinator.transfer(session, source, domainId, target, transaction,
            java.util.Map.of(key(), java.util.Set.of(oldReceipt))));
        assertEquals(HugeAmount.of(100), target.getAmount(key()));
        Files.delete(file.resolve("keep")); Files.delete(file); Files.move(held, file);
        var restarted = session(path); var reloaded = restarted.open(disk, null).data(); var destination = domain(restarted);
        // No drive position or callback exists during cold recovery; aliases must come from the durable journal.
        assertTrue(StorageTransferCoordinator.transfer(restarted, reloaded, domainId, destination, transaction));
        assertEquals(HugeAmount.of(100), destination.getAmount(key()));
        assertTrue(reloaded.amounts().isEmpty()); assertFalse(reloaded.legacyMigration());
    }

    @Test void legacyPerKeyReceiptDoesNotAffectANewIndependentMigration(@TempDir Path path) {
        var session = session(path); var source = session.open(legacy(100), null).data(); var target = domain(session);
        target.insertOnce(UUID.randomUUID(), key(), 100); assertTrue(target.flush());
        assertTrue(StorageTransferCoordinator.transfer(session, source, UUID.randomUUID(), target, UUID.randomUUID()));
        assertEquals(HugeAmount.of(200), target.getAmount(key()));
    }

    private static final class PowerLoss extends Error {}
}
