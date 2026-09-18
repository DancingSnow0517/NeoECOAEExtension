package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpecialPatternResolverTest {
    @Test
    void returnedCatalystIsSharedAcrossPatternsButNotAcrossSimultaneousSlots() throws Exception {
        var key = org.mockito.Mockito.mock(appeng.api.stacks.AEKey.class);
        var source = org.mockito.Mockito.mock(appeng.api.crafting.IPatternDetails.IInput.class);
        org.mockito.Mockito.when(source.getPossibleInputs()).thenReturn(new appeng.api.stacks.GenericStack[] {
            new appeng.api.stacks.GenericStack(key, 1) });
        org.mockito.Mockito.when(source.getMultiplier()).thenReturn(1L);
        org.mockito.Mockito.when(source.getRemainingKey(key)).thenReturn(key);
        var input = new cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput(
            source, key, 1L, true, "", key, 1L);
        var requirement = new cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.SpecialPatternAnalysis.Requirement(
            input, key, cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.SpecialPatternAnalysis.Type.REUSABLE, 0, 0);
        var pattern = org.mockito.Mockito.mock(cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern.class);
        org.mockito.Mockito.when(pattern.specialAnalysis()).thenReturn(
            new cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.SpecialPatternAnalysis(java.util.List.of(requirement)));
        var stock = new appeng.api.stacks.KeyCounter();
        stock.add(key, 2);
        var state = new SolveState(stock);
        var resolver = new SpecialPatternResolver(null, state, java.util.Map.of(),
            cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation.NONE, false);
        resolver.resolve(pattern, PlannerAmount.of(1000));
        resolver.resolve(pattern, PlannerAmount.of(2000));
        assertEquals(1L, state.usedItems().get(key));
        org.mockito.Mockito.when(pattern.specialAnalysis()).thenReturn(
            new cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.SpecialPatternAnalysis(java.util.List.of(requirement, requirement)));
        resolver.resolve(pattern, PlannerAmount.of(3000));
        assertEquals(2L, state.usedItems().get(key));
    }

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
