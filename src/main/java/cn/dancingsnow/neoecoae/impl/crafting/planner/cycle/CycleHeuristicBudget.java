package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

/** Independent, non-semantic budget for the optional greedy probe. Exhaustion always falls back to exact search. */
final class CycleHeuristicBudget {
    private final int maxCandidateEvaluations;
    private final int maxLookaheadNodes;
    private final int maxMacroSteps;
    private int candidateEvaluations;
    private int lookaheadNodes;
    private int macroSteps;
    private boolean exhausted;

    CycleHeuristicBudget(int maxCandidateEvaluations, int maxLookaheadNodes, int maxMacroSteps) {
        this.maxCandidateEvaluations = maxCandidateEvaluations;
        this.maxLookaheadNodes = maxLookaheadNodes;
        this.maxMacroSteps = maxMacroSteps;
    }

    boolean candidate() { return take(++candidateEvaluations <= maxCandidateEvaluations); }
    boolean lookahead() { return take(++lookaheadNodes <= maxLookaheadNodes); }
    boolean macroStep() { return take(++macroSteps <= maxMacroSteps); }
    int candidateEvaluations() { return candidateEvaluations; }
    int lookaheadNodes() { return lookaheadNodes; }
    int macroSteps() { return macroSteps; }
    boolean exhausted() { return exhausted; }
    void markExhausted() { exhausted = true; }

    private boolean take(boolean allowed) {
        if (!allowed) exhausted = true;
        return allowed;
    }
}
