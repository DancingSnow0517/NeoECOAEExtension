package cn.dancingsnow.neoecoae.api.me.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ECOBatchCraftingHelperTest {
    @Test
    void fullEnergyFastPathUsesOneSimulation() {
        AtomicInteger simulations = new AtomicInteger();

        int result = ECOBatchCraftingHelper.maxAffordableCrafts(3.0D, 64, requested -> {
            simulations.incrementAndGet();
            return requested;
        });

        assertEquals(64, result);
        assertEquals(1, simulations.get());
    }

    @Test
    void constrainedEnergyFastPathUsesBinarySearch() {
        AtomicInteger simulations = new AtomicInteger();

        int result = ECOBatchCraftingHelper.maxAffordableCrafts(2.0D, 64, requested -> {
            simulations.incrementAndGet();
            return Math.min(requested, 20.0D);
        });

        assertEquals(10, result);
        assertEquals(7, simulations.get());
    }

    @Test
    void clampsUntrustedEnergySearchInputs() {
        AtomicInteger simulations = new AtomicInteger();

        int bounded = ECOBatchCraftingHelper.maxAffordableCrafts(1.0D, Integer.MAX_VALUE, requested -> {
            simulations.incrementAndGet();
            return requested;
        });
        int invalid = ECOBatchCraftingHelper.maxAffordableCrafts(Double.NaN, 64, requested -> {
            simulations.incrementAndGet();
            return requested;
        });

        assertEquals(ECOBatchCraftingHelper.MAX_BATCH_SIZE, bounded);
        assertEquals(0, invalid);
        assertEquals(1, simulations.get());
    }

    @Test
    void rejectsInvalidBatchRequestBoundariesBeforeDispatch() {
        IPatternDetails details = (IPatternDetails) Proxy.newProxyInstance(
            IPatternDetails.class.getClassLoader(),
            new Class<?>[] { IPatternDetails.class },
            (proxy, method, args) -> null
        );
        ECOFastPathKey key = ECOFastPathKey.of("request", new KeyCounter[0], null, 0L).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> new ECOBatchCraftingRequest(
            details, key, ECOBatchCraftingHelper.MAX_BATCH_SIZE + 1, List.of(), List.of(), List.of(), null
        ));
        assertThrows(IllegalArgumentException.class, () -> new ECOBatchCraftingRequest(
            details, key, 1, List.of(), List.of(), List.of(), null
        ));
    }
}
