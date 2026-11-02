package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
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
        // Not even as a dominance proof for a harder request.
        cache.solveVerdict(request(0, 3, 0, false), ECOCancellation.NONE);
        assertEquals(3, calls.get());
    }

    @Test
    void impossibilityUnderImportCapsIsNotReusedWithoutThem() throws Exception {
        var calls = new AtomicInteger();
        var cache = new CycleFailureCache((request, cancellation) -> {
            calls.incrementAndGet();
            return failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT);
        });
        var capped = request(0, 2, 0, true).withBoundaryImportCaps(Map.of(fuel, PlannerAmount.ZERO));
        cache.solve(capped, ECOCancellation.NONE);
        cache.solve(capped, ECOCancellation.NONE);
        assertEquals(1, calls.get());
        cache.solve(request(0, 2, 0, true), ECOCancellation.NONE);
        assertEquals(2, calls.get());
        cache.solve(request(0, 2, 0, true).withBoundaryImportCaps(Map.of(fuel, PlannerAmount.of(1L))),
            ECOCancellation.NONE);
        assertEquals(3, calls.get());
    }

    @Test
    void cacheHitStillHonorsCancellation() throws Exception {
        var cache = new CycleFailureCache((request, cancellation) ->
            failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT));
        cache.solve(request(0, 2, 0, false), ECOCancellation.NONE);
        assertThrows(InterruptedException.class, () -> cache.solve(request(0, 2, 0, false), () -> {
            throw new InterruptedException("cancelled");
        }));
        assertThrows(InterruptedException.class, () -> cache.solveVerdict(request(0, 3, 0, false), () -> {
            throw new InterruptedException("cancelled");
        }));
    }

    @Test
    void verdictCallersReuseAProofForHarderRequestsOnly() throws Exception {
        var calls = new AtomicInteger();
        var cache = new CycleFailureCache((request, cancellation) -> {
            calls.incrementAndGet();
            return failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT);
        });
        cache.solve(request(0, 10, 5, false), ECOCancellation.NONE);
        // Asks for more, owns less: the earlier impossibility proof covers it, whatever the component id.
        var reused = cache.solveVerdict(request(3, 12, 3, false), ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, reused.status());
        assertTrue(reused.diagnostics().stream().anyMatch(d ->
            d.code() == CycleSolveDiagnostic.Code.PROVEN_FAILURE_REUSED));
        assertEquals(1, calls.get());
        assertEquals(1, cache.reusedFailures());
        // Asks for less, or owns more: each needs its own solve.
        cache.solveVerdict(request(0, 8, 5, false), ECOCancellation.NONE);
        cache.solveVerdict(request(0, 12, 6, false), ECOCancellation.NONE);
        assertEquals(3, calls.get());
        // Plain solve keeps exact-match reuse only, so callers that read the shortfall get their own numbers.
        cache.solve(request(0, 13, 2, false), ECOCancellation.NONE);
        assertEquals(4, calls.get());
    }

    @Test
    void boundaryCapsOnlyDominateWhenTheyAreTighter() throws Exception {
        var calls = new AtomicInteger();
        var cache = new CycleFailureCache((request, cancellation) -> {
            calls.incrementAndGet();
            return failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT);
        });
        cache.solve(request(0, 10, 5, true).withBoundaryImportCaps(Map.of(fuel, PlannerAmount.of(2L))),
            ECOCancellation.NONE);
        cache.solveVerdict(request(0, 10, 5, true).withBoundaryImportCaps(Map.of(fuel, PlannerAmount.of(1L))),
            ECOCancellation.NONE);
        assertEquals(1, calls.get());
        // A looser cap, or no cap at all (unlimited imports), may succeed where the capped proof failed.
        cache.solveVerdict(request(0, 10, 5, true).withBoundaryImportCaps(Map.of(fuel, PlannerAmount.of(3L))),
            ECOCancellation.NONE);
        cache.solveVerdict(request(0, 10, 5, true), ECOCancellation.NONE);
        assertEquals(3, calls.get());
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
