package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ECOPhaseSchedulerTest {
    private final PlannerTestKey a = PlannerTestKey.of("a"), b = PlannerTestKey.of("b");
    private final PlannerFixtures.Pattern p1 = PlannerFixtures.pattern("p1", a, 1);
    private final PlannerFixtures.Pattern p2 = PlannerFixtures.pattern("p2", b, 1);
    private ECOExecutionSchedule.ComponentExecutionPhase dag() { return new ECOExecutionSchedule.ComponentExecutionPhase(1, ECOExecutionSchedule.Type.DAG, Set.of(p1,p2), List.of()); }
    private ECOExecutionSchedule.ComponentExecutionPhase cycle() { return new ECOExecutionSchedule.ComponentExecutionPhase(2, ECOExecutionSchedule.Type.CYCLE, Set.of(p1,p2), List.of(p1,p2)); }

    @Test void dagPhaseAllowsAllMembersButNoDependentPhasePattern() {
        assertTrue(ECOPhaseScheduler.canDispatch(dag(), 0, p1));
        assertTrue(ECOPhaseScheduler.canDispatch(dag(), 0, p2));
        assertFalse(ECOPhaseScheduler.canDispatch(dag(), 0, PlannerFixtures.pattern("later", a, 1)));
    }
    @Test void cyclePhaseAllowsOnlyCurrentWitnessStep() {
        assertTrue(ECOPhaseScheduler.canDispatch(cycle(), 0, p1));
        assertFalse(ECOPhaseScheduler.canDispatch(cycle(), 0, p2));
        assertTrue(ECOPhaseScheduler.canDispatch(cycle(), 1, p2));
    }
    @Test void dagWaitsForRemainingTasks() { assertFalse(ECOPhaseScheduler.isComplete(dag(), 0, p -> p == p1 ? 1 : 0, k -> false)); }
    @Test void dagWaitsForInFlightOutput() { assertFalse(ECOPhaseScheduler.isComplete(dag(), 0, p -> 0, k -> k.equals(a))); }
    @Test void dagCompletesAfterTasksAndOutputs() { assertTrue(ECOPhaseScheduler.isComplete(dag(), 0, p -> 0, k -> false)); }
    @Test void cycleWaitsForWitnessEnd() { assertFalse(ECOPhaseScheduler.isComplete(cycle(), 1, p -> 0, k -> false)); }
    @Test void cycleWaitsForRemainingTaskAndInFlightOutput() {
        assertFalse(ECOPhaseScheduler.isComplete(cycle(), 2, p -> p == p2 ? 1 : 0, k -> false));
        assertFalse(ECOPhaseScheduler.isComplete(cycle(), 2, p -> 0, k -> k.equals(b)));
    }
    @Test void cycleCompletesAfterWitnessTasksAndOutputs() { assertTrue(ECOPhaseScheduler.isComplete(cycle(), 2, p -> 0, k -> false)); }
    @Test void busyProviderOrMissingInputDoesNotAdvanceWitness() {
        assertEquals(0, ECOPhaseScheduler.witnessAfterDispatch(0, false));
        assertEquals(1, ECOPhaseScheduler.witnessAfterDispatch(0, true));
    }
    @Test void scheduleUsesExplicitSupplierToConsumerIds() {
        var c1 = new ComponentPlanningResult(1, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, java.util.Map.of(), Set.of(p1), null, null, null);
        var c2 = new ComponentPlanningResult(2, ComponentPlanningResult.Type.CYCLIC,
            ComponentPlanningResult.Status.PLANNED, java.util.Map.of(), Set.of(p2), null, null, null);
        var schedule = ECOExecutionSchedule.from(List.of(c2, c1), List.of(1, 2));
        assertEquals(List.of(1, 2), schedule.phases().stream().map(ECOExecutionSchedule.ComponentExecutionPhase::componentId).toList());
    }
    @Test void missingOrderedMetadataIsFailSafeWhilePureDagRemainsNative() {
        assertFalse(ECOPhaseScheduler.metadataAvailable(true, null, true));
        assertTrue(ECOPhaseScheduler.metadataAvailable(false, null, false));
    }
}
