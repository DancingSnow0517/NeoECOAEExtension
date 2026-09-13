package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpecialPatternResolverTest {
    @Test
    void finalBreakingUseStillCountsAsOneCraft() {
        assertEquals(1, SpecialPatternResolver.durabilityUsesBeforeBreak(99, 4, 100));
    }

    @Test
    void durabilityCapacityRoundsUpToTheBreakingCraft() {
        assertEquals(3, SpecialPatternResolver.durabilityUsesBeforeBreak(90, 4, 100));
        assertEquals(0, SpecialPatternResolver.durabilityUsesBeforeBreak(100, 4, 100));
    }
}
