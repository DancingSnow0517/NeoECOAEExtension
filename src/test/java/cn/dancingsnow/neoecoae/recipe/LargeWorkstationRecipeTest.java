package cn.dancingsnow.neoecoae.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LargeWorkstationRecipeTest {
    @Test
    void overlappingIngredientsCanReassignEarlierAllocations() {
        assertTrue(LargeWorkstationRecipe.matchesQuantities(
                new long[] {5, 5}, new long[] {5, 5}, new boolean[][] {{true, true}, {true, false}}));
        assertFalse(LargeWorkstationRecipe.matchesQuantities(
                new long[] {5, 5}, new long[] {5, 5}, new boolean[][] {{true, true}, {false, false}}));
    }

    @Test
    void largeCountsStayCompactAndSurplusIsRejected() {
        assertTrue(LargeWorkstationRecipe.matchesQuantities(
                new long[] {Integer.MAX_VALUE}, new long[] {Integer.MAX_VALUE}, new boolean[][] {{true}}));
        assertFalse(LargeWorkstationRecipe.matchesQuantities(
                new long[] {6}, new long[] {5}, new boolean[][] {{true}}));
    }
}
