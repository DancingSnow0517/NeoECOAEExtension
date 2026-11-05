package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TerminalInventoryChangesTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void viewersAndSlowReadersKeepIndependentBaselinesWithoutChangingSharedSnapshots() {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var first = new TerminalInventoryChanges();
        var second = new TerminalInventoryChanges();
        var initial = counter(Map.of(stone, 10L, dirt, 20L));
        var next = counter(Map.of(stone, 11L, iron, 30L));
        first.between(new KeyCounter(), initial);
        second.between(new KeyCounter(), initial);
        assertEquals(Set.of(stone, dirt, iron), first.between(initial, next));
        assertEquals(Map.of(stone, 10L, dirt, 20L), contents(initial));
        assertEquals(Map.of(stone, 11L, iron, 30L), contents(next));
        var latest = counter(Map.of(stone, 5L, dirt, 40L));
        first.between(next, latest);
        assertEquals(Set.of(stone, dirt), second.between(initial, latest));
        assertEquals(Map.of(stone, 10L, dirt, 20L), contents(initial));
        assertEquals(Map.of(stone, 11L, iron, 30L), contents(next));
        assertEquals(Map.of(stone, 5L, dirt, 40L), contents(latest));
    }

    @Test void unchangedInventoryHasNoChangesAndZeroEntriesDoNotPreventRemovals() {
        var key = AEItemKey.of(Items.STONE);
        var changes = new TerminalInventoryChanges();
        var initial = counter(Map.of(key, 10L));
        changes.between(new KeyCounter(), initial);
        assertTrue(changes.between(initial, initial).isEmpty());
        assertTrue(changes.changedKeys().isEmpty());
        var empty = counter(Map.of(key, 0L));
        assertEquals(Set.of(key), changes.between(initial, empty));
        assertEquals(Set.of(key), changes.changedKeys());
        assertTrue(changes.between(empty, new KeyCounter()).isEmpty());
        assertTrue(changes.changedKeys().isEmpty());
    }

    @Test void differenceDoesNotReachMutationHooksUsedByCell4ForGlobalStock() {
        var key = AEItemKey.of(Items.STONE);
        var previous = spy(counter(Map.of(key, Long.MAX_VALUE)));
        var current = spy(counter(Map.of(key, 13L)));
        var changes = new TerminalInventoryChanges();
        assertEquals(Set.of(key), changes.between(previous, current));
        assertEquals(Set.of(key), changes.between(current, previous));
        for (KeyCounter counter : new KeyCounter[] {previous, current}) {
            verify(counter, never()).set(any(), anyLong());
            verify(counter, never()).add(any(), anyLong());
            verify(counter, never()).remove(any(), anyLong());
            verify(counter, never()).removeAll(any());
            verify(counter, never()).removeZeros();
            verify(counter, never()).clear();
        }
        assertEquals(Long.MAX_VALUE, previous.get(key));
        assertEquals(13, current.get(key));
    }

    @Test void aReusedMutableSourceStillComparesAgainstItsLastObservedAmounts() {
        var key = AEItemKey.of(Items.STONE);
        var inventory = counter(Map.of(key, 10L));
        var changes = new TerminalInventoryChanges();
        changes.between(new KeyCounter(), inventory);
        inventory.set(key, 13);
        assertEquals(Set.of(key), changes.between(inventory, inventory));
        assertEquals(13, inventory.get(key));
    }

    @Test void replacedBaselineAndEqualKeysUseValueEqualityAndNeverTruncateLongAmounts() {
        var key = AEItemKey.of(new ItemStack(Items.STONE));
        var equalKey = AEItemKey.of(new ItemStack(Items.STONE));
        var changes = new TerminalInventoryChanges();
        var first = counter(Map.of(key, Long.MAX_VALUE));
        changes.between(new KeyCounter(), first);
        assertEquals(Set.of(key), changes.between(first, counter(Map.of(equalKey, 1L))));
        var reset = counter(Map.of(key, 12L));
        assertEquals(Set.of(key), changes.between(reset, counter(Map.of(equalKey, 5L))));
        assertEquals(12, reset.get(key));
        assertThrows(UnsupportedOperationException.class, () -> changes.changedKeys().clear());
    }

    @Test void variedUpdatesMatchVanillaChangedKeysAcrossReinitialization() {
        AEKey[] keys = {AEItemKey.of(Items.STONE), AEItemKey.of(Items.DIRT), AEItemKey.of(Items.GOLD_INGOT), AEItemKey.of(Items.APPLE)};
        var changes = new TerminalInventoryChanges();
        KeyCounter previous = new KeyCounter();
        Random random = new Random(814293);
        for (int tick = 0; tick < 1000; tick++) {
            if (tick % 37 == 0) previous = counter(contents(previous));
            var current = new KeyCounter();
            for (AEKey key : keys) {
                long amount = tick % 11 == 0 ? Long.MAX_VALUE : random.nextInt(100);
                if (random.nextBoolean()) current.set(key, amount);
            }
            var expected = counter(contents(previous));
            expected.removeAll(current);
            expected.removeZeros();
            var before = contents(previous);
            var after = contents(current);
            Set<AEKey> actual = changes.between(previous, current);
            assertEquals(expected.keySet(), actual, "tick " + tick);
            assertEquals(expected.keySet(), changes.changedKeys());
            assertEquals(before, contents(previous));
            assertEquals(after, contents(current));
            previous = current;
        }
    }

    private static KeyCounter counter(Map<? extends AEKey, Long> values) {
        var result = new KeyCounter();
        values.forEach(result::set);
        return result;
    }

    private static Map<AEKey, Long> contents(KeyCounter counter) {
        var result = new HashMap<AEKey, Long>();
        for (var entry : counter) result.put(entry.getKey(), entry.getLongValue());
        return result;
    }
}
