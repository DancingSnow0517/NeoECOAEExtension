package cn.dancingsnow.neoecoae.multiblock.cluster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NECraftingNetworkPowerTest {
    @Test
    void allWorkersShareOneVirtualPowerChargePerTick() {
        var network = new NECraftingNetworkCluster(null);
        var charges = new AtomicInteger();
        for (int worker = 0; worker < 88; worker++) {
            assertTrue(network.tryConsumeVirtualCraftingPower(10, amount -> {
                assertEquals(100.0, amount);
                charges.incrementAndGet();
                return true;
            }));
        }
        assertEquals(1, charges.get());
        assertFalse(network.tryConsumeVirtualCraftingPower(11, amount -> {
            charges.incrementAndGet();
            return false;
        }));
        assertFalse(network.tryConsumeVirtualCraftingPower(11, amount -> {
            fail("A failed tick must not charge again");
            return true;
        }));
        assertEquals(2, charges.get());
    }
}
