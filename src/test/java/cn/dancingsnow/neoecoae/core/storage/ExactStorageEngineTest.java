package cn.dancingsnow.neoecoae.core.storage;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExactStorageEngineTest {
    @Test void genericKeysPromoteDowngradeAndListExactlyAgainstReference() {
        var engine = new ExactStorageEngine<String>();
        Map<String, BigInteger> expected = new HashMap<>();
        Random random = new Random(114415);
        for (int i = 0; i < 3000; i++) {
            String key = "channel-" + random.nextInt(5);
            BigInteger before = expected.getOrDefault(key, BigInteger.ZERO);
            long amount = i % 11 == 0 ? Long.MAX_VALUE : random.nextInt(10_000) + 1;
            if (random.nextBoolean()) {
                engine.add(key, amount);
                expected.put(key, before.add(BigInteger.valueOf(amount)));
            } else {
                long extracted = before.min(BigInteger.valueOf(amount)).longValueExact();
                engine.subtract(key, extracted);
                BigInteger after = before.subtract(BigInteger.valueOf(extracted));
                if (after.signum() == 0) expected.remove(key); else expected.put(key, after);
            }
            assertEquals(expected.getOrDefault(key, BigInteger.ZERO), engine.exact(key));
            assertEquals(engine.exact(key).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(), engine.visible(key));
        }
        Map<String, BigInteger> actual = new HashMap<>();
        engine.visitExact(actual::put);
        assertEquals(expected, actual);
        for (String key : expected.keySet()) engine.set(key, BigInteger.ZERO);
        assertTrue(engine.isEmpty());
        assertFalse(engine.hasOverflow());
    }

    @Test void zeroAndRejectedMutationsCannotCreatePhantomOrNegativeAccounts() {
        var engine = new ExactStorageEngine<Integer>();
        engine.add(1, 0L);
        engine.subtract(1, 0L);
        assertTrue(engine.isEmpty());
        engine.set(1, BigInteger.TEN);
        assertThrows(IllegalArgumentException.class, () -> engine.add(1, -1L));
        assertThrows(IllegalArgumentException.class, () -> engine.add(1, BigInteger.ONE.negate()));
        assertThrows(IllegalArgumentException.class, () -> engine.set(1, BigInteger.ONE.negate()));
        assertThrows(IllegalArgumentException.class, () -> engine.subtract(1, 11L));
        assertEquals(BigInteger.TEN, engine.exact(1));
    }

    @Test void overflowCanDowngradeToLongAndThenEmptyWithoutStaleSideEntries() {
        var engine = new ExactStorageEngine<String>();
        engine.add("water", Long.MAX_VALUE);
        engine.add("water", 9L);
        assertTrue(engine.hasOverflow());
        engine.subtract("water", Long.MAX_VALUE);
        assertFalse(engine.hasOverflow());
        assertEquals(9L, engine.visible("water"));
        engine.subtract("water", 9L);
        assertTrue(engine.isEmpty());
    }

    @Test void incrementalExactStatisticsHandleBothDeltaOverflowDirections() {
        var total = new ExactStorageTotal();
        BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);
        total.add(max.multiply(BigInteger.valueOf(4)));
        for (int i = 0; i < 4; i++) total.subtract(Long.MAX_VALUE);
        assertEquals(BigInteger.ZERO, total.snapshot());
        for (int i = 0; i < 4; i++) total.add(Long.MAX_VALUE);
        assertEquals(max.multiply(BigInteger.valueOf(4)), total.snapshot());
        total.subtract(max.multiply(BigInteger.valueOf(4)).subtract(BigInteger.ONE));
        assertEquals(BigInteger.ONE, total.snapshot());
    }

    @Test void wideStatisticsRejectOverdraftBeforeMutatingThePendingDelta() {
        var total = new ExactStorageTotal();
        total.add(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        total.subtract(Long.MAX_VALUE);
        // Do not snapshot here: the wide base and negative pending delta must both be checked.
        assertThrows(IllegalArgumentException.class, () -> total.subtract(2L));
        assertEquals(BigInteger.ONE, total.snapshot());
        assertThrows(IllegalArgumentException.class, () -> total.add(BigInteger.ONE.negate()));
        assertThrows(IllegalArgumentException.class, () -> total.subtract(BigInteger.ONE.negate()));
        assertEquals(BigInteger.ONE, total.snapshot());
        total.subtract(1L);
        assertEquals(BigInteger.ZERO, total.snapshot());
    }

    @Test void pendingWideStatisticsMatchReferenceAcrossUnsignedDebitBoundary() {
        var total = new ExactStorageTotal();
        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(5));
        total.add(expected);
        for (int i = 0; i < 5; i++) total.subtract(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> total.subtract(1L));
        assertEquals(BigInteger.ZERO, total.snapshot());
    }
}
