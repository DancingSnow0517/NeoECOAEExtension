package cn.dancingsnow.neoecoae.api.me.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ECOPlanningResultRegistryTest {
    @AfterEach
    void clearRegistry() {
        ECOPlanningResultRegistry.clear();
    }

    @Test
    void newestCalculationReplacesSameCompletePlanIdentityWithoutAmbiguity() {
        AEKey output = mock(AEKey.class);
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of());
        CraftingPlan plan = mock(CraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(output, 1L));
        when(plan.patternTimes()).thenReturn(Map.of(pattern, 2L));
        when(plan.usedItems()).thenReturn(new KeyCounter());
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.missingItems()).thenReturn(new KeyCounter());

        ECOPlanningResult first = result(plan);
        ECOPlanningResult second = result(plan);
        ECOPlanningResultRegistry.register(plan, first);
        ECOPlanningResultRegistry.register(plan, second);

        assertEquals(1, ECOPlanningResultRegistry.registeredMetadataCount());
        assertSame(second, ECOPlanningResultRegistry.find(plan));
    }

    @Test
    void exactLookupDoesNotTransferOwnershipToAnEquivalentPlanObject() {
        AEKey output = mock(AEKey.class);
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of());
        CraftingPlan original = plan(output, pattern);
        CraftingPlan copy = plan(output, pattern);
        ECOPlanningResult result = result(original);

        ECOPlanningResultRegistry.register(original, result);

        assertSame(result, ECOPlanningResultRegistry.findExact(original));
        assertNull(ECOPlanningResultRegistry.findExact(copy));
        assertSame(result, ECOPlanningResultRegistry.find(copy));
    }

    @Test
    void exactLookupRetainsNonExecutableDiagnosticResult() {
        AEKey output = mock(AEKey.class);
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of());
        CraftingPlan plan = plan(output, pattern);
        ECOPlanningResult result = result(plan, PlanningStatus.MISSING_ITEMS);

        ECOPlanningResultRegistry.register(plan, result);

        assertSame(result, ECOPlanningResultRegistry.findExact(plan));
        assertNull(ECOPlanningResultRegistry.find(plan));
    }

    private static CraftingPlan plan(AEKey output, IPatternDetails pattern) {
        CraftingPlan plan = mock(CraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(output, 1L));
        when(plan.patternTimes()).thenReturn(Map.of(pattern, 2L));
        when(plan.usedItems()).thenReturn(new KeyCounter());
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.missingItems()).thenReturn(new KeyCounter());
        return plan;
    }

    private static ECOPlanningResult result(CraftingPlan plan) {
        return result(plan, PlanningStatus.SUCCESS);
    }

    private static ECOPlanningResult result(CraftingPlan plan, PlanningStatus status) {
        ECOExecutionSchedule schedule = mock(ECOExecutionSchedule.class);
        ECOExecutionSchedule.ComponentExecutionPhase phase =
                mock(ECOExecutionSchedule.ComponentExecutionPhase.class);
        when(schedule.phases()).thenReturn(List.of(phase));
        ECOExecutionPlan executionPlan = mock(ECOExecutionPlan.class);
        when(executionPlan.schedule()).thenReturn(schedule);
        ECOPlanningResult result = mock(ECOPlanningResult.class);
        when(result.plan()).thenReturn(plan);
        when(result.status()).thenReturn(status);
        when(result.executionRequirement()).thenReturn(ECOExecutionRequirement.NONE);
        when(result.executionPlan()).thenReturn(executionPlan);
        when(result.planningId()).thenReturn(UUID.randomUUID());
        return result;
    }
}
