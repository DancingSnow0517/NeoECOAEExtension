package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Complete answer of one cycle solve. Every numeric field is expressed in the SCC's own terms:
 *
 * <ul>
 *   <li>{@code patternTimes} — how many times each SCC pattern fires.</li>
 *   <li>{@code externalDemand} — material the SCC boundary must deliver, net of the SCC's own byproducts.</li>
 *   <li>{@code requiredSeed} — start-up stock the witness needs before the loop is self-sustaining.</li>
 *   <li>{@code seedShortfall} — the part of {@code requiredSeed} the stock snapshot cannot cover.</li>
 *   <li>{@code producedOutputs} — gross production of the witness, byproducts included.</li>
 *   <li>{@code deliverableOutputs} — what is on hand for the required keys once the witness has run.</li>
 *   <li>{@code executionWitness} — a concrete, replayable, never-negative firing order.</li>
 *   <li>{@code executionPlan} — the same work in compact {@code pattern × count} form.</li>
 * </ul>
 *
 * <p>{@code requiredSeed} together with {@code externalDemand} is exactly what the witness needs:
 * replaying the witness from {@code requiredSeed}, importing {@code externalDemand} across the
 * boundary, keeps every stock level at or above zero.
 *
 * <p>{@code executionPlan} is metadata, never a second source of truth. For an expanded witness it is the
 * order-preserving run-length encoding of {@code executionWitness}. The bounded solver may also keep a verified
 * multi-pattern witness in compact batch form when expanding it would be proportional to millions of firings; in
 * that case the witness list is empty and the ordered {@code executionPlan} is the available execution trace.
 */
public record CycleSolveResult(
    CycleSolveStatus status,
    Map<IPatternDetails, Long> patternTimes,
    Map<AEKey, Long> externalDemand,
    Map<AEKey, Long> requiredSeed,
    Map<AEKey, Long> seedShortfall,
    Map<AEKey, Long> producedOutputs,
    Map<AEKey, Long> deliverableOutputs,
    List<CycleFiring> executionWitness,
    List<PatternRun> executionPlan,
    List<CycleSolveDiagnostic> diagnostics,
    CycleSolveMetrics metrics
) {
    public CycleSolveResult {
        patternTimes = Map.copyOf(patternTimes);
        externalDemand = Map.copyOf(externalDemand);
        requiredSeed = Map.copyOf(requiredSeed);
        seedShortfall = Map.copyOf(seedShortfall);
        producedOutputs = Map.copyOf(producedOutputs);
        deliverableOutputs = Map.copyOf(deliverableOutputs);
        executionWitness = List.copyOf(executionWitness);
        executionPlan = List.copyOf(executionPlan);
        diagnostics = List.copyOf(diagnostics);
        if (status == CycleSolveStatus.SUCCESS && !seedShortfall.isEmpty()) {
            throw new IllegalArgumentException("A successful cycle solve cannot report a seed shortfall");
        }
        if (!executionWitness.isEmpty() && totalRunCount(executionPlan) != executionWitness.size()) {
            throw new IllegalArgumentException("A compact execution plan must account for every witness step");
        }
    }

    /** Legacy shape: the compact plan is the run-length encoding of the per-firing witness. */
    public CycleSolveResult(CycleSolveStatus status, Map<IPatternDetails, Long> patternTimes,
            Map<AEKey, Long> externalDemand, Map<AEKey, Long> requiredSeed, Map<AEKey, Long> seedShortfall,
            Map<AEKey, Long> producedOutputs, Map<AEKey, Long> deliverableOutputs,
            List<CycleFiring> executionWitness, List<CycleSolveDiagnostic> diagnostics,
            CycleSolveMetrics metrics) {
        this(status, patternTimes, externalDemand, requiredSeed, seedShortfall, producedOutputs,
            deliverableOutputs, executionWitness, compress(executionWitness), diagnostics, metrics);
    }

    /** Order-preserving run-length encoding; it never merges non-adjacent runs. */
    private static List<PatternRun> compress(List<CycleFiring> witness) {
        List<PatternRun> runs = new ArrayList<>();
        CompiledPattern current = null;
        long count = 0;
        for (CycleFiring firing : witness) {
            if (current != null && current.details() == firing.pattern().details()) {
                count++;
                continue;
            }
            if (current != null) runs.add(new PatternRun(current, count));
            current = firing.pattern();
            count = 1;
        }
        if (current != null) runs.add(new PatternRun(current, count));
        return List.copyOf(runs);
    }

    private static long totalRunCount(List<PatternRun> runs) {
        long total = 0;
        for (PatternRun run : runs) total = Math.addExact(total, run.count());
        return total;
    }

    public static CycleSolveResult failure(CycleSolveStatus status, List<CycleSolveDiagnostic> diagnostics,
            CycleSolveMetrics metrics) {
        return new CycleSolveResult(status, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(),
            diagnostics, metrics);
    }

    public static CycleSolveResult failure(CycleSolveStatus status, CycleSolveDiagnostic.Code code, String message) {
        return failure(status, List.of(new CycleSolveDiagnostic(code, message)), CycleSolveMetrics.NONE);
    }

    public static CycleSolveResult cancelled() {
        return failure(CycleSolveStatus.CANCELLED, CycleSolveDiagnostic.Code.CANCELLED,
            "Cycle solving was cancelled");
    }

    public static CycleSolveResult notImplemented(String message) {
        return failure(CycleSolveStatus.NOT_IMPLEMENTED, CycleSolveDiagnostic.Code.NOT_IMPLEMENTED, message);
    }

    /** Pure annotation: same answer, extra explanation. Used to record why a fast path stepped aside. */
    public CycleSolveResult withAdditionalDiagnostics(List<CycleSolveDiagnostic> extra) {
        if (extra.isEmpty()) return this;
        List<CycleSolveDiagnostic> merged = new ArrayList<>(extra);
        merged.addAll(diagnostics);
        return new CycleSolveResult(status, patternTimes, externalDemand, requiredSeed, seedShortfall,
            producedOutputs, deliverableOutputs, executionWitness, executionPlan, merged, metrics);
    }

    /** Exact total firings in the plan; it may exceed the legacy long projection. */
    public PlannerAmount plannerTotalFirings() {
        PlannerAmount total = PlannerAmount.ZERO;
        for (long count : patternTimes.values()) total = total.add(count);
        return total;
    }

    /**
     * Legacy long accessor. Callers that cannot prove representability must use
     * {@link #plannerTotalFirings()} instead; this conversion intentionally never saturates or truncates.
     */
    public long totalFirings() {
        return plannerTotalFirings().longValueExact();
    }

    public String summary() {
        String detail = diagnostics.stream()
            .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
            .collect(Collectors.joining("; "));
        return detail.isEmpty() ? status.name() : status.name() + " (" + detail + ")";
    }

    /** Positive-only view, convenient for merging into planner reporting. */
    public Map<AEKey, Long> positiveExternalDemand() {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        externalDemand.forEach((key, amount) -> {
            if (amount != null && amount > 0) result.put(key, amount);
        });
        return Map.copyOf(result);
    }
}
