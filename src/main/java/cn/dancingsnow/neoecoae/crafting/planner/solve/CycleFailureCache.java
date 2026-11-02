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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Invocation-local proofs only. Boundary/seed provisioning is still checked by each caller. */
final class CycleFailureCache {
    /** Dominance checks are linear in the remembered failures; keep them a small, recent working set. */
    private static final int MAX_FAILURES_PER_STRUCTURE = 64;

    private final CycleSolver solver;
    private final Map<Fingerprint, CycleSolveResult> failures = new HashMap<>();
    private final Map<Structure, List<Proof>> proofsByStructure = new HashMap<>();
    private int reusedFailures;
    private int solverCalls;

    CycleFailureCache(CycleSolver solver) { this.solver = solver; }

    int solverCalls() { return solverCalls; }

    int reusedFailures() { return reusedFailures; }

    CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        Fingerprint key = Fingerprint.of(request);
        CycleSolveResult previous = failures.get(key);
        if (previous != null) {
            reusedFailures++;
            return previous.withAdditionalDiagnostics(List.of(new CycleSolveDiagnostic(
                CycleSolveDiagnostic.Code.PROVEN_FAILURE_REUSED,
                "Reused current-stock impossibility proof for identical structure, targets, stock and boundary")));
        }
        solverCalls++;
        CycleSolveResult result = solver.solve(request, cancellation);
        // This status is a proof under the CycleSolver contract. UNKNOWN_BUDGET and capability
        // failures never become nogoods, and a cached seed proposal never bypasses recovery.
        if (result.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT) {
            failures.put(key, result);
            List<Proof> proofs = proofsByStructure.computeIfAbsent(key.structure(), ignored -> new ArrayList<>());
            if (proofs.size() >= MAX_FAILURES_PER_STRUCTURE) proofs.remove(0);
            proofs.add(new Proof(key.required(), key.stock(), key.boundaryImportCaps(), result));
        }
        return result;
    }

    /**
     * For callers that only branch on the verdict. INSUFFICIENT_EXTERNAL_INPUT is monotone: with the same
     * structure, a request that asks for at least as much, owns at most the same stock and may import no more on
     * any boundary key cannot succeed either. Such a request reuses the earlier proof. Its seed shortfall and
     * startup proposals describe the earlier request, so callers that read them must use {@link #solve}.
     */
    CycleSolveResult solveVerdict(CycleSolveRequest request, ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        Fingerprint key = Fingerprint.of(request);
        if (!failures.containsKey(key)) {
            for (Proof proof : proofsByStructure.getOrDefault(key.structure(), List.of())) {
                cancellation.checkpoint();
                if (!proof.dominates(key)) continue;
                reusedFailures++;
                return proof.result().withAdditionalDiagnostics(List.of(new CycleSolveDiagnostic(
                    CycleSolveDiagnostic.Code.PROVEN_FAILURE_REUSED,
                    "Reused impossibility proof of a request with no more targets, at least as much stock and no"
                        + " tighter boundary imports for the same cycle structure")));
            }
        }
        return solve(request, cancellation);
    }

    /** Structure part of the fingerprint: everything except amounts. */
    private record Structure(Set<AEKey> members, List<CompiledPattern> patterns, Set<CraftingGraphEdge> internalEdges,
            Set<CraftingGraphEdge> boundaryEdges, CycleSolveLimits limits) {}

    private record Proof(Map<AEKey, PlannerAmount> required, Map<AEKey, Long> stock,
            Map<AEKey, PlannerAmount> caps, CycleSolveResult result) {
        boolean dominates(Fingerprint request) {
            for (var entry : required.entrySet()) {
                PlannerAmount asked = request.required().getOrDefault(entry.getKey(), PlannerAmount.ZERO);
                if (asked.compareTo(entry.getValue()) < 0) return false;
            }
            for (var entry : request.stock().entrySet()) {
                if (entry.getValue() > stock.getOrDefault(entry.getKey(), 0L)) return false;
            }
            // An uncapped boundary key imports without limit; a request may only tighten what the proof allowed.
            for (var entry : caps.entrySet()) {
                PlannerAmount allowed = request.boundaryImportCaps().get(entry.getKey());
                if (allowed == null || allowed.compareTo(entry.getValue()) > 0) return false;
            }
            return true;
        }
    }

    /** Component numbering can change when unrelated routes change; it is not solver state. */
    private record Fingerprint(Set<AEKey> members, List<CompiledPattern> patterns,
            Set<CraftingGraphEdge> internalEdges, Set<CraftingGraphEdge> boundaryEdges,
            Map<AEKey, PlannerAmount> required, Map<AEKey, Long> stock, CycleSolveLimits limits,
            Map<AEKey, PlannerAmount> boundaryImportCaps) {
        static Fingerprint of(CycleSolveRequest request) {
            return new Fingerprint(new HashSet<>(request.component().members()),
                request.component().patterns().stream().sorted(Comparator.comparingInt(CompiledPattern::id)).toList(),
                new HashSet<>(request.component().internalEdges()),
                request.externalResourceBoundary().stream().flatMap(edge -> edge.relationships().stream())
                    .collect(Collectors.toSet()),
                request.plannerRequiredOutputs(), request.availableRelevantStock(), request.options().limits(),
                request.boundaryImportCaps());
        }

        Structure structure() {
            return new Structure(members, patterns, internalEdges, boundaryEdges, limits);
        }
    }
}
