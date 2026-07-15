package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ECOIntegratedWorkingStationRecipeHelperTest {
    @Test
    void plansDistinctRecipeInputsWithoutDoubleSpendingSlots() {
        int[] plan = ECOIntegratedWorkingStationRecipeHelper.createItemConsumptionPlan(
                new int[] {4, 2, 2},
                new boolean[][] {
                    {true, false, false},
                    {false, true, false},
                    {false, false, true}
                },
                new int[] {4, 2, 2});

        assertArrayEquals(new int[] {4, 2, 2}, plan);
    }

    @Test
    void rejectsOverlappingRequirementsThatExceedAvailableItems() {
        int[] plan = ECOIntegratedWorkingStationRecipeHelper.createItemConsumptionPlan(
                new int[] {4}, new boolean[][] {{true}, {true}}, new int[] {3, 2});

        assertNull(plan);
    }

    @Test
    void reallocatesBroadIngredientToPreserveNarrowIngredientMatch() {
        int[] plan = ECOIntegratedWorkingStationRecipeHelper.createItemConsumptionPlan(
                new int[] {1, 1}, new boolean[][] {{true, true}, {true, false}}, new int[] {1, 1});

        assertArrayEquals(new int[] {1, 1}, plan);
    }
}
