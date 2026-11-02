package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnsupportedRouteFallbackTest {
    private final AEKey goal = key("boundary");
    private final AEKey blocked = key("blocked");
    private final AEKey stock = key("stock");
    private final AEKey cycleKey = key("cycle");
    private final CompiledPattern preferred = pattern(0, goal, true,
        new GenericStack(stock, 1), new GenericStack(blocked, 1));
    private final CompiledPattern alternate = pattern(1, goal, true, new GenericStack(stock, 2));
    private final CompiledPattern unsupported = pattern(2, blocked, false);

    @Test
    void unsupportedChildRetriesParentAndRollsBackStock() throws Exception {
        var inventory = inventory();
        var result = new AcyclicCraftingSolver().solve(network(true),
            new AcyclicRoutePlan(List.of(goal, stock, blocked)), inventory, 1, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(2L, result.state().usedItems().get(stock));
        assertEquals(Map.of(alternate.details(), 1L), result.state().patternTimes());
        assertTrue(result.state().unsupported.isEmpty());
        assertEquals(2L, inventory.get(stock), "Input snapshot must remain untouched");
    }

    @Test
    void cycleBoundaryCanUseAnAlternateParentRoute() throws Exception {
        var result = external(true);
        assertEquals(CycleExternalDemandStatus.SOLVED, result.status());
        assertEquals(Set.of(alternate.details()), result.selectedPatterns());
        assertEquals(2L, result.states().getFirst().usedItems().get(stock));
    }

    @Test
    void missingThenUnsupportedCandidateStillReachesTheThirdRoute() throws Exception {
        var missing = key("missing");
        var first = pattern(3, goal, true, new GenericStack(missing, 1));
        var network = new CompiledNetwork(goal, Map.of(goal, List.of(first, preferred, alternate),
            missing, List.of(), blocked, List.of(unsupported), stock, List.of()), Set.of(), 4, 4);
        var result = new AcyclicCraftingSolver().solve(network,
            new AcyclicRoutePlan(List.of(goal, missing)), inventory(), 1, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(Map.of(alternate.details(), 1L), result.state().patternTimes());
        assertTrue(result.state().missingItems().isEmpty());
        assertEquals(2L, result.state().usedItems().get(stock));
    }

    @Test
    void unsupportedLastAlternativeDoesNotEraseCompleteShortage() throws Exception {
        var missing = key("missing");
        var first = pattern(3, goal, true, new GenericStack(missing, 4));
        var network = new CompiledNetwork(goal, Map.of(goal, List.of(first, preferred),
            missing, List.of(), blocked, List.of(unsupported), stock, List.of()), Set.of(), 3, 3);
        var result = new AcyclicCraftingSolver().solve(network,
            new AcyclicRoutePlan(List.of(goal, missing)), inventory(), 1000, ECOCancellation.NONE);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertEquals(4000L, result.state().missingItems().get(missing));
        assertTrue(result.state().unsupported.isEmpty());
    }

    @Test
    void largeBoundaryDemandRemainsAggregatedAcrossFallback() throws Exception {
        var inventory = new KeyCounter();
        inventory.add(stock, 1_000_000_000L);
        var cycle = new CycleComponent(0, List.of(cycleKey), List.of(), List.of(), List.of(), List.of());
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(network(true),
            cycle, Map.of(goal, 500_000_000L), inventory, new SolveState(inventory), Map.of(), Set.of(),
            ECOCancellation.NONE);
        assertEquals(CycleExternalDemandStatus.SOLVED, result.status());
        assertEquals(Map.of(alternate.details(), 500_000_000L), result.states().getFirst().patternTimes());
        assertEquals(1_000_000_000L, result.states().getFirst().usedItems().get(stock));
    }

    @Test
    void exhaustedBoundaryReportsTheKeyAndUnderlyingReason() throws Exception {
        var result = external(false);
        assertEquals(CycleExternalDemandStatus.UNSUPPORTED, result.status());
        assertTrue(result.diagnostic().contains("boundary"), result.diagnostic());
        assertTrue(result.diagnostic().contains("blocked"), result.diagnostic());
        assertTrue(result.diagnostic().contains("test unsupported contract"), result.diagnostic());
        assertTrue(result.states().isEmpty(), "Failed plans must not be committed");
    }

    private ExternalDemandPlanner.Outcome external(boolean withAlternate) throws Exception {
        var inventory = inventory();
        var cycle = new CycleComponent(0, List.of(cycleKey), List.of(), List.of(), List.of(), List.of());
        return new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(network(withAlternate),
            cycle, Map.of(goal, 1L), inventory, new SolveState(inventory), Map.of(), Set.of(), ECOCancellation.NONE);
    }

    private CompiledNetwork network(boolean withAlternate) {
        return new CompiledNetwork(goal, Map.of(goal, withAlternate ? List.of(preferred, alternate) : List.of(preferred),
            blocked, List.of(unsupported), stock, List.of()), Set.of(), withAlternate ? 3 : 2, 3);
    }

    private KeyCounter inventory() {
        var inventory = new KeyCounter();
        inventory.add(stock, 2);
        return inventory;
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(int id, AEKey output, boolean supported, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(output, 1));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, output, PlannerAmount.ONE,
            java.util.Arrays.stream(inputs).map(i -> new CompiledInput(null, i.what(), i.amount(), false, null)).toList(),
            outputs, supported, supported ? null : "test unsupported contract", false, semantics);
    }
}
