package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningStageLogger;

/** One invocation's optional counters; no node logging or state shared with another solve. */
final class AcyclicSolveMetrics {
    final long startedNanos = System.nanoTime();
    int attempts;
    int candidateSwitches;
    int universeBuilds;
    long universeNanos;
    int activeGraphBuilds;
    long activeGraphNanos;
    int prunedGraphBuilds;
    long prunedGraphNanos;
    int sccPasses;
    long sccNanos;
    int condensationBuilds;
    long condensationNanos;
    /** Complete material passes; resumed passes are counted by incrementalRestores. */
    int runOnceCalls;
    long runOnceNanos;
    int incrementalRestores;
    int incrementalFallbacks;
    int checkpointCopies;
    String checkpointBoundary = "NONE";
    String checkpointDecision = "NOT_REQUESTED";
    int checkpointPrefixSteps;
    int checkpointPrefixExpansions;
    long checkpointPrefixInputs;
    long checkpointNanos;
    long prefixCheckNanos;
    long restoreNanos;
    long retainedMaterialVisits;
    long retainedPatternExpansions;
    long retainedInputExpansions;
    long materialVisits;
    long patternExpansions;
    long inputExpansions;
    int graphCacheHits;
    int graphInvalidations;
    long edgeRowsReused;
    long edgeRowsRebuilt;
    long expansionCacheHits;
    long expansionCacheMisses;
    long expansionInvalidations;
    long expansionCacheBypasses;
    String status = "INTERRUPTED";

    void finish() {
        ECOPlanningStageLogger.statistics("acyclic_retries", startedNanos,
            "status=" + status + " attempts=" + attempts + " switches=" + candidateSwitches
                + " universeBuilds=" + universeBuilds + " universeMs=" + universeNanos / 1_000_000.0D
                + " graphBuilds=" + activeGraphBuilds + " graphMs=" + activeGraphNanos / 1_000_000.0D
                + " prunedGraphBuilds=" + prunedGraphBuilds + " prunedGraphMs=" + prunedGraphNanos / 1_000_000.0D
                + " sccPasses=" + sccPasses + " sccMs=" + sccNanos / 1_000_000.0D
                + " condensationBuilds=" + condensationBuilds + " condensationMs=" + condensationNanos / 1_000_000.0D
                + " runOnce=" + runOnceCalls + " runOnceMs=" + runOnceNanos / 1_000_000.0D
                + " incrementalRestores=" + incrementalRestores + " incrementalFallbacks=" + incrementalFallbacks
                + " checkpointCopies=" + checkpointCopies + " checkpointMs=" + checkpointNanos / 1_000_000.0D
                + " checkpointBoundary=" + checkpointBoundary + " checkpointDecision=" + checkpointDecision
                + " checkpointPrefixSteps=" + checkpointPrefixSteps
                + " checkpointPrefixExpansions=" + checkpointPrefixExpansions + " checkpointPrefixInputs=" + checkpointPrefixInputs
                + " prefixCheckMs=" + prefixCheckNanos / 1_000_000.0D + " restoreMs=" + restoreNanos / 1_000_000.0D
                + " retainedMaterialVisits=" + retainedMaterialVisits
                + " retainedPatternExpansions=" + retainedPatternExpansions + " retainedInputExpansions=" + retainedInputExpansions
                + " materialVisits=" + materialVisits + " patternExpansions=" + patternExpansions
                + " inputExpansions=" + inputExpansions
                + " graphCacheHits=" + graphCacheHits + " graphInvalidations=" + graphInvalidations
                + " edgeRowsReused=" + edgeRowsReused + " edgeRowsRebuilt=" + edgeRowsRebuilt
                + " expansionCacheHits=" + expansionCacheHits + " expansionCacheMisses=" + expansionCacheMisses
                + " expansionInvalidations=" + expansionInvalidations + " expansionCacheBypasses=" + expansionCacheBypasses);
    }
}
