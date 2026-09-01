package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveMetrics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlanBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CycleExecutionDispositionTest {
    private final PlannerTestKey goal = PlannerTestKey.of("disposition_goal");
    private final PlannerTestKey a = PlannerTestKey.of("disposition_a");
    private final PlannerTestKey b = PlannerTestKey.of("disposition_b");
    private final PlannerFixtures.Pattern grow = PlannerFixtures.pattern("disposition_grow", a, 2, a, 1L);

    @Test void plannerRecordsStockSatisfiedInternalCycleWithoutRuntimeCyclePhase() throws Exception {
        ComponentPlanner.Outcome outcome = planGrowingInternalCycle(100, 100);
        ComponentPlanningResult cycle = cycle(outcome);

        assertEquals(CycleExecutionDisposition.STOCK_SATISFIED, cycle.cycleDisposition());
        assertEquals(100L, cycle.stockReservations().get(a));
        assertEquals(100L, outcome.state().usedItems().get(a));
        assertTrue(cycle.cycleResult().patternTimes().isEmpty());

        var schedule = ECOExecutionSchedule.from(outcome.components(), outcome.executionComponentOrder(),
            outcome.state().patternTimes());
        assertTrue(schedule.phases().stream().noneMatch(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE));

        CraftingPlan submitted = new AE2CraftingPlanBridge().success(goal, 100, false, false, outcome.state());
        ECOPlanningResult result = new ECOPlanningResult(outcome.status(), submitted, outcome.trace(),
            outcome.cycles(), outcome.components(), outcome.executionComponentOrder(), 1L);
        assertNull(result.executionPlanError());
        assertTrue(result.executionContract().executable());
        assertTrue(result.executionContract().phased(), "the remaining acyclic final-output work still has a DAG phase");
        assertTrue(result.executionSchedule().phases().stream()
            .noneMatch(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE));
    }

    @Test void plannerKeepsPartialStockAndPositiveFiringsAsOrderedExecution() throws Exception {
        ComponentPlanner.Outcome outcome = planGrowingInternalCycle(100, 30);
        ComponentPlanningResult cycle = cycle(outcome);

        assertEquals(CycleExecutionDisposition.ORDERED_EXECUTION, cycle.cycleDisposition());
        assertEquals(30L, cycle.stockReservations().get(a));
        assertEquals(30L, outcome.state().usedItems().get(a));
        assertEquals(70L, cycle.cycleResult().patternTimes().get(grow));
        assertTrue(ECOExecutionSchedule.from(outcome.components(), outcome.executionComponentOrder(),
            outcome.state().patternTimes()).phases().stream()
            .anyMatch(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE));
    }

    @Test void positiveGrowthCycleReservesOneNetworkSeedForHundredRequestedOutputs() throws Exception {
        var outcome = planSelfGrowingCycle(100, 1);
        var cycle = cycle(outcome);

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(CycleExecutionDisposition.ORDERED_EXECUTION, cycle.cycleDisposition());
        assertEquals(Map.of(a, 1L), cycle.cycleResult().requiredSeed());
        assertEquals(1L, outcome.state().usedItems().get(a));
        assertTrue(cycle.cycleResult().patternTimes().get(grow) > 0L);
        assertFalse(cycle.cycleResult().executionPlan().isEmpty());
    }

    @Test void positiveGrowthCycleWithoutSeedFailsWithExplicitShortfall() throws Exception {
        var outcome = planSelfGrowingCycle(100, 0);
        var cycle = cycle(outcome);

        assertNotEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(CycleExecutionDisposition.BLOCKED, cycle.cycleDisposition());
        assertNotNull(cycle.cycleResult());
        assertEquals(Map.of(a, 1L), cycle.cycleResult().seedShortfall());
        assertTrue(cycle.cycleResult().summary().toLowerCase(java.util.Locale.ROOT).contains("seed"));
    }

    @Test void stockAlreadyCoveringCycleDemandDoesNotScheduleCycleFirings() throws Exception {
        ComponentPlanner.Outcome outcome = planGrowingInternalCycle(100, 1_000);
        ComponentPlanningResult cycle = cycle(outcome);

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(CycleExecutionDisposition.STOCK_SATISFIED, cycle.cycleDisposition());
        assertTrue(cycle.cycleResult().patternTimes().isEmpty());
        assertTrue(ECOExecutionSchedule.from(outcome.components(), outcome.executionComponentOrder(),
            outcome.state().patternTimes()).phases().stream()
            .noneMatch(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE));
    }

    @Test void stockSatisfiedMissingUsedItemsFailsClosedWithComponentAccountingDiagnostic() {
        ComponentPlanningResult stock = stockSatisfied(1, a, 100);
        PlanIdentity.Signature signature = signature(Map.of(), Map.of());

        var failure = assertThrows(IllegalStateException.class, () -> ECOExecutionPlanBuilder.build(signature,
            ExecutionMode.PHASED_DAG, List.of(stock), List.of(1), Map.of()));
        assertTrue(failure.getMessage().contains("cycleComponent=1"));
        assertTrue(failure.getMessage().contains("required=100"));
        assertTrue(failure.getMessage().contains("stockReserved=100"));
        assertTrue(failure.getMessage().contains("finalUsedItems=0"));
    }

    @Test void noDemandSkipsRuntimeAndBlockedDemandFailsClosed() {
        ComponentPlanningResult none = component(1, Map.of(), Set.of(), zeroSolve(),
            CycleExecutionDisposition.NOT_REQUIRED, Map.of());
        assertTrue(ECOExecutionPlanBuilder.build(signature(Map.of(), Map.of()), ExecutionMode.PHASED_DAG,
            List.of(none), List.of(1), Map.of()).phases().isEmpty());

        ComponentPlanningResult blocked = component(2, Map.of(a, 1L), Set.of(), null,
            CycleExecutionDisposition.BLOCKED, Map.of());
        assertEquals(ECOExecutionRequirement.BLOCKED,
            ECOExecutionRequirement.classify(List.of(blocked), Map.of()));
        assertThrows(IllegalStateException.class, () -> ECOExecutionPlanBuilder.build(signature(Map.of(), Map.of()),
            ExecutionMode.PHASED_DAG, List.of(blocked), List.of(2), Map.of()));
    }

    @Test void mixedStockSatisfiedAndOrderedCycleEmitsOnlyOrderedPhase() {
        ComponentPlanningResult stock = stockSatisfied(1, a, 100);
        ComponentPlanningResult ordered = ordered(2, b, 30, grow, 70, true);
        Map<IPatternDetails, Long> tasks = Map.of(grow, 70L);
        var plan = ECOExecutionPlanBuilder.build(signature(tasks, Map.of(a, 100L, b, 30L)),
            ExecutionMode.ORDERED_CYCLE, List.of(stock, ordered), List.of(1, 2), tasks);

        assertEquals(1, plan.phases().size());
        assertEquals(2, plan.phases().getFirst().componentId());
        assertEquals(ECOExecutionSchedule.Type.CYCLE, plan.phases().getFirst().type());
    }

    @Test void orderedExecutionWithoutCompactMetadataFailsClosed() {
        ComponentPlanningResult ordered = ordered(2, b, 0, grow, 1, false);
        Map<IPatternDetails, Long> tasks = Map.of(grow, 1L);
        assertThrows(IllegalStateException.class, () -> ECOExecutionPlanBuilder.build(signature(tasks, Map.of()),
            ExecutionMode.ORDERED_CYCLE, List.of(ordered), List.of(2), tasks));
    }

    @Test void stockSatisfiedPlanningResultUsesNativeContractAndDoesNotRequireCycleMetadata() {
        ComponentPlanningResult stock = stockSatisfied(1, a, 100);
        KeyCounter used = stock(a, 100);
        CraftingPlan plan = new CraftingPlan(new GenericStack(goal, 1), 0, false, false,
            used, new KeyCounter(), new KeyCounter(), Map.of());
        ECOPlanningResult result = new ECOPlanningResult(PlanningStatus.SUCCESS, plan, new ECOPlanTrace(),
            List.of(), List.of(stock), List.of(1), 1L);

        assertEquals(ECOExecutionRequirement.NONE, result.executionRequirement());
        assertTrue(result.executionContract().executable());
        assertNull(result.executionPlanError());
    }

    private ComponentPlanner.Outcome planGrowingInternalCycle(long amount, long storedA) throws Exception {
        var finish = PlannerFixtures.compiled(0,
            PlannerFixtures.pattern("disposition_finish", goal, 1, a, 1L), goal, true, "");
        var growing = PlannerFixtures.compiled(1, grow, a, true, "");
        CompiledNetwork network = PlannerFixtures.network(goal,
            Map.of(goal, List.of(finish), a, List.of(growing)));
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock(a, storedA), amount, true, ECOCancellation.NONE);
    }

    private ComponentPlanner.Outcome planSelfGrowingCycle(long amount, long storedA) throws Exception {
        CompiledNetwork network = PlannerFixtures.network(a, Map.of(a,
            List.of(PlannerFixtures.compiled(1, grow, a, true, ""))));
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock(a, storedA), amount, true, ECOCancellation.NONE);
    }

    private ComponentPlanningResult stockSatisfied(int id, AEKey key, long amount) {
        return component(id, Map.of(key, amount), Set.of(), zeroSolve(),
            CycleExecutionDisposition.STOCK_SATISFIED, Map.of(key, amount));
    }

    private ComponentPlanningResult ordered(int id, AEKey stockKey, long stockAmount,
            PlannerFixtures.Pattern pattern, long firings, boolean compactMetadata) {
        var compiled = PlannerFixtures.compiled(20 + id, pattern, a, true, "");
        CycleSolveResult solve = new CycleSolveResult(CycleSolveStatus.SUCCESS, Map.of(pattern, firings),
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(),
            compactMetadata ? List.of(new PatternRun(compiled, firings)) : List.of(), List.of(), CycleSolveMetrics.NONE);
        return component(id, Map.of(a, 100L), Set.of(pattern), solve,
            CycleExecutionDisposition.ORDERED_EXECUTION,
            stockAmount > 0 ? Map.of(stockKey, stockAmount) : Map.of());
    }

    private ComponentPlanningResult component(int id, Map<AEKey, Long> required,
            Set<IPatternDetails> executionPatterns, CycleSolveResult solve,
            CycleExecutionDisposition disposition, Map<AEKey, Long> reservations) {
        return new ComponentPlanningResult(id, ComponentPlanningResult.Type.CYCLIC,
            disposition == CycleExecutionDisposition.NOT_REQUIRED ? ComponentPlanningResult.Status.NOT_REQUIRED
                : ComponentPlanningResult.Status.SOLVED_NOT_EMITTED,
            required, executionPatterns, executionPatterns, solve == null ? CyclePlanningStatus.UNKNOWN_BUDGET
                : CyclePlanningStatus.SOLVED, null, Map.of(), null, solve, disposition, reservations);
    }

    private CycleSolveResult zeroSolve() {
        return new CycleSolveResult(CycleSolveStatus.SUCCESS, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
            Map.of(), List.of(), List.of(), List.of(), CycleSolveMetrics.NONE);
    }

    private PlanIdentity.Signature signature(Map<IPatternDetails, Long> tasks, Map<AEKey, Long> used) {
        return new PlanIdentity.Signature(goal, 1L, PlanIdentity.taskSignature(tasks), used, Map.of(), Map.of());
    }

    private static ComponentPlanningResult cycle(ComponentPlanner.Outcome outcome) {
        return outcome.components().stream()
            .filter(component -> component.type() == ComponentPlanningResult.Type.CYCLIC)
            .findFirst().orElseThrow();
    }

    private static KeyCounter stock(AEKey key, long amount) {
        KeyCounter result = new KeyCounter();
        if (amount > 0L) result.add(key, amount);
        return result;
    }
}
