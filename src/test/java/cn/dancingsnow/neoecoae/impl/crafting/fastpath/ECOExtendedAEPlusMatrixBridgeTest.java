package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.ECOExtendedAEPlusMatrixBridge;
import org.junit.jupiter.api.Test;

class ECOExtendedAEPlusMatrixBridgeTest {
    @Test
    void multipliesEachInputSlotWithoutFlatteningTheMolecularGrid() {
        var first = FastPathTestKey.of("first");
        var second = FastPathTestKey.of("second");
        var source = new KeyCounter[] {counter(first, 2L), counter(second, 3L), new KeyCounter()};

        var multiplied = ECOExtendedAEPlusMatrixBridge.multiplyInputHolder(source, 7L);

        assertEquals(14L, multiplied[0].get(first));
        assertEquals(21L, multiplied[1].get(second));
        assertEquals(3, multiplied.length);
        assertEquals(0L, multiplied[2].get(first));
    }

    @Test
    void rejectsInputTableMultiplicationOverflowBeforeDispatch() {
        var source = new KeyCounter[] {counter(FastPathTestKey.of("large"), Long.MAX_VALUE)};

        assertNull(ECOExtendedAEPlusMatrixBridge.multiplyInputHolder(source, 2L));
    }

    private static KeyCounter counter(FastPathTestKey key, long amount) {
        var counter = new KeyCounter();
        counter.add(key, amount);
        return counter;
    }
}
