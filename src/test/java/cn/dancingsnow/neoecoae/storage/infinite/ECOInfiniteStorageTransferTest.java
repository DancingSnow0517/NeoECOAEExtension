package cn.dancingsnow.neoecoae.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOInfiniteStorageTransferTest {
    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final UUID DOMAIN = UUID.fromString("00000000-0000-0000-0000-00000000d0d0");

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static List<AEKey> keys(int count, int offset) {
        List<AEKey> keys = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            if (offset-- > 0) continue;
            keys.add(key(item));
            if (keys.size() == count) break;
        }
        return keys;
    }

    /** AE2's generic key codec needs its registries; tests only need a stable per-key tag. */
    private static AEItemKey key(net.minecraft.world.item.Item item) {
        var key = spy(AEItemKey.of(item));
        var tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
        tag.putString("#c", "ae2:i");
        doReturn(tag).when(key).toTagGeneric(any());
        return key;
    }

    private static IECOStorageMigrationCell cell(List<AEKey> keys) {
        var contents = new Object2LongLinkedOpenHashMap<AEKey>();
        for (int i = 0; i < keys.size(); i++) contents.put(keys.get(i), i + 1L);
        var cell = mock(IECOStorageMigrationCell.class);
        when(cell.isInfiniteStorageEligible()).thenReturn(true);
        when(cell.migrationEntries()).thenAnswer(ignored -> contents.object2LongEntrySet().iterator());
        return cell;
    }

    private static final class Source {
        final UUID migration = UUID.randomUUID();
        final Object stack = new Object();
        final IECOStorageMigrationCell cell;
        final AtomicInteger completed = new AtomicInteger();
        final long position;

        Source(List<AEKey> keys, long position) {
            this.cell = cell(keys);
            this.position = position;
        }

        ECOInfiniteStorageTransfer.StepResult step(ECOInfiniteStorageTransfer transfer, ECOInfiniteStorageEngine engine,
                                                   ECOInfiniteStorageTransfer.Budget budget) {
            return transfer.step(migration, stack, cell, engine, REGISTRIES, () -> {}, completed::incrementAndGet,
                (key, tag, amount) -> ECOInfiniteStorageTransfer.legacyReceipt(DOMAIN, "to-domain", position, tag, amount),
                budget);
        }
    }

    @Test void timeBudgetStopsAtTheDeadlineButEveryTickAdvancesAtLeastOneType() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        var source = new Source(keys(40, 0), 1L);
        var transfer = new ECOInfiniteStorageTransfer();
        var now = new AtomicLong();
        // Every clock read advances 1 µs; a 5 µs budget therefore admits a handful of types per tick.
        var clock = (java.util.function.LongSupplier) () -> now.getAndAdd(1_000L);
        int ticks = 0;
        double last = 0;
        ECOInfiniteStorageTransfer.StepResult result;
        do {
            var budget = new ECOInfiniteStorageTransfer.Budget(clock, 5_000L, 512);
            result = source.step(transfer, engine, budget);
            assertTrue(budget.used() >= 1, "each tick advances at least one type");
            assertTrue(budget.used() <= 5, "the deadline bounds a tick: " + budget.used());
            if (result.state() == ECOInfiniteStorageTransfer.State.IN_PROGRESS) {
                double progress = transfer.progress(source.migration);
                assertTrue(progress > last, "progress is strictly increasing while types are processed");
                last = progress;
            }
            ticks++;
        } while (result.state() == ECOInfiniteStorageTransfer.State.IN_PROGRESS && ticks < 100);
        assertEquals(ECOInfiniteStorageTransfer.State.COMPLETED, result.state());
        assertEquals(1, source.completed.get());

        // A zero budget still moves exactly one type per tick.
        var slow = new Source(keys(3, 50), 2L);
        for (int tick = 1; tick <= 3; tick++) {
            var budget = new ECOInfiniteStorageTransfer.Budget(clock, 0L, 512);
            result = slow.step(transfer, engine, budget);
            assertEquals(1, budget.used());
            if (tick < 3) assertEquals(ECOInfiniteStorageTransfer.State.IN_PROGRESS, result.state());
        }
        assertEquals(ECOInfiniteStorageTransfer.State.COMPLETED, result.state());
    }

    @Test void typeCapIsSharedAcrossSourcesThatCompleteInOrder() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        List<AEKey> firstKeys = keys(3, 0);
        var first = new Source(firstKeys, 1L);
        var second = new Source(keys(5, 10), 2L);
        var transfer = new ECOInfiniteStorageTransfer();
        var budget = new ECOInfiniteStorageTransfer.Budget(() -> 0L, Long.MAX_VALUE / 4, 5);
        assertEquals(ECOInfiniteStorageTransfer.State.COMPLETED, first.step(transfer, engine, budget).state());
        assertEquals(ECOInfiniteStorageTransfer.State.IN_PROGRESS, second.step(transfer, engine, budget).state());
        assertEquals(5, budget.used());
        assertEquals(0.4D, transfer.progress(second.migration), 1e-9);
        assertFalse(budget.hasRemaining());

        budget = new ECOInfiniteStorageTransfer.Budget(() -> 0L, Long.MAX_VALUE / 4, 5);
        assertEquals(ECOInfiniteStorageTransfer.State.COMPLETED, second.step(transfer, engine, budget).state());
        assertEquals(3, budget.used());
        assertEquals(1, first.completed.get());
        assertEquals(1, second.completed.get());
        long expected = 0;
        for (AEKey key : firstKeys) expected += engine.getAmount(key).toLongSaturated();
        assertEquals(1 + 2 + 3, expected);
    }

    @Test void receiptsMatchThePersistedLegacyFormulaAndEachTagIsSerializedOnce() {
        var key = key(Items.STONE);
        var engine = mock(ECOInfiniteStorageEngine.class);
        List<Set<UUID>> receipts = new ArrayList<>();
        when(engine.insertOnce(anySet(), any(AEKey.class), anyLong())).thenAnswer(call -> {
            receipts.add(Set.copyOf(call.<Set<UUID>>getArgument(0)));
            return call.<Long>getArgument(2);
        });
        when(engine.commit()).thenReturn(new ECOInfiniteStorageEngine.CommitResult(true, 1, null));
        var source = new Source(List.of(key), 0x1234_5678_9ABCL);
        var result = source.step(new ECOInfiniteStorageTransfer(), engine,
            new ECOInfiniteStorageTransfer.Budget(() -> 0L, 1_000_000L, 512));
        assertEquals(ECOInfiniteStorageTransfer.State.COMPLETED, result.state());
        verify(key, times(1)).toTagGeneric(any());

        CompoundTag tag = key.toTagGeneric(REGISTRIES);
        long amount = 1L;
        // The formulas below are the pre-refactoring code, copied verbatim.
        UUID legacy = UUID.nameUUIDFromBytes((DOMAIN + ":" + "to-domain" + ":" + source.position + ":"
            + tag + ":" + amount).getBytes(StandardCharsets.UTF_8));
        UUID sealed = UUID.nameUUIDFromBytes((source.migration + ":" + tag).getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of(Set.of(legacy, sealed)), receipts);
        assertEquals(legacy, ECOInfiniteStorageTransfer.legacyReceipt(DOMAIN, "to-domain", source.position,
            String.valueOf(tag), amount));
        assertEquals(sealed, ECOInfiniteStorageTransfer.sealedReceipt(source.migration, String.valueOf(tag)));
    }

    @Test void blockedDomainsReportTheirReasonWithoutConsumingTheSource() {
        var stone = key(Items.STONE);
        var transfer = new ECOInfiniteStorageTransfer();
        var budget = new ECOInfiniteStorageTransfer.Budget(() -> 0L, 1_000_000L, 512);

        var snapshot = ECOInfiniteStorageData.createNew().save(new CompoundTag(), REGISTRIES);
        var undecodable = new CompoundTag();
        undecodable.put("key", new CompoundTag());
        undecodable.put("amount", new CompoundTag());
        ListTag entries = new ListTag();
        entries.add(undecodable);
        entries.add(undecodable.copy());
        snapshot.put("entries", entries);
        var raw = ECOInfiniteStorageData.load(snapshot, REGISTRIES);
        var source = new Source(List.of(stone), 1L);
        var result = source.step(transfer, new SavedDataInfiniteStorageEngine(raw), budget);
        assertEquals(ECOInfiniteStorageTransfer.State.BLOCKED, result.state());
        assertEquals(ECOInfiniteStorageEngine.MigrationBlock.Kind.UNDECODABLE_ENTRIES, result.block().kind());
        assertEquals("2", result.block().detail());
        assertEquals(0, source.completed.get());

        var recovery = ECOInfiniteStorageData.createNew();
        recovery.markIncompleteLegacy(List.of("legacy fixture"));
        result = source.step(transfer, new SavedDataInfiniteStorageEngine(recovery), budget);
        assertEquals(ECOInfiniteStorageEngine.MigrationBlock.Kind.RECOVERY_READ_ONLY, result.block().kind());
        assertEquals("legacy fixture", result.block().detail());

        var unreadable = ECOInfiniteStorageData.createNew();
        unreadable.markUnreadable("broken fixture");
        result = source.step(transfer, new SavedDataInfiniteStorageEngine(unreadable), budget);
        assertEquals(ECOInfiniteStorageEngine.MigrationBlock.Kind.UNREADABLE, result.block().kind());

        // A refused commit is a block too: the source is not cleared until the domain accepts it.
        var engine = mock(ECOInfiniteStorageEngine.class);
        when(engine.insertOnce(anySet(), any(AEKey.class), anyLong())).thenAnswer(call -> call.<Long>getArgument(2));
        when(engine.commit()).thenReturn(new ECOInfiniteStorageEngine.CommitResult(false, 0, "disk full"),
            new ECOInfiniteStorageEngine.CommitResult(true, 1, null));
        result = source.step(transfer, engine, budget);
        assertEquals(ECOInfiniteStorageEngine.MigrationBlock.Kind.COMMIT_FAILED, result.block().kind());
        assertEquals("disk full", result.block().detail());
        assertEquals(0, source.completed.get());
        assertEquals(ECOInfiniteStorageTransfer.State.COMPLETED, source.step(transfer, engine, budget).state());
        assertEquals(1, source.completed.get());

        // A rejected insert keeps the cursor on that type until the domain accepts it.
        var rejecting = mock(ECOInfiniteStorageEngine.class);
        when(rejecting.insertOnce(anySet(), any(AEKey.class), anyLong())).thenReturn(0L, 1L);
        when(rejecting.commit()).thenReturn(new ECOInfiniteStorageEngine.CommitResult(true, 1, null));
        var retried = new Source(List.of(stone), 2L);
        result = retried.step(transfer, rejecting, budget);
        assertEquals(ECOInfiniteStorageEngine.MigrationBlock.Kind.INSERT_REJECTED, result.block().kind());
        assertEquals(ECOInfiniteStorageTransfer.State.COMPLETED, retried.step(transfer, rejecting, budget).state());
        verify(rejecting, times(2)).insertOnce(anySet(), eq(stone), eq(1L));
    }

    @Test void migrationBlockMirrorsMigrationAdmission() {
        var data = ECOInfiniteStorageData.createNew();
        assertNull(data.migrationBlock());
        assertTrue(data.canMigrate());
        data.markIncompleteLegacy(List.of("x"));
        assertFalse(data.canMigrate());
        assertNotNull(data.migrationBlock());
        var engine = new SavedDataInfiniteStorageEngine(data);
        assertEquals(0, engine.insertOnce(Set.of(UUID.randomUUID()), AEItemKey.of(Items.STONE), 1));
    }
}
