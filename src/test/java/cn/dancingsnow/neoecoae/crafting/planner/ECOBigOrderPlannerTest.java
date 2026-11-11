package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ECOBigOrderPlannerTest {
    private ECOPlanningResult result(PlanningStatus status, long bytes) {
        var plan = mock(CraftingPlan.class);
        when(plan.bytes()).thenReturn(bytes);
        when(plan.usedItems()).thenReturn(new appeng.api.stacks.KeyCounter());
        when(plan.emittedItems()).thenReturn(new appeng.api.stacks.KeyCounter());
        when(plan.missingItems()).thenReturn(new appeng.api.stacks.KeyCounter());
        return new ECOPlanningResult(status, plan, new ECOPlanTrace(), List.of(), 0);
    }

    @Test void overflowAndBytesCauseWholeCandidateReplanning() throws Exception {
        var probes = new ArrayList<Long>();
        var answer = ECOBigOrderPlanner.search(Long.MAX_VALUE, 50, amount -> {
            probes.add(amount);
            if (amount > Long.MAX_VALUE / 2) return result(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, 0);
            return result(PlanningStatus.SUCCESS, amount > Long.MAX_VALUE / 4 ? 100 : 50);
        });
        assertFalse(answer.capacity());
        assertFalse(answer.fatal());
        assertEquals(List.of(Long.MAX_VALUE, Long.MAX_VALUE / 2, Long.MAX_VALUE / 4), probes);
    }

    @Test void missingMaterialsWaitButUnresolvedCyclesNeverBecomeChildren() throws Exception {
        var waiting = ECOBigOrderPlanner.search(8, 100, amount -> result(PlanningStatus.MISSING_ITEMS, 0));
        assertFalse(waiting.fatal());
        assertFalse(waiting.capacity());
        var unsupported = ECOBigOrderPlanner.search(8, 100, amount -> result(PlanningStatus.CYCLE_UNRESOLVED, 0));
        assertTrue(unsupported.fatal());
    }

    @Test void infeasibilityDiagnosticPreventsSubmittingAnApparentlySuccessfulSegment() throws Exception {
        var rejected = result(PlanningStatus.SUCCESS, 8);
        rejected.trace().addDiagnostic(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic(
                cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic.Code.PLAN_MATERIAL_CLOSURE_INVALID,
                "Candidate does not conserve materials"));
        var probes = new ArrayList<Long>();
        var answer = ECOBigOrderPlanner.search(64, 100, amount -> {
            probes.add(amount);
            return rejected;
        });
        assertTrue(answer.fatal());
        assertSame(rejected, answer.result());
        assertEquals(List.of(64L), probes);
    }

    @Test void aNewSearchUsesNewInventoryAndGraphResults() throws Exception {
        var unavailable = ECOBigOrderPlanner.search(64, 100, amount -> result(PlanningStatus.MISSING_ITEMS, 0));
        assertEquals(PlanningStatus.MISSING_ITEMS, unavailable.result().status());
        var next = ECOBigOrderPlanner.search(64, 100, amount -> result(PlanningStatus.SUCCESS, 8));
        assertEquals(PlanningStatus.SUCCESS, next.result().status());
    }

    @Test void overflowingLargeProbeDoesNotHideMissingMaterialsAtTheSmallestProbe() throws Exception {
        var answer = ECOBigOrderPlanner.search(8, 100, amount -> result(amount > 1
            ? PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE : PlanningStatus.MISSING_ITEMS, 0));
        assertFalse(answer.capacity());
        assertFalse(answer.fatal());
    }
}
