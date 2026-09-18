package cn.dancingsnow.neoecoae.terminal.bigamount;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExactAmountComparatorTest {
    private static final int CONTAINER = 42;

    @AfterEach void clearCache() {
        ExactAmountClientCache.clear(CONTAINER);
    }

    @Test void sortsAdjacentHugeCountsAlongsideLongAndInfinityInBothDirections() {
        var ordinary = entry(1, Long.MAX_VALUE, 1);
        var huge = entry(2, Long.MAX_VALUE, 1);
        var larger = entry(3, Long.MAX_VALUE, 1);
        var infinite = entry(4, Long.MAX_VALUE, 1);
        var amount = BigInteger.TEN.pow(100);
        ExactAmountClientCache.replace(CONTAINER, Map.of(
            huge.getWhat(), ExactAmount.finite(amount),
            larger.getWhat(), ExactAmount.finite(amount.add(BigInteger.ONE)),
            infinite.getWhat(), ExactAmount.unbounded()));
        var entries = new ArrayList<>(List.of(larger, ordinary, infinite, huge));
        var comparator = ExactAmountComparator.ascending(CONTAINER);
        entries.sort(comparator);
        assertEquals(List.of(ordinary, huge, larger, infinite), entries);
        entries.sort(comparator.reversed());
        assertEquals(List.of(infinite, larger, huge, ordinary), entries);
        assertEquals(0, comparator.compare(infinite, infinite));
    }

    @Test void normalizesUnitsWithoutDiscardingFractionsOrHugePrecision() {
        var item = entry(1, Long.MAX_VALUE, 1);
        var fluid = entry(2, Long.MAX_VALUE, 1000);
        var amount = BigInteger.TEN.pow(100);
        var comparator = ExactAmountComparator.ascending(CONTAINER);
        ExactAmountClientCache.replace(CONTAINER, Map.of(
            item.getWhat(), ExactAmount.finite(amount),
            fluid.getWhat(), ExactAmount.finite(amount.multiply(BigInteger.valueOf(1000)))));
        assertEquals(0, comparator.compare(item, fluid));
        ExactAmountClientCache.replace(CONTAINER, Map.of(
            item.getWhat(), ExactAmount.finite(amount),
            fluid.getWhat(), ExactAmount.finite(amount.multiply(BigInteger.valueOf(1000)).add(BigInteger.ONE))));
        assertTrue(comparator.compare(item, fluid) < 0);
    }

    @Test void usesOrdinaryCountsForOtherContainersAndAfterExactDataIsRemoved() {
        var left = entry(1, 1, 1);
        var right = entry(2, 2, 1);
        var comparator = ExactAmountComparator.ascending(CONTAINER);
        long revision = ExactAmountClientCache.revision();
        ExactAmountClientCache.replace(CONTAINER, Map.of(left.getWhat(), ExactAmount.unbounded()));
        assertNotEquals(revision, ExactAmountClientCache.revision());
        assertTrue(comparator.compare(left, right) > 0);
        assertTrue(ExactAmountComparator.ascending(CONTAINER + 1).compare(left, right) < 0);
        ExactAmountClientCache.replace(CONTAINER, Map.of());
        assertTrue(comparator.compare(left, right) < 0);
        assertTrue(comparator.compare(entry(3, 0, 1), right) < 0);
    }

    private static GridInventoryEntry entry(long serial, long amount, int perUnit) {
        AEKey key = mock(AEKey.class);
        when(key.getAmountPerUnit()).thenReturn(perUnit);
        return new GridInventoryEntry(serial, key, amount, 0, false);
    }
}
