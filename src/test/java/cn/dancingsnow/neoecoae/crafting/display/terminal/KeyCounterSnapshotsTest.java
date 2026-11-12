package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeyCounterSnapshotsTest {
    // Unit tests do not run Mixin. Use the same directory interface over the real AE2 counter through reflection.
    private static final Field LISTS;
    static {
        try { LISTS = KeyCounter.class.getDeclaredField("lists"); LISTS.setAccessible(true); }
        catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }
    private record Access(KeyCounter counter) implements KeyCounterSnapshotAccess {
        @SuppressWarnings("unchecked")
        public Reference2ObjectMap<Object, ?> neoecoae$primaryMaps() {
            try { return (Reference2ObjectMap<Object, ?>) LISTS.get(counter); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        }
        public void neoecoae$primaryMaps(Reference2ObjectMap<Object, ?> value) {
            try { LISTS.set(counter, value); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        }
    }
    private static AEKey key(Object primary) {
        AEKey key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(primary);
        return key;
    }
    private static KeyCounter copy(KeyCounter current, KeyCounter previous, Set<AEKey> changed) {
        return KeyCounterSnapshots.copy(current, previous, changed, Access::new);
    }

    @Test void sparseSnapshotsShareUnchangedGroupsAndPreserveEveryOlderQuantity() {
        var working = new KeyCounter();
        var keys = new ArrayList<AEKey>();
        for (int i = 0; i < 64; i++) { var key = key(new Object()); keys.add(key); working.set(key, 10); }
        var first = copy(working, null, Set.copyOf(keys));
        AEKey hot = keys.getFirst();
        working.set(hot, 11);
        var second = copy(working, first, Set.of(hot));
        assertTrue(KeyCounterSnapshots.shared(new Access(second).neoecoae$primaryMaps()));
        assertEquals(10, first.get(hot));
        assertEquals(11, second.get(hot));
        Object coldPrimary = keys.get(1).getPrimaryKey();
        assertSame(new Access(first).neoecoae$primaryMaps().get(coldPrimary), new Access(second).neoecoae$primaryMaps().get(coldPrimary));
        assertNotSame(new Access(first).neoecoae$primaryMaps().get(hot.getPrimaryKey()), new Access(second).neoecoae$primaryMaps().get(hot.getPrimaryKey()));
        working.remove(hot);
        var third = copy(working, second, Set.of(hot));
        assertEquals(0, third.get(hot));
        assertEquals(11, second.get(hot));
        working.set(hot, 23);
        var fourth = copy(working, third, Set.of(hot));
        assertEquals(23, fourth.get(hot));
        assertEquals(0, third.get(hot));
        assertEquals(64, fourth.size());
        assertEquals(new HashSet<>(keys), fourth.keySet());
        assertSame(keys.get(1), fourth.findFuzzy(keys.get(1), FuzzyMode.IGNORE_ALL).iterator().next().getKey());
    }

    @Test void changingOneVariantPreservesTheWholeFuzzyGroupAndDenseUpdatesDetachEverything() {
        var working = new KeyCounter();
        var keys = new ArrayList<AEKey>();
        for (int i = 0; i < 32; i++) { var key = key(new Object()); keys.add(key); working.set(key, i + 1); }
        var firstVariant = keys.getFirst();
        var otherVariant = key(firstVariant.getPrimaryKey());
        working.set(otherVariant, 100);
        var first = copy(working, null, Set.copyOf(keys));
        working.set(firstVariant, 9);
        var second = copy(working, first, Set.of(firstVariant));
        assertEquals(2, second.findFuzzy(firstVariant, FuzzyMode.IGNORE_ALL).size());
        assertEquals(100, second.get(otherVariant));
        for (var key : keys) working.set(key, 42);
        var dense = copy(working, second, Set.copyOf(keys));
        assertEquals(9, second.get(firstVariant));
        assertEquals(42, dense.get(firstVariant));
        assertEquals(100, dense.get(otherVariant));
        var mutable = new KeyCounter();
        mutable.addAll(second);
        mutable.set(otherVariant, 5);
        assertEquals(100, second.get(otherVariant));
    }
}
