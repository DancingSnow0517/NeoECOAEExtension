package cn.dancingsnow.neoecoae.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOInfiniteStorageMigrationBudgetTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void budgetStopsAtTheDeadlineButAlwaysMovesOneResource() {
        var engine = new FakeEngine();
        var cell = cell(keys(40, "budget"));
        var transfer = new ECOInfiniteStorageTransfer();
        UUID migration = UUID.randomUUID();
        AtomicLong clock = new AtomicLong();
        // Each clock read advances 100 ns; a 1000 ns budget admits only a handful of resources.
        var budget = new ECOInfiniteStorageTransfer.Budget(() -> clock.addAndGet(100), 1_000, 512);
        var result = transfer.migrate(migration, "source", cell, engine, () -> {}, () -> {}, (k, e, a) -> UUID.randomUUID(), budget);
        assertEquals(ECOInfiniteStorageTransfer.Outcome.IN_PROGRESS, result.outcome());
        assertTrue(budget.used() > 0 && budget.used() < 40, "used " + budget.used());
        assertEquals(budget.used(), engine.stored.size());

        // A tick that starts past its deadline still migrates exactly one resource.
        int before = engine.stored.size();
        var exhausted = new ECOInfiniteStorageTransfer.Budget(() -> clock.addAndGet(1_000_000), 0, 512);
        assertEquals(ECOInfiniteStorageTransfer.Outcome.IN_PROGRESS,
            transfer.migrate(migration, "source", cell, engine, () -> {}, () -> {}, (k, e, a) -> UUID.randomUUID(), exhausted).outcome());
        assertEquals(before + 1, engine.stored.size());
    }

    @Test void typeLimitCapsEachTickAndProgressIsMonotonic() {
        var engine = new FakeEngine();
        var contents = keys(10, "limit");
        var cell = cell(contents);
        var transfer = new ECOInfiniteStorageTransfer();
        UUID migration = UUID.randomUUID();
        int[] completed = {0};
        double last = 0;
        int ticks = 0;
        ECOInfiniteStorageTransfer.StepResult result;
        do {
            var budget = new ECOInfiniteStorageTransfer.Budget(() -> 0L, Long.MAX_VALUE / 4, 3);
            result = transfer.migrate(migration, "source", cell, engine, () -> {}, () -> completed[0]++,
                (k, e, a) -> UUID.randomUUID(), budget);
            assertTrue(budget.used() <= 3);
            double progress = result.complete() ? 1.0 : transfer.progress(migration);
            assertTrue(progress >= last, "progress went backwards");
            last = progress;
            ticks++;
        } while (!result.complete());
        assertEquals(4, ticks);
        assertEquals(1, completed[0]);
        contents.forEach((key, amount) -> assertEquals(amount.longValue(), engine.stored.getOrDefault(key, 0L)));
    }

    @Test void drivesCompleteInOrderAndShareOneTickBudget() {
        var engine = new FakeEngine();
        var first = cell(keys(5, "first"));
        var second = cell(keys(5, "second"));
        var transfer = new ECOInfiniteStorageTransfer();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<String> completions = new ArrayList<>();
        // Seven resources per tick: tick 1 finishes the first disk and starts the second one in the same tick.
        var budget = new ECOInfiniteStorageTransfer.Budget(() -> 0L, Long.MAX_VALUE / 4, 7);
        var r1 = transfer.migrate(a, "a", first, engine, () -> {}, () -> completions.add("a"), (k, e, x) -> UUID.randomUUID(), budget);
        assertTrue(r1.complete());
        var r2 = transfer.migrate(b, "b", second, engine, () -> {}, () -> completions.add("b"), (k, e, x) -> UUID.randomUUID(), budget);
        assertEquals(ECOInfiniteStorageTransfer.Outcome.IN_PROGRESS, r2.outcome());
        assertEquals(0.4, transfer.progress(b), 1e-9);
        assertEquals(List.of("a"), completions);
        var next = new ECOInfiniteStorageTransfer.Budget(() -> 0L, Long.MAX_VALUE / 4, 7);
        assertTrue(transfer.migrate(b, "b", second, engine, () -> {}, () -> completions.add("b"),
            (k, e, x) -> UUID.randomUUID(), next).complete());
        assertEquals(List.of("a", "b"), completions);
    }

    @Test void blockedMigrationReportsItsReasonAndKeepsTheSource() {
        var engine = new FakeEngine();
        engine.block = new ECOInfiniteStorageEngine.MigrationBlock("undecodable", "3");
        var cell = cell(keys(2, "blocked"));
        var transfer = new ECOInfiniteStorageTransfer();
        UUID migration = UUID.randomUUID();
        boolean[] completed = {false};
        var result = transfer.migrate(migration, "s", cell, engine, () -> {}, () -> completed[0] = true,
            (k, e, a) -> UUID.randomUUID(), new ECOInfiniteStorageTransfer.Budget(() -> 0L, 1_000_000, 512));
        assertTrue(result.blocked());
        assertEquals("undecodable", result.block().code());
        assertEquals("gui.neoecoae.storage.migration.blocked.undecodable", result.block().translationKey());
        assertTrue(result.block().message().contains("3"));
        assertFalse(completed[0]);

        // A failed commit is reported too, and nothing is completed before it succeeds.
        engine.block = null;
        engine.commitFails = true;
        result = transfer.migrate(migration, "s", cell, engine, () -> {}, () -> completed[0] = true,
            (k, e, a) -> UUID.randomUUID(), new ECOInfiniteStorageTransfer.Budget(() -> 0L, 1_000_000, 512));
        assertTrue(result.blocked());
        assertEquals("commit_failed", result.block().code());
        assertFalse(completed[0]);
        engine.commitFails = false;
        assertTrue(transfer.migrate(migration, "s", cell, engine, () -> {}, () -> completed[0] = true,
            (k, e, a) -> UUID.randomUUID(), new ECOInfiniteStorageTransfer.Budget(() -> 0L, 1_000_000, 512)).complete());
        assertTrue(completed[0]);
    }

    @Test void domainReportsSpecificBlockReasons() {
        var healthy = ECOInfiniteStorageData.createNew();
        assertNull(healthy.migrationBlock());
        var readOnly = ECOInfiniteStorageData.createNew();
        readOnly.markIncompleteLegacy(List.of("legacy entry"));
        assertEquals("read_only", readOnly.migrationBlock().code());
        assertEquals("legacy entry", readOnly.migrationBlock().detail());
        var unreadable = ECOInfiniteStorageData.createNew();
        unreadable.markUnreadable("bad file");
        assertEquals("unreadable", new SavedDataInfiniteStorageEngine(unreadable).migrationBlock().code());
    }

    @Test void receiptsAreByteIdenticalToTheOriginalFormulas() {
        var cellContents = keys(3, "receipt");
        var engine = new FakeEngine();
        var cell = cell(cellContents);
        UUID migration = UUID.randomUUID();
        Map<AEKey, String> encodedSeen = new HashMap<>();
        new ECOInfiniteStorageTransfer().migrate(migration, "s", cell, engine, () -> {}, () -> {},
            (key, encoded, amount) -> {
                encodedSeen.put(key, encoded);
                return UUID.nameUUIDFromBytes(("legacy:" + key.toTagGeneric() + ":" + amount).getBytes(StandardCharsets.UTF_8));
            },
            new ECOInfiniteStorageTransfer.Budget(() -> 0L, 1_000_000, 512));
        for (AEKey key : cellContents.keySet()) {
            assertEquals(key.toTagGeneric().toString(), encodedSeen.get(key));
            UUID oldSealed = UUID.nameUUIDFromBytes((migration + ":" + key.toTagGeneric()).getBytes(StandardCharsets.UTF_8));
            UUID oldLegacy = UUID.nameUUIDFromBytes(("legacy:" + key.toTagGeneric() + ":" + cellContents.get(key))
                .getBytes(StandardCharsets.UTF_8));
            assertEquals(oldSealed, ECOInfiniteStorageTransfer.sealedReceipt(migration, ECOInfiniteStorageTransfer.encodedKey(key)));
            assertEquals(Set.of(oldLegacy, oldSealed), engine.receipts.get(key));
        }
    }

    private static Map<AEKey, Long> keys(int count, String salt) {
        Map<AEKey, Long> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            ItemStack stack = new ItemStack(Items.STONE);
            stack.getOrCreateTag().putString("salt", salt);
            stack.getOrCreateTag().putInt("index", i);
            result.put(AEItemKey.of(stack), (long) (i + 1) * 7);
        }
        return result;
    }

    private static IECOStorageMigrationCell cell(Map<AEKey, Long> contents) {
        var cell = mock(IECOStorageMigrationCell.class);
        when(cell.isInfiniteStorageEligible()).thenReturn(true);
        var map = new Object2LongLinkedOpenHashMap<AEKey>();
        contents.forEach(map::put);
        when(cell.migrationEntries()).thenAnswer(call -> map.object2LongEntrySet().iterator());
        return cell;
    }

    private static final class FakeEngine implements ECOInfiniteStorageEngine {
        final Map<AEKey, Long> stored = new HashMap<>();
        final Map<AEKey, Set<UUID>> receipts = new HashMap<>();
        final Set<UUID> seen = new HashSet<>();
        MigrationBlock block;
        boolean commitFails;

        @Override public long insertOnce(Set<UUID> ids, AEKey key, long amount) {
            if (block != null) return 0;
            receipts.put(key, Set.copyOf(ids));
            if (ids.stream().anyMatch(seen::contains)) return amount;
            seen.addAll(ids);
            stored.merge(key, amount, Long::sum);
            return amount;
        }
        @Override public MigrationBlock migrationBlock() { return block; }
        @Override public boolean flush() { return true; }
        @Override public CommitResult commit() { return new CommitResult(!commitFails, 0, commitFails ? "denied" : null); }
        @Override public long insert(AEKey key, long amount, Actionable mode) { return 0; }
        @Override public BigInteger insert(AEKey key, BigInteger amount, Actionable mode) { return BigInteger.ZERO; }
        @Override public BigInteger usedBytes() { return BigInteger.ZERO; }
        @Override public long extract(AEKey key, long amount, Actionable mode) { return 0; }
        @Override public HugeAmount getAmount(AEKey key) { return HugeAmount.of(stored.getOrDefault(key, 0L)); }
        @Override public void getAvailableStacks(KeyCounter out) { stored.forEach(out::add); }
        @Override public boolean isEmpty() { return stored.isEmpty(); }
        @Override public Collection<TypeStats> getTypeStats() { return List.of(); }
        @Override public boolean hasHugeStacks() { return false; }
        @Override public boolean reserveRestores(Map<AEKey, UUID> t, Set<UUID> s, Map<AEKey, Map<UUID, RestoreTargetAmounts>> p) { return false; }
        @Override public Map<UUID, RestoreTargetAmounts> restorePlan(AEKey key) { return Map.of(); }
        @Override public boolean finishRestores(Map<AEKey, UUID> transactions) { return false; }
    }
}
