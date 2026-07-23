package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;

/** Selects the linear DAG path before falling back to bounded integer hyperflow search. */
public final class ECOPlanningSolver {
    private ECOPlanningSolver() {
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOSolveBudget budget
    ) {
        return ECODagDemandSolver.trySolve(problem)
            .orElseGet(() -> ECOIntegerHyperflowSolver.solve(problem, budget));
    }
}
