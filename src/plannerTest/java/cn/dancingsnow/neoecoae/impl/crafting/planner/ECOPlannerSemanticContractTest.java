package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.SccComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ECOPlanMaterialValidator;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOPlannerSemanticContractTest {
    @Test
    void sameOutputDifferentPlannerCandidatesNeverCrossWire() throws Exception {
        AEKey goal = PlannerTestKey.of("candidate_binding_goal");
        AEKey input = PlannerTestKey.of("candidate_binding_input");
        var ecoPattern = PlannerFixtures.pattern("eco_candidate", goal, 1, input, 1L);
        var ecoPlan = new CraftingPlan(new GenericStack(goal, 1), 1, false, false,
            counter(input, 1), new KeyCounter(), new KeyCounter(), Map.of(ecoPattern, 2L));
        var ecoResult = new ECOPlanningResult(PlanningStatus.SUCCESS, ecoPlan,
            new cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace(), List.of(), List.of(), List.of(), 1L);
        // The other planner selected the same final output but a different physical task vector.
        var thunderPattern = PlannerFixtures.pattern("thunder_candidate", goal, 1, input, 1L);
        var thunderPlan = new CraftingPlan(new GenericStack(goal, 1), 1, false, false,
            counter(input, 1), new KeyCounter(), new KeyCounter(), Map.of(thunderPattern, 3L));
        assertNotEquals(ecoPlan.patternTimes(), thunderPlan.patternTimes());
        assertSame(thunderPlan, ECOPlanningResultRegistry.withSubmissionAlias(thunderPlan, ecoResult, () -> {
            assertSame(thunderPlan, ECOPlanningResultRegistry.resolveSubmissionPlan(thunderPlan));
            assertNull(ECOPlanningResultRegistry.activeSubmissionMetadata(thunderPlan));
            return thunderPlan;
        }));
        assertEquals(3L, taskExecutions(thunderPlan));
    }

    @Test
    void thunderContractWithoutCompleteSemanticsDeclinesInsteadOfReturningSuccess() throws Exception {
        AEKey goal = PlannerTestKey.of("thunder_unsupported_goal");
        AEKey input = PlannerTestKey.of("thunder_unsupported_input");
        IPatternDetails pattern = new ThunderUnsupportedPattern(goal, input);
        CompiledNetwork network = new CraftingNetworkCompiler().compile(
            service(Map.of(goal, List.of(pattern))), goal, true, ECOCancellation.NONE);
        assertFalse(network.producersOf(goal).getFirst().fastSupported());
        assertEquals("THUNDER_UNSUPPORTED_SEMANTICS", network.producersOf(goal).getFirst().unsupportedReason());

        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, new KeyCounter(), 1, true, ECOCancellation.NONE);
        assertNotEquals(PlanningStatus.SUCCESS, outcome.status());
    }

    @Test
    void completeThunderFeedbackSemanticsReachSccAndFinalPatternTimes() throws Exception {
        AEKey a = PlannerTestKey.of("thunder_feedback_a");
        AEKey b = PlannerTestKey.of("thunder_feedback_b");
        var p1 = new ThunderSemanticPattern("feedback_p1", b, a, a);
        var p2 = new ThunderSemanticPattern("feedback_p2", a, b, b);
        CompiledNetwork network = new CraftingNetworkCompiler().compile(service(Map.of(
            a, List.of(p2), b, List.of(p1))), a, true, ECOCancellation.NONE);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        List<SccComponent> sccs = new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE);
        assertTrue(sccs.stream().anyMatch(SccComponent::cyclic));
        var condensation = CondensationGraph.build(graph, sccs, ECOCancellation.NONE);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, counter(a, 1), 1, true, ECOCancellation.NONE);

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        var cycle = outcome.components().stream()
            .filter(component -> component.type() == ComponentPlanningResult.Type.CYCLIC)
            .findFirst().orElseThrow();
        assertEquals(CyclePlanningStatus.SOLVED, cycle.cycleStatus());
        assertNotNull(cycle.cycleResult());
        assertTrue(cycle.cycleResult().patternTimes().values().stream().anyMatch(value -> value > 0));
        assertTrue(outcome.state().patternTimes().keySet().containsAll(List.of(p1, p2)));

        var plan = new AE2CraftingPlanBridge().success(a, 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), plan, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        assertTrue(ECOPlanningResultRegistry.cycleExpected(result));
        ECOExecutionSchedule schedule = result.executionSchedule();
        assertTrue(schedule.phases().stream().anyMatch(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE));
        assertTrue(schedule.phases().stream().flatMap(phase -> phase.patternSet().stream())
            .anyMatch(pattern -> pattern == p1));
    }

    @Test
    void patchedDagProducerIsOrderedBeforeConsumerFromNormalizedSemantics() {
        AEKey dust = PlannerTestKey.of("energized_crystal_dust");
        AEKey goal = PlannerTestKey.of("omniversal_pattern");
        var producer = PlannerFixtures.pattern("producer", dust, 1);
        var consumer = PlannerFixtures.pattern("consumer", goal, 1, dust, 1L);
        var patchedProducer = PlannerFixtures.pattern("patched-producer", dust, 1);
        var patchedConsumer = PlannerFixtures.pattern("patched-consumer", goal, 1, dust, 1L);
        var producerComponent = component(2, Map.of(dust, 1L), Set.of(producer));
        var consumerComponent = component(1, Map.of(goal, 1L), Set.of(consumer));

        var schedule = ECOExecutionSchedule.from(List.of(consumerComponent, producerComponent), List.of(1, 2),
            Map.of(patchedConsumer, 1L, patchedProducer, 1L));
        assertEquals(List.of(2, 1), schedule.phases().stream()
            .map(ECOExecutionSchedule.ComponentExecutionPhase::componentId).toList());
        assertEquals(Set.of(patchedProducer), schedule.phases().getFirst().patternSet());
    }

    @Test
    void omittedRouteInputCannotRemainAClosedSuccessfulPlan() throws Exception {
        AEKey input = PlannerTestKey.of("closure_input");
        AEKey goal = PlannerTestKey.of("closure_goal");
        var pattern = PlannerFixtures.pattern("closure_goal", goal, 1, input, 1L);
        CompiledNetwork network = PlannerFixtures.network(goal, new LinkedHashMap<>(Map.of(
            input, List.of(), goal, List.of(PlannerFixtures.compiled(0, pattern, goal, true, "")))));

        // This deliberately malformed route mirrors the failure mode: the solver can only be considered closed
        // after every input it discovers has been included in the route.
        var outcome = new AcyclicCraftingSolver().solve(network,
            new cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan(List.of(goal)),
            new KeyCounter(), 1, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());

        var issue = ECOPlanMaterialValidator.firstDeficit(outcome.state(), goal, 1);
        assertNotNull(issue);
        assertEquals(input, issue.key());
        assertEquals("MATERIAL_DEFICIT", issue.reason());
    }

    @Test
    void materiallyClosedPlanPassesTheExecutionContract() throws Exception {
        AEKey input = PlannerTestKey.of("closed_input");
        AEKey goal = PlannerTestKey.of("closed_goal");
        var pattern = PlannerFixtures.pattern("closed_goal", goal, 1, input, 1L);
        CompiledNetwork network = PlannerFixtures.network(goal, new LinkedHashMap<>(Map.of(
            input, List.of(), goal, List.of(PlannerFixtures.compiled(0, pattern, goal, true, "")))));
        var outcome = new AcyclicCraftingSolver().solve(network,
            new cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan(List.of(goal, input)),
            counter(input, 1), 1, ECOCancellation.NONE);

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertNull(ECOPlanMaterialValidator.firstDeficit(outcome.state(), goal, 1));
    }

    private static ComponentPlanningResult component(int id, Map<AEKey, Long> requiredOutputs,
            Set<IPatternDetails> patterns) {
        return new ComponentPlanningResult(id, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, requiredOutputs, patterns, patterns,
            null, null, Map.of(), null, null,
            cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExecutionDisposition.NOT_REQUIRED, Map.of());
    }

    private static KeyCounter counter(AEKey key, long amount) {
        KeyCounter result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static long taskExecutions(CraftingPlan plan) {
        return plan.patternTimes().values().stream().mapToLong(Long::longValue).sum();
    }

    private static ICraftingService service(Map<AEKey, ? extends Collection<IPatternDetails>> patterns) {
        return (ICraftingService) Proxy.newProxyInstance(ICraftingService.class.getClassLoader(),
            new Class<?>[] {ICraftingService.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getCraftingFor" -> {
                    Collection<IPatternDetails> matching = patterns.get((AEKey) args[0]);
                    yield matching == null ? List.<IPatternDetails>of() : matching;
                }
                case "canEmitFor" -> false;
                case "toString" -> "SemanticContractTestService";
                default -> method.getReturnType() == boolean.class ? false
                    : method.getReturnType() == long.class ? 0L
                    : method.getReturnType() == int.class ? 0 : null;
            });
    }

    private static final class ThunderUnsupportedPattern implements IPatternDetails {
        private final PlannerFixtures.Pattern delegate;

        private ThunderUnsupportedPattern(AEKey output, AEKey input) {
            delegate = PlannerFixtures.pattern("thunder-unsupported", output, 1, input, 1L);
        }

        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return delegate.getInputs(); }
        @Override public List<GenericStack> getOutputs() { return delegate.getOutputs(); }
    }

    /** Public so ThunderPatternSemanticAdapter can invoke the optional bridge method reflectively. */
    public static final class ThunderSemanticPattern implements IPatternDetails {
        private final PlannerFixtures.Pattern delegate;
        private final PatternSemantics semantics;

        private ThunderSemanticPattern(String name, AEKey output, AEKey input, AEKey returned) {
            delegate = PlannerFixtures.pattern(name, output, 1, input, 1L);
            var source = delegate.getInputs()[0];
            var inputSemantics = new PatternSemantics.Input(source, input, PlannerAmount.of(1), returned,
                PlannerAmount.of(1));
            semantics = new PatternSemantics(this, null, List.of(inputSemantics), delegate.getOutputs(),
                List.of(new GenericStack(returned, 1)),
                List.of(new PatternSemantics.FeedbackEdge(returned, output, PlannerAmount.of(1))),
                PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        }

        public PatternSemantics getPatternSemantics() {
            return semantics;
        }

        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return delegate.getInputs(); }
        @Override public List<GenericStack> getOutputs() { return delegate.getOutputs(); }
        @Override public String toString() { return delegate.toString(); }
    }
}
