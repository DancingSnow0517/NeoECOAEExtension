package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;

/** Selects the linear/component path before falling back to bounded integer search. */
public final class ECOPlanningSolver {
    private ECOPlanningSolver() {
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOSolveBudget budget
    ) {
        long deadlineNanos = budget.deadlineNanos();
        return solve(problem, budget, deadlineNanos);
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOSolveBudget budget,
        long deadlineNanos
    ) {
        ECOPlanningGraph<K, R> graph = ECOGraphPruner.targetReachable(
            new ECOPlanningGraph<>(problem.operations()),
            problem.requested().keySet()
        );
        return solve(problem, graph, budget, deadlineNanos);
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        ECOSolveBudget budget
    ) {
        return solve(problem, graph, budget, budget.deadlineNanos());
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        ECOSolveBudget budget,
        long deadlineNanos
    ) {
        var dag = ECODagDemandSolver.trySolve(problem, graph);
        if (dag.isPresent() && !ECOSolveBudget.shouldStop(deadlineNanos)) {
            return dag.get();
        }
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return ECOIntegerHyperflowSolver.solve(problem, graph, budget, deadlineNanos);
        }
        var component = ECOComponentDemandSolver.trySolve(problem, graph, deadlineNanos);
        if (component.isPresent()
            && !ECOSolveBudget.shouldStop(deadlineNanos)
            && component.get().status() != ECOHyperflowResult.Status.NO_ROUTE) {
            return component.get();
        }
        return ECOIntegerHyperflowSolver.solve(problem, graph, budget, deadlineNanos);
    }
}
