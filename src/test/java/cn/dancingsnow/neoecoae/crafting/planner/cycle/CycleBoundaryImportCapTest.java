package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CycleBoundaryImportCapTest {
    private final AEKey a = mock(AEKey.class, "A"), b = mock(AEKey.class, "B");
    private final AEKey f = mock(AEKey.class, "F"), g = mock(AEKey.class, "G");
    private final CompiledPattern p1 = pattern(0, ordered(a, 1L, f, 1L), Map.of(b, 2L));
    private final CompiledPattern p2 = pattern(1, ordered(a, 1L, g, 1L), Map.of(b, 2L));
    private final CompiledPattern p3 = pattern(2, Map.of(b, 1L), Map.of(a, 1L));
    private final List<ComponentDependency> boundary = List.of(new ComponentDependency(0, 1, List.of(
        new CraftingGraphEdge(a, f, p1, p1.inputs().get(1)), new CraftingGraphEdge(a, g, p2, p2.inputs().get(1)))));
    private final CycleComponent cycle = new CycleComponent(0, List.of(a, b), List.of(p1, p2, p3),
        List.of(), List.of(), boundary);

    @Test void capSteersFiringsToTheUncappedAlternative() throws Exception {
        var request = new CycleSolveRequest(cycle, Map.of(b, 4L), Map.of(a, 1L), boundary, null);
        var open = solve(request);
        assertEquals(CycleSolveStatus.SUCCESS, open.status(), open.summary());

        var steered = solve(request.withBoundaryImportCaps(Map.of(f, PlannerAmount.ZERO)));
        assertEquals(CycleSolveStatus.SUCCESS, steered.status(), steered.summary());
        assertFalse(steered.exactPatternTimes().containsKey(p1.details()));
        assertTrue(steered.externalDemand().containsKey(g));
        assertFalse(steered.externalDemand().containsKey(f));
        assertTrue(steered.diagnostics().stream().anyMatch(d ->
            d.code() == CycleSolveDiagnostic.Code.BOUNDARY_IMPORT_CAPPED));

        var blocked = solve(request.withBoundaryImportCaps(Map.of(f, PlannerAmount.ZERO, g, PlannerAmount.ZERO)));
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, blocked.status(), blocked.summary());
        assertTrue(blocked.externalDemand().isEmpty());
    }

    @Test void capSplitsOwnedStockFromImport() throws Exception {
        // B = k + 1 for k firings of P1/P2, so B=3 needs two F, one owned and one imported.
        var request = new CycleSolveRequest(cycle, Map.of(b, 3L), Map.of(a, 1L, f, 1L), boundary, null)
            .withBoundaryImportCaps(Map.of(f, PlannerAmount.of(1L), g, PlannerAmount.ZERO));
        var result = solve(request);
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertTrue(result.seedShortfall().isEmpty());
        assertEquals(1L, result.requiredSeed().get(f));
        assertEquals(1L, result.externalDemand().get(f));
        assertFalse(result.externalDemand().containsKey(g));
        assertEquals(2L, result.patternTimes().get(p1.details()));

        var overrun = solve(new CycleSolveRequest(cycle, Map.of(b, 4L), Map.of(a, 1L, f, 1L), boundary, null)
            .withBoundaryImportCaps(Map.of(f, PlannerAmount.of(1L), g, PlannerAmount.ZERO)));
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, overrun.status(), overrun.summary());
        assertTrue(overrun.externalDemand().getOrDefault(f, 0L) <= 1L);
    }

    @Test void fullyReturnedCatalystNeedsOneCopy() throws Exception {
        AEKey k = mock(AEKey.class, "K"), fuel = mock(AEKey.class, "fuel"), t = mock(AEKey.class, "T");
        var make = pattern(0, ordered(k, 1L, fuel, 1L), ordered(t, 1L, k, 1L));
        var loop = new CraftingGraphEdge(k, k, make, make.inputs().getFirst());
        var catalyst = new CycleComponent(0, List.of(k), List.of(make), List.of(loop), List.of(), List.of());
        var result = solve(new CycleSolveRequest(catalyst, Map.of(t, 5L), Map.of(k, 1L, fuel, 5L), List.of(), null));
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertTrue(result.seedShortfall().isEmpty());
        assertEquals(1L, result.requiredSeed().get(k));
        assertEquals(5L, result.totalFirings());
    }

    private static CycleSolveResult solve(CycleSolveRequest request) throws Exception {
        return new BoundedCycleSolver().solve(request, ECOCancellation.NONE);
    }

    private static Map<AEKey, Long> ordered(AEKey first, long firstAmount, AEKey second, long secondAmount) {
        var map = new LinkedHashMap<AEKey, Long>();
        map.put(first, firstAmount);
        map.put(second, secondAmount);
        return map;
    }

    private static CompiledPattern pattern(int id, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
        var details = mock(IPatternDetails.class, "pattern" + id);
        var stacks = outputs.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
        when(details.getOutputs()).thenReturn(stacks);
        var semantics = new PatternSemantics(details, null, List.of(), stacks, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, stacks.getFirst().what(), PlannerAmount.of(stacks.getFirst().amount()),
            inputs.entrySet().stream().map(e -> new CompiledInput(null, e.getKey(), e.getValue(), true, null)).toList(),
            stacks, true, null, false, semantics);
    }
}
