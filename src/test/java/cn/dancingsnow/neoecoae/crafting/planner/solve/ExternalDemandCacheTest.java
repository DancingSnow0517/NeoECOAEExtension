package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExternalDemandCacheTest {
    private final AEKey output = key("boundary"), raw = key("raw"), member = key("cycle");
    private final CompiledPattern recipe = pattern(output, raw, 2L);
    private final CompiledNetwork network = network(recipe);
    private final CycleComponent cycle = cycle(0, List.of());
    private final AcyclicCraftingSolver solver = spy(new AcyclicCraftingSolver());
    private final ExternalDemandPlanner planner = new ExternalDemandPlanner(solver);

    @Test
    void identicalShortageAvoidsRepeatedExternalSolvesAcrossRenumberedComponents() throws Exception {
        var stock = stock(1L);
        var base = new SolveState(stock);
        var first = solve(network, cycle, 2L, stock, base);
        var renumbered = new CycleComponent(17, List.of(member), List.of(), List.of(), List.of(),
            List.of(new ComponentDependency(17, 23, List.of(new CraftingGraphEdge(member, output, null, null)))));
        var second = solve(network, renumbered, 2L, stock, base);

        assertEquals(CycleExternalDemandStatus.MISSING, second.status());
        assertEquals(Map.of(raw, 3L), first.missingLeaves());
        assertEquals(first.missingLeaves(), second.missingLeaves());
        assertNotSame(first.states().getFirst(), second.states().getFirst());
        assertExternalSolves(1);

        new ExternalDemandPlanner(solver).solveDemands(network, cycle, Map.of(output, 2L), stock, base,
            Map.of(), Set.of(), ECOCancellation.NONE);
        assertExternalSolves(2); // A later invocation owns fresh proofs.
    }

    @Test
    void cachedSuccessfulStatesAndReservationsAreIsolatedFromTheirConsumers() throws Exception {
        var stock = stock(10L);
        stock.add(member, 2L);
        var base = new SolveState(stock);
        Map<AEKey, Long> demands = new LinkedHashMap<>();
        demands.put(member, 1L);
        demands.put(output, 2L);
        var first = planner.solveDemands(network, cycle, demands, stock, base, Map.of(), Set.of(), ECOCancellation.NONE);
        assertTrue(first.solved());
        var state = first.states().getFirst();
        var originalProvenance = state.provenance.copy().freeze();
        state.used.add(raw, 100L);
        state.patternTimes.clear();
        state.provenance.replaceWith(new cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialProvenance());
        first.directReservations().set(member, 100L);
        first.selectedPatterns().clear();

        var second = planner.solveDemands(network, cycle, demands, stock, base, Map.of(), Set.of(), ECOCancellation.NONE);
        assertEquals(1L, second.directReservations().get(member));
        assertEquals(4L, second.states().getFirst().usedItems().get(raw));
        assertEquals(Map.of(recipe.details(), 2L), second.states().getFirst().patternTimes());
        assertEquals(Set.of(recipe.details()), second.selectedPatterns());
        assertEquals(originalProvenance, second.states().getFirst().executionProvenance());
        second.states().getFirst().executionProvenance().requireComplete();
        second.states().getFirst().used.add(raw, 100L);
        second.directReservations().set(member, 100L);

        var third = planner.solveDemands(network, cycle, demands, stock, base, Map.of(), Set.of(), ECOCancellation.NONE);
        assertEquals(4L, third.states().getFirst().usedItems().get(raw));
        assertEquals(1L, third.directReservations().get(member));
        assertExternalSolves(1);
        assertEquals(10L, stock.get(raw));
        assertTrue(base.usedItems().isEmpty());
    }

    @Test
    void cachedMissingStatesAndLeafCountsAreIsolated() throws Exception {
        var stock = stock(1L);
        var base = new SolveState(stock);
        var first = solve(network, cycle, 2L, stock, base);
        first.missingLeaves().clear();
        first.states().getFirst().missing.set(raw, PlannerAmount.ZERO);
        var second = solve(network, cycle, 2L, stock, base);

        assertEquals(Map.of(raw, 3L), second.missingLeaves());
        assertEquals(3L, second.states().getFirst().missingItems().get(raw));
        assertExternalSolves(1);
    }

    @Test
    void anUnrepresentableLargeDemandIsSolvedOnceAndKeepsItsExactDiagnostic() throws Exception {
        var stock = new KeyCounter();
        var base = new SolveState(stock);
        var first = solve(network, cycle, Long.MAX_VALUE, stock, base);
        var second = solve(network, cycle, Long.MAX_VALUE, stock, base);

        assertEquals(CycleExternalDemandStatus.UNREPRESENTABLE, second.status());
        assertEquals(first.diagnostic(), second.diagnostic());
        assertTrue(second.diagnostic().contains("18446744073709551614"), second.diagnostic());
        assertExternalSolves(1);
    }

    @Test
    void changedDemandRemainingInventoryOrReservationsRequiresANewSolve() throws Exception {
        var stock = stock(10L);
        var base = new SolveState(stock);
        assertTrue(solve(network, cycle, 2L, stock, base).solved());
        assertEquals(6L, solve(network, cycle, 3L, stock, base).states().getFirst().usedItems().get(raw));
        stock.set(raw, 1L);
        assertEquals(Map.of(raw, 3L), solve(network, cycle, 2L, stock, base).missingLeaves());
        stock.set(raw, 10L);
        base.used.set(raw, PlannerAmount.of(9L));
        assertEquals(Map.of(raw, 3L), solve(network, cycle, 2L, stock, base).missingLeaves());
        assertExternalSolves(3); // Equivalent remaining inventory can share a proof.

        base.used.set(raw, PlannerAmount.ZERO);
        var reserved = planner.solveDemands(network, cycle, Map.of(output, 2L), stock, base,
            Map.of(raw, 9L), Set.of(), ECOCancellation.NONE);
        assertEquals(Map.of(raw, 3L), reserved.missingLeaves());
        assertExternalSolves(4);
    }

    @Test
    void changedNetworkMembersOrExcludedRecipesRequiresANewSolve() throws Exception {
        var stock = stock(4L);
        var base = new SolveState(stock);
        assertTrue(solve(network, cycle, 2L, stock, base).solved());
        var excluded = solve(network, cycle(0, List.of(recipe)), 2L, stock, base);
        assertEquals(CycleExternalDemandStatus.MISSING, excluded.status());
        assertEquals(Map.of(output, 2L), excluded.missingLeaves());

        var differentMembers = new CycleComponent(0, List.of(output), List.of(recipe), List.of(), List.of(), List.of());
        assertEquals(CycleExternalDemandStatus.FORBIDDEN_ROUTE,
            solve(network, differentMembers, 2L, stock, base).status());

        var changedRecipe = pattern(output, raw, 3L);
        assertEquals(Map.of(raw, 2L), solve(network(changedRecipe), cycle, 2L, stock, base).missingLeaves());
        assertExternalSolves(4);
    }

    @Test
    void unboundedSupplyDelegationAndSubstitutionSettingsRemainDistinct() throws Exception {
        var stock = new KeyCounter();
        stock.add(output, Long.MAX_VALUE);
        var finite = new SolveState(stock);
        var creative = new SolveState(PlannerInventorySnapshot.of(stock, Set.of(output)));
        var delegated = planner.solveDemands(network, cycle, Map.of(output, 1L), stock, finite,
            Map.of(), Set.of(output), false, ECOCancellation.NONE);
        assertEquals(Map.of(output, 1L), delegated.delegatedCycleDemands());

        var creativeResult = planner.solveDemands(network, cycle, Map.of(output, 1L), stock, creative,
            Map.of(), Set.of(output), false, ECOCancellation.NONE);
        assertTrue(creativeResult.delegatedCycleDemands().isEmpty());
        assertEquals(1L, creativeResult.directReservations().get(output));
        var nonDelegated = planner.solveDemands(network, cycle, Map.of(output, 1L), stock, finite,
            Map.of(), Set.of(), false, ECOCancellation.NONE);
        assertTrue(nonDelegated.delegatedCycleDemands().isEmpty());
        assertEquals(1L, nonDelegated.directReservations().get(output));

        var rawStock = stock(10L);
        var base = new SolveState(rawStock);
        solve(network, cycle, 2L, rawStock, base);
        planner.solveDemands(network, cycle, Map.of(output, 2L), rawStock, base,
            Map.of(), Set.of(), true, ECOCancellation.NONE);
        assertExternalSolves(2);
    }

    @Test
    void cacheHitsHonorCancellationAndUnknownSearchResultsAreRetried() throws Exception {
        var stock = stock(10L);
        var base = new SolveState(stock);
        solve(network, cycle, 2L, stock, base);
        assertThrows(InterruptedException.class, () -> planner.solveDemands(network, cycle,
            Map.of(output, 2L), stock, base, Map.of(), Set.of(), () -> { throw new InterruptedException("cancelled"); }));
        assertExternalSolves(1);

        var unknownSolver = mock(AcyclicCraftingSolver.class);
        var unknown = new AcyclicCraftingSolver.Outcome(PlanningStatus.CYCLE_UNRESOLVED, new SolveState(stock), new ECOPlanTrace());
        when(unknownSolver.solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any())).thenReturn(unknown);
        var unknownPlanner = new ExternalDemandPlanner(unknownSolver);
        for (int i = 0; i < 2; i++) assertEquals(CycleExternalDemandStatus.UNSUPPORTED,
            unknownPlanner.solveDemands(network, cycle, Map.of(output, 2L), stock, base,
                Map.of(), Set.of(), ECOCancellation.NONE).status());
        verify(unknownSolver, times(2)).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
    }

    @Test
    void anInterruptedSolveDoesNotLeaveACachedFailure() throws Exception {
        var stock = stock(10L);
        var base = new SolveState(stock);
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 1L, Long.MAX_VALUE, () -> 0L);
        assertThrows(ECOPlanningBudget.Exhausted.class, () -> planner.solveDemands(network, cycle,
            Map.of(output, 2L), stock, base, Map.of(), Set.of(), budget));
        assertTrue(solve(network, cycle, 2L, stock, base).solved());
        assertExternalSolves(1);
    }

    private ExternalDemandPlanner.Outcome solve(CompiledNetwork graph, CycleComponent component, long amount,
            KeyCounter stock, SolveState base) throws Exception {
        return planner.solveDemands(graph, component, Map.of(output, amount), stock, base,
            Map.of(), Set.of(), ECOCancellation.NONE);
    }

    private void assertExternalSolves(int count) throws Exception {
        verify(solver, times(count)).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
    }

    private KeyCounter stock(long amount) {
        var result = new KeyCounter();
        result.add(raw, amount);
        return result;
    }

    private CompiledNetwork network(CompiledPattern producer) {
        return new CompiledNetwork(output, Map.of(output, List.of(producer), raw, List.of()), Set.of(), 1, 1);
    }

    private CycleComponent cycle(int id, List<CompiledPattern> excluded) {
        return new CycleComponent(id, List.of(member), excluded, List.of(), List.of(), List.of());
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(AEKey output, AEKey input, long amount) {
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(output, 1L));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(0, details, output, PlannerAmount.ONE,
            List.of(new CompiledInput(null, input, amount, true, null)), outputs, true, null, false, semantics);
    }
}
