package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;

/** Selects the linear/component path before falling back to bounded integer search. */
public final class ECOPlanningSolver {
    private ECOPlanningSolver() {
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOSolveBudget budget
    ) {
        var dag = ECODagDemandSolver.trySolve(problem);
        if (dag.isPresent()) {
            return dag.get();
        }
        var component = ECOComponentDemandSolver.trySolve(problem);
        if (component.isPresent()
            && component.get().status() != ECOHyperflowResult.Status.NO_ROUTE) {
            return component.get();
        }
        return ECOIntegerHyperflowSolver.solve(problem, budget);
    }
}
