package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.RuntimeExecutionState;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DynamicCyclePlanningTest {
    @Test
    void nineNodeCycleUsesLargeBudgetAndDynamicPhase() throws Exception {
        List<PlannerTestKey> keys = new ArrayList<>();
        for (int i = 0; i < 9; i++) keys.add(PlannerTestKey.of("large_cycle_" + i));
        Map<appeng.api.stacks.AEKey, List<cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern>>
            producers = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            var input = keys.get(i);
            var output = keys.get((i + 1) % keys.size());
            var pattern = PlannerFixtures.pattern("large_step_" + i, output, i == 0 ? 2 : 1, input, 1L);
            producers.put(output, List.of(PlannerFixtures.compiled(i, pattern, output, true, "")));
        }
        var goal = keys.getFirst();
        var network = PlannerFixtures.network(goal, producers);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(goal, 1);

        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 1, true, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        var cycle = outcome.components().stream()
            .filter(component -> component.type() == ComponentPlanningResult.Type.CYCLIC)
            .findFirst().orElseThrow();
        assertEquals(CycleExecutionDisposition.DYNAMIC_EXECUTION, cycle.cycleDisposition());
        assertTrue(cycle.cycleResult().hasExactExecutionCounts());

        var submitted = new AE2CraftingPlanBridge().success(goal, 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), submitted, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        assertEquals(ExecutionMode.DYNAMIC_CYCLE, result.executionPlan().mode());
        var phase = result.executionPlan().phases().stream()
            .filter(candidate -> candidate.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE)
            .findFirst().orElseThrow();
        assertTrue(phase.steps().isEmpty());
        assertEquals(cycle.cycleResult().patternTimes().values().stream().mapToLong(Long::longValue).sum(),
            phase.dynamicFirings().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(cycle.cycleResult().requiredSeed(), phase.initialSeed());
    }

    @Test
    void terminalByproductDoesNotBecomeTheSelectedUpstreamProducer() {
        var dust = PlannerTestKey.of("byproduct_dust");
        var intermediate = PlannerTestKey.of("byproduct_intermediate");
        var goal = PlannerTestKey.of("byproduct_goal");
        var source = PlannerFixtures.pattern("dust_source", dust, 1);
        var consumer = PlannerFixtures.pattern("dust_consumer", intermediate, 1, dust, 1L);
        var terminal = PlannerFixtures.multiOutput("terminal",
            List.of(new GenericStack(goal, 1), new GenericStack(dust, 1)), intermediate, 1L);
        var sourceComponent = dagComponent(1, Map.of(), source);
        var consumerComponent = dagComponent(2, Map.of(intermediate, 1L), consumer);
        var terminalComponent = dagComponent(3, Map.of(goal, 1L), terminal);

        var schedule = ECOExecutionSchedule.from(
            List.of(terminalComponent, consumerComponent, sourceComponent), List.of(3, 2, 1),
            Map.of(source, 1L, consumer, 1L, terminal, 1L));

        assertEquals(List.of(1, 2, 3), schedule.phases().stream()
            .map(ECOExecutionSchedule.ComponentExecutionPhase::componentId).toList());
    }

    @Test
    void dynamicFiringsGateAggregateRemainderAndSurviveRestore() {
        var a = PlannerTestKey.of("dynamic_runtime_a");
        var b = PlannerTestKey.of("dynamic_runtime_b");
        var first = PlannerFixtures.pattern("dynamic_first", b, 1, a, 1);
        var second = PlannerFixtures.pattern("dynamic_second", a, 1, b, 1);
        var firstIdentity = PlanIdentity.patternIdentityFor(first);
        var secondIdentity = PlanIdentity.patternIdentityFor(second);
        var tasks = List.of(
            new ECOExecutionPlan.TaskSpec(0, firstIdentity, first,
                ECOExecutionPlan.PatternRuntimeInfo.from(first), 3, 0,
                ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC),
            new ECOExecutionPlan.TaskSpec(1, secondIdentity, second,
                ECOExecutionPlan.PatternRuntimeInfo.from(second), 1, 0,
                ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC));
        var phase = new ECOExecutionPlan.PhaseSpec(0, 7, ECOExecutionSchedule.Type.DYNAMIC_CYCLE,
            List.of(0, 1), List.of(), List.of(), Map.of(0, 2L, 1, 1L), Map.of(a, 1L));
        var signature = new PlanIdentity.Signature(a, 1, Map.of(firstIdentity, 3L, secondIdentity, 1L),
            Map.of(), Map.of(), Map.of());
        var schedule = new ECOExecutionSchedule(List.of(new ECOExecutionSchedule.ComponentExecutionPhase(
            7, ECOExecutionSchedule.Type.DYNAMIC_CYCLE, Set.of(first, second), List.of())));
        var plan = new ECOExecutionPlan(signature, ExecutionMode.DYNAMIC_CYCLE, tasks, List.of(phase), schedule);

        var state = new RuntimeExecutionState(plan);
        state.restoreOwnership(Map.of(a, 1L));
        assertEquals(Set.of(0, 1), Set.copyOf(state.eligibleTaskIds()));
        assertEquals(2L, state.dispatchLimit(0));
        assertEquals(1L, state.pendingCycleFeedbackReserve(a));
        assertThrows(IllegalArgumentException.class, () -> state.applyAccepted(0, 3));
        state.applyAccepted(0, 1);

        var restored = new RuntimeExecutionState(plan);
        restored.restore(state.remainingSnapshot(), state.dynamicRemainingSnapshot(),
            new int[] { state.stepIndex(0) }, new long[] { state.stepRemaining(0) });
        restored.restoreOwnership(Map.of(a, 1L));
        assertEquals(1L, restored.dynamicRemaining(0));
        restored.applyAccepted(0, 1);
        assertEquals(List.of(1), restored.eligibleTaskIds());
        restored.acceptOutput(b, 1L);
        restored.applyAccepted(1, 1);
        assertEquals(List.of(0), restored.eligibleTaskIds());
        assertEquals(1L, restored.pendingCycleFeedbackReserve(a),
            "the bootstrap seed remains reserved while aggregate cycle remainder is still runnable");
        assertEquals(1L, restored.dispatchLimit(0));
        restored.acceptOutput(a, 1L);
        restored.applyAccepted(0, 1);
        assertEquals(0L, restored.pendingCycleFeedbackReserve(a));
        assertTrue(restored.finished());
    }

    private static ComponentPlanningResult dagComponent(int id, Map<appeng.api.stacks.AEKey, Long> required,
            PlannerFixtures.Pattern pattern) {
        return new ComponentPlanningResult(id, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, required, Set.of(pattern), Set.of(pattern), null, null,
            Map.of(), null, null, CycleExecutionDisposition.NOT_REQUIRED, Map.of());
    }
}
