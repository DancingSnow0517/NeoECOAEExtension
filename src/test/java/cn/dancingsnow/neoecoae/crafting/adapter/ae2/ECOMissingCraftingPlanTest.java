package cn.dancingsnow.neoecoae.crafting.adapter.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.KeyCounter;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOMissingCraftingPlanTest {
    @Test
    void onlyTurnsSimulationIntoAnExecutablePlan() {
        KeyCounter used = new KeyCounter();
        KeyCounter emitted = new KeyCounter();
        KeyCounter missing = new KeyCounter();
        Map<IPatternDetails, Long> patterns = Map.of();
        ICraftingPlan delegate = (ICraftingPlan) Proxy.newProxyInstance(
                ICraftingPlan.class.getClassLoader(), new Class<?>[] {ICraftingPlan.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "finalOutput" -> null;
                        case "bytes" -> 42L;
                        case "simulation", "multiplePaths" -> true;
                        case "usedItems" -> used;
                        case "emittedItems" -> emitted;
                        case "missingItems" -> missing;
                        case "patternTimes" -> patterns;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });

        var plan = new ECOMissingCraftingPlan(delegate);

        assertSame(delegate, plan.delegate());
        assertNull(plan.finalOutput());
        assertEquals(42L, plan.bytes());
        assertFalse(plan.simulation());
        assertTrue(plan.multiplePaths());
        assertSame(used, plan.usedItems());
        assertSame(emitted, plan.emittedItems());
        assertSame(missing, plan.missingItems());
        assertSame(patterns, plan.patternTimes());
    }
}
