package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.*;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CycleFailureCacheTest {
    private final AEKey a = mock(AEKey.class, "A");
    private final AEKey fuel = mock(AEKey.class, "fuel");

    @Test
    void reuseIgnoresComponentRenumberingButNotStockTargetsOrBoundary() throws Exception {
        var calls = new AtomicInteger();
        CycleSolver solver = (request, cancellation) -> {
            calls.incrementAndGet();
            return failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT);
        };
        var cache = new CycleFailureCache(solver);
        cache.solve(request(0, 2, 0, false), ECOCancellation.NONE);
        var reused = cache.solve(request(7, 2, 0, false), ECOCancellation.NONE);
        assertEquals(1, calls.get());
        assertTrue(reused.diagnostics().stream().anyMatch(d ->
            d.code() == CycleSolveDiagnostic.Code.PROVEN_FAILURE_REUSED));
        cache.solve(request(0, 3, 0, false), ECOCancellation.NONE);
        cache.solve(request(0, 2, 1, false), ECOCancellation.NONE);
        cache.solve(request(0, 2, 0, true), ECOCancellation.NONE);
        assertEquals(4, calls.get());
        new CycleFailureCache(solver).solve(request(0, 2, 0, false), ECOCancellation.NONE);
        assertEquals(5, calls.get(), "Proofs must not survive a planning invocation");
    }

    @Test
    void budgetExhaustionIsNeverAnImpossibilityProof() throws Exception {
        var calls = new AtomicInteger();
        var cache = new CycleFailureCache((request, cancellation) -> {
            calls.incrementAndGet();
            return failure(CycleSolveStatus.UNKNOWN_BUDGET);
        });
        cache.solve(request(0, 2, 0, false), ECOCancellation.NONE);
        cache.solve(request(0, 2, 0, false), ECOCancellation.NONE);
        assertEquals(2, calls.get());
    }

    @Test
    void cacheHitStillHonorsCancellation() throws Exception {
        var cache = new CycleFailureCache((request, cancellation) ->
            failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT));
        cache.solve(request(0, 2, 0, false), ECOCancellation.NONE);
        assertThrows(InterruptedException.class, () -> cache.solve(request(0, 2, 0, false), () -> {
            throw new InterruptedException("cancelled");
        }));
    }

    private CycleSolveRequest request(int id, long target, long stock, boolean boundary) {
        var edges = boundary ? List.of(new ComponentDependency(id, id + 1,
            List.of(new CraftingGraphEdge(a, fuel, null, null)))) : List.<ComponentDependency>of();
        var component = new CycleComponent(id, List.of(a), List.of(), List.of(), List.of(), edges);
        return new CycleSolveRequest(component, Map.of(a, target), Map.of(a, stock), edges,
            new CycleSolveRequest.PlannerOptions());
    }

    private static CycleSolveResult failure(CycleSolveStatus status) {
        return CycleSolveResult.failure(status,
            status == CycleSolveStatus.UNKNOWN_BUDGET ? CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED
                : CycleSolveDiagnostic.Code.PROVEN_INFEASIBLE_AT_CURRENT_STOCK, "test result");
    }
}
