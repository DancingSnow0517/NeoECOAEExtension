package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.*;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.*;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CycleStartupRecoveryTest {
    private final AEKey seed = key("seed");
    private final AEKey raw = key("raw");
    private final AEKey fuel = key("fuel");
    private final CompiledPattern growth = pattern(0, seed, 2, new GenericStack(seed, 1), new GenericStack(fuel, 1));
    private final CompiledPattern bootstrap = pattern(1, seed, 1, new GenericStack(raw, 1));

    @Test
    void recoveredWitnessReplansItsActualBoundaryDemand() throws Exception {
        var outcome = plan(success(), 10);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), outcome.trace().diagnostics().toString());
        assertEquals(Map.of(growth.details(), 10L, bootstrap.details(), 1L), outcome.state().patternTimes());
        assertEquals(1L, outcome.state().usedItems().get(raw));
        assertEquals(10L, outcome.state().usedItems().get(fuel));
    }

    @Test
    void recoveredBoundaryShortageReportsTheActualLeafAndCommitsNothing() throws Exception {
        var outcome = plan(success(), 3);
        assertEquals(7L, outcome.state().missingItems().get(fuel));
        assertTrue(outcome.state().patternTimes().isEmpty());
        assertTrue(outcome.state().usedItems().isEmpty());
        assertEquals(CycleExternalDemandStatus.MISSING, outcome.components().stream()
            .filter(c -> c.type() == ComponentPlanningResult.Type.CYCLIC).findFirst().orElseThrow().externalDemandStatus());
    }

    @Test
    void recoveredUnrepresentableResultKeepsItsStatus() throws Exception {
        var outcome = plan(result(CycleSolveStatus.UNREPRESENTABLE, Map.of(), Map.of(), Map.of()), 10);
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, outcome.status());
        assertTrue(outcome.state().patternTimes().isEmpty());
    }

    @Test
    void stillInsufficientAfterRecoveryReportsUncommittedSeedDemand() throws Exception {
        var outcome = plan(result(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, Map.of(), Map.of(), Map.of(seed, 2L)), 10);
        assertEquals(3L, outcome.state().missingItems().get(seed),
            "The one projected seed was never committed; include it along with the new shortfall");
        assertTrue(outcome.state().patternTimes().isEmpty());
        assertTrue(outcome.state().usedItems().isEmpty());
    }

    private ComponentPlanner.Outcome plan(CycleSolveResult recovered, long fuelStock) throws Exception {
        var network = new CompiledNetwork(seed, Map.of(seed, List.of(growth, bootstrap), raw, List.of(), fuel, List.of()),
            Set.of(), 2, 3);
        var selection = new ActiveRouteSelector().select(new CraftingGraphBuilder().build(network, ECOCancellation.NONE),
            false, ECOCancellation.NONE);
        var inventory = new KeyCounter();
        inventory.add(raw, 1);
        inventory.add(fuel, fuelStock);
        var calls = new AtomicInteger();
        CycleSolver solver = (request, cancellation) -> calls.getAndIncrement() == 0
            ? result(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, Map.of(), Map.of(), Map.of(seed, 1L)) : recovered;
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), solver)
            .plan(network, selection, inventory, 10, true, ECOCancellation.NONE);
        assertEquals(2, calls.get());
        return outcome;
    }

    private CycleSolveResult success() {
        return result(CycleSolveStatus.SUCCESS, Map.of(growth.details(), 10L), Map.of(fuel, 10L), Map.of());
    }

    private CycleSolveResult result(CycleSolveStatus status, Map<IPatternDetails, Long> times,
            Map<AEKey, Long> external, Map<AEKey, Long> shortfall) {
        return new CycleSolveResult(status, times, external, Map.of(seed, 1L), shortfall,
            Map.of(seed, 20L), Map.of(seed, 11L), List.of(), List.of(), CycleSolveMetrics.NONE);
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(int id, AEKey output, long amount, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(output, amount));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, output, PlannerAmount.of(amount),
            java.util.Arrays.stream(inputs).map(i -> new CompiledInput(null, i.what(), i.amount(), false, null)).toList(),
            outputs, true, null, false, semantics);
    }
}
