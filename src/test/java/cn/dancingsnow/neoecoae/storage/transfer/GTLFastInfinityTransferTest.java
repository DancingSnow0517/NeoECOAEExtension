package cn.dancingsnow.neoecoae.storage.transfer;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor;
import cn.dancingsnow.neoecoae.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellSavedData;
import cn.dancingsnow.neoecoae.storage.persistence.StorageAmounts;
import cn.dancingsnow.neoecoae.storage.persistence.StorageTransferData;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GTLFastInfinityTransferTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @TempDir Path directory;
    private final UUID domain = UUID.randomUUID();
    private final AEKey stone = AEItemKey.of(Items.STONE);
    private final AEKey dirt = AEItemKey.of(Items.DIRT);
    private final BigInteger huge = new BigInteger("3370000000000000000000001");

    @Test void importsMoreThanFiveHugeResourceTypesInOneCall() {
        var cell = source();
        List<AEKey> keys = List.of(stone, dirt, AEItemKey.of(Items.SAND), AEItemKey.of(Items.GRAVEL),
            AEItemKey.of(Items.GLASS), AEItemKey.of(Items.COBBLESTONE));
        for (AEKey key : keys) cell.data.amounts().set(key, HugeAmount.of(huge));
        var target = target();
        assertEquals(Long.MAX_VALUE, GTLFastInfinityTransfer.transfer(cell, domain, target, key -> true));
        assertTrue(cell.data.amounts().isEmpty());
        for (AEKey key : keys) assertEquals(huge, target.getAmount(key).toBigInteger());
        assertTrue(cell.journal.completed());
        assertTrue(target.canUseMountedStorage());
        assertEquals(1, cell.retireCalls);
    }

    @Test void preservesFilteredSmallSurvivorAndCanImportNewContentsLater() {
        var cell = source();
        cell.data.amounts().set(stone, HugeAmount.of(huge));
        cell.data.amounts().setLong(dirt, 1);
        var target = target();
        GTLFastInfinityTransfer.transfer(cell, domain, target, stone::equals);
        assertEquals(1, cell.data.amounts().visible(dirt));
        assertEquals(huge, target.getAmount(stone).toBigInteger());
        assertTrue(target.getAmount(dirt).isZero());
        cell.data.amounts().setLong(stone, 7);
        GTLFastInfinityTransfer.transfer(cell, domain, target, key -> true);
        assertEquals(huge.add(BigInteger.valueOf(7)), target.getAmount(stone).toBigInteger());
        assertEquals(1, target.getAmount(dirt).toLongSaturated());
    }

    @Test void destinationWriteFailureLeavesSealedSourceAndRetryDoesNotDoubleCredit() throws Exception {
        var cell = source();
        cell.data.amounts().set(stone, HugeAmount.of(huge));
        var targetData = ECOInfiniteStorageData.createNew();
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        targetData.bind(blocked.resolve("target.dat"));
        var target = new SavedDataInfiniteStorageEngine(targetData);
        assertEquals(0, GTLFastInfinityTransfer.transfer(cell, domain, target, key -> true));
        assertTrue(cell.neoecoae$sealed());
        assertEquals(huge, cell.data.amounts().get(stone).toBigInteger());
        assertEquals(0, cell.retireCalls);
        Files.delete(blocked);
        GTLFastInfinityTransfer.transfer(cell, domain, target, key -> false);
        assertEquals(huge, target.getAmount(stone).toBigInteger());
        assertTrue(cell.data.amounts().isEmpty());
        assertFalse(cell.neoecoae$sealed());
        assertTrue(target.canUseMountedStorage());
    }

    @Test void restartAfterDestinationSaveReplaysJournalWithoutDuplicatingQuantity() throws Exception {
        var cell = source();
        cell.data.amounts().set(stone, HugeAmount.of(huge));
        var target = target();
        cell.failRetire = true;
        assertThrows(IllegalStateException.class,
            () -> GTLFastInfinityTransfer.transfer(cell, domain, target, key -> true));
        var reopened = new Cell(ECOCellSavedData.load(
            NbtIo.readCompressed(cell.data.dataFile().toFile()).getCompound("data"), cell.data.id()),
            StorageTransferData.load(NbtIo.readCompressed(cell.journal.dataFile().toFile()).getCompound("data"), cell.data.id()));
        reopened.data.bind(cell.data.dataFile());
        reopened.journal.bind(cell.journal.dataFile());
        var restoredData = ECOInfiniteStorageData.load(NbtIo.readCompressed(directory.resolve("target.dat").toFile()).getCompound("data"));
        restoredData.bind(directory.resolve("target.dat"));
        var restored = new SavedDataInfiniteStorageEngine(restoredData);
        assertEquals(0, GTLFastInfinityTransfer.transfer(reopened, UUID.randomUUID(), restored, key -> true));
        GTLFastInfinityTransfer.transfer(reopened, domain, restored, key -> false);
        assertEquals(huge, restored.getAmount(stone).toBigInteger());
        assertTrue(reopened.data.amounts().isEmpty());
        assertTrue(reopened.journal.completed());
    }

    @Test void sourceFlushFailureCanResumeAlreadyRetiredSource() {
        var cell = source();
        cell.data.amounts().set(stone, HugeAmount.of(huge));
        var target = target();
        cell.failAfterRetire = true;
        assertThrows(IllegalStateException.class,
            () -> GTLFastInfinityTransfer.transfer(cell, domain, target, key -> true));
        assertTrue(cell.neoecoae$sealed());
        assertFalse(target.canUseMountedStorage());
        cell.failAfterRetire = false;
        GTLFastInfinityTransfer.transfer(cell, domain, target, key -> false);
        assertEquals(huge, target.getAmount(stone).toBigInteger());
        assertEquals(1, cell.retireCalls);
        assertFalse(cell.neoecoae$sealed());
        assertTrue(target.canUseMountedStorage());
    }

    @Test void resumesPartiallyRetiredBatchWithoutDuplicatingEitherResource() {
        var cell = source();
        cell.data.amounts().set(stone, HugeAmount.of(huge));
        cell.data.amounts().setLong(dirt, 1);
        var target = target();
        cell.failDuringRetire = true;
        assertThrows(IllegalStateException.class,
            () -> GTLFastInfinityTransfer.transfer(cell, domain, target, key -> true));
        assertEquals(1, cell.data.amounts().size());
        assertTrue(cell.neoecoae$sealed());
        cell.failDuringRetire = false;
        GTLFastInfinityTransfer.transfer(cell, domain, target, key -> false);
        assertEquals(huge, target.getAmount(stone).toBigInteger());
        assertEquals(1, target.getAmount(dirt).toLongSaturated());
        assertTrue(cell.data.amounts().isEmpty());
        assertFalse(cell.neoecoae$sealed());
    }

    @Test void finalJournalWriteFailureKeepsSourceSealedUntilCompletionIsDurable() throws Exception {
        var cell = source();
        cell.data.amounts().set(stone, HugeAmount.of(huge));
        var target = target();
        cell.blockFinalJournalSave = true;
        assertEquals(0, GTLFastInfinityTransfer.transfer(cell, domain, target, key -> true));
        assertTrue(cell.journal.completed());
        assertNotNull(cell.journal.writeFailure());
        assertTrue(cell.neoecoae$sealed());
        assertFalse(target.canUseMountedStorage());
        Files.delete(cell.journal.dataFile());
        cell.blockFinalJournalSave = false;
        GTLFastInfinityTransfer.transfer(cell, domain, target, key -> true);
        assertEquals(huge, target.getAmount(stone).toBigInteger());
        assertFalse(cell.neoecoae$sealed());
        assertTrue(target.canUseMountedStorage());
    }

    @Test void honorsMountedExtractionFilterAndStorageBusExclusion() {
        var cell = source();
        cell.data.amounts().set(stone, HugeAmount.of(huge));
        var wrapper = new Bus(cell);
        var network = new Network(wrapper);
        var target = target();
        assertEquals(0, GTLFastInfinityTransfer.transfer(network, domain, target, true, IActionSource.empty(), key -> true));
        wrapper.allow = false;
        assertEquals(0, GTLFastInfinityTransfer.transfer(network, domain, target, false, IActionSource.empty(), key -> true));
        wrapper.allow = true;
        assertEquals(Long.MAX_VALUE, GTLFastInfinityTransfer.transfer(network, domain, target, false, IActionSource.empty(), key -> true));
        assertEquals(huge, target.getAmount(stone).toBigInteger());
        assertTrue(cell.data.amounts().isEmpty());
    }

    @Test void decodesUnsignedLowWordAndMaximumPositiveInt128Exactly() {
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE),
            GTLFastInfinityTransfer.amount(new Words(0, -1)));
        assertEquals(BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE),
            GTLFastInfinityTransfer.amount(new Words(Long.MAX_VALUE, -1)));
        assertThrows(IllegalStateException.class, () -> GTLFastInfinityTransfer.amount(new Words(-1, -1)));
    }

    public record Words(long high, long low) {
        public long getHigh() { return high; }
        public long getLow() { return low; }
    }

    private Cell source() {
        UUID id = UUID.randomUUID();
        var data = new ECOCellSavedData(id);
        data.bind(directory.resolve("source.dat"));
        var journal = new StorageTransferData(id);
        journal.bind(directory.resolve("journal.dat"));
        return new Cell(data, journal);
    }

    private SavedDataInfiniteStorageEngine target() {
        var data = ECOInfiniteStorageData.createNew();
        data.bind(directory.resolve("target.dat"));
        return new SavedDataInfiniteStorageEngine(data);
    }

    private static class Cell implements GTLFastInfinityTransfer.Source {
        final ECOCellSavedData data;
        final StorageTransferData journal;
        int retireCalls;
        boolean failRetire, failAfterRetire, failDuringRetire, blockFinalJournalSave;
        Cell(ECOCellSavedData data, StorageTransferData journal) { this.data = data; this.journal = journal; }
        @Override public StorageTransferData neoecoae$transferJournal() { return journal; }
        @Override public StorageAmounts neoecoae$physicalContents() { return data.amounts(); }
        @Override public void neoecoae$retireContents(Set<AEKey> keys) {
            if (failRetire) throw new IllegalStateException("Injected failure after destination save");
            retireCalls++;
            for (AEKey key : keys) {
                data.amounts().setLong(key, 0);
                if (failDuringRetire) throw new IllegalStateException("Injected partial native retirement failure");
            }
        }
        @Override public void neoecoae$flushContents() {
            if (retireCalls > 0 && failAfterRetire) throw new IllegalStateException("Injected source write failure");
            data.setDirty();
            if (!data.flush()) throw new IllegalStateException("Source write failed");
            if (retireCalls > 0 && blockFinalJournalSave) {
                try {
                    Files.move(journal.dataFile(), journal.dataFile().resolveSibling("sealed-journal.dat"));
                    Files.createDirectory(journal.dataFile());
                } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            }
        }
        @Override public void getAvailableStacks(KeyCounter out) {
            if (!neoecoae$sealed()) data.amounts().visitVisible(out::set);
        }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) { return 0; }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode != Actionable.SIMULATE) fail("Bulk transfer must not loop through long extraction");
            return neoecoae$sealed() ? 0 : Math.min(amount, data.amounts().visible(key));
        }
        @Override public Component getDescription() { return Component.literal("test fast cell"); }
    }

    private static class Bus implements MEStorage, DelegatingMEInventoryAccessor, StorageBusTransferFilter.StorageBusInventory {
        final MEStorage delegate;
        boolean allow = true;
        Bus(MEStorage delegate) { this.delegate = delegate; }
        @Override public MEStorage neoecoae$getDelegate() { return delegate; }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            return allow ? delegate.extract(key, amount, mode, source) : 0;
        }
        @Override public void getAvailableStacks(KeyCounter out) { delegate.getAvailableStacks(out); }
        @Override public Component getDescription() { return delegate.getDescription(); }
    }

    private static class Network implements MEStorage, NetworkInventoryCache.Mounts {
        final MEStorage child;
        Network(MEStorage child) { this.child = child; }
        @Override public long neoecoae$mountGeneration() { return 0; }
        @Override public boolean neoecoae$withMounts(Consumer<List<MEStorage>> action) {
            action.accept(List.of(child)); return true;
        }
        @Override public void getAvailableStacks(KeyCounter out) { child.getAvailableStacks(out); }
        @Override public Component getDescription() { return child.getDescription(); }
    }
}
