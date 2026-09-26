package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveDiagnostic;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveLimits;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Invocation-local proofs only. Boundary/seed provisioning is still checked by each caller. */
final class CycleFailureCache {
    private final CycleSolver solver;
    private final Map<Fingerprint, CycleSolveResult> failures = new HashMap<>();

    CycleFailureCache(CycleSolver solver) { this.solver = solver; }

    CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        Fingerprint key = Fingerprint.of(request);
        CycleSolveResult previous = failures.get(key);
        if (previous != null) {
            return previous.withAdditionalDiagnostics(List.of(new CycleSolveDiagnostic(
                CycleSolveDiagnostic.Code.PROVEN_FAILURE_REUSED,
                "Reused current-stock impossibility proof for identical structure, targets, stock and boundary")));
        }
        CycleSolveResult result = solver.solve(request, cancellation);
        // This status is a proof under the CycleSolver contract. UNKNOWN_BUDGET and capability
        // failures never become nogoods, and a cached seed proposal never bypasses recovery.
        if (result.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT) failures.put(key, result);
        return result;
    }

    /** Component numbering can change when unrelated routes change; it is not solver state. */
    private record Fingerprint(Set<AEKey> members, List<CompiledPattern> patterns,
            Set<CraftingGraphEdge> internalEdges, Set<CraftingGraphEdge> boundaryEdges,
            Map<AEKey, PlannerAmount> required, Map<AEKey, Long> stock, CycleSolveLimits limits) {
        static Fingerprint of(CycleSolveRequest request) {
            return new Fingerprint(Set.copyOf(request.component().members()),
                request.component().patterns().stream().sorted(Comparator.comparingInt(CompiledPattern::id)).toList(),
                Set.copyOf(request.component().internalEdges()),
                request.externalResourceBoundary().stream().flatMap(edge -> edge.relationships().stream())
                    .collect(Collectors.toUnmodifiableSet()),
                request.plannerRequiredOutputs(), request.availableRelevantStock(), request.options().limits());
        }
    }
}
