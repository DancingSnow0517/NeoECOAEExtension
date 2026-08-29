package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
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
 * </ul>
 *
 * <p>{@code requiredSeed} together with {@code externalDemand} is exactly what the witness needs:
 * replaying the witness from {@code requiredSeed}, importing {@code externalDemand} across the
 * boundary, keeps every stock level at or above zero.
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
        diagnostics = List.copyOf(diagnostics);
        if (status == CycleSolveStatus.SUCCESS && !seedShortfall.isEmpty()) {
            throw new IllegalArgumentException("A successful cycle solve cannot report a seed shortfall");
        }
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

    /** Total firings in the witness; equals {@code executionWitness.size()}. */
    public long totalFirings() {
        return patternTimes.values().stream().mapToLong(Long::longValue).sum();
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
