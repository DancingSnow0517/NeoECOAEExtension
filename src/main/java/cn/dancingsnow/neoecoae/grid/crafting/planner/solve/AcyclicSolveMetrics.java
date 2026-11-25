package cn.dancingsnow.neoecoae.grid.crafting.planner.solve;

import cn.dancingsnow.neoecoae.grid.crafting.planner.control.ECOPlanningStageLogger;

/** One invocation's optional counters; no node logging or state shared with another solve. */
public final class AcyclicSolveMetrics {
    final long startedNanos = System.nanoTime();
    int attempts;
    int candidateSwitches;
    int universeBuilds;
    long universeNanos;
    public int activeGraphBuilds;
    public long activeGraphNanos;
    public int prunedGraphBuilds;
    public long prunedGraphNanos;
    public int sccPasses;
    public long sccNanos;
    public int condensationBuilds;
    public long condensationNanos;
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
    public int graphCacheHits;
    public int graphInvalidations;
    public long edgeRowsReused;
    public long edgeRowsRebuilt;
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
