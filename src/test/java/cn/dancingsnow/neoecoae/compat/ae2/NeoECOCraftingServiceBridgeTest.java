package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class NeoECOCraftingServiceBridgeTest {

    @Test
    void rejectsIncompletePlanBeforeAllocatingEcoCpu() {
        ICraftingPlan incompletePlan = (ICraftingPlan) Proxy.newProxyInstance(
                ICraftingPlan.class.getClassLoader(), new Class<?>[] {ICraftingPlan.class}, (proxy, method, args) -> {
                    if (method.getName().equals("simulation")) {
                        return true;
                    }
                    throw new AssertionError("Incomplete plan submission accessed " + method.getName());
                });

        var result = NeoECOCraftingServiceBridge.submitJob(null, incompletePlan, null, null, null);

        assertSame(CraftingSubmitResult.INCOMPLETE_PLAN, result);
    }
}
