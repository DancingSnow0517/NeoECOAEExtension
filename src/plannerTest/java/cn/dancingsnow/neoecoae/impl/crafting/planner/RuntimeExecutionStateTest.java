package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveMetrics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlanBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.RuntimeExecutionState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeExecutionStateTest {
    private final PlannerTestKey a = PlannerTestKey.of("runtime_a");
    private final PlannerTestKey b = PlannerTestKey.of("runtime_b");
    private final PlannerTestKey c = PlannerTestKey.of("runtime_c");
    private final PlannerFixtures.Pattern first = PlannerFixtures.pattern("first", a, 1);
    private final PlannerFixtures.Pattern second = PlannerFixtures.pattern("second", b, 1);
    private final PlannerFixtures.Pattern consumer = PlannerFixtures.pattern("consumer", c, 1, a, 1L);

    @Test
    void compressedTenThousandRunAdvancesAtomicallyThenReleasesAggregateRemainder() {
        ECOExecutionPlan plan = plan();
        RuntimeExecutionState state = new RuntimeExecutionState(plan);

        assertEquals(List.of(0), state.eligibleTaskIds());
        assertEquals(10_000L, state.dispatchLimit(0));
        state.applyAccepted(0, 10_000L);
        assertEquals(1, state.stepIndex());
        assertEquals(List.of(1), state.eligibleTaskIds());

        state.applyAccepted(1, 1L);
        assertEquals(List.of(0), state.eligibleTaskIds());
        state.applyAccepted(0, 2L);
        assertEquals(3, state.stepIndex());
        assertEquals(Set.of(0, 1), Set.copyOf(state.eligibleTaskIds()),
            "after the verified cycle trace, the cycle phase owns the aggregate DAG remainder");

        state.applyAccepted(0, 3L);
        state.applyAccepted(1, 1L);
        assertEquals(1, state.phaseIndex(), "phase completion depends only on dispatched task counts");
        assertEquals(List.of(2), state.eligibleTaskIds());
        state.applyAccepted(2, 1L);
        assertTrue(state.finished());
    }

    @Test
    void restoreIncludesCompressedStepRemainderAndRejectsCursorAccountingDrift() {
        ECOExecutionPlan plan = plan();
        RuntimeExecutionState state = new RuntimeExecutionState(plan);
        state.restore(new long[] {5_005L, 2L, 1L}, new int[] {0, 0}, new long[] {5_000L, 0L});
        assertEquals(5_000L, state.dispatchLimit(0));
        assertThrows(IllegalArgumentException.class,
            () -> state.restore(new long[] {15_005L, 2L, 1L}, new int[] {0, 0}, new long[] {5_000L, 0L}));
        assertThrows(IllegalArgumentException.class,
            () -> state.restore(new long[] {5_005L, 2L, 1L}, new int[] {1, 0}, new long[] {1L, 0L}));
    }

    @Test
    void builderUsesCompactSolverRunsWithoutExpandingOrReadingLegacyWitness() {
        var firstCompiled = PlannerFixtures.compiled(0, first, a, true, "test");
        var secondCompiled = PlannerFixtures.compiled(1, second, b, true, "test");
        var cycleResult = new CycleSolveResult(CycleSolveStatus.SUCCESS,
            Map.of(first, 10_000L, second, 2L), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
            List.of(), List.of(new PatternRun(firstCompiled, 10_000L), new PatternRun(secondCompiled, 2L)),
            List.of(), CycleSolveMetrics.NONE);
        var component = new ComponentPlanningResult(10, ComponentPlanningResult.Type.CYCLIC,
            ComponentPlanningResult.Status.PLANNED, Map.of(a, 1L), Set.of(first, second),
            Set.of(first, second), CyclePlanningStatus.SOLVED, null, Map.of(), null, cycleResult);
        var signature = new PlanIdentity.Signature(a, 1L,
            Map.of(PlanIdentity.patternIdentityFor(first), 10_000L,
                PlanIdentity.patternIdentityFor(second), 2L), Map.of(), Map.of(), Map.of());

        ECOExecutionPlan built = ECOExecutionPlanBuilder.build(signature, ExecutionMode.ORDERED_CYCLE,
            List.of(component), List.of(10), Map.of(first, 10_000L, second, 2L));

        assertEquals(List.of(10_000L, 2L), built.phases().getFirst().steps().stream()
            .map(ECOExecutionPlan.ExecutionStep::count).toList());
        assertTrue(built.schedule().phases().getFirst().cycleWitness().isEmpty(),
            "the immutable execution plan must not depend on an expanded legacy witness");
    }

    @Test
    void independentPhasesRunTogetherAndUnlockConsumerWithoutWaveBarrier() {
        var firstIdentity = PlanIdentity.patternIdentityFor(first);
        var secondIdentity = PlanIdentity.patternIdentityFor(second);
        var consumerIdentity = PlanIdentity.patternIdentityFor(consumer);
        var tasks = List.of(
            new ECOExecutionPlan.TaskSpec(0, firstIdentity, first, ECOExecutionPlan.PatternRuntimeInfo.from(first), 1, 0, ECOExecutionPlan.TaskKind.DAG),
            new ECOExecutionPlan.TaskSpec(1, secondIdentity, second, ECOExecutionPlan.PatternRuntimeInfo.from(second), 2, 1, ECOExecutionPlan.TaskKind.DAG),
            new ECOExecutionPlan.TaskSpec(2, consumerIdentity, consumer, ECOExecutionPlan.PatternRuntimeInfo.from(consumer), 1, 2, ECOExecutionPlan.TaskKind.DAG));
        var phases = List.of(
            new ECOExecutionPlan.PhaseSpec(0, 1, ECOExecutionSchedule.Type.DAG, List.of(0), List.of(), List.of()),
            new ECOExecutionPlan.PhaseSpec(1, 2, ECOExecutionSchedule.Type.DAG, List.of(1), List.of(), List.of()),
            new ECOExecutionPlan.PhaseSpec(2, 3, ECOExecutionSchedule.Type.DAG, List.of(2), List.of(), List.of(0)));
        var signature = new PlanIdentity.Signature(c, 1, Map.of(firstIdentity, 1L, secondIdentity, 2L, consumerIdentity, 1L), Map.of(), Map.of(), Map.of());
        var schedulePhases = List.of(
            new ECOExecutionSchedule.ComponentExecutionPhase(1, ECOExecutionSchedule.Type.DAG, Set.of(first), List.of()),
            new ECOExecutionSchedule.ComponentExecutionPhase(2, ECOExecutionSchedule.Type.DAG, Set.of(second), List.of()),
            new ECOExecutionSchedule.ComponentExecutionPhase(3, ECOExecutionSchedule.Type.DAG, Set.of(consumer), List.of()));
        var state = new RuntimeExecutionState(new ECOExecutionPlan(signature, ExecutionMode.PHASED_DAG, tasks, phases,
            new ECOExecutionSchedule(schedulePhases, List.of(new ECOExecutionSchedule.PhaseDependency(0, 2)))));

        assertEquals(Set.of(0, 1), Set.copyOf(state.eligibleTaskIds()));
        state.applyAccepted(0, 1);
        assertEquals(Set.of(1, 2), Set.copyOf(state.eligibleTaskIds()),
            "consumer should unlock as soon as its own producer completes");
    }

    private ECOExecutionPlan plan() {
        var firstIdentity = PlanIdentity.patternIdentityFor(first);
        var secondIdentity = PlanIdentity.patternIdentityFor(second);
        var consumerIdentity = PlanIdentity.patternIdentityFor(consumer);
        var signature = new PlanIdentity.Signature(c, 1L,
            Map.of(firstIdentity, 10_005L, secondIdentity, 2L, consumerIdentity, 1L),
            Map.of(), Map.of(), Map.of());
        var tasks = List.of(
            new ECOExecutionPlan.TaskSpec(0, firstIdentity, first,
                ECOExecutionPlan.PatternRuntimeInfo.from(first), 10_005L, 0,
                ECOExecutionPlan.TaskKind.CYCLE_ORDERED),
            new ECOExecutionPlan.TaskSpec(1, secondIdentity, second,
                ECOExecutionPlan.PatternRuntimeInfo.from(second), 2L, 0,
                ECOExecutionPlan.TaskKind.CYCLE_ORDERED),
            new ECOExecutionPlan.TaskSpec(2, consumerIdentity, consumer,
                ECOExecutionPlan.PatternRuntimeInfo.from(consumer), 1L, 1,
                ECOExecutionPlan.TaskKind.DAG));
        var phases = List.of(
            new ECOExecutionPlan.PhaseSpec(0, 10, ECOExecutionSchedule.Type.CYCLE, List.of(0, 1),
                List.of(new ECOExecutionPlan.ExecutionStep(0, 10_000L),
                    new ECOExecutionPlan.ExecutionStep(1, 1L),
                    new ECOExecutionPlan.ExecutionStep(0, 2L)), List.of()),
            new ECOExecutionPlan.PhaseSpec(1, 11, ECOExecutionSchedule.Type.DAG, List.of(2), List.of(), List.of(0)));
        var schedule = new ECOExecutionSchedule(List.of(
            new ECOExecutionSchedule.ComponentExecutionPhase(10, ECOExecutionSchedule.Type.CYCLE,
                Set.of(first, second), List.of()),
            new ECOExecutionSchedule.ComponentExecutionPhase(11, ECOExecutionSchedule.Type.DAG,
                Set.of(consumer), List.of())),
            List.of(new ECOExecutionSchedule.PhaseDependency(0, 1)));
        return new ECOExecutionPlan(signature, ExecutionMode.ORDERED_CYCLE, tasks, phases, schedule);
    }
}
