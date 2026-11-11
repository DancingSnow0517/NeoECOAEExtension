package cn.dancingsnow.neoecoae.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.Random;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InfiniteStorageAmountsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void existingAndSimulatedKeysNeverReenterTheFactory() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        var key = AEItemKey.of(Items.STONE);
        var equal = AEItemKey.of(Items.STONE);
        try (var factory = mockStatic(ECOKeyFactory.class)) {
            factory.when(() -> ECOKeyFactory.canonicalize(key)).thenReturn(key);
            assertEquals(10, engine.insert(key, 10, Actionable.SIMULATE));
            assertFalse(engine.contains(key));
            factory.verifyNoInteractions();
            engine.insert(key, 10, Actionable.MODULATE);
            for (int i = 0; i < 100; i++) {
                assertTrue(engine.contains(equal));
                engine.insert(equal, 1, Actionable.MODULATE);
                assertEquals(1, engine.extract(key, 1, Actionable.MODULATE));
                assertEquals(HugeAmount.of(10), engine.getAmount(equal));
            }
            factory.verify(() -> ECOKeyFactory.canonicalize(key), times(1));
            factory.verifyNoMoreInteractions();
        }
    }

    @Test void mutableWideUpdatesCrossEveryBoundaryAndPreservePublishedValues() {
        var key = AEItemKey.of(Items.STONE);
        var values = new InfiniteStorageAmounts();
        var random = new Random(912731);
        for (int bits : new int[] {63, 64, 65, 100, 125, 126, 127, 128, 129, 160, 192, 256, 1024, 4096}) {
            BigInteger expected = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
            values.set(key, HugeAmount.of(expected));
            HugeAmount published = values.get(key);
            for (int i = 0; i < 200; i++) {
                long delta = i % 11 == 0 ? Long.MAX_VALUE : random.nextInt(1_000_000) + 1;
                values.add(key, delta);
                expected = expected.add(BigInteger.valueOf(delta));
                assertEquals(expected, values.get(key).toBigInteger());
                values.subtract(key, delta);
                expected = expected.subtract(BigInteger.valueOf(delta));
                assertEquals(expected, values.get(key).toBigInteger());
                assertEquals(expected, published.toBigInteger(), "old snapshots are immutable");
            }
        }
        values.set(key, HugeAmount.of(BigInteger.ONE.shiftLeft(64)));
        values.subtract(key, Long.MAX_VALUE);
        values.subtract(key, Long.MAX_VALUE);
        assertEquals(HugeAmount.of(2), values.get(key));
        assertFalse(values.hasOverflow());
        values.subtract(key, 2);
        assertTrue(values.isEmpty());
        values.touch();
        values.add(AEItemKey.of(Items.STONE), 5);
        assertEquals(5, values.visible(key));
    }
}
