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
        assertFalse(ECOPhaseScheduler.canDispatch(dag(), 0, PlannerFixtures.pattern("later", a, 1, b, 1L)));
    }
    @Test void cyclePhaseAllowsOnlyCurrentWitnessStep() {
        assertTrue(ECOPhaseScheduler.canDispatch(cycle(), 0, p1));
        assertFalse(ECOPhaseScheduler.canDispatch(cycle(), 0, p2));
        assertTrue(ECOPhaseScheduler.canDispatch(cycle(), 1, p2));
    }
    @Test void cycleAllowsAggregateRemainderAfterWitnessIsReplayed() {
        assertTrue(ECOPhaseScheduler.canDispatch(cycle(), 2, p1));
        assertTrue(ECOPhaseScheduler.canDispatch(cycle(), 2, p2));
    }
    @Test void dagWaitsForRemainingTasks() { assertFalse(ECOPhaseScheduler.isComplete(dag(), 0, p -> p == p1 ? 1 : 0)); }
    @Test void dagCompletesWhenTasksDispatchedEvenIfOutputIsInFlight() {
        // In-flight intermediate output is consumed by the next phase's input extraction;
        // it must not gate completion of the producing phase.
        assertTrue(ECOPhaseScheduler.isComplete(dag(), 0, p -> 0));
    }
    @Test void cycleWaitsForWitnessEnd() { assertFalse(ECOPhaseScheduler.isComplete(cycle(), 1, p -> 0)); }
    @Test void cycleWaitsForRemainingTaskButNotInFlightOutput() {
        assertFalse(ECOPhaseScheduler.isComplete(cycle(), 2, p -> p == p2 ? 1 : 0));
        assertTrue(ECOPhaseScheduler.isComplete(cycle(), 2, p -> 0));
    }
    @Test void cycleCompletesAfterWitnessTasksAndOutputs() { assertTrue(ECOPhaseScheduler.isComplete(cycle(), 2, p -> 0)); }
    @Test void busyProviderOrMissingInputDoesNotAdvanceWitness() {
        assertEquals(0, ECOPhaseScheduler.witnessAfterDispatch(0, false));
        assertEquals(1, ECOPhaseScheduler.witnessAfterDispatch(0, true));
    }
    @Test void scheduleUsesExplicitSupplierToConsumerIds() {
        var c1 = new ComponentPlanningResult(1, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, java.util.Map.of(), Set.of(p1), null, null, java.util.Map.of(), null, null);
        var c2 = new ComponentPlanningResult(2, ComponentPlanningResult.Type.CYCLIC,
            ComponentPlanningResult.Status.PLANNED, java.util.Map.of(), Set.of(p2), null, null, java.util.Map.of(), null, null);
        var schedule = ECOExecutionSchedule.from(List.of(c2, c1), List.of(1, 2));
        assertEquals(List.of(1, 2), schedule.phases().stream().map(ECOExecutionSchedule.ComponentExecutionPhase::componentId).toList());
    }
    @Test void physicalPatternOwnedByCycleIsNotAlsoGatedByDagPhase() {
        var dagView = new ComponentPlanningResult(1, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, java.util.Map.of(), Set.of(p1), null, null, java.util.Map.of(), null, null);
        var cycleOwner = new ComponentPlanningResult(2, ComponentPlanningResult.Type.CYCLIC,
            ComponentPlanningResult.Status.PLANNED, java.util.Map.of(), Set.of(p1), null, null, java.util.Map.of(), null, null);
        var schedule = ECOExecutionSchedule.from(List.of(dagView, cycleOwner), List.of(1, 2));
        assertEquals(1, schedule.phases().size());
        assertEquals(ECOExecutionSchedule.Type.CYCLE, schedule.phases().getFirst().type());
        assertEquals(Set.of(p1), schedule.phases().getFirst().patternSet());
    }
    @Test void physicalPatternHasExactlyOneDagOwner() {
        var firstView = component(1, ComponentPlanningResult.Type.ACYCLIC, Set.of(p1), Set.of(p1));
        var secondView = component(2, ComponentPlanningResult.Type.ACYCLIC, Set.of(p1), Set.of(p1));
        var schedule = ECOExecutionSchedule.from(
            List.of(firstView, secondView), List.of(1, 2), java.util.Map.of(p1, 7L));
        assertEquals(1, schedule.phases().size());
        assertEquals(1, schedule.phases().getFirst().componentId());
        assertEquals(Set.of(p1), schedule.phases().getFirst().patternSet());
    }
    @Test void unselectedCandidatesAndEmptyStructuralComponentsAreOmitted() {
        var selectedDag = component(1, ComponentPlanningResult.Type.ACYCLIC, Set.of(p1, p2), Set.of(p1));
        var unusedCycle = component(2, ComponentPlanningResult.Type.CYCLIC, Set.of(p2), Set.of());
        var schedule = ECOExecutionSchedule.from(
            List.of(selectedDag, unusedCycle), List.of(1, 2), java.util.Map.of(p1, 3L));
        assertEquals(1, schedule.phases().size());
        assertEquals(ECOExecutionSchedule.Type.DAG, schedule.phases().getFirst().type());
        assertEquals(Set.of(p1), schedule.phases().getFirst().patternSet());
        assertTrue(ECOPhaseScheduler.hasExecutionPhases(schedule));
        assertFalse(ECOPhaseScheduler.requiresComponentScheduling(schedule));
    }
    @Test void activeDagScheduleRejectsUnownedPlanTasks() {
        var dagOwner = component(1, ComponentPlanningResult.Type.ACYCLIC, Set.of(p1), Set.of(p1));
        assertThrows(IllegalStateException.class, () -> ECOExecutionSchedule.from(
            List.of(dagOwner), List.of(1), java.util.Map.of(p1, 1L, p2, 1L)));
    }
    @Test void activeCycleScheduleRejectsUnownedPlanTasks() {
        var cycleOwner = component(1, ComponentPlanningResult.Type.CYCLIC, Set.of(p1), Set.of(p1));
        assertThrows(IllegalStateException.class, () -> ECOExecutionSchedule.from(
            List.of(cycleOwner), List.of(1), java.util.Map.of(p1, 1L, p2, 1L)));
    }
    @Test void finalPatternDependenciesOverrideStaleComponentOrder() {
        var consumer = PlannerFixtures.pattern("alternate_consumer", a, 1, b, 1L);
        var supplier = PlannerFixtures.pattern("alternate_supplier", b, 1);
        var consumerComponent = component(1, ComponentPlanningResult.Type.ACYCLIC,
            java.util.Map.of(a, 1L), Set.of(consumer), Set.of(consumer));
        var supplierComponent = component(2, ComponentPlanningResult.Type.ACYCLIC,
            java.util.Map.of(b, 1L), Set.of(supplier), Set.of(supplier));

        var schedule = ECOExecutionSchedule.from(List.of(consumerComponent, supplierComponent),
            List.of(1, 2), java.util.Map.of(consumer, 1L, supplier, 1L));

        assertEquals(List.of(2, 1), schedule.phases().stream()
            .map(ECOExecutionSchedule.ComponentExecutionPhase::componentId).toList());
    }
    @Test void selfReturnedInputDoesNotHideAnotherPlannedSupplier() {
        var consumer = new PlannerFixtures.Pattern("self_returning_consumer",
            new appeng.api.crafting.IPatternDetails.IInput[] {new PlannerFixtures.Input(b, 1, true)},
            List.of(new appeng.api.stacks.GenericStack(a, 1)));
        var supplier = PlannerFixtures.pattern("actual_supplier", b, 1);
        var consumerComponent = component(1, ComponentPlanningResult.Type.ACYCLIC,
            java.util.Map.of(b, 1L), Set.of(consumer), Set.of(consumer));
        var supplierComponent = component(2, ComponentPlanningResult.Type.ACYCLIC,
            java.util.Map.of(b, 1L), Set.of(supplier), Set.of(supplier));

        var schedule = ECOExecutionSchedule.from(
            List.of(supplierComponent, consumerComponent), List.of(1, 2),
            java.util.Map.of(consumer, 1L, supplier, 1L));

        assertEquals(List.of(2, 1), schedule.phases().stream()
            .map(ECOExecutionSchedule.ComponentExecutionPhase::componentId).toList());
    }
    @Test void missingOrderedMetadataIsFailSafeWhilePureDagRemainsNative() {
        assertFalse(ECOPhaseScheduler.metadataAvailable(true, null, true));
        assertTrue(ECOPhaseScheduler.metadataAvailable(false, null, false));
    }
    @Test void compactCycleWithoutExpandedWitnessStillRequiresComponentScheduling() {
        var compactCycle = new ECOExecutionSchedule.ComponentExecutionPhase(2,
            ECOExecutionSchedule.Type.CYCLE, Set.of(p1), List.of());
        assertTrue(ECOPhaseScheduler.requiresComponentScheduling(
            new ECOExecutionSchedule(List.of(dag(), compactCycle))));
        var dagSchedule = new ECOExecutionSchedule(List.of(dag()));
        assertTrue(ECOPhaseScheduler.hasExecutionPhases(dagSchedule));
        assertFalse(ECOPhaseScheduler.requiresComponentScheduling(dagSchedule));
    }
    @Test void compactSelfGrowingCycleRetainsFeedbackForAllRemainingCrafts() {
        var growing = PlannerFixtures.pattern("growing", a, 2, a, 1L, b, 7L);
        var compactCycle = new ECOExecutionSchedule.ComponentExecutionPhase(2,
            ECOExecutionSchedule.Type.CYCLE, Set.of(growing), List.of());
        assertEquals(8L, ECOPhaseScheduler.compactCycleFeedbackReserve(compactCycle, p -> 8L, a));
        assertEquals(0L, ECOPhaseScheduler.compactCycleFeedbackReserve(compactCycle, p -> 0L, a));
        assertEquals(0L, ECOPhaseScheduler.compactCycleFeedbackReserve(compactCycle, p -> 8L, b));

        var consumesTwo = PlannerFixtures.pattern("growing_by_four", a, 6, a, 2L);
        var scaledCycle = new ECOExecutionSchedule.ComponentExecutionPhase(3,
            ECOExecutionSchedule.Type.CYCLE, Set.of(consumesTwo), List.of());
        assertEquals(16L, ECOPhaseScheduler.compactCycleFeedbackReserve(scaledCycle, p -> 8L, a));
    }

    private static ComponentPlanningResult component(int id, ComponentPlanningResult.Type type,
            Set<appeng.api.crafting.IPatternDetails> candidates,
            Set<appeng.api.crafting.IPatternDetails> executionPatterns) {
        return component(id, type, java.util.Map.of(), candidates, executionPatterns);
    }

    private static ComponentPlanningResult component(int id, ComponentPlanningResult.Type type,
            java.util.Map<appeng.api.stacks.AEKey, Long> requiredOutputs,
            Set<appeng.api.crafting.IPatternDetails> candidates,
            Set<appeng.api.crafting.IPatternDetails> executionPatterns) {
        return new ComponentPlanningResult(id, type, ComponentPlanningResult.Status.PLANNED,
            requiredOutputs, candidates, executionPatterns, null, null, java.util.Map.of(), null, null);
    }
}
