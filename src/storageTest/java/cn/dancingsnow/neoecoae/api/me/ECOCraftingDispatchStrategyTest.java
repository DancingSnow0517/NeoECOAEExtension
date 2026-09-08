package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingDispatchStrategyTest {
    @Test
    void ordinaryBudgetUsesThePreviousThreeAcceptedTicks() {
        var strategy = new ECOCraftingDispatchStrategy();

        assertEquals(2, strategy.beginTick(1, 2));
        strategy.finishTick(2);
        assertEquals(0, strategy.beginTick(1, 2));
        strategy.finishTick(0);
        assertEquals(0, strategy.beginTick(1, 2));
        strategy.finishTick(0);
        assertEquals(0, strategy.beginTick(1, 2));
        strategy.finishTick(0);
        assertEquals(2, strategy.beginTick(1, 2));
    }

    @Test
    void configuredLimitAndCpuParallelismBothBoundTheBudget() {
        var strategy = new ECOCraftingDispatchStrategy();

        assertEquals(3, strategy.beginTick(2, 100));
        strategy.finishTick(3);
        strategy.reset();
        assertEquals(1, strategy.beginTick(100, 1));
        strategy.finishTick(1);
        strategy.reset();
        assertEquals(0, strategy.beginTick(100, 0));
    }
}
